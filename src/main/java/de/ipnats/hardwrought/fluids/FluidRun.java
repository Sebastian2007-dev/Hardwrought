package de.ipnats.hardwrought.fluids;

import de.ipnats.hardwrought.machinery.ConnectedPipeBlock;
import de.ipnats.hardwrought.machinery.PipeLink;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * One run of fluid pipes, as seen from a pipe in it: the pumps that drive it, and the blocks it can
 * fill, nearest first. A run is everything joined to the pipe through pipes and pumps.
 *
 * <p>The pumps of a run push so much a tick between them, and everything the run draws in that tick,
 * at however many sides, shares it ({@link #spend}). A run without a running pump draws nothing.
 */
final class FluidRun {
    /** How many pipes and pumps a run is followed through at most. */
    private static final int LIMIT = 512;

    /**
     * A block the run can fill.
     *
     * @param distance pipes between it and the pipe the run was seen from
     * @param narrowest the most a tick that gets through the narrowest pipe on the way, in droplets
     */
    record Destination(BlockPos pos, Storage<FluidVariant> storage, int distance, long narrowest) { }

    final long id;
    final double pumped;
    final List<Destination> destinations;

    private FluidRun(long id, double pumped, List<Destination> destinations) {
        this.id = id;
        this.pumped = pumped;
        this.destinations = destinations;
    }

    /** The run the fluid pipe at start belongs to. */
    static FluidRun of(Level level, BlockPos start) {
        Map<BlockPos, long[]> seen = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<Destination> destinations = new ArrayList<>();
        double pumped = 0;
        long id = start.asLong();
        seen.put(start, new long[] {0, Long.MAX_VALUE});
        queue.add(start);
        while (!queue.isEmpty() && seen.size() <= LIMIT) {
            BlockPos at = queue.poll();
            long[] here = seen.get(at);
            id = Math.min(id, at.asLong());
            BlockState state = level.getBlockState(at);
            if (state.getBlock() instanceof FluidPumpBlock) {
                if (level.getBlockEntity(at) instanceof FluidPump pump) pumped += pump.pumpRate();
                // A pump joins the pipes that join it.
                for (Direction side : Direction.values()) {
                    BlockPos next = at.relative(side);
                    BlockState neighbour = level.getBlockState(next);
                    if (neighbour.getBlock() instanceof FluidPipeBlock
                            && ConnectedPipeBlock.link(neighbour, side.getOpposite()).joined() && !seen.containsKey(next)) {
                        seen.put(next, new long[] {here[0] + 1, here[1]});
                        queue.add(next);
                    }
                }
                continue;
            }
            if (!(state.getBlock() instanceof FluidPipeBlock pipe)) continue;
            long through = Math.min(here[1], pipe.tier().dropletsPerTick());
            for (Direction side : Direction.values()) {
                PipeLink link = ConnectedPipeBlock.link(state, side);
                if (!link.joined()) continue;
                BlockPos next = at.relative(side);
                if (seen.containsKey(next)) continue;
                BlockState neighbour = level.getBlockState(next);
                if (neighbour.getBlock() instanceof FluidPipeBlock || neighbour.getBlock() instanceof FluidPumpBlock) {
                    seen.put(next, new long[] {here[0] + 1, through});
                    queue.add(next);
                    continue;
                }
                // A block the run draws from is never filled by it.
                if (link == PipeLink.EXTRACT) continue;
                Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, next, side.getOpposite());
                if (storage != null && storage.supportsInsertion()) {
                    destinations.add(new Destination(next, storage, (int) here[0], through));
                }
            }
        }
        destinations.sort((a, b) -> Integer.compare(a.distance(), b.distance()));
        return new FluidRun(id, pumped, destinations);
    }

    // ---------------------------------------------------------------- what the pumps push, shared out

    private record Spent(long tick, Map<Long, Double> byRun) { }

    private static final Map<Level, Spent> SPENT = new WeakHashMap<>();

    /** How much is left for this tick of what the run's pumps push, in millibuckets. */
    double left(Level level) {
        Spent spent = spent(level);
        return pumped - spent.byRun().getOrDefault(id, 0.0);
    }

    /** Counts what has been drawn into the run this tick. */
    void spend(Level level, double millibuckets) {
        Spent spent = spent(level);
        spent.byRun().merge(id, millibuckets, Double::sum);
    }

    private static Spent spent(Level level) {
        Spent spent = SPENT.get(level);
        if (spent == null || spent.tick() != level.getGameTime()) {
            spent = new Spent(level.getGameTime(), new HashMap<>());
            SPENT.put(level, spent);
        }
        return spent;
    }
}
