package de.ipnats.hardwrought.water;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.simulation.SimulationScheduler;
import de.ipnats.hardwrought.core.simulation.SimulationTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The water simulation itself: water is a quantity that moves, and no block creates it.
 *
 * <p>Two rules, applied in that order to every cell that has been disturbed:
 *
 * <pre>
 *   1. fall     — give everything the block below can take
 *   2. rise     - pass anything above a full block on upward, it has nowhere else to go
 *   3. level    — share what is left with the neighbours that have less
 * </pre>
 *
 * <p>Nothing is ever produced or destroyed by either rule, so a lake that drains into a cave ends up
 * in the cave. Water only enters the world where something puts it there: a bucket, the rain, or a
 * spring seeping up out of the water table.
 *
 * <p>Only disturbed water is looked at. Vanilla already schedules a fluid tick on water whenever
 * anything next to it changes, and that scheduled tick is what wakes a cell here, so still water
 * costs nothing at all. The work per pass is capped, which means a very large disturbance is slow
 * rather than expensive — draining an ocean would take an ocean's worth of time, as it should.
 */
public final class WaterFlow {
    /** Cells moved per tick, so this remains a hard ceiling on the simulation work. */
    public static final int BUDGET_PER_PASS = 1024;
    /**
     * Wall-clock guard for one server tick. A cell count alone is not enough because changing a
     * visible block is much more expensive than inspecting a settled one.
    */
    public static final long MAX_NANOS_PER_TICK = 1_500_000L;
    /** Back off before water competes with entities, falling blocks and networking for a 50 ms tick. */
    private static final long CONGESTED_TICK_NANOS = 35_000_000L;
    private static final long OVERLOADED_TICK_NANOS = 45_000_000L;
    private static final long CONGESTED_WATER_NANOS = 500_000L;
    private static final long OVERLOADED_WATER_NANOS = 100_000L;
    public static final int NEAR_PLAYER_RADIUS = 32;
    public static final int MID_PLAYER_RADIUS = 96;
    /**
     * How many disturbed cells are remembered at once. Past this the oldest are dropped: water then
     * settles where the action is instead of the server growing a world-sized queue.
     */
    public static final int MAX_ACTIVE = 32_768;
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    /** How many cells are stepped between readings of the clock. A power of two, so the test is an and. */
    private static final int CLOCK_INTERVAL = 16;

    private final MinecraftServer server;
    private final Map<ResourceKey<Level>, ActiveQueue> active = new LinkedHashMap<>();
    /** Reused every tick so the solver allocates nothing at all in its hot path. */
    private final List<ServerLevel> levels = new ArrayList<>();
    private int nextLevel;
    private long failedCells;
    private int lastProcessedCells;
    private long signals;
    private long worldLookups;
    private long lastWorkNanos;
    private long lastBudgetNanos;

    public WaterFlow(MinecraftServer server, SimulationScheduler scheduler) {
        this.server = server;
        // Hydraulic pressure feels unresponsive when a wave may advance by only one cell every five
        // ticks. Keep the bounded queue and budget, but let an active wave advance every game tick.
        scheduler.register("hardwrought:water_flow", SimulationTier.CRITICAL, this::tickFlow);
    }

    /** Wakes a cell. Called from the fluid tick vanilla already schedules on disturbed water. */
    public static void disturb(ServerLevel level, BlockPos pos) {
        var runtime = CoreLifecycle.find(level.getServer());
        if (runtime == null) return;
        runtime.waterFlow().signal(level, pos);
    }

    /**
     * Advances a cell once right away. Only direct player actions use this path, so a newly poured
     * bucket reacts immediately. Vanilla fluid ticks must use {@link #disturb}; otherwise block
     * updates recursively schedule unbudgeted work outside the normal solver.
     */
    public static void disturbImmediately(ServerLevel level, BlockPos pos) {
        var runtime = CoreLifecycle.find(level.getServer());
        if (runtime == null) return;
        WaterFlow flow = runtime.waterFlow();
        flow.stepSafely(level, pos);
    }

    public void activate(ServerLevel level, BlockPos pos) {
        // Only water can move. Avoid the much more expensive six-neighbour equilibrium scan here:
        // transfers signal the same cells repeatedly, and the queue can deduplicate a cheap signal
        // before the bounded solver examines each surviving cell once.
        //
        // A transfer wakes eleven neighbours and most of them are already queued, so the queue is
        // asked first. A hash lookup on a packed long is far cheaper than a chunk and section lookup
        // for a block state, and the solver re-checks for water when it reaches the cell anyway.
        ActiveQueue queue = active.computeIfAbsent(level.dimension(), key -> new ActiveQueue());
        signals++;
        if (queue.contains(pos)) return;
        worldLookups++;
        // One chunk lookup, not two. Asking whether the chunk is loaded and then asking the level
        // for the block state looks the chunk up twice; taking the chunk once and reading the state
        // off it does the same work with half the map lookups, and returns null rather than loading
        // a chunk the solver has no business loading.
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !WaterStorage.containsWater(chunk.getBlockState(pos))) return;
        queue.activate(level, pos);
    }

    /**
     * Cheap wake-up path for vanilla's scheduled fluid ticks. Their only job is to remember the
     * cell; the bounded solver decides later whether it really needs work. In particular this avoids
     * scanning six neighbours for every overdue fluid tick after loading a busy chunk.
     */
    private void signal(ServerLevel level, BlockPos pos) {
        activate(level, pos);
    }

    public int activeCells() {
        return active.values().stream().mapToInt(ActiveQueue::size).sum();
    }

    public long failedCells() {
        return failedCells;
    }

    public int lastProcessedCells() {
        return lastProcessedCells;
    }

    public long lastWorkNanos() {
        return lastWorkNanos;
    }

    public long lastBudgetNanos() {
        return lastBudgetNanos;
    }

    /** How many times a cell has been woken since the server started. */
    public long signals() {
        return signals;
    }

    /**
     * How many of those wake-ups had to look at the world. The rest were already queued and cost a
     * hash lookup on a packed position instead of a chunk lookup, a section lookup and a block state.
     */
    public long worldLookups() {
        return worldLookups;
    }

    private void tickFlow() {
        int budget = BUDGET_PER_PASS;
        long started = System.nanoTime();
        long timeBudget = timeBudget();
        int processed = 0;
        levels.clear();
        server.getAllLevels().forEach(levels::add);
        if (levels.isEmpty()) {
            finishTick(started, timeBudget, processed);
            return;
        }

        int startLevel = Math.floorMod(nextLevel, levels.size());
        for (int offset = 0; offset < levels.size(); offset++) {
            int levelIndex = (startLevel + offset) % levels.size();
            ServerLevel level = levels.get(levelIndex);
            ActiveQueue queue = active.get(level.dimension());
            if (queue == null || queue.isEmpty()) continue;

            // Moving water writes newly disturbed neighbours back into this queue. Polling a single
            // primitive position avoids allocating and boxing a new batch on every wave.
            while (!queue.isEmpty() && budget > 0) {
                stepSafely(level, BlockPos.of(queue.poll()));
                budget--;
                processed++;
                // Reading the clock costs about as much as inspecting a settled cell, so it is read
                // once a batch rather than once a cell. Overshooting the time budget by fifteen
                // cells is cheaper than asking the clock fifteen more times.
                if (budget <= 0 || (processed & (CLOCK_INTERVAL - 1)) == 0
                        && System.nanoTime() - started >= timeBudget) {
                    nextLevel = (levelIndex + 1) % levels.size();
                    finishTick(started, timeBudget, processed);
                    return;
                }
            }
        }
        nextLevel = (startLevel + 1) % levels.size();
        finishTick(started, timeBudget, processed);
    }

    private void finishTick(long started, long budget, int processed) {
        lastProcessedCells = processed;
        lastWorkNanos = Math.max(0, System.nanoTime() - started);
        lastBudgetNanos = budget;
    }

    private long timeBudget() {
        long averageTick = server.getAverageTickTimeNanos();
        if (averageTick >= OVERLOADED_TICK_NANOS) return OVERLOADED_WATER_NANOS;
        if (averageTick >= CONGESTED_TICK_NANOS) return CONGESTED_WATER_NANOS;
        return MAX_NANOS_PER_TICK;
    }

    /** A malformed cell must never disable water for the entire server session. */
    private void stepSafely(ServerLevel level, BlockPos pos) {
        try {
            step(level, pos);
        } catch (RuntimeException exception) {
            failedCells++;
            // Full traces for the first few failures diagnose the cause. Afterwards powers of two
            // provide evidence that it continues without flooding the log or stealing server time.
            if (failedCells <= 3 || (failedCells & (failedCells - 1)) == 0) {
                Hardwrought.LOGGER.error("Water cell {} in {} failed (failure #{}) and was skipped",
                        pos.toShortString(), level.dimension().identifier(), failedCells, exception);
            }
        }
    }

    /** One cell, one pass. Everything it changes is woken up again for the next pass. */
    public void step(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return;
        int amount = WaterStorage.amount(level, pos);
        if (amount <= 0) return;

        amount = fall(level, pos, amount);
        if (amount <= 0) return;
        amount = rise(level, pos, amount);
        if (amount <= 0) return;
        if (amount >= WaterAmounts.SPREAD_THRESHOLD) level(level, pos, amount);
    }

    /**
     * Rule two: water under pressure rises. A cell carries only what belongs at its depth and presses
     * the rest into the cell above, which presses on in turn. That is what lets water enter a tank
     * from underneath and come out at the top, what stops a bucket poured into a full block from
     * vanishing, and what makes two connected shafts come to rest at the same height.
     */
    private int rise(ServerLevel level, BlockPos pos, int amount) {
        BlockPos above = pos.above();
        if (!WaterStorage.canHold(level, above)) return amount;
        int overhead = WaterStorage.amount(level, above);
        // Keep only what a cell at this depth should carry; the rest is pressed upward.
        int move = amount - WaterAmounts.stableState(amount + overhead);
        if (move <= 0) return amount;
        return transfer(level, pos, amount, above, overhead,
                Math.min(move, WaterAmounts.MAX_RISE_PER_PASS));
    }

    /**
     * Rule one: water falls, and it keeps pressing after the block below is full. The cell below
     * settles at a little over a full block, and that little over is the weight of everything
     * standing on top of it.
     */
    private int fall(ServerLevel level, BlockPos pos, int amount) {
        BlockPos below = pos.below();
        if (!WaterStorage.canHold(level, below)) return amount;
        int beneath = WaterStorage.amount(level, below);
        int move = Math.min(amount, WaterAmounts.stableState(amount + beneath) - beneath);
        if (move <= 0) return amount;
        return transfer(level, pos, amount, below, beneath,
                Math.min(move, WaterAmounts.MAX_FALL_PER_PASS));
    }

    /** Rule three: water levels out. Each neighbour with less gets a share of the difference. */
    private void level(ServerLevel level, BlockPos pos, int amount) {
        int mine = amount;
        for (Direction side : SIDES) {
            BlockPos next = pos.relative(side);
            if (!WaterStorage.canHold(level, next)) continue;
            int theirs = WaterStorage.amount(level, next);
            int difference = mine - theirs;
            if (difference < WaterAmounts.LEVELLING_THRESHOLD) continue;
            // Half the difference is the exact equilibrium of this pair. Rounding up is safe: an
            // odd difference can only reverse the pair by one millibucket, far below the threshold.
            int move = Math.min(WaterAmounts.MAX_SPREAD_PER_PASS, Math.min((difference + 1) / 2,
                    Math.min(mine, WaterAmounts.pressureRoom(theirs))));
            if (move <= 0) continue;
            mine = transfer(level, pos, mine, next, theirs, move);
        }
    }

    /** Commits a transfer using the quantities the caller has already read. */
    private int transfer(ServerLevel level, BlockPos from, int source,
                         BlockPos to, int destination, int millibuckets) {
        int move = Math.min(millibuckets, Math.min(source, WaterAmounts.pressureRoom(destination)));
        if (move <= 0) return source;
        // Water takes what is dissolved in it with it. Ordinary water moving into ordinary water
        // costs two map lookups here and nothing else; only marked water is mixed.
        WaterQualityStorage.carry(level, from, source, to, destination, move);
        WaterStorage.setAmounts(level, from, source - move, to, destination + move);
        activate(level, from);
        activate(level, to);
        activate(level, from.above());
        activate(level, to.above());
        // Wake both sides of the transfer. This lets the interior of a lake feed a draining edge;
        // waking only around the destination made the first shoreline cell empty and then stall.
        for (Direction side : SIDES) {
            activate(level, from.relative(side));
            activate(level, to.relative(side));
        }
        activate(level, to.below());
        return source - move;
    }

    public void shutdown() {
        active.clear();
    }

    /** Three insertion-ordered queues avoid sorting a world-sized backlog every server tick. */
    private static final class ActiveQueue {
        private final LongLinkedOpenHashSet near = new LongLinkedOpenHashSet();
        private final LongLinkedOpenHashSet mid = new LongLinkedOpenHashSet();
        private final LongLinkedOpenHashSet far = new LongLinkedOpenHashSet();
        private int priorityTurn;
        /**
         * Where the players stood when this tick began, as flat x/y/z triples.
         *
         * <p>One transfer wakes eleven cells and the solver makes up to a thousand of them a tick,
         * so working out how near a cell is to a player used to walk the player list tens of
         * thousands of times a tick. The list cannot move within a tick, so it is read once.
         */
        private double[] players = new double[0];
        private int playerCount;
        private long playersTick = Long.MIN_VALUE;

        int size() {
            return near.size() + mid.size() + far.size();
        }

        boolean isEmpty() {
            return near.isEmpty() && mid.isEmpty() && far.isEmpty();
        }

        boolean contains(BlockPos pos) {
            long packed = pos.asLong();
            return near.contains(packed) || mid.contains(packed) || far.contains(packed);
        }

        void activate(ServerLevel level, BlockPos pos) {
            long packed = pos.asLong();
            if (near.contains(packed)) return;
            refreshPlayers(level);
            int priority = priority(pos);
            if (mid.contains(packed)) {
                if (priority == 0) {
                    mid.remove(packed);
                    near.add(packed);
                }
                return;
            }
            if (far.contains(packed)) {
                if (priority < 2) {
                    far.remove(packed);
                    bucket(priority).add(packed);
                }
                return;
            }
            if (size() >= MAX_ACTIVE && !makeRoomFor(priority)) return;
            bucket(priority).add(packed);
        }

        long poll() {
            // Eight near, two middle-distance and one far slot. Empty bands fall through to the
            // closest available work, so proximity stays dominant without freezing the far side of
            // a large connected lake forever.
            int slot = Math.floorMod(priorityTurn++, 11);
            if (slot < 8 && !near.isEmpty()) return near.removeFirstLong();
            if (slot < 10 && !mid.isEmpty()) return mid.removeFirstLong();
            if (slot == 10 && !far.isEmpty()) return far.removeFirstLong();
            if (!near.isEmpty()) return near.removeFirstLong();
            if (!mid.isEmpty()) return mid.removeFirstLong();
            return far.removeFirstLong();
        }

        private boolean makeRoomFor(int priority) {
            if (priority == 0) {
                if (!far.isEmpty()) far.removeFirstLong();
                else if (!mid.isEmpty()) mid.removeFirstLong();
                else return false;
                return true;
            }
            if (priority == 1 && !far.isEmpty()) {
                far.removeFirstLong();
                return true;
            }
            return false;
        }

        private LongLinkedOpenHashSet bucket(int priority) {
            return priority == 0 ? near : priority == 1 ? mid : far;
        }

        /** Reads the player positions once a tick; they cannot move within one. */
        private void refreshPlayers(ServerLevel level) {
            long now = level.getGameTime();
            if (now == playersTick) return;
            playersTick = now;
            var present = level.players();
            playerCount = present.size();
            if (players.length < playerCount * 3) players = new double[playerCount * 3];
            for (int index = 0; index < playerCount; index++) {
                var player = present.get(index);
                players[index * 3] = player.getX();
                players[index * 3 + 1] = player.getY();
                players[index * 3 + 2] = player.getZ();
            }
        }

        private int priority(BlockPos pos) {
            if (playerCount == 0) return 2;
            double nearest = Double.MAX_VALUE;
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;
            for (int index = 0; index < playerCount; index++) {
                double dx = players[index * 3] - x;
                double dy = players[index * 3 + 1] - y;
                double dz = players[index * 3 + 2] - z;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance < nearest) nearest = distance;
            }
            if (nearest <= NEAR_PLAYER_RADIUS * NEAR_PLAYER_RADIUS) return 0;
            if (nearest <= MID_PLAYER_RADIUS * MID_PLAYER_RADIUS) return 1;
            return 2;
        }
    }
}
