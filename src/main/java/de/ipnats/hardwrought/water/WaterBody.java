package de.ipnats.hardwrought.water;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.function.LongConsumer;

/**
 * One bounded flood fill over connected water, the counterpart of the environmental cell of
 * Milestone 3. Specification section 23.4 makes evaporation depend on the size of the water body, so
 * the size has to be a real, measured value rather than a guess from the block underfoot.
 *
 * <p>The same budget rule applies: a fill that closes inside the budget is a measured body, one that
 * runs past it is a {@link Size#LARGE} body whose real volume is never counted. An ocean is not
 * going to be drained by hand, and counting it would be the kind of unbounded work section 105
 * rules out.
 */
public record WaterBody(Size size, int volume, int millibuckets, int sources, int skyExposed,
                        int lowestY, int highestY) {
    public enum Size {
        /** No water at the position at all. */
        NONE,
        /** Small enough to have been counted completely: a puddle, a pool, a well shaft. */
        MEASURED,
        /** Past the budget: a lake, a river or an ocean. */
        LARGE
    }

    /** The largest body Hardwrought counts block by block. */
    public static final int MAX_VOLUME = 512;
    /** At or below this a body is a puddle: shallow enough for the sun to take it. */
    public static final int PUDDLE_VOLUME = 24;

    public static final WaterBody NONE = new WaterBody(Size.NONE, 0, 0, 0, 0, 0, 0);

    public WaterBody {
        if (size == null || volume < 0 || millibuckets < 0 || sources < 0 || skyExposed < 0
                || highestY < lowestY) {
            throw new IllegalArgumentException("Invalid water body");
        }
    }

    public boolean exists() {
        return size != Size.NONE;
    }

    /** What the whole body actually holds, which is what a bucket or a drought takes from. */
    public int litres() {
        return millibuckets / 1000;
    }

    /** A body small enough that losing a block to the sun is a real change to it. */
    public boolean puddle() {
        return size == Size.MEASURED && volume <= PUDDLE_VOLUME;
    }

    /** How much of the body the sun and wind can actually reach, 0 to 1. */
    public double exposure() {
        return volume == 0 ? 0 : Math.min(1.0, skyExposed / (double) volume);
    }

    public int depth() {
        return highestY - lowestY + 1;
    }

    public static WaterBody scan(ServerLevel level, BlockPos start) {
        return scan(level, start, ignored -> { });
    }

    /** Package-private visitor lets nearby callers avoid measuring one connected body repeatedly. */
    static WaterBody scan(ServerLevel level, BlockPos start, LongConsumer waterVisitor) {
        if (!level.hasChunkAt(start) || !isWater(level, start)) return NONE;

        var queue = new LongArrayFIFOQueue();
        var visited = new LongOpenHashSet();
        queue.enqueue(start.asLong());
        visited.add(start.asLong());

        int volume = 0;
        int millibuckets = 0;
        int sources = 0;
        int skyExposed = 0;
        int lowestY = start.getY();
        int highestY = start.getY();

        while (!queue.isEmpty()) {
            BlockPos pos = BlockPos.of(queue.dequeueLong());
            waterVisitor.accept(pos.asLong());
            volume++;
            if (volume > MAX_VOLUME) {
                return new WaterBody(Size.LARGE, MAX_VOLUME, millibuckets, sources, skyExposed, lowestY, highestY);
            }
            FluidState fluid = level.getFluidState(pos);
            millibuckets += WaterStorage.amount(level, pos);
            if (fluid.isSource()) sources++;
            if (pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - 1) {
                skyExposed++;
            }
            lowestY = Math.min(lowestY, pos.getY());
            highestY = Math.max(highestY, pos.getY());
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                // Never load a chunk to finish counting; an unfinished count is a large body.
                if (!level.hasChunkAt(next)) {
                    return new WaterBody(Size.LARGE, MAX_VOLUME, millibuckets, sources, skyExposed, lowestY, highestY);
                }
                if (!visited.add(next.asLong())) continue;
                if (isWater(level, next)) queue.enqueue(next.asLong());
            }
        }
        return new WaterBody(Size.MEASURED, volume, millibuckets, sources, skyExposed, lowestY, highestY);
    }

    public static boolean isWater(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }
}
