package de.ipnats.hardwrought.mobs;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Mob specification §§ 26 and 27: hoglins and zoglins charge. From a few blocks off they lower their
 * heads and rush the target; what they reach they hit hard and throw back. A charge is a straight
 * line: it can be sidestepped — or met. A player crouching behind a spear braces it, and the charger
 * runs onto the point: the blow is turned back on it, doubled, and the charger is staggered.
 */
public class ChargeGoal extends Goal {
    static final double MIN_DISTANCE = 4, MAX_DISTANCE = 12;
    static final double SPEED = 1.1;
    static final int CHARGE_TICKS = 18, COOLDOWN_TICKS = 100;
    static final float IMPACT_FACTOR = 1.5f;
    static final double IMPACT_KNOCKBACK = 1.6;
    static final double HIT_DISTANCE = 2.0;
    /** A braced spear turns the charge back on the charger this many times over. */
    static final float BRACE_FACTOR = 2.0f;

    private final Mob mob;
    private int charging;
    private long readyAt;
    private Vec3 heading;

    public ChargeGoal(Mob mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel level) || level.getGameTime() < readyAt || !mob.onGround()) return false;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || !mob.hasLineOfSight(target)) return false;
        double distance = mob.distanceTo(target);
        return distance >= MIN_DISTANCE && distance <= MAX_DISTANCE && mob.getRandom().nextInt(10) == 0;
    }

    @Override
    public void start() {
        charging = CHARGE_TICKS;
        Vec3 toward = mob.getTarget().position().subtract(mob.position()).multiply(1, 0, 1);
        heading = toward.lengthSqr() < 1.0e-4 ? Vec3.ZERO : toward.normalize();
        mob.level().playSound(null, mob.blockPosition(), SoundEvents.HOGLIN_ANGRY, SoundSource.HOSTILE, 1.2f, 0.8f);
    }

    @Override
    public boolean canContinueToUse() {
        return charging > 0 && mob.getTarget() != null && mob.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        charging--;
        // Straight on: the heading is fixed at the start, so a sidestep lets it run past.
        Vec3 motion = mob.getDeltaMovement();
        mob.setDeltaMovement(heading.x * SPEED * 0.5, motion.y, heading.z * SPEED * 0.5);
        LivingEntity target = mob.getTarget();
        if (target == null || mob.distanceTo(target) > HIT_DISTANCE || !(mob.level() instanceof ServerLevel level)) return;
        charging = 0;
        float damage = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * IMPACT_FACTOR;
        if (target instanceof Player player && braced(player)) {
            mob.hurtServer(level, level.damageSources().playerAttack(player), damage * BRACE_FACTOR);
            var runtime = CoreLifecycle.find(level.getServer());
            if (runtime != null) runtime.combat().stagger(mob, 40);
            MobTraits.shove(mob, 0.8, target.getX(), target.getZ());
            return;
        }
        target.hurtServer(level, level.damageSources().mobAttack(mob), damage);
        MobTraits.shove(target, IMPACT_KNOCKBACK, mob.getX(), mob.getZ());
    }

    @Override
    public void stop() {
        readyAt = mob.level().getGameTime() + COOLDOWN_TICKS;
    }

    /** A crouching player holding a spear, facing the charge, has braced it. */
    static boolean braced(Player player) {
        return player.isShiftKeyDown() && (player.getMainHandItem().is(ItemTags.SPEARS) || player.getOffhandItem().is(ItemTags.SPEARS));
    }
}
