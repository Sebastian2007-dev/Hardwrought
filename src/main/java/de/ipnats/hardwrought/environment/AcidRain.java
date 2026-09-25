package de.ipnats.hardwrought.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The rain washing out the gas under the clouds, and what it does when there is too much of it.
 *
 * <p>Wherever it rains, the rain takes the gas of the cloud layer above with it, a little at a time.
 * Where the layer is thin that is all. Where it is thick — six units or more in a block — the rain
 * that brings it down is acid, and it poisons what grows beneath: crops wither back and die, flowers
 * and grass die off, grass turns to bare dirt, and wild trees lose their leaves. Leaves someone put
 * there on purpose are left alone.
 */
public final class AcidRain {
    /** A cloud block this full makes the rain beneath it acid. */
    public static final int ACID = 6;
    /** How much of a block the rain takes with it at a time. */
    public static final int WASHED_PER_TICK = 2;
    /** How far from under the cloud block the acid lands. */
    private static final int SPREAD = 6;

    private AcidRain() { }

    /**
     * Lets the rain wash out some of the cloud gas at this place, if it is raining on the ground
     * beneath it. Returns how many units went.
     */
    public static int washOut(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState state = level.getBlockState(pos);
        int total = Gases.total(state);
        if (total == 0 || !level.isRaining()) return 0;
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        if (!level.isRainingAt(ground)) return 0;
        int washed = 0;
        for (Gas gas : Gas.values()) {
            while (washed < WASHED_PER_TICK && Gases.units(state, gas) > 0) {
                state = Gases.with(state, gas, -1);
                washed++;
            }
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (total >= ACID) fall(level, pos, random, total - ACID + 1);
        return washed;
    }

    /** Acid rain on a few places around the column under this position. */
    public static void fall(ServerLevel level, BlockPos above, RandomSource random, int drops) {
        for (int i = 0; i < drops; i++) {
            int x = above.getX() + random.nextInt(2 * SPREAD + 1) - SPREAD;
            int z = above.getZ() + random.nextInt(2 * SPREAD + 1) - SPREAD;
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
            if (level.isLoaded(surface)) poison(level, surface);
        }
    }

    /**
     * What one drop of acid rain does at the surface: to what grows there, or else to the block it
     * lands on. Returns whether anything was harmed.
     */
    public static boolean poison(ServerLevel level, BlockPos surface) {
        BlockState plant = level.getBlockState(surface);
        if (plant.getBlock() instanceof CropBlock crop) {
            int age = crop.getAge(plant);
            if (age > 0) level.setBlock(surface, crop.getStateForAge(age - 1), Block.UPDATE_ALL);
            else level.destroyBlock(surface, false);
            return true;
        }
        if (plant.is(BlockTags.FLOWERS) || plant.is(BlockTags.SAPLINGS) || plant.is(Blocks.SHORT_GRASS)
                || plant.is(Blocks.TALL_GRASS) || plant.is(Blocks.FERN) || plant.is(Blocks.LARGE_FERN)) {
            level.destroyBlock(surface, false);
            return true;
        }
        BlockPos below = surface.below();
        BlockState ground = level.getBlockState(below);
        if (ground.is(BlockTags.LEAVES)) {
            if (ground.hasProperty(LeavesBlock.PERSISTENT) && ground.getValue(LeavesBlock.PERSISTENT)) return false;
            level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return true;
        }
        if (ground.is(Blocks.GRASS_BLOCK)) {
            level.setBlock(below, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
            return true;
        }
        return false;
    }
}
