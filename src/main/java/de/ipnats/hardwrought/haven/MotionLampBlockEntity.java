package de.ipnats.hardwrought.haven;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * A lamp with an eye — a sensor of a quartz oscillator and a circuit — that is dark until something
 * moves within {@link #REACH} blocks of it: then it lights for {@link #HOLD_SECONDS}, and while lit it
 * gives a redstone signal, so that it can ring a bell or close a door. Its sensor needs current: an unlit
 * line sees nothing.
 *
 * <p>Anything alive that moves counts, a creeper as much as a cow or a player; standing still, nothing
 * does.
 */
public class MotionLampBlockEntity extends BulbBlockEntity {
    public static final double REACH = 8.0;
    public static final int HOLD_SECONDS = 10;
    /** How far something must have moved in its last step to be seen moving. */
    private static final double MOVED = 0.05;

    private int hold;

    public MotionLampBlockEntity(BlockPos pos, BlockState state) {
        super(HavenBlocks.MOTION_LAMP_ENTITY, pos, state);
    }

    /** Whether it has seen something and is lit for it. */
    public boolean triggered() {
        return hold > 0;
    }

    @Override
    protected boolean wants() {
        return hold > 0;
    }

    /** The sensor is fed from the line whether the lamp is lit or not: it looks while there is voltage. */
    @Override
    protected void work(ServerLevel level, double power) {
        super.work(level, power);
        boolean live = Math.abs(volts()) >= appliance().ratedVolts() * STALL_SHARE || hold > 0;
        boolean seen = live && seesMovement(level);
        int before = hold;
        if (seen) hold = HOLD_SECONDS;
        else if (hold > 0) hold--;
        if ((before > 0) != (hold > 0)) {
            electricDirty = true;
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
        if (seen != (before > 0)) setChanged();
    }

    private boolean seesMovement(ServerLevel level) {
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(worldPosition).inflate(REACH),
                entity -> entity.isAlive() && !entity.isSpectator() && !(entity instanceof ArmorStand));
        for (LivingEntity entity : near) {
            double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo;
            if (dx * dx + dz * dz > MOVED * MOVED) return true;
        }
        return false;
    }

    /** Lit for something it saw, it gives a full signal. */
    public int signal() {
        return hold > 0 && !getBlockState().getValue(ApplianceBlock.BROKEN) ? 15 : 0;
    }

    /** Seen something: for tests, as if it had. */
    public void sense(ServerLevel level) {
        hold = HOLD_SECONDS;
        electricDirty = true;
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        setChanged();
    }

    @Override
    protected Component state() {
        return Component.translatable(hold > 0 ? "message.hardwrought.meter.motion.seen" : "message.hardwrought.meter.motion.watching");
    }
}
