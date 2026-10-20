package de.ipnats.hardwrought.mobs;

import de.ipnats.hardwrought.haven.Floodlights;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A monster in the light of an arc floodlight walks out of it (docs/electricity-roadmap.md, stage E3):
 * the floodlit yard is a place it will not stay in. Only a monster already close on its target —
 * within {@link #CLOSE} blocks — fights on where it stands: a player who runs into the light with
 * something at their heels is not saved by it.
 */
public class AvoidFloodlightGoal extends Goal {
    static final double SPEED = 1.15, CLOSE = 3.0;
    private static final int LOOK_TICKS = 20;

    private final PathfinderMob mob;
    private BlockPos light;

    public AvoidFloodlightGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.tickCount % LOOK_TICKS != 0) return false;
        LivingEntity target = mob.getTarget();
        if (target != null && mob.distanceTo(target) < CLOSE) return false;
        light = Floodlights.near(mob.level(), mob.position(), Floodlights.AVOID_REACH);
        return light != null;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = mob.getTarget();
        if (target != null && mob.distanceTo(target) < CLOSE) return false;
        return light != null && Floodlights.near(mob.level(), mob.position(), Floodlights.AVOID_REACH) != null;
    }

    @Override
    public void start() {
        flee();
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        light = null;
    }

    @Override
    public void tick() {
        if (mob.getNavigation().isDone()) flee();
    }

    /** Off along the line from the light, to the edge of its reach and a little past. */
    private void flee() {
        Vec3 away = mob.position().subtract(Vec3.atCenterOf(light));
        if (away.horizontalDistanceSqr() < 1.0e-4) away = new Vec3(1, 0, 0);
        away = new Vec3(away.x, 0, away.z).normalize();
        double out = Floodlights.AVOID_REACH + 4.0 - mob.position().distanceTo(Vec3.atCenterOf(light));
        Vec3 to = mob.position().add(away.scale(Math.max(4.0, out)));
        mob.getNavigation().moveTo(to.x, to.y, to.z, SPEED);
    }
}
