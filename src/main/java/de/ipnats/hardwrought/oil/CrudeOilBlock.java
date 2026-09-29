package de.ipnats.hardwrought.oil;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Standing crude oil. It burns: fire catches on it and runs across a pool of it, lava sets it alight,
 * and what burns is used up. Its flammability is registered with the other burning blocks in
 * {@code Hardwrought}.
 */
public class CrudeOilBlock extends LiquidBlock {
    /** How readily fire beside it catches on it, and how readily it burns away: faster than leaves. */
    public static final int CATCH_CHANCE = 60;
    public static final int BURN_CHANCE = 100;

    public CrudeOilBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }
}
