package de.ipnats.hardwrought.environment;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Snow that piles up, and melts off again. Specification section 24.
 *
 * <p>Vanilla snow is one layer and then it stops, because {@code maxSnowAccumulationHeight} is 1
 * and because a snow layer may only sit on another one when that one is a full eight layers deep.
 * Both of those are worked with rather than against here: this adds layers the way vanilla would
 * have, and when a block of snow is full it starts the next one on top of it. A long winter in open
 * country therefore buries the ground, and the drifts are deepest where the sky is widest.
 *
 * <p>How deep it gets is the interesting part. Under a canopy the snow barely gathers, in the open
 * it gathers to three blocks by deep winter, and in any other season a fall settles to about a
 * block and no more. That gives a player somewhere to walk — treelines and overhangs stay passable
 * while the fields do not — which is the difference between weather and an obstacle.
 *
 * <p>Driven by {@link Weathering} from vanilla's own per-chunk precipitation tick, so snow gathers
 * evenly across the whole loaded world rather than in a circle around whoever is watching.
 */
public final class Snowfall {
    /** A full block of snow, in layers. */
    private static final int LAYERS_PER_BLOCK = SnowLayerBlock.MAX_HEIGHT;
    /** The most snow any column may hold, in whole blocks, at the very deepest of winter. */
    private static final int MAX_DRIFT_BLOCKS = 3;
    /** What gathers under a canopy, where most of the fall never reaches the ground. */
    private static final int SHELTERED_LAYERS = 3;
    /** What a fall outside winter settles to: a covering, not a drift. */
    private static final int ORDINARY_LAYERS = LAYERS_PER_BLOCK;

    /**
     * Above this, lying snow goes.
     *
     * <p>Deliberately well clear of freezing, and the reason is the sun. {@code outdoorTemperature}
     * adds three degrees to an open, sunlit place, so a winter plains at 0 °C reads as 3 °C at
     * midday — and a melting point of 1.5 had snow quietly evaporating off the fields every winter
     * afternoon faster than it could fall. Four degrees leaves a winter day below it and a spring
     * day (14 °C, 17 in the sun) far above it, which is where the thaw belongs.
     */
    private static final double MELTS_ABOVE = 4.0;
    /** Melting is slower than falling; a thaw should take a morning rather than a minute. */
    private static final float MELT_CHANCE = 0.25f;
    /** How far down a column is searched for the snow already lying in it. */
    private static final int SEARCH_DEPTH = MAX_DRIFT_BLOCKS + 2;

    private Snowfall() { }

    /** Add a layer here if it is snowing, there is room, and the snow has somewhere to sit. */
    static void settle(ServerLevel level, BlockPos top, RandomSource random) {
        Biome biome = level.getBiome(top).value();
        if (biome.getPrecipitationAt(top, level.getSeaLevel()) != Biome.Precipitation.SNOW) return;
        if (!level.isInsideBuildHeight(top.getY())) return;

        BlockPos base = base(level, top);
        int depth = depth(level, base);
        if (depth >= maxDepth(level, base)) return;

        BlockPos crown = base.offset(0, Math.max(0, (depth - 1) / LAYERS_PER_BLOCK), 0);
        BlockState state = level.getBlockState(crown);
        if (state.is(Blocks.SNOW)) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            if (layers < LAYERS_PER_BLOCK) {
                level.setBlockAndUpdate(crown, state.setValue(SnowLayerBlock.LAYERS, layers + 1));
                return;
            }
            crown = crown.above();
        }
        BlockState fresh = Blocks.SNOW.defaultBlockState();
        if (level.getBlockState(crown).isAir() && fresh.canSurvive(level, crown)) {
            level.setBlockAndUpdate(crown, fresh);
        }
    }

    /** Take a layer back off if the air here has turned. */
    static void thaw(ServerLevel level, BlockPos top, RandomSource random) {
        if (random.nextFloat() >= MELT_CHANCE) return;
        BlockPos base = base(level, top);
        if (!level.getBlockState(base).is(Blocks.SNOW)) return;
        if (outdoorTemperature(level, base) <= MELTS_ABOVE) return;

        int depth = depth(level, base);
        BlockPos crown = base.offset(0, Math.max(0, (depth - 1) / LAYERS_PER_BLOCK), 0);
        BlockState state = level.getBlockState(crown);
        if (!state.is(Blocks.SNOW)) return;
        int layers = state.getValue(SnowLayerBlock.LAYERS);
        if (layers > 1) {
            level.setBlockAndUpdate(crown, state.setValue(SnowLayerBlock.LAYERS, layers - 1));
        } else {
            level.removeBlock(crown, false);
        }
    }

    /**
     * The lowest snow block in this column. The heightmap stops at the first block that blocks
     * motion, and a thin layer of snow does not, so the top of a drift and the top of the column
     * are not the same place and have to be walked between.
     */
    private static BlockPos base(ServerLevel level, BlockPos top) {
        BlockPos base = top;
        for (int step = 0; step < SEARCH_DEPTH * LAYERS_PER_BLOCK; step++) {
            if (!level.getBlockState(base.below()).is(Blocks.SNOW)) break;
            base = base.below();
        }
        return base;
    }

    /** How many layers of snow are lying here, counted across however many blocks they fill. */
    private static int depth(ServerLevel level, BlockPos base) {
        int depth = 0;
        for (int step = 0; step <= SEARCH_DEPTH; step++) {
            BlockState state = level.getBlockState(base.above(step));
            if (!state.is(Blocks.SNOW)) break;
            depth += state.getValue(SnowLayerBlock.LAYERS);
        }
        return depth;
    }

    /**
     * How deep the snow is allowed to get here, in layers. Winter in the open is the only place
     * that drifts; a canopy keeps most of it off, and the rest of the year a fall is a covering.
     */
    public static int maxDepth(ServerLevel level, BlockPos pos) {
        boolean open = level.canSeeSky(pos);
        if (SeasonState.season() != Season.WINTER) {
            return open ? ORDINARY_LAYERS : SHELTERED_LAYERS;
        }
        if (!open) return SHELTERED_LAYERS;
        // Deep winter drifts deepest: how far into the season it is, is how far in the drifts are.
        double through = SeasonState.dayInSeason() / (double) SeasonCycle.DAYS_PER_SEASON;
        int blocks = 1 + (int) Math.round((MAX_DRIFT_BLOCKS - 1) * Math.min(1.0, through));
        return blocks * LAYERS_PER_BLOCK;
    }

    static double outdoorTemperature(ServerLevel level, BlockPos pos) {
        return CoreLifecycle.require(level.getServer()).environment().outdoorTemperature(level, pos);
    }
}
