package de.ipnats.hardwrought.environment;

import de.ipnats.hardwrought.smithing.ForgeHoodBlock;
import de.ipnats.hardwrought.smithing.GasPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * Where the gas a hood catches comes out.
 *
 * <p>A flue is everything joined to the hood: the other hoods of its canopy, the hoods stacked on it,
 * and the gas pipes run off it. The way out is the open end of a pipe — a pipe joined on one side
 * only is open on the other — and the nearest one is taken. A flue with no pipe lets the gas out
 * above the highest of its hoods, which is where a chimney of stacked hoods ends. A flue whose every
 * way out is walled up has none, and the gas stays where it was.
 *
 * <p>A canopy also draws: every pass it pulls the gas around it to its outlet, as far as two blocks
 * for every hood in it, through the air it is open to — never through a wall. The nearest gas goes
 * first, and a canopy moves at most a block's worth of gas per hood per pass.
 */
public final class Flues {
    /** The most hoods and pipes one flue is followed through. */
    private static final int MAX_LENGTH = 256;
    /** How far a canopy draws, per hood in it. */
    public static final int DRAW_PER_HOOD = 2;
    /** The furthest any canopy draws, however large it is. */
    public static final int MAX_DRAW = 32;
    /** Gas this close to the outlet has just come out of it and is left alone. */
    private static final int OUTLET_CLEARANCE = 4;
    /** The most places one draw looks through. */
    private static final int MAX_DRAW_SEARCH = 40_000;

    private Flues() { }

    private static boolean part(BlockState state) {
        return state.getBlock() instanceof ForgeHoodBlock || state.getBlock() instanceof GasPipeBlock;
    }

    /** The place the gas caught by this hood comes out, or null where the flue has no way out. */
    public static BlockPos outlet(Level level, BlockPos hood) {
        if (!(level.getBlockState(hood).getBlock() instanceof ForgeHoodBlock)) return null;
        return outletFrom(level, hood);
    }

    /**
     * The way out of the flue this hood or pipe belongs to, wherever the gas enters it: a well head
     * lets its gas into the pipe on top of it the same way a hood lets in what it catches.
     */
    public static BlockPos outletFrom(Level level, BlockPos start) {
        if (!part(level.getBlockState(start))) return null;
        BlockPos hood = start;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(hood.immutable());
        seen.add(hood.asLong());
        BlockPos chimney = null;
        int visited = 0;
        while (!queue.isEmpty() && visited++ < MAX_LENGTH) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof GasPipeBlock) {
                BlockPos end = openEnd(level, pos, state);
                if (end != null) return end;
            } else if (state.getBlock() instanceof ForgeHoodBlock) {
                BlockPos above = pos.above();
                if (!part(level.getBlockState(above)) && Gases.passable(level.getBlockState(above))
                        && (chimney == null || pos.getY() > chimney.getY() - 1)) {
                    chimney = above;
                }
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!level.isLoaded(next) || !seen.add(next.asLong())) continue;
                BlockState neighbour = level.getBlockState(next);
                if (!part(neighbour)) continue;
                // A pipe only carries on where it is actually joined.
                if (state.getBlock() instanceof GasPipeBlock
                        && !state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction))) continue;
                queue.add(next);
            }
        }
        return chimney;
    }

    /** The hoods joined into one canopy with this one: side by side and stacked, pipes not counted. */
    public static List<BlockPos> canopy(Level level, BlockPos hood) {
        List<BlockPos> hoods = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(hood.immutable());
        seen.add(hood.asLong());
        while (!queue.isEmpty() && hoods.size() < MAX_LENGTH) {
            BlockPos pos = queue.poll();
            if (!(level.getBlockState(pos).getBlock() instanceof ForgeHoodBlock)) continue;
            hoods.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (level.isLoaded(next) && seen.add(next.asLong())) queue.add(next);
            }
        }
        return hoods;
    }

    /** How far this many hoods draw. */
    public static int reach(int hoods) {
        return Math.min(MAX_DRAW, DRAW_PER_HOOD * hoods);
    }

    /**
     * Pulls the gas around the canopy of this hood to its outlet. The hoods of the canopy are added to
     * {@code done}, so a pass that meets the same canopy again does not draw twice. Returns how many
     * units were moved.
     */
    public static int draw(Level level, BlockPos hood, Set<Long> done) {
        List<BlockPos> hoods = canopy(level, hood);
        for (BlockPos pos : hoods) done.add(pos.asLong());
        if (hoods.isEmpty()) return 0;
        BlockPos outlet = outlet(level, hood);
        if (outlet == null) return 0;
        int reach = reach(hoods.size());
        int budget = Gas.CAPACITY * hoods.size();

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        for (BlockPos pos : hoods) {
            seen.add(pos.asLong());
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!seen.add(next.asLong()) || !level.isLoaded(next)) continue;
                if (Gases.passable(level.getBlockState(next))) queue.add(next);
            }
        }
        int moved = 0;
        int searched = 0;
        while (!queue.isEmpty() && budget > 0 && searched++ < MAX_DRAW_SEARCH) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            if (Gases.isGas(state) && pos.distSqr(outlet) > OUTLET_CLEARANCE * OUTLET_CLEARANCE) {
                for (Gas gas : Gas.values()) {
                    int units = Math.min(budget, Gases.units(level.getBlockState(pos), gas));
                    if (units == 0) continue;
                    int taken = units - Gases.emit(level, outlet, gas, units);
                    if (taken <= 0) return moved;
                    level.setBlock(pos, Gases.with(level.getBlockState(pos), gas, -taken), Block.UPDATE_ALL);
                    budget -= taken;
                    moved += taken;
                }
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!seen.add(next.asLong()) || !level.isLoaded(next) || !within(hoods, next, reach)) continue;
                if (Gases.passable(level.getBlockState(next))) queue.add(next);
            }
        }
        return moved;
    }

    private static boolean within(List<BlockPos> hoods, BlockPos pos, int reach) {
        long limit = (long) reach * reach;
        for (BlockPos hood : hoods) {
            if (hood.distSqr(pos) <= limit) return true;
        }
        return false;
    }

    /** The open end of a pipe joined on one side only, if there is room there for gas. */
    private static BlockPos openEnd(Level level, BlockPos pos, BlockState pipe) {
        Direction joined = null;
        for (Direction direction : Direction.values()) {
            if (!pipe.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction))) continue;
            if (joined != null) return null;
            joined = direction;
        }
        if (joined == null) return null;
        BlockPos end = pos.relative(joined.getOpposite());
        return level.isLoaded(end) && Gases.passable(level.getBlockState(end)) ? end : null;
    }
}
