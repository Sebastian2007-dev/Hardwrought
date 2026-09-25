package de.ipnats.hardwrought.environment;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Reading and writing gas in the world: what a position holds, putting gas into it, and what the air
 * there is made of for whoever breathes it.
 *
 * <p>A position holds gas only as a {@link GasBlock}, and only where there is nothing else: air can
 * take gas, a gas block can take more until its eight places are full, and every other block is in
 * the way.
 */
public final class Gases {
    /** How far a puff looks for room when the place it was let out is already full. */
    private static final int EMIT_SEARCH = 3;

    private Gases() { }

    public static boolean isGas(BlockState state) {
        return state.getBlock() instanceof GasBlock;
    }

    public static int units(BlockState state, Gas gas) {
        return isGas(state) ? state.getValue(GasBlock.property(gas)) : 0;
    }

    public static int total(BlockState state) {
        if (!isGas(state)) return 0;
        int total = 0;
        for (Gas gas : Gas.values()) total += state.getValue(GasBlock.property(gas));
        return total;
    }

    /** How many more units fit here: all eight in plain air, the rest of a gas block, none elsewhere. */
    public static int room(BlockState state) {
        if (state.isAir()) return Gas.CAPACITY;
        if (isGas(state)) return Gas.CAPACITY - total(state);
        return 0;
    }

    /** Whether gas can be here at all, full or not: air or a gas block. */
    public static boolean passable(BlockState state) {
        return state.isAir() || isGas(state);
    }

    /** This position with one gas changed by the given amount. Air where nothing is left. */
    public static BlockState with(BlockState state, Gas gas, int delta) {
        BlockState base = isGas(state) ? state : ModBlocks.GAS.defaultBlockState();
        int now = units(state, gas) + delta;
        if (now < 0 || now > Gas.CAPACITY) throw new IllegalArgumentException(gas + " " + now);
        BlockState next = base.setValue(GasBlock.property(gas), now);
        return total(next) == 0 ? Blocks.AIR.defaultBlockState() : next;
    }

    /** Puts as much of this gas here as fits and returns how much did. */
    public static int add(Level level, BlockPos pos, Gas gas, int amount) {
        if (amount <= 0 || !level.isLoaded(pos)) return 0;
        BlockState state = level.getBlockState(pos);
        int moved = Math.min(amount, room(state));
        if (moved > 0) level.setBlock(pos, with(state, gas, moved), Block.UPDATE_ALL);
        return moved;
    }

    /**
     * Lets gas out at a place. What does not fit there goes to the nearest place near it that has
     * room, the way the gas itself is drifting first; what finds no room at all within a few blocks
     * has nowhere to go and stays in whatever made it. Returns how much found no room.
     */
    public static int emit(Level level, BlockPos pos, Gas gas, int amount) {
        int left = amount - add(level, pos, gas, amount);
        if (left <= 0) return 0;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(pos);
        seen.add(pos.asLong());
        while (!queue.isEmpty() && left > 0) {
            BlockPos at = queue.poll();
            for (Direction direction : order(gas)) {
                BlockPos next = at.relative(direction);
                if (next.distManhattan(pos) > EMIT_SEARCH || !seen.add(next.asLong())) continue;
                if (!level.isLoaded(next) || !passable(level.getBlockState(next))) continue;
                left -= add(level, next, gas, left);
                queue.add(next);
            }
        }
        return left;
    }

    /** The drift direction of a gas first, then sideways, then against its drift. */
    private static Direction[] order(Gas gas) {
        Direction drift = gas.drift();
        return new Direction[] {drift, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST,
                drift.getOpposite()};
    }

    /**
     * The air at a position, as whoever breathes it there gets it: the air outside at that height,
     * with the gas in the block pushing its share of the oxygen out.
     */
    public static GasMixture sample(LevelReader level, BlockPos pos) {
        GasMixture outside = Altitude.outsideAir(pos.getY());
        BlockState state = level.getBlockState(pos);
        if (!isGas(state)) return outside;
        double carbonDioxide = units(state, Gas.CARBON_DIOXIDE) * Gas.CARBON_DIOXIDE.fractionPerUnit()
                + units(state, Gas.DECAYED_CARBON_DIOXIDE) * Gas.DECAYED_CARBON_DIOXIDE.fractionPerUnit();
        double methane = units(state, Gas.METHANE) * Gas.METHANE.fractionPerUnit();
        double carbonMonoxide = units(state, Gas.CARBON_MONOXIDE) * Gas.CARBON_MONOXIDE.fractionPerUnit();
        double displaced = carbonDioxide + methane + carbonMonoxide;
        return new GasMixture(outside.oxygen() * (1.0 - displaced), outside.carbonDioxide() + carbonDioxide,
                methane, carbonMonoxide);
    }
}
