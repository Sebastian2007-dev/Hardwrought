package de.ipnats.hardwrought.machinery;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Section 74: sails in the wind.
 *
 * <p>The wind is stronger the higher the sails stand — this world goes up to a thousand blocks, and
 * a mill on a peak does many times the work of one at the shore. Rain freshens it and a thunderstorm
 * more so, and it rises and falls on its own over some minutes. The sails sweep five blocks across,
 * and every block in the way takes a share of the wind; with too much in the way they do not turn.
 */
public class WindmillBlockEntity extends GeneratorBlockEntity {
    public static final int RADIUS = 2;
    public static final float MAX_SPEED = 24.0f;
    public static final float MAX_CAPACITY = 1024.0f;
    /** Below this share of the sails' circle standing free, there is not wind enough to turn them. */
    private static final float MIN_CLEAR = 0.6f;
    /** How long one rise and fall of the wind takes, in ticks. */
    private static final double GUST_PERIOD = 6000.0;

    public WindmillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WINDMILL, pos, state);
    }

    @Override
    protected int interval() {
        return 40;
    }

    @Override
    protected float[] measure(ServerLevel level, BlockPos pos, BlockState state) {
        float wind = wind(level, pos, state.getValue(WindmillBlock.FACING));
        if (wind <= 0.1f) return new float[] {0.0f, 0.0f};
        return new float[] {Math.min(MAX_SPEED, 4.0f + 12.0f * wind), Math.min(MAX_CAPACITY, 512.0f * wind)};
    }

    /** How much wind these sails catch, 0 to about 4. */
    static float wind(ServerLevel level, BlockPos hub, Direction facing) {
        float clear = clearShare(level, hub, facing);
        if (clear < MIN_CLEAR) return 0.0f;
        float height = (float) Math.clamp((hub.getY() - level.getSeaLevel()) / 64.0, 0.25, 3.0);
        float weather = level.isThundering() ? 1.6f : level.isRaining() ? 1.25f : 1.0f;
        // Each region of the world has its own moment in the cycle, so not every mill slows at once.
        double phase = (((hub.getX() >> 6) * 31 + (hub.getZ() >> 6) * 17) & 0xFF) / 256.0;
        float gust = (float) (0.7 + 0.3 * Math.sin(2 * Math.PI * (level.getGameTime() / GUST_PERIOD + phase)));
        return height * weather * gust * clear * clear;
    }

    /** The share of the sails' circle, hub excluded, that is open air. */
    static float clearShare(ServerLevel level, BlockPos hub, Direction facing) {
        int open = 0;
        int total = 0;
        for (int u = -RADIUS; u <= RADIUS; u++) {
            for (int v = -RADIUS; v <= RADIUS; v++) {
                if (u == 0 && v == 0) continue;
                int dx = facing.getAxis() == Direction.Axis.Z ? u : 0;
                int dz = facing.getAxis() == Direction.Axis.X ? u : 0;
                total++;
                if (level.getBlockState(hub.offset(dx, v, dz)).isAir()) open++;
            }
        }
        return open / (float) total;
    }
}
