package de.ipnats.hardwrought.water;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Taking a fixed amount of water out of the water around one point rather than out of one block.
 *
 * <p>A bucket dipped into a pond takes a bucket of pond. With the water model a block rarely holds
 * exactly a thousand millibuckets — a lake that has just been drawn from, a shallow stream, a pool
 * still levelling out — so asking one block for a whole bucket refused it at almost every shore.
 * Instead the dipped block gives what it has, and the water connected to it makes up the rest,
 * nearest first. The flow simulation then evens the dent out on its own.
 *
 * <p>Bounded like everything else: at most {@link #MAX_CELLS} blocks, none more than
 * {@link #MAX_REACH} steps from where the bucket went in, and never a chunk that is not loaded.
 * All or nothing: when the water in reach does not add up to the amount, nothing is taken.
 */
public final class WaterDraw {
    /** How far, in steps through water, a bucket reaches from the block it was dipped into. */
    public static final int MAX_REACH = 4;
    /** How many blocks one draw may look at before it gives up. */
    public static final int MAX_CELLS = 64;

    private WaterDraw() { }

    /** One block's share of a draw. */
    private record Share(BlockPos pos, int present, int taken) { }

    /**
     * Takes exactly {@code millibuckets} out of the water connected to {@code origin}, the origin
     * itself first. Returns false, and changes nothing, when there is not that much within reach.
     */
    public static boolean draw(ServerLevel level, BlockPos origin, int millibuckets) {
        List<Share> plan = plan(level, origin, millibuckets);
        if (plan == null) return false;
        for (Share share : plan) {
            WaterStorage.setAmount(level, share.pos(), share.present() - share.taken());
            WaterFlow.disturb(level, share.pos());
        }
        return true;
    }

    /** Which blocks give how much, or null when the water in reach falls short. */
    private static List<Share> plan(ServerLevel level, BlockPos origin, int wanted) {
        List<Share> plan = new ArrayList<>();
        int still = wanted;
        for (BlockPos pos : reachable(level, origin)) {
            int present = WaterStorage.amount(level, pos);
            int taken = Math.min(present, still);
            if (taken <= 0) continue;
            plan.add(new Share(pos, present, taken));
            still -= taken;
            if (still == 0) return plan;
        }
        return null;
    }

    /**
     * The water connected to the origin, in the order a bucket would take it: the origin, then its
     * neighbours, then theirs. Breadth first, so nearer water is always drawn before farther water.
     */
    private static List<BlockPos> reachable(ServerLevel level, BlockPos origin) {
        List<BlockPos> found = new ArrayList<>();
        if (!level.hasChunkAt(origin) || WaterStorage.amount(level, origin) <= 0) return found;
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> pending = new ArrayDeque<>();
        BlockPos start = origin.immutable();
        seen.add(start);
        pending.add(start);
        while (!pending.isEmpty() && found.size() < MAX_CELLS) {
            BlockPos here = pending.poll();
            found.add(here);
            for (Direction side : Direction.values()) {
                BlockPos next = here.relative(side);
                if (next.distManhattan(origin) > MAX_REACH || !seen.add(next)) continue;
                if (!level.hasChunkAt(next) || WaterStorage.amount(level, next) <= 0) continue;
                pending.add(next);
            }
        }
        return found;
    }
}
