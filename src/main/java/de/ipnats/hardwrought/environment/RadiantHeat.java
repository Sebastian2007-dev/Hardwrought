package de.ipnats.hardwrought.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Standing by a fire. Specification section 19 and the warming half of section 17.
 *
 * <p>A sealed room is warmed by what burns in it, and {@link EnvironmentSystem} has owned that for
 * a while. Outdoors there is no room to warm, and a fire still has to count for something — on a
 * winter night it is the difference between a camp and a corpse. So open flame radiates: strongest
 * at the flame, nothing at all a few blocks out.
 *
 * <p>Two things this is careful about that the old flat <q>+8 °C somewhere in a 7×5×7 box</q> was
 * not. A campfire that has been put out gives no heat, and neither does an unlit furnace; and a
 * fire two blocks away is worth a great deal more than one at the edge of its reach, so there is a
 * right distance to sit at and a player can find it.
 */
public final class RadiantHeat {
    /** How far any flame is felt at all. */
    public static final double RANGE = 5.0;
    /** Degrees at the flame itself for a source of full strength. */
    public static final double HEAT_AT_SOURCE = 26.0;
    /** However many fires are lit, the air around them only gets so hot. */
    public static final double MAX_HEAT = 35.0;

    /** Relative strength of each kind of flame. An open fire is the measure; the rest are less. */
    private static final double OPEN_FIRE = 1.00;
    private static final double CONTAINED_FIRE = 0.80;
    private static final double WORKING_FIRE = 0.60;
    private static final double SMALL_FLAME = 0.22;

    /** The box searched around the player. Kept small: this runs once a second per player. */
    private static final int REACH = 5;

    private RadiantHeat() { }

    /**
     * Degrees the fires around this position add to the air. The search is a bounded box and never
     * loads a chunk, in keeping with the rest of the environment passes.
     */
    public static double nearby(ServerLevel level, BlockPos pos) {
        double heat = 0;
        for (BlockPos candidate : BlockPos.betweenClosed(pos.offset(-REACH, -REACH, -REACH),
                pos.offset(REACH, REACH, REACH))) {
            double strength = strength(level.getBlockState(candidate));
            if (strength <= 0) continue;
            heat += strength * HEAT_AT_SOURCE * falloff(Math.sqrt(candidate.distSqr(pos)));
            if (heat >= MAX_HEAT) return MAX_HEAT;
        }
        return Math.min(MAX_HEAT, heat);
    }

    /**
     * How much of a flame's heat survives the distance to it. Square falloff rather than linear,
     * because the point of this is that a player can tell the difference between beside the fire
     * and near it.
     */
    public static double falloff(double distance) {
        if (distance >= RANGE) return 0;
        double share = 1.0 - distance / RANGE;
        return share * share;
    }

    /**
     * What this block is worth as a fire, from 0 for anything that is not burning. A campfire or a
     * furnace that is not lit is a block of nothing, and is treated as one.
     */
    public static double strength(BlockState state) {
        if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) return OPEN_FIRE;
        if (state.is(Blocks.MAGMA_BLOCK)) return WORKING_FIRE;
        if (state.getBlock() instanceof CampfireBlock) return lit(state) ? CONTAINED_FIRE : 0;
        if (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)) {
            return lit(state) ? WORKING_FIRE : 0;
        }
        if (RoomScan.isForgeFire(state)) return WORKING_FIRE;
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) {
            return SMALL_FLAME;
        }
        if (state.is(BlockTags.CANDLES) || state.is(BlockTags.CANDLE_CAKES)) {
            return lit(state) ? SMALL_FLAME : 0;
        }
        // Stage E3: a heat lamp warms like a working fire, while it is lit.
        if (state.is(de.ipnats.hardwrought.haven.HavenBlocks.of(de.ipnats.hardwrought.haven.Appliance.HEAT_LAMP))) {
            return state.getValue(de.ipnats.hardwrought.haven.ApplianceBlock.ON) ? WORKING_FIRE : 0;
        }
        return 0;
    }

    private static boolean lit(BlockState state) {
        return state.getOptionalValue(BlockStateProperties.LIT).orElse(false);
    }
}
