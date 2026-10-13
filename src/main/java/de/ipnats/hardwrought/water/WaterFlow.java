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
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The water simulation itself: water is a quantity that moves, and no block creates it.
 *
 * <p>Six rules, applied in that order to every cell that has been disturbed:
 *
 * <pre>
 *   1. fall     — give everything the block below can take; a full cell under full water stays
 *   2. rise     — pass anything above a full block on upward, it has nowhere else to go
 *   3. draw     — room under full water is filled from the surface of the body, as one sheet
 *   4. spill    — a surface standing higher than the surface beside it gives the difference
 *                 away as one sheet: the cells one lower first, then one level at this height
 *   5. level    — share what is left with the neighbours that have less, a pair at a time
 *   6. press    — only if none of those moved anything: look through the full water this cell
 *                 is joined to for a place its water belongs, and move it there
 * </pre>
 *
 * <p>Rules three and four are what make a sea drainable. Cell by cell, a hole in the bed emptied
 * one column at a time and left a dent in the surface, and the dents then had to find each other
 * across the whole sea by levelling a pair at a time; draining a pond of a few thousand blocks took
 * twenty million cell steps. Drawn and spilled as sheets, the same pond takes three hundred
 * thousand, and the surface sinks as one surface, which is also what it looks like.
 *
 * <p>Nothing is ever produced or destroyed by any rule, so a lake that drains into a cave ends up
 * in the cave. Water only enters the world where something puts it there: a bucket, the rain, or a
 * spring seeping up out of the water table. The one exception is a trace: a film thinner than a
 * quarter of a visible step, on solid ground, that nothing feeds and that cannot run anywhere,
 * soaks into the ground of its region after ten seconds, or dries where the ground is saturated.
 *
 * <p>Only disturbed water is looked at. Vanilla already schedules a fluid tick on water whenever
 * anything next to it changes, and that scheduled tick is what wakes a cell here, so still water
 * costs nothing at all. The work per pass is capped by the time the server has to spare, which
 * means a very large disturbance is slow rather than expensive.
 */
public final class WaterFlow {
    /** Cells moved per tick, so this remains a hard ceiling on the simulation work. */
    public static final int BUDGET_PER_PASS = 4_096;
    /**
     * The most wall-clock time one server tick gives to water. A cell count alone is not enough
     * because changing a visible block is much more expensive than inspecting a settled one.
     *
     * <p>What a tick actually gives is a share of what the rest of the server leaves over, see
     * {@link #timeBudget}: this much when the server is idle, and down to a tenth of a millisecond
     * as the rest of the tick fills up, so a heavier world cannot be dragged under by water. The
     * old fixed budget of five milliseconds starved a draining sea beside an idle server, and the
     * congestion steps it had were a cliff where this is a slope.
     */
    public static final long MAX_NANOS_PER_TICK = 8_000_000L;
    /** The least a tick gives water, so that it never stops entirely. */
    private static final long MIN_NANOS_PER_TICK = 100_000L;
    /** What the rest of the server may take of a tick before water gets only its minimum. */
    private static final long HEADROOM_TICK_NANOS = 35_000_000L;
    /** How much of the headroom left under that water may take: a quarter. */
    private static final int HEADROOM_SHARE = 4;
    public static final int NEAR_PLAYER_RADIUS = 32;
    public static final int MID_PLAYER_RADIUS = 96;
    /**
     * How many disturbed cells are remembered at once. Past this the oldest are dropped: water then
     * settles where the action is instead of the server growing a world-sized queue. Sheets keep
     * the queue small — a pond of three thousand blocks drains with under a thousand cells waiting —
     * so this is a ceiling for a whole ocean front, not something ordinary play reaches.
     */
    public static final int MAX_ACTIVE = 131_072;
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final Direction[] ALL = Direction.values();
    /** How many cells are stepped between readings of the clock. A power of two, so the test is an and. */
    private static final int CLOCK_INTERVAL = 16;
    /**
     * How many full cells one pressure search may pass through. A pipe five hundred blocks long, or a
     * tank eight by eight by eight; past that the two ends are too far apart to answer for each other in
     * one step, and the search gives up rather than walk a lake.
     */
    public static final int PRESSURE_REACH = 512;
    /** How far up a column a pressure search climbs to take water from its top. */
    private static final int PRESSURE_CLIMB = 64;
    /**
     * How many cells of a surface sheet one draw may walk to find the highest water on it. A disk
     * about eighteen blocks across: a drained bed lowers the sea in a dish that wide at a time.
     */
    public static final int SHEET_REACH = 1_024;
    /** How many cells of a sheet one draw or spill writes at least, when there are that many; the rest wait for the next. */
    private static final int SHEET_WINDOW = 128;
    /** The most one sheet write takes from or gives to a cell: under the levelling threshold, so the cells beside it do not level into the difference. */
    private static final int SIP = WaterAmounts.LEVELLING_THRESHOLD - 2;
    /** How many passes a cell is spared the sheet walk after one that moved nothing. */
    private static final int FUTILE_PASSES = 100;
    /** How long a trace of water lies on the ground before it soaks in: ten seconds. */
    private static final int TRACE_PASSES = 200;
    /** Under this a film is a trace: a quarter of the smallest step that can be drawn. */
    private static final int TRACE_THRESHOLD = WaterAmounts.DISPLAY_STEP / 4;
    /** How often one walk is used again before the surface is walked afresh. */
    private static final int SHEET_REUSE = 16;

    /** Reused by every pressure search, so looking costs no allocation. */
    private final LongArrayFIFOQueue frontier = new LongArrayFIFOQueue();
    private final LongOpenHashSet reached = new LongOpenHashSet();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos beside = new BlockPos.MutableBlockPos();
    /** The last walk a draw made and the last walk a spill made, each kept for its own next use. */
    private final Sheet drawSheet = new Sheet();
    private final Sheet spillSheet = new Sheet();
    private final IntArrayList sources = new IntArrayList();
    private final IntArrayList sinks = new IntArrayList();
    private final IntArrayList sourceShares = new IntArrayList();
    private final IntArrayList sinkShares = new IntArrayList();
    /** What the four sides of the cell being stepped hold, read once by scanSides for level. */
    private final int[] sideAmounts = new int[4];
    private final int[] sideUnder = new int[4];
    private boolean sidesScanned;
    private final IntArrayList scratch = new IntArrayList();
    private final IntArrayList scratchCopy = new IntArrayList();
    private int windowOffset;
    private int passesSinceForgetting;
    private int passesSinceSoaking;
    private final LongArrayList scratchTraces = new LongArrayList();
    private long seeped;
    private long dried;
    private long voided;
    /** The most one cell gives or takes in the sheet write being shared out. */
    private int shareLimit = Integer.MAX_VALUE;
    /** Whether the cell last levelled stands in open water: water on all four sides of it. */
    private boolean openWater;
    /** Set by a pressure search, so the tick loop reads the clock after one instead of after sixteen. */
    private boolean searched;
    private long pressureSearches;
    private long droppedCells;

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
        WaterFlow flow = runtime.waterFlow();
        if (flow.activate(level, pos)) return;
        // Nothing here to wake: the cell was emptied, by a bucket or the sun. What has to move
        // now is the water beside the hole. Vanilla would get round to telling it five ticks
        // later, and only where the chunk happens to be ticking.
        for (Direction direction : ALL) flow.activate(level, pos.relative(direction));
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

    public boolean activate(ServerLevel level, BlockPos pos) {
        // Only water can move. Avoid the much more expensive six-neighbour equilibrium scan here:
        // transfers signal the same cells repeatedly, and the queue can deduplicate a cheap signal
        // before the bounded solver examines each surviving cell once.
        //
        // A transfer wakes eleven neighbours and most of them are already queued, so the queue is
        // asked first. A hash lookup on a packed long is far cheaper than a chunk and section lookup
        // for a block state, and the solver re-checks for water when it reaches the cell anyway.
        ActiveQueue queue = queueFor(level);
        signals++;
        if (queue.contains(pos)) return true;
        worldLookups++;
        // One chunk lookup, not two. Asking whether the chunk is loaded and then asking the level
        // for the block state looks the chunk up twice; taking the chunk once and reading the state
        // off it does the same work with half the map lookups, and returns null rather than loading
        // a chunk the solver has no business loading.
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !WaterStorage.containsWater(chunk.getBlockState(pos))) return false;
        queue.activate(level, pos);
        return true;
    }

    /** A trace under a quarter of a visible step, on solid ground, with no neighbour holding more to feed it. */
    private boolean isStrandedTrace(ServerLevel level, BlockPos pos, int amount) {
        if (amount <= 0 || amount >= TRACE_THRESHOLD) return false;
        if (WaterStorage.probe(level, pos.below()) >= 0) return false;
        for (Direction side : SIDES) {
            if (WaterStorage.probe(level, pos.relative(side)) > amount) return false;
        }
        return true;
    }

    /**
     * Sweeps the traces remembered since the last sweep. A trace found stranded at two sweeps
     * running has lain still for ten seconds at least: it soaks into the ground of its region where
     * the ground has room, and dries up where it has not. Anything still fed, or still a film
     * thicker than a trace, is kept and looked at again, because nothing else will step it once
     * its feeder has run dry.
     */
    private void dryTraces() {
        for (Map.Entry<ResourceKey<Level>, ActiveQueue> entry : active.entrySet()) {
            ActiveQueue queue = entry.getValue();
            if (queue.traces.isEmpty() && queue.stranded.isEmpty()) continue;
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                queue.traces.clear();
                queue.stranded.clear();
                continue;
            }
            for (long packed : queue.stranded) {
                BlockPos pos = BlockPos.of(packed);
                int amount = WaterStorage.probe(level, pos);
                if (!isStrandedTrace(level, pos, amount)) {
                    if (amount > 0 && amount < WaterAmounts.SPREAD_THRESHOLD) queue.traces.add(packed);
                    continue;
                }
                int soaked = Groundwater.soakAt(level, pos, amount);
                WaterStorage.setAmount(level, pos, 0);
                seeped += soaked;
                dried += amount - soaked;
            }
            queue.stranded.clear();
            scratchTraces.clear();
            for (long packed : queue.traces) {
                BlockPos pos = BlockPos.of(packed);
                int amount = WaterStorage.probe(level, pos);
                if (amount <= 0 || amount >= WaterAmounts.SPREAD_THRESHOLD) continue;
                if (isStrandedTrace(level, pos, amount)) queue.stranded.add(packed);
                else scratchTraces.add(packed);
            }
            queue.traces.clear();
            queue.traces.addAll(scratchTraces);
        }
    }

    /** Millibuckets of stranded traces that have soaked into the ground since the server started. */
    public long seeped() {
        return seeped;
    }

    /** Millibuckets of stranded traces that have dried up on saturated ground since the server started. */
    public long dried() {
        return dried;
    }

    /** Millibuckets that have fallen out of the bottom of the world since the server started. */
    public long voided() {
        return voided;
    }

    private ActiveQueue queueFor(ServerLevel level) {
        return active.computeIfAbsent(level.dimension(), key -> new ActiveQueue());
    }

    /**
     * Cells whose sheet walk found nothing get one more chance: a lip may have opened beside them
     * since, or the sea may have come down to them. Waking them is what guarantees that water never
     * stops for good short of level.
     */
    private void forgetFutility() {
        for (Map.Entry<ResourceKey<Level>, ActiveQueue> entry : active.entrySet()) {
            ActiveQueue queue = entry.getValue();
            if (queue.futile.isEmpty() && queue.pressed.isEmpty()) continue;
            ServerLevel level = server.getLevel(entry.getKey());
            if (level != null) {
                for (long packed : queue.futile) activate(level, BlockPos.of(packed));
                for (long packed : queue.pressed) activate(level, BlockPos.of(packed));
            }
            queue.futile.clear();
            queue.pressed.clear();
        }
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

    /** How many times a stalled cell has gone looking through full water since the server started. */
    public long pressureSearches() {
        return pressureSearches;
    }

    /** How many disturbed cells the queue has had to forget because it was full. */
    public long droppedCells() {
        return droppedCells + active.values().stream().mapToLong(queue -> queue.dropped).sum();
    }

    private void tickFlow() {
        runPass(timeBudget(), BUDGET_PER_PASS);
    }

    /**
     * One pass of the solver with the given limits. The scheduler calls it once a tick; tests call
     * it directly to run a long drain in one tick, with the queue and its order exactly as in play.
     *
     * @return how many cells were stepped
     */
    public int runPass(long timeBudget, int cellBudget) {
        int budget = cellBudget;
        if (++passesSinceForgetting >= FUTILE_PASSES) {
            passesSinceForgetting = 0;
            forgetFutility();
        }
        if (++passesSinceSoaking >= TRACE_PASSES) {
            passesSinceSoaking = 0;
            dryTraces();
        }
        long started = System.nanoTime();
        int processed = 0;
        levels.clear();
        server.getAllLevels().forEach(levels::add);
        if (levels.isEmpty()) {
            finishTick(started, timeBudget, processed);
            return processed;
        }

        int startLevel = Math.floorMod(nextLevel, levels.size());
        for (int offset = 0; offset < levels.size(); offset++) {
            int levelIndex = (startLevel + offset) % levels.size();
            ServerLevel level = levels.get(levelIndex);
            ActiveQueue queue = active.get(level.dimension());
            if (queue == null || queue.isEmpty()) continue;
            queue.beginPass();

            // Moving water writes newly disturbed neighbours back into this queue. Polling a single
            // primitive position avoids allocating and boxing a new batch on every wave.
            while (queue.hasDue() && budget > 0) {
                stepSafely(level, BlockPos.of(queue.poll()));
                budget--;
                processed++;
                // Reading the clock costs about as much as inspecting a settled cell, so it is read
                // once a batch rather than once a cell. Overshooting the time budget by fifteen
                // cells is cheaper than asking the clock fifteen more times.
                // A pressure search is worth hundreds of ordinary cells, so one is followed by a
                // look at the clock straight away.
                boolean due = searched || (processed & (CLOCK_INTERVAL - 1)) == 0;
                searched = false;
                if (budget <= 0 || due && System.nanoTime() - started >= timeBudget) {
                    nextLevel = (levelIndex + 1) % levels.size();
                    finishTick(started, timeBudget, processed);
                    return processed;
                }
            }
        }
        nextLevel = (startLevel + 1) % levels.size();
        finishTick(started, timeBudget, processed);
        return processed;
    }

    private void finishTick(long started, long budget, int processed) {
        lastProcessedCells = processed;
        lastWorkNanos = Math.max(0, System.nanoTime() - started);
        lastBudgetNanos = budget;
    }

    /**
     * A quarter of what the rest of the server leaves of a tick, between the minimum and the most.
     * The average tick includes the water itself, so what water took last tick is taken back out
     * before asking how busy the rest is; otherwise a busy drain would read as a busy server and
     * throttle itself.
     */
    private long timeBudget() {
        long rest = server.getAverageTickTimeNanos() - lastWorkNanos;
        long headroom = (HEADROOM_TICK_NANOS - rest) / HEADROOM_SHARE;
        return Math.max(MIN_NANOS_PER_TICK, Math.min(MAX_NANOS_PER_TICK, headroom));
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
        // A cell whose block state disagrees with what it holds looks full while the simulation
        // sees a trickle, and every symptom of that reads as water being stuck. Correcting it here
        // costs a state read on a cell already being worked on, and heals worlds that have one.
        WaterStorage.healDisplay(level, pos, amount);
        sidesScanned = false;

        int held = fall(level, pos, amount);
        if (held > 0) held = rise(level, pos, held);
        // Room under full water is the one thing standing water never has. Whatever opened it — a
        // hole in the bed, a bucket, a cell that just emptied itself into the hole beneath — the
        // body refills it from its own surface, which is where the level visibly drops.
        BlockPos above = pos.above();
        int overhead = WaterStorage.probe(level, above);
        if (held < WaterAmounts.BLOCK && overhead >= WaterAmounts.BLOCK) {
            drawFromSurface(level, pos, held, above);
            return;
        }
        if (held <= 0) return;
        // A surface standing higher than the surface beside it settles as a sheet, not cell by
        // cell: see spillOverSheet for why.
        if (overhead <= 0 && scanSides(level, pos, held) && spillOverSheet(level, pos, held)) return;
        held = level(level, pos, held);
        // Pressure is the last resort of water that could not move by itself. A cell that gave
        // something away is still on its way somewhere and will be back; a cell with water on all
        // four sides is part of a surface, and a surface settles by levelling.
        // A search that found nothing is not repeated until something has changed around the cell:
        // every cell along a shore or a tank wall was otherwise searching the whole body again each
        // time a neighbour so much as stirred, and that was most of what a draining sea cost.
        if (held == amount && !openWater && !queueFor(level).pressed.contains(pos.asLong())
                && !press(level, pos, held)) {
            queueFor(level).pressed.add(pos.asLong());
        }
        // A trace on solid ground that nothing feeds and that cannot run anywhere does what a trace
        // does: it soaks in. Not now, though. It is remembered, and if it is still the same trace
        // when the memory is swept it goes into the ground, so that water on its way somewhere is
        // never taken for a trace.
        if (held == amount && held < WaterAmounts.SPREAD_THRESHOLD) queueFor(level).traces.add(pos.asLong());
    }

    /**
     * Rule two: water that has been pressed into a cell rises out of it. A cell carries only what
     * belongs at its depth and presses the rest into the cell above, which presses on in turn. That
     * is what lets water enter a tank from underneath and come out at the top, and what stops a
     * bucket poured into a full block from vanishing.
     */
    private int rise(ServerLevel level, BlockPos pos, int amount) {
        BlockPos above = pos.above();
        int overhead = WaterStorage.probe(level, above);
        if (overhead < 0) return amount;
        // Keep only what a cell at this depth should carry; the rest is pressed upward.
        int move = amount - WaterAmounts.stableState(amount + overhead);
        if (move <= 0) return amount;
        return transfer(level, pos, amount, above, overhead,
                Math.min(move, WaterAmounts.MAX_RISE_PER_PASS));
    }

    /**
     * Rule one: water falls. It fills the cell below and then stands on it; only water that is
     * itself under pressure presses on into a cell that is already full.
     *
     * <p>A full cell with full water standing on it does not fall. It is held where it is by the
     * weight above, and room that opens under it is filled from the surface of the body instead
     * (see {@link #drawFromSurface}). Letting it fall was how a drained bed emptied an ocean: one
     * cell at a time down every column, each collapse leaving a dent one cell wide in the surface,
     * and the dents then had to find each other across the whole sea by levelling.
     */
    private int fall(ServerLevel level, BlockPos pos, int amount) {
        BlockPos below = pos.below();
        if (below.getY() < level.getMinY()) return fallIntoVoid(level, pos, amount);
        int beneath = WaterStorage.probe(level, below);
        if (beneath < 0) return amount;
        int move = Math.min(amount, WaterAmounts.stableState(amount + beneath) - beneath);
        if (move <= 0) return amount;
        if (amount >= WaterAmounts.BLOCK && beneath < WaterAmounts.BLOCK
                && WaterStorage.probe(level, pos.above()) >= WaterAmounts.BLOCK) {
            // Room under a standing column. Fill it from the top, on the room's behalf: the cell
            // below may be air, and air is never stepped on its own.
            drawFromSurface(level, below, beneath, pos);
            return amount;
        }
        return transfer(level, pos, amount, below, beneath,
                Math.min(move, WaterAmounts.MAX_FALL_PER_PASS));
    }

    /**
     * Water at the very bottom of the world with nothing under it falls out of the world, at the
     * rate it falls anywhere else. This is the one place water is destroyed on purpose: a sea with a
     * hole through to the void drains away for good, and the count of what went is kept.
     */
    private int fallIntoVoid(ServerLevel level, BlockPos pos, int amount) {
        int lost = Math.min(amount, WaterAmounts.MAX_FALL_PER_PASS);
        WaterStorage.setAmount(level, pos, amount - lost);
        voided += lost;
        // What stands on it and beside it follows; the cell itself goes on falling while it holds any.
        activate(level, pos);
        activate(level, pos.above());
        for (Direction side : SIDES) activate(level, pos.relative(side));
        return amount - lost;
    }

    /**
     * Reads the four sides of a surface cell once for both {@link #spillOverSheet} and
     * {@link #level}, and says whether the surface beside it stands lower: a neighbouring column
     * whose top is one below this one, or one at the same height holding noticeably less.
     */
    private boolean scanSides(ServerLevel level, BlockPos pos, int amount) {
        boolean lower = false;
        for (int index = 0; index < 4; index++) {
            beside.setWithOffset(pos, SIDES[index]);
            int theirs = WaterStorage.probe(level, beside);
            sideAmounts[index] = theirs;
            if (theirs == 0) {
                int under = WaterStorage.probe(level, beside.getX(), beside.getY() - 1, beside.getZ());
                sideUnder[index] = under;
                // Water one down, or a drop: either way the surface beside this one is lower. Dry
                // floor beside a cell too thin to wet it is the front standing still: the sheet
                // behind it is asked whether it has more to give, which is what moves a front on
                // when the water is there but spread too evenly to cross the threshold anywhere.
                if (under >= 0 || amount < WaterAmounts.SPREAD_THRESHOLD) lower = true;
            } else {
                sideUnder[index] = WaterStorage.WALL;
                if (theirs > 0 && amount - theirs >= WaterAmounts.PRESSURE_THRESHOLD) lower = true;
            }
        }
        sidesScanned = true;
        return lower;
    }

    /**
     * Rule three: water levels out. Each neighbour with less gets a share of the difference.
     *
     * <p>The spread threshold applies to <em>wetting new ground</em> and to nothing else. That
     * distinction is the whole of this rule and it was missing: the threshold used to gate the
     * entire call, so a cell holding less than an eighth of a block would not level with anything,
     * including the cell beside it holding four times as much. Water therefore spread until every
     * cell it had reached was under the threshold and then stopped dead, uneven, wherever it
     * happened to be — a dug pit came to rest as a wedge and stayed one.
     *
     * <p>Keeping the threshold on dry neighbours keeps the behaviour it was written for: a film
     * does not creep on across a floor forever, and no cell is drawn as an eighth of a block that
     * is not really there. Two cells that both already hold water have neither problem, and
     * {@link WaterAmounts#LEVELLING_THRESHOLD} is what stops them jittering.
     *
     * <p>The two cases also move at different rates. Wetting dry ground is the front a player
     * watches and stays slow; water meeting water is the body settling behind it and is quick.
     */
    private int level(ServerLevel level, BlockPos pos, int amount) {
        int mine = amount;
        boolean surrounded = true;
        boolean scanned = sidesScanned;
        sidesScanned = false;
        for (int index = 0; index < 4; index++) {
            BlockPos next = pos.relative(SIDES[index]);
            int theirs = scanned ? sideAmounts[index] : WaterStorage.probe(level, next);
            if (theirs <= 0) surrounded = false;
            if (theirs < 0) continue;
            // A film may settle with water it can reach, but may not go wetting dry ground — unless
            // that ground is the lip of a drop, where it will not lie as a film but run off.
            if (theirs <= 0 && mine < WaterAmounts.SPREAD_THRESHOLD) {
                int under = scanned ? sideUnder[index] : WaterStorage.probe(level, next.below());
                if (under < 0 || under >= WaterAmounts.BLOCK) continue;
            }
            int difference = mine - theirs;
            // Two cells of a body settle locally only once they visibly disagree; anything finer is
            // the sheet's to level, and levelling it a pair at a time was a cascade across the body
            // for every sip a draw took. A film or a front is finer work and keeps the fine threshold.
            int threshold = theirs >= WaterAmounts.SPREAD_THRESHOLD ? WaterAmounts.PRESSURE_THRESHOLD : WaterAmounts.LEVELLING_THRESHOLD;
            if (difference < threshold) continue;
            // Water meeting water is quick; the front — dry ground, or a cell only just wetted that
            // has not yet gathered a visible step — advances at the pace a player can watch.
            int rate = theirs >= WaterAmounts.SPREAD_THRESHOLD ? WaterAmounts.MAX_LEVEL_PER_PASS : WaterAmounts.MAX_SPREAD_PER_PASS;
            // Half the difference is the exact equilibrium of this pair. Rounding down is what
            // keeps a pair from trading one millibucket back and forth for ever.
            int move = Math.min(rate, Math.min(difference / 2,
                    Math.min(mine, WaterAmounts.pressureRoom(theirs))));
            if (move <= 0) continue;
            mine = transfer(level, pos, mine, next, theirs, move);
        }
        openWater = surrounded;
        return mine;
    }

    // ---------------------------------------------------------------- sheets

    /**
     * One walk over a surface and what it found, kept so that the next draw or spill from the same
     * place can use it again. A hole in a sea bed asks for the same walk thousands of times over,
     * and a block landing on a pond asks for the same walk as the block before it.
     */
    private static final class Sheet {
        /** The top cell of every column the walk reached, and what it held when seen. */
        final LongArrayList cells = new LongArrayList();
        final IntArrayList amounts = new IntArrayList();
        /** The walk's queue. What was still to be walked when the reach ran out, from {@code head} on, is the rim. */
        final LongArrayList queue = new LongArrayList();
        int head;
        /** A draw's walk that found nothing to take: not asked again until it lapses. */
        boolean dry;
        /** What draws have taken since the rim was last woken. */
        int drawnSinceWake;
        final LongOpenHashSet reached = new LongOpenHashSet();
        /** The columns that are in the sheet, as opposed to merely looked at and found to be ground. */
        final LongOpenHashSet members = new LongOpenHashSet();
        /** The cells a draw takes from, or the cells at a spill's own height. */
        final IntArrayList chosen = new IntArrayList();
        /** For a spill: the cells one below its height, which are filled first. */
        final IntArrayList lower = new IntArrayList();
        /** What the walk was made for; a different place, or too many uses, means a new walk. */
        long key = Long.MIN_VALUE;
        int highest;
        int uses;
        boolean valid;
        /** The one entry a draw writes its target into, so the cached cells stay what they are. */
        int target = -1;

        /** The height the walk was made at; a spill from another height is another sheet. */
        int height;

        /** Whether a walk made for one place serves another: the same height, and within the walk. */
        boolean covers(long column, int atHeight) {
            return valid && uses < SHEET_REUSE && height == atHeight && members.contains(column);
        }

        /** Whether a walk serves a draw into room at {@code floor} under a column it reached. */
        boolean coversDraw(long column, int floor) {
            return valid && uses < SHEET_REUSE && highest > floor && members.contains(column);
        }
    }

    /**
     * Walks the surface of the body around {@code start}: outward column by column, up over
     * taller water and down over lower, until {@link #SHEET_REACH} columns have been seen or
     * there are no more. Each column's top cell and what it holds go into the sheet; what was
     * still to be walked is left as its rim.
     */
    private void walkSheet(Sheet sheet, ServerLevel level, BlockPos start, int startAmount, long key) {
        pressureSearches++;
        searched = true;
        sheet.valid = true;
        sheet.key = key;
        sheet.uses = 0;
        sheet.target = -1;
        sheet.queue.clear();
        sheet.head = 0;
        sheet.dry = false;
        sheet.drawnSinceWake = 0;
        sheet.reached.clear();
        sheet.members.clear();
        sheet.cells.clear();
        sheet.amounts.clear();
        sheet.chosen.clear();
        sheet.lower.clear();
        cursor.set(start);
        sheet.reached.add(columnKey(cursor));
        sheet.members.add(columnKey(cursor));
        sheet.queue.add(cursor.asLong());
        sheet.cells.add(cursor.asLong());
        sheet.amounts.add(startAmount);
        int highest = cursor.getY();
        // A film walks across dry floor at its own height. Water on a floor runs to the drain in
        // the floor however it is spread over it, and a sheet drained evenly breaks up into islands
        // as its first cells run dry; without this the islands were stranded a few blocks from the
        // lip. Deeper water does not: a sea must not be drawn through the beach into the lake
        // behind it. The floor crossed is walked, not wetted, and not counted as the sheet.
        boolean bridging = startAmount < WaterAmounts.SPREAD_THRESHOLD;
        int bridges = 0;
        while (sheet.head < sheet.queue.size() && sheet.cells.size() < SHEET_REACH) {
            cursor.set(sheet.queue.getLong(sheet.head++));
            for (Direction side : SIDES) {
                beside.setWithOffset(cursor, side);
                if (!sheet.reached.add(columnKey(beside))) continue;
                int there = WaterStorage.probe(level, beside);
                if (there < 0) continue;
                if (there > 0) {
                    there = climb(level, beside, there);
                } else {
                    there = descend(level, beside);
                    if (there < 0) {
                        if (bridging && beside.getY() == highest && bridges < SHEET_REACH) {
                            bridges++;
                            sheet.queue.add(beside.asLong());
                        }
                        continue;
                    }
                    if (there == 0) {
                        // A lip: open air with a drop under it. Water pours over it as a sheet, but
                        // nothing lies beyond it to walk to.
                        sheet.members.add(columnKey(beside));
                        sheet.cells.add(beside.asLong());
                        sheet.amounts.add(0);
                        continue;
                    }
                }
                sheet.members.add(columnKey(beside));
                sheet.queue.add(beside.asLong());
                sheet.cells.add(beside.asLong());
                sheet.amounts.add(there);
                if (beside.getY() > highest) highest = beside.getY();
            }
        }
        sheet.highest = highest;
    }

    /**
     * Fills room under full water from the surface of the body it is part of.
     *
     * <p>{@code column} is full water directly above {@code target}. The search climbs it to the
     * top and walks the surface from there, and what the room needs is then taken from the highest
     * surface cells found, a little from each, so the surface sinks as one sheet. That is the whole
     * point of taking it this way: a bite out of one cell left a dent, the dent's neighbours
     * levelled into it, theirs into them, and a sea being drained spent nearly all of its time
     * smoothing dents.
     *
     * <p>Where the column ends under a ceiling there is no surface, and the water under the ceiling
     * is what gets taken: a sealed tank drains from the top down, as it should.
     */
    private void drawFromSurface(ServerLevel level, BlockPos target, int targetAmount, BlockPos column) {
        int room = WaterAmounts.BLOCK - targetAmount;
        if (room <= 0) return;
        Sheet sheet = drawSheet;
        long key = columnKey(column);
        boolean walked = false;
        // A column is a column of x and z, and the walk knows nothing of what lies under the surface
        // it found. Room under a tank buried beneath a lake sits in a column the lake's walk has
        // seen, and the lake would have filled it through solid rock. So the column is climbed first:
        // only water that really stands all the way up to the sheet is drawn from it.
        boolean covered = sheet.coversDraw(key, target.getY());
        if (covered) {
            cursor.set(column);
            climb(level, cursor, WaterAmounts.BLOCK);
            covered = cursor.getY() == sheet.highest;
        }
        if (covered && sheet.dry) {
            // Nothing above this column to take from, and that was found out not long ago. What
            // changes it is water arriving from beyond the rim, which was woken then.
            sheet.uses++;
            return;
        }
        if (!covered || sheet.chosen.isEmpty()) {
            cursor.set(column);
            int topAmount = climb(level, cursor, WaterAmounts.BLOCK);
            walkSheet(sheet, level, cursor, topAmount, key);
            int floor = target.getY();
            for (int index = 0; index < sheet.cells.size(); index++) {
                long packed = sheet.cells.getLong(index);
                // The highest surface, and never lifted: what is drawn must stand higher than
                // where it goes.
                if (BlockPos.getY(packed) != sheet.highest || BlockPos.getY(packed) <= floor
                        || sheet.amounts.getInt(index) <= 0) continue;
                sheet.chosen.add(index);
            }
            if (sheet.chosen.isEmpty()) {
                sheet.dry = true;
                wakeRim(level, sheet);
                return;
            }
            sheet.target = sheet.cells.size();
            sheet.cells.add(target.asLong());
            sheet.amounts.add(targetAmount);
            walked = true;
        }
        sheet.uses++;
        sheet.cells.set(sheet.target, target.asLong());
        sheet.amounts.set(sheet.target, targetAmount);
        sources.clear();
        sources.addAll(sheet.chosen);
        sinks.clear();
        sinks.add(sheet.target);
        // At a rate: room under a sea fills as fast as water falls, not all at once.
        int moved = redistribute(level, sheet, Math.min(room, WaterAmounts.MAX_DRAW_PER_PASS));
        // Cells that have run dry are not asked again.
        int kept = 0;
        for (int index = 0; index < sheet.chosen.size(); index++) {
            int at = sheet.chosen.getInt(index);
            if (sheet.amounts.getInt(at) > 0) sheet.chosen.set(kept++, at);
        }
        sheet.chosen.size(kept);
        if (moved <= 0) return;
        activate(level, target);
        activate(level, target.below());
        // The top of the column drawn through is where pressure is next decided: it may now stand
        // above an open shaft and have to push, and nothing else wakes it.
        activate(level, BlockPos.of(sheet.cells.getLong(0)));
        // Beyond the rim the water is now higher than the sheet by what the sheet has sunk. Once
        // that is a difference worth levelling, what stands there is woken to come in.
        sheet.drawnSinceWake += moved;
        if (walked || sheet.drawnSinceWake >= WaterAmounts.LEVELLING_THRESHOLD * Math.max(1, sheet.chosen.size())) {
            wakeRim(level, sheet);
        }
    }

    /**
     * A surface standing higher than the surface beside it gives the difference away as a sheet.
     *
     * <p>The cells one lower that have room are filled first, all of them by the same amount;
     * then everything at this height is brought to one level. One walk, a bounded number of
     * writes, and nothing left for the cells to do with each other afterwards. Levelling a pair at
     * a time does the same work as a cascade: a block poured onto a pond, or a dish a drain had
     * sunk into a sea, set off hundreds of half-the-difference moves for every block that actually
     * went anywhere.
     *
     * <p>Water only spreads over water here. Dry ground beside a sheet is wetted by the front, at
     * the front's pace, which is what keeps a film from appearing across a whole floor at once.
     *
     * <p>A walk that finds nothing to move is remembered, and the cell is not walked from again
     * until the memory lapses: a film too thin to share out was otherwise asked the same expensive
     * question every time a neighbour moved.
     *
     * @return whether anything moved
     */
    private boolean spillOverSheet(ServerLevel level, BlockPos pos, int amount) {
        long key = pos.asLong();
        ActiveQueue queue = queueFor(level);
        if (queue.futile.contains(key)) return false;
        int height = pos.getY();
        Sheet sheet = spillSheet;
        boolean walked = false;
        // A walk is used again only while it knows the neighbours that set this spill off: the
        // front wets a new cell every pass, and a sheet that did not know it would starve it. A
        // neighbour it does not know is simply added, as long as there is room for it.
        boolean covered = sheet.covers(columnKey(pos), height);
        for (int index = 0; covered && index < 4; index++) {
            if (sideUnder[index] < 0 && sideAmounts[index] <= 0) continue;
            beside.setWithOffset(pos, SIDES[index]);
            long column = columnKey(beside);
            if (sheet.members.contains(column)) continue;
            if (sheet.cells.size() >= SHEET_REACH) {
                covered = false;
                break;
            }
            sheet.members.add(column);
            if (sideAmounts[index] > 0) {
                sheet.chosen.add(sheet.cells.size());
                sheet.cells.add(beside.asLong());
                sheet.amounts.add(sideAmounts[index]);
            } else if (sideUnder[index] < WaterAmounts.BLOCK) {
                sheet.lower.add(sheet.cells.size());
                sheet.cells.add(BlockPos.asLong(beside.getX(), height - 1, beside.getZ()));
                sheet.amounts.add(sideUnder[index]);
            } else {
                sheet.chosen.add(sheet.cells.size());
                sheet.cells.add(beside.asLong());
                sheet.amounts.add(0);
            }
        }
        if (!covered) {
            walkSheet(sheet, level, pos, amount, key);
            sheet.height = height;
            // Everything at this height, including the air over columns whose top is full one
            // below: that is where the next layer of a rising surface begins.
            int count = sheet.cells.size();
            for (int index = 0; index < count; index++) {
                long packed = sheet.cells.getLong(index);
                int y = BlockPos.getY(packed);
                if (y == height) {
                    sheet.chosen.add(index);
                } else if (y == height - 1) {
                    if (sheet.amounts.getInt(index) < WaterAmounts.BLOCK) {
                        sheet.lower.add(index);
                    } else if (WaterStorage.probe(level, BlockPos.getX(packed), height, BlockPos.getZ(packed)) == 0) {
                        sheet.chosen.add(sheet.cells.size());
                        sheet.cells.add(BlockPos.asLong(BlockPos.getX(packed), height, BlockPos.getZ(packed)));
                        sheet.amounts.add(0);
                    }
                }
            }
            walked = true;
        }
        sheet.uses++;
        boolean moved = false;
        // First the cells one lower.
        if (!sheet.lower.isEmpty()) {
            sources.clear();
            int selfAt = sheet.cells.indexOf(key);
            for (int index = 0; index < sheet.chosen.size(); index++) {
                int at = sheet.chosen.getInt(index);
                // The cell pouring is spared, so that it is the last of its sheet to run dry: a
                // sheet drained evenly to nothing leaves its last film stranded a step from the lip.
                if (sheet.amounts.getInt(at) > 0 && (at != selfAt || sheet.chosen.size() == 1)) sources.add(at);
            }
            sinks.clear();
            sinks.addAll(sheet.lower);
            // Over a lip at the rate water falls: no cell pours or receives more than that in a pass.
            shareLimit = WaterAmounts.MAX_FALL_PER_PASS;
            moved |= redistribute(level, sheet, Integer.MAX_VALUE) > 0;
            shareLimit = Integer.MAX_VALUE;
        }
        // Then one level across this height: a window of it at a time, read afresh, and only once
        // it is visibly out of level. The window rotates, so the whole sheet comes level over
        // successive spills without any one of them writing a thousand cells.
        moved |= equalizeWindow(level, sheet, pos) > 0;
        if (moved) {
            // The sheet was written quietly, so nothing else will ask this cell again. It asks itself:
            // a cell beside a lip keeps pouring the sheet over it until the sheet is gone.
            activate(level, pos);
            // A sheet that has thinned to a film is near its end, and what stands beyond its rim has
            // to follow it down now or be left standing when it is gone.
            if (walked || amount < WaterAmounts.SPREAD_THRESHOLD) wakeRim(level, sheet);
        } else {
            queue.futile.add(key);
        }
        return moved;
    }

    /**
     * Moves up to {@code wanted} millibuckets from {@link #sources} to {@link #sinks}, spread as
     * evenly over each side as what they hold and have room for allows. Every touched cell is
     * written once, and water carries its quality with it pair by pair.
     */
    private int redistribute(ServerLevel level, Sheet sheet, int wanted) {
        if (wanted <= 0 || sources.isEmpty() || sinks.isEmpty()) return 0;
        // Enough cells on each side that no cell moves more than a sip: cells next to the window
        // would otherwise see a step and level into it one pair at a time.
        long guess = 0;
        for (int index = 0; index < sources.size(); index++) guess += Math.max(0, sheet.amounts.getInt(sources.getInt(index)));
        long roomGuess = 0;
        for (int index = 0; index < sinks.size(); index++) roomGuess += roomIn(sheet, sinks.getInt(index));
        int cells = (int) Math.min(Integer.MAX_VALUE, (Math.min(wanted, Math.min(guess, roomGuess)) + SIP - 1) / SIP);
        window(sources, false, cells);
        window(sinks, false, cells);
        refresh(level, sheet, sources);
        refresh(level, sheet, sinks);
        int available = 0;
        int room = 0;
        for (int index = 0; index < sources.size(); index++) available += Math.max(0, sheet.amounts.getInt(sources.getInt(index)));
        for (int index = 0; index < sinks.size(); index++) room += roomIn(sheet, sinks.getInt(index));
        wanted = Math.min(wanted, Math.min(available, room));
        if (wanted <= 0) return 0;
        shareOut(sheet, sources, wanted, true);
        shareOut(sheet, sinks, wanted, false);
        return commit(level, sheet);
    }

    /**
     * Levels one rotating window of the cells at a spill's own height to their mean. What is
     * read is read now, not remembered from the walk, so the decision is never stale.
     */
    private int equalizeWindow(ServerLevel level, Sheet sheet, BlockPos self) {
        if (sheet.chosen.isEmpty()) return 0;
        scratch.clear();
        scratch.addAll(sheet.chosen);
        window(scratch, true, SHEET_WINDOW);
        // The cell that set this off, and the neighbours that set it off, are in the window whatever
        // the rotation says: they are what is out of level, and a window without them would find
        // nothing and leave them to level pair by pair.
        int slot = 0;
        int selfAt = sheet.cells.indexOf(self.asLong());
        if (selfAt >= 0 && !scratch.contains(selfAt)) scratch.set(slot++, selfAt);
        for (Direction side : SIDES) {
            int at = sheet.cells.indexOf(self.relative(side).asLong());
            if (at >= 0 && !scratch.contains(at) && slot < scratch.size()) scratch.set(slot++, at);
        }
        refresh(level, sheet, scratch);
        long total = 0;
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        int open = 0;
        for (int index = 0; index < scratch.size(); index++) {
            int present = sheet.amounts.getInt(scratch.getInt(index));
            if (present < 0) continue;
            total += present;
            lowest = Math.min(lowest, present);
            highest = Math.max(highest, present);
            open++;
        }
        if (open == 0 || highest - lowest < WaterAmounts.LEVELLING_THRESHOLD) return 0;
        int mean = (int) (total / open);
        sources.clear();
        sinks.clear();
        for (int index = 0; index < scratch.size(); index++) {
            int at = scratch.getInt(index);
            int present = sheet.amounts.getInt(at);
            if (present > mean) sources.add(at);
            else if (present >= 0 && present < mean) sinks.add(at);
        }
        if (sources.isEmpty() || sinks.isEmpty()) return 0;
        sinkShares.size(sinks.size());
        int wanted = 0;
        // Towards the mean at the rate water levels, so a block landing on a pond spreads over a
        // few passes instead of vanishing into it in one.
        long offered = 0;
        for (int index = 0; index < sources.size(); index++) {
            offered += Math.min(WaterAmounts.MAX_LEVEL_PER_PASS, sheet.amounts.getInt(sources.getInt(index)) - mean);
        }
        for (int index = 0; index < sinks.size(); index++) {
            int take = Math.min(WaterAmounts.MAX_LEVEL_PER_PASS, mean - sheet.amounts.getInt(sinks.getInt(index)));
            sinkShares.set(index, take);
            wanted += take;
        }
        if (wanted > offered) {
            // The sources cannot give that much this pass: every sink gets its part of what there is.
            int left = (int) offered;
            for (int index = 0; index < sinks.size(); index++) {
                int take = (int) ((long) sinkShares.getInt(index) * offered / wanted);
                sinkShares.set(index, take);
                left -= take;
            }
            for (int index = 0; index < sinks.size() && left > 0; index++, left--) sinkShares.set(index, sinkShares.getInt(index) + 1);
            wanted = (int) offered;
        }
        if (wanted <= 0) return 0;
        shareLimit = WaterAmounts.MAX_LEVEL_PER_PASS;
        shareOut(sheet, sources, wanted, true);
        shareLimit = Integer.MAX_VALUE;
        return commit(level, sheet);
    }

    /** Brings every sink up to {@code mean}, taking the difference evenly from the sources. */
    private int redistributeTo(ServerLevel level, Sheet sheet, int mean) {
        if (sources.isEmpty() || sinks.isEmpty()) return 0;
        window(sources, true, SHEET_WINDOW);
        window(sinks, true, SHEET_WINDOW);
        refresh(level, sheet, sources);
        refresh(level, sheet, sinks);
        sinkShares.size(sinks.size());
        int wanted = 0;
        for (int index = 0; index < sinks.size(); index++) {
            int at = sinks.getInt(index);
            int take = Math.max(0, Math.min(mean - sheet.amounts.getInt(at), roomIn(sheet, at)));
            sinkShares.set(index, take);
            wanted += take;
        }
        if (wanted <= 0) return 0;
        shareOut(sheet, sources, wanted, true);
        return commit(level, sheet);
    }

    /**
     * Reads the cells about to be written again rather than trusting the walk: the front, a
     * bucket or another sheet may have been here since. A cell that is no longer open at all
     * (something was built there) is marked as such and left alone.
     */
    private void refresh(ServerLevel level, Sheet sheet, IntArrayList cells) {
        for (int index = 0; index < cells.size(); index++) {
            int at = cells.getInt(index);
            long packed = sheet.cells.getLong(at);
            sheet.amounts.set(at, WaterStorage.probe(level, BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed)));
        }
    }

    private static int roomIn(Sheet sheet, int at) {
        int present = sheet.amounts.getInt(at);
        return present < 0 ? 0 : WaterAmounts.BLOCK - present;
    }

    /**
     * Keeps the writes of one draw or spill bounded: past {@link #SHEET_WINDOW} cells only a
     * rotating slice of them is touched this time, and the rest next time. The sheet stays within
     * one sip of level.
     */
    private void window(IntArrayList cells, boolean overlapping, int wanted) {
        int count = cells.size();
        int size = Math.max(SHEET_WINDOW, wanted);
        if (count <= size) return;
        IntArrayList copy = cells == scratch ? scratchCopy : scratch;
        copy.clear();
        int start = Math.floorMod(windowOffset, count);
        for (int index = 0; index < size; index++) copy.add(cells.getInt((start + index) % count));
        // Sips rotate a whole window on, so every cell is sipped once a round and the sheet stays
        // within one sip of level. Levelling rotates half a window, so its windows overlap and no
        // seam is left between them.
        windowOffset += overlapping ? size / 2 : size;
        cells.clear();
        cells.addAll(copy);
    }

    /**
     * Splits {@code total} evenly over the cells in {@code cells}, never asking a cell for more
     * than it holds (sources) or has room for (sinks). The result lands in the matching share list.
     */
    private void shareOut(Sheet sheet, IntArrayList cells, int total, boolean giving) {
        IntArrayList shares = giving ? sourceShares : sinkShares;
        shares.size(cells.size());
        int left = total;
        int open = cells.size();
        for (int index = 0; index < cells.size(); index++) shares.set(index, 0);
        // Round after round: each open cell takes an equal part of what is left, up to its cap.
        // A cell that hits its cap leaves the round, and the rest is shared again.
        while (left > 0 && open > 0) {
            int base = left / open;
            int extra = left % open;
            int before = left;
            open = 0;
            for (int index = 0; index < cells.size() && left > 0; index++) {
                int at = cells.getInt(index);
                int cap = Math.min(shareLimit, giving ? Math.max(0, sheet.amounts.getInt(at)) : roomIn(sheet, at));
                int have = shares.getInt(index);
                if (have >= cap) continue;
                int want = base + (extra > 0 ? 1 : 0);
                if (extra > 0) extra--;
                int give = Math.min(want, cap - have);
                shares.set(index, have + give);
                left -= give;
                if (have + give < cap) open++;
            }
            if (left == before) break;
        }
    }

    /** Applies the shares: pairs sources with sinks in order, carries quality, writes each cell once. */
    private int commit(ServerLevel level, Sheet sheet) {
        int si = 0;
        int ki = 0;
        int sourceLeft = 0;
        int sinkLeft = 0;
        int moved = 0;
        while (true) {
            while (sourceLeft <= 0 && si < sources.size()) sourceLeft = sourceShares.getInt(si++);
            while (sinkLeft <= 0 && ki < sinks.size()) sinkLeft = sinkShares.getInt(ki++);
            if (sourceLeft <= 0 || sinkLeft <= 0) break;
            int quantity = Math.min(sourceLeft, sinkLeft);
            int from = sources.getInt(si - 1);
            int to = sinks.getInt(ki - 1);
            int fromHas = sheet.amounts.getInt(from);
            int toHas = sheet.amounts.getInt(to);
            WaterQualityStorage.carry(level, BlockPos.of(sheet.cells.getLong(from)), fromHas,
                    BlockPos.of(sheet.cells.getLong(to)), toHas, quantity);
            sheet.amounts.set(from, fromHas - quantity);
            sheet.amounts.set(to, toHas + quantity);
            sourceLeft -= quantity;
            sinkLeft -= quantity;
            moved += quantity;
        }
        if (moved <= 0) return 0;
        for (int index = 0; index < sources.size(); index++) {
            if (sourceShares.getInt(index) <= 0) continue;
            int at = sources.getInt(index);
            WaterStorage.setAmount(level, BlockPos.of(sheet.cells.getLong(at)), sheet.amounts.getInt(at));
            // A film left behind is a trace in the making, and nothing else will come by to notice.
            if (sheet.amounts.getInt(at) > 0 && sheet.amounts.getInt(at) < WaterAmounts.SPREAD_THRESHOLD) queueFor(level).traces.add(sheet.cells.getLong(at));
        }
        for (int index = 0; index < sinks.size(); index++) {
            if (sinkShares.getInt(index) <= 0) continue;
            int at = sinks.getInt(index);
            WaterStorage.setAmount(level, BlockPos.of(sheet.cells.getLong(at)), sheet.amounts.getInt(at));
            // What was poured into a lip has to fall, and nothing else will tell it to.
            BlockPos sink = BlockPos.of(sheet.cells.getLong(at));
            if (WaterStorage.probe(level, sink.below()) == 0) activate(level, sink);
        }
        return moved;
    }

    /** Wakes what stands beyond the rim of a walk: it may now be higher than the sheet. */
    private void wakeRim(ServerLevel level, Sheet sheet) {
        sheet.drawnSinceWake = 0;
        LongOpenHashSet futile = queueFor(level).futile;
        for (int index = sheet.head; index < sheet.queue.size(); index++) {
            cursor.set(sheet.queue.getLong(index));
            for (Direction side : SIDES) {
                beside.setWithOffset(cursor, side);
                if (sheet.members.contains(columnKey(beside))) continue;
                // The sheet has changed under it, so whatever it found last time no longer holds.
                futile.remove(beside.asLong());
                activate(level, beside);
            }
        }
    }

    /** Moves a mutable position up its column of water to the surface; returns what the top holds. */
    private static int climb(ServerLevel level, BlockPos.MutableBlockPos at, int amount) {
        for (int climbed = 0; climbed < PRESSURE_CLIMB; climbed++) {
            int higher = WaterStorage.probe(level, at.getX(), at.getY() + 1, at.getZ());
            if (higher <= 0) return amount;
            at.move(Direction.UP);
            amount = higher;
        }
        return amount;
    }

    /**
     * Moves a mutable position down through air to the water under it. Returns what that water
     * holds; zero, with the position one down, where the air goes on falling (a lip); and a wall
     * where the next thing under the air is ground at this height, which is dry floor, not surface.
     */
    private static int descend(ServerLevel level, BlockPos.MutableBlockPos at) {
        int lower = WaterStorage.probe(level, at.getX(), at.getY() - 1, at.getZ());
        if (lower < 0) return WaterStorage.WALL;
        at.move(Direction.DOWN);
        if (lower > 0) return lower;
        lower = WaterStorage.probe(level, at.getX(), at.getY() - 1, at.getZ());
        if (lower <= 0) return 0;
        at.move(Direction.DOWN);
        return lower;
    }

    private static long columnKey(BlockPos pos) {
        return BlockPos.asLong(pos.getX(), 0, pos.getZ());
    }

    /**
     * Rule four: water joined through full water stands at one height.
     *
     * <p>This is what makes two connected shafts come to rest level, lets a tank on a tower feed a
     * pipe that climbs again, and floods a chamber from below. It used to be done by letting deep
     * water hold a little more than a block, so that the surplus could travel. That made the weight
     * of a column a quantity every cell under it had to carry, and so every full cell of every lake
     * was permanently a little out of balance with the next.
     *
     * <p>Here nothing is stored at all. A cell that is stuck asks one question in each direction:
     *
     * <ul>
     *   <li><b>push</b> — it stands on full water. Is there, through the full water below it, an
     *       open place lower than itself, or one at its own height holding less? Then its water
     *       belongs there.</li>
     *   <li><b>pull</b> — it is full with room above. Is there, through the full water it is part
     *       of, water standing higher than that room, or more of it at the same height? Then that
     *       water belongs here.</li>
     * </ul>
     *
     * <p>Both are needed, because only one end of a pipe is ever awake: a bucket emptied into one
     * shaft wakes the top of that shaft, and a plug pulled out of the other wakes the water under
     * the plug. Either answer moves water strictly downhill or evens out one pair, so it cannot
     * oscillate, and a search that finds nothing leaves the cell asleep.
     */
    private boolean press(ServerLevel level, BlockPos pos, int amount) {
        BlockPos above = pos.above();
        int overhead = WaterStorage.probe(level, above);
        // Only the top of a column pushes. Water with water standing on it is held where it is,
        // and if the column has somewhere to go, its top is the cell that goes. Without this
        // every cell along the wall of a tank or the bed of a sea went looking, at any depth.
        if (overhead <= 0 && WaterStorage.probe(level, pos.below()) >= WaterAmounts.BLOCK
                && push(level, pos, amount)) {
            return true;
        }
        return amount >= WaterAmounts.BLOCK && overhead >= 0 && overhead < WaterAmounts.BLOCK
                && pull(level, pos, above, overhead);
    }

    /** Looks below this cell's own height for somewhere its water should be instead. */
    private boolean push(ServerLevel level, BlockPos pos, int amount) {
        int height = pos.getY();
        beginSearch(pos.below());
        reached.add(pos.asLong());
        for (int passed = 0; passed < PRESSURE_REACH && !frontier.isEmpty(); passed++) {
            cursor.set(frontier.dequeueLong());
            for (Direction direction : ALL) {
                beside.setWithOffset(cursor, direction);
                // Only through water lower than the source: pressure does not lift water above
                // the surface it comes from.
                if (beside.getY() > height || !reached.add(beside.asLong())) continue;
                int there = WaterStorage.probe(level, beside);
                if (there < 0) continue;
                if (beside.getY() < height) {
                    if (there >= WaterAmounts.BLOCK) {
                        frontier.enqueue(beside.asLong());
                        continue;
                    }
                    transfer(level, pos, amount, beside.immutable(), there,
                            Math.min(WaterAmounts.MAX_RISE_PER_PASS, WaterAmounts.BLOCK - there));
                    return true;
                }
                // The same height as the source: the two surfaces even out, exactly as they would
                // side by side, and under the same rule about not wetting dry ground with a film.
                int difference = amount - there;
                if (difference < WaterAmounts.PRESSURE_THRESHOLD) continue;
                if (there <= 0 && amount < WaterAmounts.SPREAD_THRESHOLD) continue;
                transfer(level, pos, amount, beside.immutable(), there,
                        Math.min(WaterAmounts.MAX_RISE_PER_PASS, (difference + 1) / 2));
                return true;
            }
        }
        return false;
    }

    /** Looks through the full water this cell is part of for water that should be in the room above it. */
    private boolean pull(ServerLevel level, BlockPos pos, BlockPos above, int overhead) {
        int height = above.getY();
        beginSearch(pos);
        reached.add(above.asLong());
        for (int passed = 0; passed < PRESSURE_REACH && !frontier.isEmpty(); passed++) {
            cursor.set(frontier.dequeueLong());
            for (Direction direction : ALL) {
                beside.setWithOffset(cursor, direction);
                if (!reached.add(beside.asLong())) continue;
                int there = WaterStorage.probe(level, beside);
                if (there <= 0) continue;
                if (beside.getY() > height) {
                    // Take it from the top of that column: the surface is what should sink, and
                    // taking from the middle would only have the rest fall in after it.
                    BlockPos top = beside.immutable();
                    for (int climbed = 0; climbed < PRESSURE_CLIMB; climbed++) {
                        int higher = WaterStorage.probe(level, top.above());
                        if (higher <= 0) break;
                        top = top.above();
                        there = higher;
                    }
                    transfer(level, top, there, above, overhead,
                            Math.min(WaterAmounts.MAX_RISE_PER_PASS, WaterAmounts.BLOCK - overhead));
                    return true;
                }
                if (beside.getY() == height) {
                    int difference = there - overhead;
                    if (difference >= WaterAmounts.PRESSURE_THRESHOLD
                            && (overhead > 0 || there >= WaterAmounts.SPREAD_THRESHOLD)) {
                        transfer(level, beside.immutable(), there, above, overhead,
                                Math.min(WaterAmounts.MAX_RISE_PER_PASS, (difference + 1) / 2));
                        return true;
                    }
                }
                if (there >= WaterAmounts.BLOCK) frontier.enqueue(beside.asLong());
            }
        }
        return false;
    }

    private void beginSearch(BlockPos start) {
        pressureSearches++;
        searched = true;
        frontier.clear();
        reached.clear();
        reached.add(start.asLong());
        frontier.enqueue(start.asLong());
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
        // Water that moves is a current while it moves: it pushes what floats and turns wheels.
        // Water displaced through a pipe is not: its two ends may be a hundred blocks apart, and
        // a current that long would throw whatever stood in it.
        if (from.distManhattan(to) == 1) WaterCurrent.record(level, from, to, move);
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
        // The cell under the source is the top of its column once the source has run dry.
        activate(level, from.below());
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
        long dropped;
        /** How many cells of each band are still this pass's work; see beginPass. */
        private int dueNear;
        private int dueMid;
        private int dueFar;
        /** Cells whose last sheet walk moved nothing, spared the walk until forgetFutility. */
        final LongOpenHashSet futile = new LongOpenHashSet();
        /** Cells whose last pressure search found nothing, spared the search until forgetFutility. */
        final LongOpenHashSet pressed = new LongOpenHashSet();
        /** Traces of water seen lying stranded, to be soaked into the ground if still there at the sweep. */
        final LongOpenHashSet traces = new LongOpenHashSet();
        /** Traces found stranded at the last sweep; stranded at the next as well, they dry. */
        final LongOpenHashSet stranded = new LongOpenHashSet();
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
            if (size() >= MAX_ACTIVE && !makeRoomFor(priority)) {
                dropped++;
                return;
            }
            bucket(priority).add(packed);
        }

        /**
         * Marks what is waiting now as this pass's work. A cell woken during the pass waits for
         * the next one, however much of the budget is left.
         *
         * <p>That is what makes every rate in the model a rate per tick. Without it a cell that
         * gave a quarter block away woke itself, came round again in the same tick and gave the
         * next quarter, and went on until the tick's time ran out: on an idle server a bucket
         * crossed a room and a waterfall reached the floor within one tick, and how fast water
         * moved depended on how busy the server was.
         */
        void beginPass() {
            dueNear = near.size();
            dueMid = mid.size();
            dueFar = far.size();
        }

        /** Whether any of this pass's work is left. */
        boolean hasDue() {
            return dueNear > 0 && !near.isEmpty() || dueMid > 0 && !mid.isEmpty() || dueFar > 0 && !far.isEmpty();
        }

        long poll() {
            // Eight near, two middle-distance and one far slot. Empty bands fall through to the
            // closest available work, so proximity stays dominant without freezing the far side of
            // a large connected lake forever.
            int slot = Math.floorMod(priorityTurn++, 11);
            boolean nearDue = dueNear > 0 && !near.isEmpty();
            boolean midDue = dueMid > 0 && !mid.isEmpty();
            boolean farDue = dueFar > 0 && !far.isEmpty();
            int band = slot < 8 && nearDue ? 0 : slot < 10 && midDue ? 1 : slot == 10 && farDue ? 2
                    : nearDue ? 0 : midDue ? 1 : 2;
            if (band == 0) {
                dueNear--;
                return near.removeFirstLong();
            }
            if (band == 1) {
                dueMid--;
                return mid.removeFirstLong();
            }
            dueFar--;
            return far.removeFirstLong();
        }

        private boolean makeRoomFor(int priority) {
            dropped++;
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
