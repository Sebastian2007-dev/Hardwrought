package de.ipnats.hardwrought.survival;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Section 23.2: the water half of hunger.
 *
 * <p>Bad water used to poison the drinker, which said the wrong thing. Drinking from a stagnant pond
 * is not being envenomed; it is spending the next while thirstier than the drink was worth.
 *
 * <p>The effect is a marker and nothing more. It does no work of its own — {@link
 * #shouldApplyEffectTickThisTick} says so outright — because {@link SurvivalSystem} already walks
 * every player once a second to drain the reserve, and reading the effect there costs one reserve
 * update and one synchronisation instead of twenty.
 */
public class ThirstEffect extends MobEffect {
    public ThirstEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }
}
