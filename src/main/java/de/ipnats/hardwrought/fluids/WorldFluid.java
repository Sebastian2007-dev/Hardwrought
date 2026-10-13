package de.ipnats.hardwrought.fluids;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FluidState;

/**
 * A liquid standing in the world, as a pipe draws it: one source block at a time, a bucket each.
 * Water is left alone: Hardwrought's water is measured by the millibucket in its own table, and a pipe
 * that took a vanilla bucket of it would make water out of nothing.
 */
final class WorldFluid {
    private WorldFluid() { }

    /** What a source block of liquid here is, as a variant; null where there is none a pipe may draw. */
    static FluidVariant source(Level level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        if (fluid.isEmpty() || !fluid.isSource() || fluid.is(FluidTags.WATER)) return null;
        // Only a block that is nothing but the liquid.
        if (!(level.getBlockState(pos).getBlock() instanceof LiquidBlock)) return null;
        return FluidVariant.of(fluid.getType());
    }

    /** Takes the source block away, as a bucket would. */
    static void take(Level level, BlockPos pos) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }
}
