package de.ipnats.hardwrought.mobs;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A mob without a target that heard something goes to where it came from; see {@link Perception}.
 * It only walks there. Whether it finds anyone is up to its eyes, as always.
 */
public class InvestigateNoiseGoal extends Goal {
    /** Close enough to have had a look. */
    static final double ARRIVED_SQR = 2.5 * 2.5;
    /** A little quicker than wandering, not a charge. */
    static final double SPEED = 1.0;

    private final PathfinderMob mob;
    private Vec3 noise;

    public InvestigateNoiseGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null) return false;
        noise = Perception.noise(mob);
        return noise != null;
    }

    @Override
    public void start() {
        mob.getNavigation().moveTo(noise.x, noise.y, noise.z, SPEED);
    }

    @Override
    public boolean canContinueToUse() {
        if (mob.getTarget() != null || mob.getNavigation().isDone()) return false;
        Vec3 latest = Perception.noise(mob);
        if (latest == null) return false;
        if (!latest.equals(noise)) {
            // Heard again, somewhere else: go there instead.
            noise = latest;
            mob.getNavigation().moveTo(noise.x, noise.y, noise.z, SPEED);
        }
        return mob.distanceToSqr(noise) > ARRIVED_SQR;
    }

    @Override
    public void stop() {
        Perception.forget(mob);
        noise = null;
    }
}
