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

    /** Water drunk too hot: a burnt mouth and throat, which may become an inflamed wound (see {@link de.ipnats.hardwrought.survival.Injuries}). */
    public static final Holder<MobEffect> SCALDED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("scalded"), new de.ipnats.hardwrought.survival.Injuries.Scald(MobEffectCategory.HARMFUL, 0xE0704A));

    /**
     * A wound left open: dirt can get into it while it lasts, and a bandage keeps it out (see
     * {@link de.ipnats.hardwrought.health.Wounds}). A bite is a dirtier wound than a blow.
     */
    public static final Holder<MobEffect> OPEN_WOUND = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("open_wound"), new de.ipnats.hardwrought.health.Wounds.OpenWound(MobEffectCategory.HARMFUL, 0xA8322D));

    /** A wound bound up: nothing gets into it while the bandage holds. */
    public static final Holder<MobEffect> BANDAGED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("bandaged"), new de.ipnats.hardwrought.survival.ThirstEffect(MobEffectCategory.BENEFICIAL, 0xE8E2D0));

    /** Antibiotics: an infection fought four times as well, a stomach bug twice. */
    public static final Holder<MobEffect> ANTIBIOTIC = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("antibiotic"), new de.ipnats.hardwrought.survival.ThirstEffect(MobEffectCategory.BENEFICIAL, 0xD8494A));

    /** Chamomile tea: a settled stomach. A stomach bug is thrown off three times as fast while it lasts. */
    public static final Holder<MobEffect> CHAMOMILE = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("chamomile"), new de.ipnats.hardwrought.survival.ThirstEffect(MobEffectCategory.BENEFICIAL, 0xE8D27A));

    /** Willow bark tea: a fever brought down, and a cold or an inflamed wound fought twice as well. */
    public static final Holder<MobEffect> WILLOW_BARK = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("willow_bark"), new de.ipnats.hardwrought.survival.ThirstEffect(MobEffectCategory.BENEFICIAL, 0x9C6B3E));

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

    /**
     * Magic: the veil of Darkness worked on oneself. Truly unseen, armour and what is held included,
     * and no monster can set on what it cannot see. A marker; {@code Veil} does the work and lifts it
     * the moment its bearer strikes at anyone.
     */
    public static final Holder<MobEffect> VEILED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("veiled"), new de.ipnats.hardwrought.survival.ThirstEffect(MobEffectCategory.BENEFICIAL, 0x3A1F5C));

    /** Magic: Fire and Earth worked on oneself. Whoever strikes its bearer by hand catches fire. A marker. */
    public static final Holder<MobEffect> EMBER_SKIN = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("ember_skin"), new de.ipnats.hardwrought.survival.ThirstEffect(MobEffectCategory.BENEFICIAL, 0xFF6A2A));

    /**
     * Heat resistance: the body pays the hot air no heed while it lasts. Given on arriving in the
     * Nether and after every respawn there (see {@code NetherCrossing}), so a player has time to get
     * their bearings. A marker: the survival metabolism reads it.
     */
    public static final Holder<MobEffect> HEAT_RESISTANCE = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("heat_resistance"), new de.ipnats.hardwrought.survival.ThirstEffect(MobEffectCategory.BENEFICIAL, 0x7FD4E8));

    /**
     * Forty minutes proof against every disease, and no wound turns, on a first arrival and after every
     * death outside Ultra (see {@code Diseases.grantImmunity}). A marker: catching a disease reads it.
     */
    public static final Holder<MobEffect> IMMUNITY = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("immunity"), new de.ipnats.hardwrought.survival.ThirstEffect(MobEffectCategory.BENEFICIAL, 0x9FE07A));

    /** Tired, exhausted, overtired: fatigue run high, its level the stage (see {@code Tiredness}). */
    public static final Holder<MobEffect> TIRED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("tired"), new de.ipnats.hardwrought.survival.Tiredness.Tired(MobEffectCategory.HARMFUL, 0x4A4A6A));
    /** A microsleep: a moment of black, and no moving (see {@code Tiredness}). */
    public static final Holder<MobEffect> MICROSLEEP = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("microsleep"), new de.ipnats.hardwrought.survival.Tiredness.Microsleep(MobEffectCategory.HARMFUL, 0x101018));

    /** Adrenaline: a fight not yet felt (see {@code Adrenaline}). A marker, its level shown. */
    public static final Holder<MobEffect> ADRENALINE = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("adrenaline"), new de.ipnats.hardwrought.survival.ThirstEffect(MobEffectCategory.BENEFICIAL, 0xE0303A));
    /** The crash after it: the fight being paid for. A marker. */
    public static final Holder<MobEffect> ADRENALINE_CRASH = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("adrenaline_crash"), new de.ipnats.hardwrought.survival.ThirstEffect(MobEffectCategory.HARMFUL, 0x5A4A50));

    /** A burn, from fire or lava: may become inflamed (see {@code Ailments}). Three levels. */
    public static final Holder<MobEffect> BURN = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("burn"), new de.ipnats.hardwrought.health.Ailments.Burn(MobEffectCategory.HARMFUL, 0xD9541E));
    /** Frostbite, from frost: slower feet and pick (see {@code Ailments}). Three levels. */
    public static final Holder<MobEffect> FROSTBITE = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("frostbite"), new de.ipnats.hardwrought.health.Ailments.Frostbite(MobEffectCategory.HARMFUL, 0xA8D8F0));
    /** Water in the lungs, from drowning: a cough, perhaps a cold (see {@code Ailments}). Three levels. */
    public static final Holder<MobEffect> WATER_IN_LUNGS = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("water_in_lungs"), new de.ipnats.hardwrought.health.Ailments.WaterInLungs(MobEffectCategory.HARMFUL, 0x3F76E4));
    /** The drain, from magic and the wither: breath slow, hunger fast (see {@code Ailments}). Three levels. */
    public static final Holder<MobEffect> DRAIN = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            Hardwrought.id("drain"), new de.ipnats.hardwrought.health.Ailments.Drain(MobEffectCategory.HARMFUL, 0x6A2C8C));

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
