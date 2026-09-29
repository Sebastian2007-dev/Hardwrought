package de.ipnats.hardwrought.mobs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

/**
 * Mob specification § 13.1: an ignited creeper leaps at the player shortly before it goes off, so
 * backing away is no longer a sure escape. The creeper stays what it was — a close-range blast, not a
 * demolition charge aimed at walls.
 *
 * <p>The leap is announced: a hiss rising in pitch the moment it pushes off. It is aimed where the
 * target stands at that moment and does not steer in the air.
 */
public final class CreeperLeap {
    /** Swelling, from 0 to 1, at which the creeper pushes off: about half a second before the blast. */
    static final float LEAP_AT = 0.6f;
    /** Only a target this close is leapt at; farther away it would land short and waste the blast. */
    static final double MAX_DISTANCE = 6.0;
    static final double HORIZONTAL = 0.55, VERTICAL = 0.38;
    /** A charged creeper leaps farther (§ 14). */
    static final double CHARGED = 1.3;

    /** Remembers whether this fuse has already been spent on a leap. Implemented by a mixin. */
    public interface Leaper {
        boolean hardwrought$leapt();

        void hardwrought$setLeapt(boolean leapt);
    }

    private CreeperLeap() { }

    public static void tick(Creeper creeper) {
        if (!(creeper.level() instanceof ServerLevel level)) return;
        Leaper leaper = (Leaper) creeper;
        float swelling = creeper.getSwelling(1.0f);
        // A fuse that has gone out may be lit again, and then leaps again.
        if (swelling <= 0) {
            leaper.hardwrought$setLeapt(false);
            return;
        }
        if (leaper.hardwrought$leapt() || swelling < LEAP_AT || !creeper.onGround()) return;
        LivingEntity target = creeper.getTarget();
        if (target == null || creeper.distanceTo(target) > MAX_DISTANCE || !creeper.hasLineOfSight(target)) return;
        Vec3 toward = target.position().subtract(creeper.position());
        Vec3 flat = new Vec3(toward.x, 0, toward.z);
        if (flat.lengthSqr() < 1.0e-4) return;
        double power = creeper.isPowered() ? CHARGED : 1.0;
        // Short of the target, a creeper need only close half the gap: the blast does the rest.
        double reach = Math.min(1.0, flat.length() / 3.0);
        Vec3 push = flat.normalize().scale(HORIZONTAL * power * reach);
        creeper.setDeltaMovement(push.x, VERTICAL * power, push.z);
        creeper.needsSync = true;
        leaper.hardwrought$setLeapt(true);
        level.playSound(null, creeper.blockPosition(), SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.0f, 1.8f);
    }
}
