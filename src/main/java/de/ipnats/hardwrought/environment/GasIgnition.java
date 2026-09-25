package de.ipnats.hardwrought.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Section 18.3: firedamp. Methane between five and fifteen percent of the air — three to seven units
 * in a gas block — goes off at the first flame it touches, and the whole connected pocket goes with
 * it. What burns becomes carbon dioxide, one unit for one, as methane does.
 */
public final class GasIgnition {
    /** The most gas blocks one blast takes with it; a mine-sized pocket is still bounded work. */
    private static final int MAX_POCKET = 512;

    private GasIgnition() { }

    /** Whether the methane in this gas block is inside its flammability window. */
    public static boolean explosive(BlockState state) {
        double methane = Gases.units(state, Gas.METHANE) * Gas.METHANE.fractionPerUnit();
        return methane >= GasMixture.METHANE_EXPLOSIVE_MIN && methane <= GasMixture.METHANE_EXPLOSIVE_MAX;
    }

    /** Anything that burns in the open: fire, lava, a torch or candle, a lit hearth, furnace or forge. */
    public static boolean isFlame(BlockState state) {
        if (RoomScan.isCombustionSource(state)) return true;
        return state.getBlock() instanceof AbstractFurnaceBlock
                && state.getOptionalValue(BlockStateProperties.LIT).orElse(false);
    }

    public static boolean flameBeside(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            if (level.isLoaded(next) && isFlame(level.getBlockState(next))) return true;
        }
        return false;
    }

    /** Sets off the methane pocket this position belongs to. */
    public static void ignite(ServerLevel level, BlockPos start) {
        List<BlockPos> pocket = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        seen.add(start.asLong());
        while (!queue.isEmpty() && pocket.size() < MAX_POCKET) {
            BlockPos pos = queue.poll();
            if (Gases.units(level.getBlockState(pos), Gas.METHANE) == 0) continue;
            pocket.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (level.isLoaded(next) && seen.add(next.asLong())) queue.add(next);
            }
        }
        if (pocket.isEmpty()) return;
        int burnt = 0;
        int[] perBlock = new int[pocket.size()];
        for (int i = 0; i < pocket.size(); i++) {
            BlockPos pos = pocket.get(i);
            BlockState state = level.getBlockState(pos);
            perBlock[i] = Gases.units(state, Gas.METHANE);
            burnt += perBlock[i];
            level.setBlock(pos, Gases.with(state, Gas.METHANE, -perBlock[i]), Block.UPDATE_ALL);
        }
        // One blast where it caught, and for a large pocket more along it, so a gallery full of gas
        // goes off along its length rather than at one end.
        float power = (float) Math.min(8.0, 1.5 + burnt / 6.0);
        int blasts = Math.min(4, 1 + pocket.size() / 24);
        for (int i = 0; i < blasts; i++) {
            BlockPos at = pocket.get(i * pocket.size() / blasts);
            level.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, power / blasts + 1.0f,
                    true, Level.ExplosionInteraction.BLOCK);
        }
        for (int i = 0; i < pocket.size(); i++) {
            if (perBlock[i] > 0) Gases.emit(level, pocket.get(i), Gas.CARBON_DIOXIDE, perBlock[i]);
        }
    }
}
