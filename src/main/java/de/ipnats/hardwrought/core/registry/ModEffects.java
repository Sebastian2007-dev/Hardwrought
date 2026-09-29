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

    /**
     * Mob specification §§ 6 and 9: the breath knocked out of a player by a husk or a stray. Like
     * {@link #THIRST} a marker: the survival metabolism reads it and recovers stamina slower.
     */
    public static final Holder<MobEffect> WINDED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("winded"), new de.ipnats.hardwrought.survival.ThirstEffect(MobEffectCategory.HARMFUL, 0xB8A27A));

    /**
     * Stamina regeneration: the stamina reserve fills while it lasts, working or resting, the way
     * vanilla regeneration mends health. A marker like {@link #THIRST}: the survival metabolism reads
     * it on every stamina tick.
     */
    public static final Holder<MobEffect> STAMINA_REGENERATION = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("stamina_regeneration"), new de.ipnats.hardwrought.survival.ThirstEffect(MobEffectCategory.BENEFICIAL, 0xE3B23C));

    /** Brewed from an awkward potion and honey; redstone draws it out, glowstone makes it stronger. */
    public static final Holder<net.minecraft.world.item.alchemy.Potion> STAMINA_REGENERATION_POTION = potion(
            "stamina_regeneration", "stamina_regeneration", 3600, 0);
    public static final Holder<net.minecraft.world.item.alchemy.Potion> LONG_STAMINA_REGENERATION_POTION = potion(
            "long_stamina_regeneration", "stamina_regeneration", 9600, 0);
    public static final Holder<net.minecraft.world.item.alchemy.Potion> STRONG_STAMINA_REGENERATION_POTION = potion(
            "strong_stamina_regeneration", "stamina_regeneration", 1800, 1);

    /**
     * Hydration regeneration, the water counterpart: the hydration reserve fills while it lasts. Read
     * by the survival metabolism on its once-a-second pass, like {@link #THIRST}.
     */
    public static final Holder<MobEffect> HYDRATION_REGENERATION = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("hydration_regeneration"), new de.ipnats.hardwrought.survival.ThirstEffect(MobEffectCategory.BENEFICIAL, 0x8FE3FF));

    /** Brewed from an awkward potion and a cactus, which keeps its water through any drought. */
    public static final Holder<net.minecraft.world.item.alchemy.Potion> HYDRATION_REGENERATION_POTION = potion(
            "hydration_regeneration", HYDRATION_REGENERATION, 3600, 0);
    public static final Holder<net.minecraft.world.item.alchemy.Potion> LONG_HYDRATION_REGENERATION_POTION = potion(
            "long_hydration_regeneration", HYDRATION_REGENERATION, 9600, 0);
    public static final Holder<net.minecraft.world.item.alchemy.Potion> STRONG_HYDRATION_REGENERATION_POTION = potion(
            "strong_hydration_regeneration", HYDRATION_REGENERATION, 1800, 1);

    private static Holder<net.minecraft.world.item.alchemy.Potion> potion(String id, String name, int ticks, int amplifier) {
        return potion(id, STAMINA_REGENERATION, ticks, amplifier);
    }

    /** A potion of this effect; its name is the effect's, so the three strengths share their words. */
    private static Holder<net.minecraft.world.item.alchemy.Potion> potion(String id, Holder<MobEffect> effect, int ticks, int amplifier) {
        String name = effect.unwrapKey().orElseThrow().identifier().getPath();
        return Registry.registerForHolder(BuiltInRegistries.POTION, Hardwrought.id(id),
                new net.minecraft.world.item.alchemy.Potion(name, new net.minecraft.world.effect.MobEffectInstance(effect, ticks, amplifier)));
    }

    private ModEffects() { }

    /** Touching the class registers everything in it; called from the mod initializer. */
    public static void initialize() { }
}
