package de.ipnats.hardwrought.nether;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;

/**
 * Nether grass: the grey, ash-dusted turf that covers the netherrack of the Ash Grove ({@link AshGrove}),
 * as nylium covers it in the crimson and warped forests. Like nylium it is only a skin: covered over,
 * it dies back to netherrack.
 */
public class NetherGrassBlock extends Block {
    public NetherGrassBlock(Properties properties) {
        super(properties);
    }

    /** Whether turf can live here: something has to be able to breathe above it. */
    static boolean canLive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos above = pos.above();
        BlockState top = level.getBlockState(above);
        int lightBlock = LightEngine.getLightDampeningInto(state, top, net.minecraft.core.Direction.UP, top.getLightDampening());
        return lightBlock < 15;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!canLive(state, level, pos)) level.setBlockAndUpdate(pos, Blocks.NETHERRACK.defaultBlockState());
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }
}
