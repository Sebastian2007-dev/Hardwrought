package de.ipnats.hardwrought.water;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.simulation.SimulationScheduler;
import de.ipnats.hardwrought.core.simulation.SimulationTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
    /** Cells moved per pass. The pass runs every five ticks, so this is the ceiling on the work. */
    public static final int BUDGET_PER_PASS = 1024;
    /**
     * How many disturbed cells are remembered at once. Past this the oldest are dropped: water then
     * settles where the action is instead of the server growing a world-sized queue.
     */
    public static final int MAX_ACTIVE = 32_768;
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private final MinecraftServer server;
    private final Map<ResourceKey<Level>, LinkedHashSet<Long>> active = new LinkedHashMap<>();

    public WaterFlow(MinecraftServer server, SimulationScheduler scheduler) {
        this.server = server;
        scheduler.register("hardwrought:water_flow", SimulationTier.FAST, this::tickFlow);
    }

    /** Wakes a cell. Called from the fluid tick vanilla already schedules on disturbed water. */
    public static void disturb(ServerLevel level, BlockPos pos) {
        var runtime = CoreLifecycle.find(level.getServer());
        if (runtime == null) return;
        runtime.waterFlow().activate(level, pos);
    }

    public void activate(ServerLevel level, BlockPos pos) {
        Set<Long> queue = active.computeIfAbsent(level.dimension(), key -> new LinkedHashSet<>());
        if (queue.size() >= MAX_ACTIVE) return;
        queue.add(pos.asLong());
    }

    public int activeCells() {
        return active.values().stream().mapToInt(Set::size).sum();
    }

    private void tickFlow() {
        int budget = BUDGET_PER_PASS;
        for (ServerLevel level : server.getAllLevels()) {
            LinkedHashSet<Long> queue = active.get(level.dimension());
            if (queue == null || queue.isEmpty()) continue;
            // Take the batch out first. Moving water wakes its neighbours, which writes back into
            // this very queue, so it must not be under an open iterator while that happens.
            List<Long> batch = new ArrayList<>(Math.min(budget, queue.size()));
            var iterator = queue.iterator();
            while (iterator.hasNext() && batch.size() < budget) {
                batch.add(iterator.next());
                iterator.remove();
            }
            budget -= batch.size();
            for (long packed : batch) step(level, BlockPos.of(packed));
            if (budget <= 0) return;
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
        transfer(level, pos, above, move);
        return amount - move;
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
        transfer(level, pos, below, move);
        return amount - move;
    }

    /** Rule three: water levels out. Each neighbour with less gets a share of the difference. */
    private void level(ServerLevel level, BlockPos pos, int amount) {
        for (Direction side : SIDES) {
            BlockPos next = pos.relative(side);
            if (!WaterStorage.canHold(level, next)) continue;
            int mine = WaterStorage.amount(level, pos);
            int theirs = WaterStorage.amount(level, next);
            int difference = mine - theirs;
            if (difference < WaterAmounts.LEVELLING_THRESHOLD) continue;
            // A third of the difference per pass converges without the two cells trading it back.
            int move = Math.min(difference / 3 + 1, Math.min(mine, WaterAmounts.pressureRoom(theirs)));
            if (move <= 0) continue;
            transfer(level, pos, next, move);
        }
    }

    private void transfer(ServerLevel level, BlockPos from, BlockPos to, int millibuckets) {
        int source = WaterStorage.amount(level, from);
        int destination = WaterStorage.amount(level, to);
        int move = Math.min(millibuckets, Math.min(source, WaterAmounts.pressureRoom(destination)));
        if (move <= 0) return;
        WaterStorage.setAmount(level, to, destination + move);
        WaterStorage.setAmount(level, from, source - move);
        activate(level, from);
        activate(level, to);
        activate(level, from.above());
        activate(level, to.above());
        for (Direction side : SIDES) activate(level, to.relative(side));
        activate(level, to.below());
    }

    public void shutdown() {
        active.clear();
    }
}
