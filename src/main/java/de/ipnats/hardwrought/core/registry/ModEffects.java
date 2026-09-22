package de.ipnats.hardwrought.core.registry;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Status effects this mod adds. */
public final class ModEffects {
    /**
     * Section 23.2: what bad water does to a drinker.
     *
     * <p>Vanilla poison was the wrong answer. Drinking from a stagnant pond is not being envenomed;
     * it is spending the next while thirstier than the drink was worth. This is the water half of
     * hunger, and like hunger it is a marker rather than a mechanism: the survival metabolism reads
     * it once a second and drains the reserve, so the drain is folded into the pass that is already
     * running and the player is still synchronised exactly once.
     */
    public static final Holder<MobEffect> THIRST = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("thirst"), new de.ipnats.hardwrought.survival.ThirstEffect(MobEffectCategory.HARMFUL, 0x3A6B7C));

    private ModEffects() { }

    /** Touching the class registers everything in it; called from the mod initializer. */
    public static void initialize() { }
}
