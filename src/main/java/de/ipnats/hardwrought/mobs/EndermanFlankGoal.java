package de.ipnats.hardwrought.mobs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.phys.Vec3;

/**
 * Mob specification § 15: an angry enderman does not only walk at its target. Now and then it
 * teleports to its back or side, changing the distance of the fight in a blink. Vanilla already has
 * it dodge projectiles.
 */
public class EndermanFlankGoal extends Goal {
    static final double MIN_DISTANCE = 3, MAX_DISTANCE = 14;
    static final int COOLDOWN_TICKS = 100;
    /** How far behind or beside the target it appears. */
    static final double BEHIND = 2.5;

    private final Enderman enderman;
    private long readyAt;

    public EndermanFlankGoal(Enderman enderman) {
        this.enderman = enderman;
    }

    @Override
    public boolean canUse() {
        if (!(enderman.level() instanceof ServerLevel level) || level.getGameTime() < readyAt) return false;
        LivingEntity target = enderman.getTarget();
        if (target == null || !target.isAlive()) return false;
        double distance = enderman.distanceTo(target);
        return distance >= MIN_DISTANCE && distance <= MAX_DISTANCE && enderman.getRandom().nextInt(4) == 0;
    }

    @Override
    public void start() {
        ServerLevel level = (ServerLevel) enderman.level();
        readyAt = level.getGameTime() + COOLDOWN_TICKS;
        LivingEntity target = enderman.getTarget();
        Vec3 look = target.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0e-4) look = new Vec3(1, 0, 0);
        // Behind, or to one side of, where the target is looking.
        double turn = Math.PI + (enderman.getRandom().nextDouble() - 0.5) * Math.PI;
        Vec3 dir = look.normalize().yRot((float) turn);
        Vec3 to = target.position().add(dir.scale(BEHIND));
        enderman.teleport(to.x, target.getY(), to.z);
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }
}
