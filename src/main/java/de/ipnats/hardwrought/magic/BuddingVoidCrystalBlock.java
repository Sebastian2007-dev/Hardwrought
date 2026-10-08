package de.ipnats.hardwrought.magic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/** Grows void-crystal buds exactly like budding amethyst grows amethyst buds. */
public final class BuddingVoidCrystalBlock extends AmethystBlock {
    public BuddingVoidCrystalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0) return;
        Direction direction = Direction.values()[random.nextInt(Direction.values().length)];
        BlockPos growthPos = pos.relative(direction);
        BlockState growth = level.getBlockState(growthPos);
        Block next = null;
        if (growth.isAir() || growth.is(Blocks.WATER) && growth.getFluidState().isFull()) {
            next = VoidCrystal.SMALL_BUD;
        } else if (growth.is(VoidCrystal.SMALL_BUD)
                && growth.getValue(AmethystClusterBlock.FACING) == direction) {
            next = VoidCrystal.MEDIUM_BUD;
        } else if (growth.is(VoidCrystal.MEDIUM_BUD)
                && growth.getValue(AmethystClusterBlock.FACING) == direction) {
            next = VoidCrystal.LARGE_BUD;
        } else if (growth.is(VoidCrystal.LARGE_BUD)
                && growth.getValue(AmethystClusterBlock.FACING) == direction) {
            next = VoidCrystal.CLUSTER;
        }
        if (next == null) return;
        level.setBlockAndUpdate(growthPos, next.defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, direction)
                .setValue(AmethystClusterBlock.WATERLOGGED, growth.getFluidState().is(Fluids.WATER)));
    }
}
