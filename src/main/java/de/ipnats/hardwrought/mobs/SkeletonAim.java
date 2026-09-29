package de.ipnats.hardwrought.mobs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Mob specification § 8: a skeleton is an archer, not an aimbot. Each shot is one of three kinds —
 * a quick shot at close range, loose; an accurate one, slow; a charged one that flies so hard it hits
 * half again as hard — and every shot is less sure at long range, in the dark, at a moving target and past cover.
 * Strays, bogged and bow-carrying wither skeletons shoot the same way.
 */
public final class SkeletonAim {
    public enum Shot { QUICK, NORMAL, ACCURATE, CHARGED }

    static final double CHARGED_DAMAGE = 1.5;

    private SkeletonAim() { }

    static Shot pick(RandomSource random, double distance) {
        int roll = random.nextInt(100);
        if (distance < 8) return roll < 55 ? Shot.QUICK : Shot.NORMAL;
        if (distance > 16) return roll < 40 ? Shot.ACCURATE : roll < 70 ? Shot.CHARGED : Shot.NORMAL;
        return roll < 20 ? Shot.QUICK : roll < 45 ? Shot.ACCURATE : roll < 65 ? Shot.CHARGED : Shot.NORMAL;
    }

    /** Spread factor for a shot at this target: 1 is vanilla's aim at about ten blocks, in the open. */
    public static double spread(AbstractSkeleton skeleton, LivingEntity target, Shot shot) {
        double factor = 0.6 + skeleton.distanceTo(target) / 24.0;
        if (skeleton.level() instanceof ServerLevel level) factor *= 1 + (1 - Perception.visibility(level, target)) * 1.5;
        factor *= 1 + target.getDeltaMovement().horizontalDistance() * 5;
        if (!skeleton.hasLineOfSight(target)) factor *= 2;
        return factor * switch (shot) {
            case QUICK -> 1.6;
            case NORMAL -> 1.0;
            case ACCURATE -> 0.45;
            case CHARGED -> 0.8;
        };
    }

    /** Fires the arrow vanilla prepared, as a shot of the kind this situation calls for. */
    public static Projectile shoot(AbstractSkeleton skeleton, LivingEntity target, Projectile arrow,
                                   float velocity, float inaccuracy, Shooter original) {
        Shot shot = pick(skeleton.getRandom(), skeleton.distanceTo(target));
        float spread = (float) (inaccuracy * spread(skeleton, target, shot));
        float speed = velocity * switch (shot) {
            case QUICK -> 1.1f;
            // An arrow hits as hard as it flies fast: a charged shot's weight is its speed.
            case CHARGED -> (float) CHARGED_DAMAGE;
            default -> 1.0f;
        };
        return original.shoot(speed, spread);
    }

    @FunctionalInterface
    public interface Shooter {
        Projectile shoot(float velocity, float inaccuracy);
    }
}
