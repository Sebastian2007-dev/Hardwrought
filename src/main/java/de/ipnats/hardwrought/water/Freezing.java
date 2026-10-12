package de.ipnats.hardwrought.water;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Water turning to ice and back. Specification sections 23.1 and 24.
 *
 * <p>Freezing is the one thing the finite-water system could never do, and the reason was always
 * the same: vanilla ice is a whole block and Hardwrought water is measured in millibuckets. With
 * {@link ThinIceBlock} the two line up, and the rule becomes the simple one it should have been —
 * a cell freezes at the height it stood at, and thaws back to the millibucket it held.
 *
 * <p>What decides it is the <strong>air</strong> over the water, not the water's own temperature.
 * That distinction cost a winter: {@link de.ipnats.hardwrought.survival.Thermal#waterTemperature}
 * models the bulk of a lake, which is slow and deep and takes on under half the air's swing, and is
 * exactly right for deciding what a lake does to a body in it. Used as a freezing test it meant the
 * air had to reach −15 °C before a surface would skin over, so a winter plains at 0 °C never froze
 * at all. A surface freezes when the air above it is below freezing; the depths of the lake have
 * nothing to do with it.
 *
 * <p>Only the surface freezes, so a frozen lake still has a lake under it and cutting a hole in one
 * is worth doing — which is also why a cold biome is survivable in winter at all: the water a
 * player needs is still there, it merely has a lid on it now.
 *
 * <p>Driven from {@link de.ipnats.hardwrought.environment.Weathering}, which rides vanilla's own
 * per-chunk precipitation tick, so lakes freeze across the world rather than around a player.
 */
public final class Freezing {
    /**
     * Air temperature at or below which a surface cell skins over, and the one it has to climb back
     * past before the ice goes. The gap between them is not an accident: a winter plains sits near
     * 0 °C and swings either side of it between noon and midnight, and a single threshold would
     * have the lake freezing and thawing twice a day.
     */
    public static final double FREEZES_AT = -2.0;
    public static final double THAWS_AT = 2.0;

    /** Freezing is not instant: a surface takes a few passes to close over, which looks like weather. */
    private static final float FREEZE_CHANCE = 0.45f;
    private static final float THAW_CHANCE = 0.35f;

    private Freezing() { }

    /** One surface cell, from the weathering pass. Freezes it, thaws it, or leaves it alone. */
    public static void tickSurface(ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockState(pos).is(ModBlocks.THIN_ICE)) {
            if (random.nextFloat() < THAW_CHANCE) thawIfWarm(level, pos);
        } else if (random.nextFloat() < FREEZE_CHANCE) {
            freezeIfCold(level, pos);
        }
    }

    /** The air over this block, which is what decides whether it freezes. */
    public static double airTemperature(ServerLevel level, BlockPos pos) {
        return CoreLifecycle.require(level.getServer()).environment().outdoorTemperature(level, pos);
    }

    /**
     * Freeze this cell if it is water, exposed, and the air over it is cold enough. The ice takes
     * the shape the water stood at, and the water itself goes into {@link FrozenWater} until the
     * thaw.
     */
    public static boolean freezeIfCold(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!WaterStorage.isFreeWater(state)) return false;
        // Only a surface freezes; water with more water on top of it is not a surface.
        if (WaterStorage.containsWater(level.getBlockState(pos.above()))) return false;
        if (!level.canSeeSky(pos.above())) return false;
        if (airTemperature(level, pos.above()) > FREEZES_AT) return false;

        int held = WaterStorage.amount(level, pos);
        if (held <= 0) return false;
        BlockState ice = ModBlocks.THIN_ICE.defaultBlockState()
                .setValue(ThinIceBlock.LAYERS, ThinIceBlock.layersFor(held));
        // Take the water out of the liquid table first, or the solver will try to drain the ice.
        WaterStorage.setAmount(level, pos, 0);
        level.setBlock(pos, ice, Block.UPDATE_ALL);
        FrozenWater.hold(level, pos, held);
        return true;
    }

    /** Give the water back if the thaw has come. Called from the pass and from the ice's own tick. */
    public static void thawIfWarm(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(ModBlocks.THIN_ICE)) return;
        if (airTemperature(level, pos.above()) < THAWS_AT) return;
        thaw(level, pos);
    }

    /**
     * Turn this ice back into the water it was made of. Removing the block is enough — the ice puts
     * its own water back as it goes — but the neighbours are woken too, so a hole in a frozen lake
     * fills from the lake rather than sitting there as a pit.
     */
    public static void thaw(ServerLevel level, BlockPos pos) {
        int held = FrozenWater.take(level, pos);
        level.removeBlock(pos, false);
        if (held > 0) WaterStorage.setAmount(level, pos, held);
        WaterFlow.disturb(level, pos);
        for (Direction direction : Direction.values()) WaterFlow.disturb(level, pos.relative(direction));
    }
}
