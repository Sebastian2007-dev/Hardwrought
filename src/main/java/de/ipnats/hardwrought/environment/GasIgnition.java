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
 *
 * <p>A pocket too rich to explode on its own, more than seven units, still goes off at a flame: at its
 * edge the gas is thinned by the air into the window, and the edge sets off the rest. Thin gas, below
 * three units, does not. A flame carried into the gas sets it off as much as one standing in it: a torch
 * or a lantern in hand, or a player on fire. The safety lamp is made for this, and does not.
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

    /** Whether a flame sets off the methane here: from the bottom of the window up, rich gas too. */
    public static boolean ignitable(BlockState state) {
        return Gases.units(state, Gas.METHANE) * Gas.METHANE.fractionPerUnit() >= GasMixture.METHANE_EXPLOSIVE_MIN;
    }

    /** How often players are checked for a flame they carry into gas. */
    private static final int CARRIED_CHECK = 5;

    public static void initialize() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_LEVEL_TICK.register(level -> {
            if (level.getGameTime() % CARRIED_CHECK != 0) return;
            for (net.minecraft.server.level.ServerPlayer player : List.copyOf(level.players())) {
                if (player.isSpectator() || !carriesFlame(player)) continue;
                BlockPos gas = gasAround(level, BlockPos.containing(player.getEyePosition()));
                if (gas == null) gas = gasAround(level, player.blockPosition());
                if (gas != null) ignite(level, gas);
            }
        });
    }

    /** A torch or lantern in either hand, or being on fire. */
    public static boolean carriesFlame(net.minecraft.world.entity.LivingEntity entity) {
        if (entity.isOnFire()) return true;
        for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
            net.minecraft.world.item.ItemStack held = entity.getItemInHand(hand);
            if (held.is(net.minecraft.world.item.Items.TORCH) || held.is(net.minecraft.world.item.Items.SOUL_TORCH)
                    || held.is(net.minecraft.world.item.Items.LANTERN) || held.is(net.minecraft.world.item.Items.SOUL_LANTERN)
                    || held.is(net.minecraft.world.item.Items.COPPER_TORCH)) {
                return true;
            }
        }
        return false;
    }

    /** The ignitable gas at a place, or, where one stands in a block gas passes through, beside it. */
    private static BlockPos gasAround(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (ignitable(state)) return pos;
        if (!Gases.permeable(state)) return null;
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            if (level.isLoaded(next) && ignitable(level.getBlockState(next))) return next;
        }
        return null;
    }

    /** Anything that burns in the open: fire, lava, a torch or candle, a lit hearth, furnace or forge. */
    public static boolean isFlame(BlockState state) {
        if (RoomScan.isCombustionSource(state)) return true;
        if (state.is(net.minecraft.world.level.block.Blocks.LANTERN) || state.is(net.minecraft.world.level.block.Blocks.SOUL_LANTERN)
                || state.is(net.minecraft.world.level.block.Blocks.COPPER_TORCH)
                || state.is(net.minecraft.world.level.block.Blocks.COPPER_WALL_TORCH)) {
            return true;
        }
        if (state.getBlock() instanceof de.ipnats.hardwrought.power.GasBurnerBlock
                || state.getBlock() instanceof de.ipnats.hardwrought.power.PetroleumLampBlock) {
            return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
        }
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
