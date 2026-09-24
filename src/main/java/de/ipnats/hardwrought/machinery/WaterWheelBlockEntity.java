package de.ipnats.hardwrought.machinery;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import de.ipnats.hardwrought.water.WaterCurrent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Section 74: a wheel turned by the water it stands in.
 *
 * <p>The wheel is three blocks across. Each of the eight cells around its hub that holds water adds
 * the push of that water's current along the way the paddle there moves — so a wheel set across a
 * river with its lower paddles in the water turns, a wheel set along the river does not, and one
 * with water falling past one side turns as an overshot wheel does. Still water turns nothing.
 *
 * <p>Every cell of the wheel has to be clear — air or water — or it jams.
 */
public class WaterWheelBlockEntity extends GeneratorBlockEntity {
    /** Turns per minute for each unit of current pushing the paddles round. */
    private static final float SPEED_PER_PUSH = 8.0f;
    public static final float MAX_SPEED = 32.0f;
    /** Strength for each unit of push: a wheel dipping three paddles into a river gives about 460. */
    private static final float CAPACITY_PER_PUSH = 256.0f;
    public static final float MAX_CAPACITY = 768.0f;

    public WaterWheelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WATER_WHEEL, pos, state);
    }

    @Override
    protected int interval() {
        return 20;
    }

    @Override
    protected float[] measure(ServerLevel level, BlockPos pos, BlockState state) {
        float push = push(level, pos, state.getValue(WaterWheelBlock.AXIS));
        if (Float.isNaN(push) || Math.abs(push) < 0.05f) return new float[] {0.0f, 0.0f};
        float speed = Math.clamp(push * SPEED_PER_PUSH, -MAX_SPEED, MAX_SPEED);
        return new float[] {speed, Math.min(MAX_CAPACITY, Math.abs(push) * CAPACITY_PER_PUSH)};
    }

    /** The water's push round the wheel's axle, or NaN where something solid is in the wheel's way. */
    static float push(ServerLevel level, BlockPos hub, Direction.Axis axis) {
        float push = 0.0f;
        for (int u = -1; u <= 1; u++) {
            for (int v = -1; v <= 1; v++) {
                if (u == 0 && v == 0) continue;
                // The wheel turns in the plane across its axle: offsets (x, y) about Z, (z, y) about X.
                int dx = axis == Direction.Axis.Z ? u : 0;
                int dz = axis == Direction.Axis.X ? u : 0;
                BlockPos cell = hub.offset(dx, v, dz);
                BlockState there = level.getBlockState(cell);
                boolean water = there.getFluidState().is(FluidTags.WATER);
                if (!water && !there.isAir() && !there.canBeReplaced()) return Float.NaN;
                if (!water) continue;
                Vec3 current = WaterCurrent.at(level, cell);
                // Which way the paddle at this offset moves when the wheel turns forwards: axle × arm.
                Vec3 paddle = axis == Direction.Axis.X ? new Vec3(0, -dz, v) : new Vec3(-v, dx, 0);
                push += (float) current.dot(paddle);
            }
        }
        return push;
    }
}
