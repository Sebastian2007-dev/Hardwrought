package de.ipnats.hardwrought.mobs;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Hit and back off (mob specification §§ 12 and 35): a cave spider retreats once its poison is in, a
 * vex pulls back after it has struck — which is the player's window to strike back.
 */
public class FleeAfterHitGoal extends Goal {
    static final double DISTANCE = 6, SPEED = 1.2;

    private final PathfinderMob mob;

    public FleeAfterHitGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() != null && MobHits.retreating(mob) && !Panic.panicking(mob);
    }

    @Override
    public void start() {
        // Cornered once too often, it does not back off at all: it keeps on biting.
        if (!Panic.startFlight(mob)) MobHits.stopRetreat(mob);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() && Panic.keepFleeing(mob);
    }

    @Override
    public void stop() {
        Panic.endFlight(mob);
        if (Panic.panicking(mob)) MobHits.stopRetreat(mob);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        Vec3 away = mob.position().subtract(target.position());
        if (away.lengthSqr() < 1.0e-4) away = new Vec3(1, 0, 0);
        Vec3 to = mob.position().add(away.normalize().scale(DISTANCE));
        // Fliers such as the vex steer straight; walkers find a path.
        if (mob.isNoGravity()) mob.getMoveControl().setWantedPosition(to.x, to.y + 1, to.z, SPEED);
        else if (mob.getNavigation().isDone()) mob.getNavigation().moveTo(to.x, to.y, to.z, SPEED);
    }
}
