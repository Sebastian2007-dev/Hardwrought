package de.ipnats.hardwrought.survival;

import de.ipnats.hardwrought.core.registry.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Burns from the inside, and what they can turn into.
 *
 * <p>Water drunk too hot scalds the mouth and throat: it hurts at once, in proportion to how hot it
 * was, and leaves the drinker {@linkplain ModEffects#SCALDED scalded} for a while. A scald that is
 * unlucky becomes inflamed: a wound infection, which runs its course like any other disease
 * ({@link de.ipnats.hardwrought.health.Diseases}).
 */
public final class Injuries {
    /** How long a scald lasts, in ticks: a minute. */
    public static final int SCALD_TICKS = 1200;
    /** How often a scald is checked for turning worse, in ticks. */
    static final int SCALD_CHECK_TICKS = 40;
    /** The chance at each check that a scald becomes inflamed, per level of the scald. Over a minute, about a third. */
    static final double INFLAMMATION_CHANCE = 0.025;

    private Injuries() { }

    /**
     * Something drunk at this heat, 0 for cool to 1 for boiling. Below a third it is only warm and does
     * nothing; above, it burns at once and scalds, the worse the hotter.
     */
    public static void drankHot(ServerLevel level, LivingEntity drinker, double heat) {
        if (heat <= 1.0 / 3.0) return;
        drinker.hurtServer(level, level.damageSources().hotFloor(), (float) (1.0 + 3.0 * heat));
        int amplifier = heat > 0.75 ? 1 : 0;
        drinker.addEffect(new MobEffectInstance(ModEffects.SCALDED, SCALD_TICKS, amplifier));
    }

    /** A scald: may become an inflamed wound while it lasts. */
    public static class Scald extends MobEffect {
        public Scald(MobEffectCategory category, int color) {
            super(category, color);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % SCALD_CHECK_TICKS == 0;
        }

        @Override
        public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
            if (entity instanceof net.minecraft.server.level.ServerPlayer player && !player.isCreative() && !player.isSpectator()
                    && entity.getRandom().nextDouble() < INFLAMMATION_CHANCE * (amplifier + 1)) {
                de.ipnats.hardwrought.health.Diseases.wounded(player, 5);
            }
            return true;
        }
    }
}
