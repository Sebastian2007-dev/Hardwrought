package de.ipnats.hardwrought.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/**
 * Finds high land without generating any of it: the height of the land at a place is read off the
 * same terrain function the generator uses, so a mountain can be found a day's walk away in a
 * fraction of a second and no chunk is made to look for it.
 *
 * <p>What is read is the height of the land before its peaks and its caves are carved: the real
 * summit of a jagged range stands some tens of blocks above the figure given, and the ground at the
 * very spot may have a hollow in it. It is where to go, not where to stand.
 */
public final class MountainFinder {
    /** How far apart the places looked at are, and how far out the search goes. */
    public static final int STEP = 96, REACH = 48_000;
    /** The measure of depth is 1.5 at -64 and falls by one every 128 blocks; at height nought it is 1. */
    private static final double DEPTH_AT_ZERO = 1.0;

    private MountainFinder() { }

    /** The height of the land at this place as the terrain function gives it, or null in a world that is not made of noise. */
    public static Integer landHeight(ServerLevel level, int x, int z) {
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) return null;
        return landHeight(level.getChunkSource().randomState(), generator.generatorSettings().value().noiseRouter().depth(), x, z);
    }

    private static int landHeight(RandomState state, DensityFunction depth, int x, int z) {
        double offset = state.sampleBlockValueUncached(depth, x, 0, z) - DEPTH_AT_ZERO;
        return (int) Math.round(-64 + (1.5 + offset) * 128);
    }

    /**
     * The nearest place where the land stands at least this high, searched outward ring by ring, with
     * its height as Y. Null where there is none within {@link #REACH}, or the world is not made of noise.
     */
    public static BlockPos nearest(ServerLevel level, BlockPos from, int height) {
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) return null;
        RandomState state = level.getChunkSource().randomState();
        DensityFunction depth = generator.generatorSettings().value().noiseRouter().depth();
        for (int ring = 0; ring * STEP <= REACH; ring++) {
            BlockPos best = null;
            double nearest = Double.MAX_VALUE;
            for (int i = -ring; i <= ring; i++) {
                for (int side = 0; side < 4; side++) {
                    // The four sides of a square ring; its corners are met twice, which costs nothing worth saving.
                    int dx = side == 0 ? i : side == 1 ? i : side == 2 ? -ring : ring;
                    int dz = side == 0 ? -ring : side == 1 ? ring : i;
                    int x = from.getX() + dx * STEP, z = from.getZ() + dz * STEP;
                    int land = landHeight(state, depth, x, z);
                    double away = Math.hypot(dx, dz);
                    if (land >= height && away < nearest) {
                        nearest = away;
                        best = new BlockPos(x, land, z);
                    }
                    if (ring == 0) break;
                }
                if (ring == 0) break;
            }
            // A square ring's nearest hit can be farther than a hit on the next ring's side; one more ring settles it.
            if (best != null) return climb(state, depth, best);
        }
        return null;
    }

    /** From a place that is high enough, uphill to the top of what it stands on, so the answer is a summit and not a shoulder. */
    private static BlockPos climb(RandomState state, DensityFunction depth, BlockPos start) {
        BlockPos at = start;
        for (int step = 0; step < 200; step++) {
            BlockPos higher = at;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = at.getX() + dx * 32, z = at.getZ() + dz * 32;
                    int land = landHeight(state, depth, x, z);
                    if (land > higher.getY()) higher = new BlockPos(x, land, z);
                }
            }
            if (higher == at) break;
            at = higher;
        }
        return at;
    }
}
