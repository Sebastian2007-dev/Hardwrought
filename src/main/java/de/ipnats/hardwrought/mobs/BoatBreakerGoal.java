package de.ipnats.hardwrought.mobs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

import java.util.EnumSet;

/**
 * Mob specification § 7: a boat is no escape from the drowned. One whose target is in a boat swims
 * to the boat and batters it until it breaks, putting the player in the water with it.
 */
public class BoatBreakerGoal extends Goal {
    static final double REACH = 2.2;
    static final int HIT_TICKS = 20;
    static final float HIT_DAMAGE = 5;
    static final double SPEED = 1.2;

    private final PathfinderMob mob;
    private int sinceHit;

    public BoatBreakerGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private AbstractBoat boat() {
        LivingEntity target = mob.getTarget();
        return target != null && target.getVehicle() instanceof AbstractBoat boat ? boat : null;
    }

    @Override
    public boolean canUse() {
        AbstractBoat boat = boat();
        return boat != null && mob.distanceTo(boat) < 24;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        AbstractBoat boat = boat();
        if (boat == null || !(mob.level() instanceof ServerLevel level)) return;
        mob.getLookControl().setLookAt(boat);
        if (mob.distanceTo(boat) > REACH) {
            mob.getNavigation().moveTo(boat, SPEED);
            return;
        }
        if (++sinceHit >= HIT_TICKS) {
            sinceHit = 0;
            mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            boat.hurtServer(level, level.damageSources().mobAttack(mob), HIT_DAMAGE);
        }
    }
}
