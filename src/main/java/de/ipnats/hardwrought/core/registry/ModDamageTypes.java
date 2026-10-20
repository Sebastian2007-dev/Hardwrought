package de.ipnats.hardwrought.core.registry;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * The damage types Hardwrought adds, and the tag that says which damage counts as suffocation.
 *
 * <p>The tag is the extension point: any later system whose damage takes the breath away — a
 * flooded shaft, a toxic industrial gas, a collapsed tunnel — is classified correctly by being
 * listed there, without touching this code.
 */
public final class ModDamageTypes {
    /** Section 18.1 and 18.2: too little oxygen, or too much carbon dioxide. */
    public static final ResourceKey<DamageType> BAD_AIR =
            ResourceKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("bad_air"));
    /** Carbon monoxide: poisoning, not suffocation, and armor is no help against it either. */
    public static final ResourceKey<DamageType> CARBON_MONOXIDE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("carbon_monoxide"));
    /** Section 18.4: smoke inhalation. */
    public static final ResourceKey<DamageType> SMOKE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("smoke"));

    /** No water left in the body. Armor is no help against it. */
    public static final ResourceKey<DamageType> DEHYDRATION =
            ResourceKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("dehydration"));

    /** Too long in the thin air of the heights or under the pressure of the deep. Armor is no help. */
    public static final ResourceKey<DamageType> EXPOSURE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("exposure"));

    /** Section 76: a live wire touched, or live water stood in. Armor is no help; metal least of all. */
    public static final ResourceKey<DamageType> ELECTROCUTION =
            ResourceKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("electrocution"));

    /** The Witch King's magic: it tears at the soul, so armour is no help and runes only half a help (see {@code WitchKing.rend}). */
    public static final ResourceKey<DamageType> HEX =
            ResourceKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("hex"));

    /** The dragon's teeth, its dives and its stamping: armour holds only half of it (see {@code Rending}). */
    public static final ResourceKey<DamageType> DRAGON_BITE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("dragon_bite"));

    /** The void the dragon breathes and calls: armour does not hold it, runes only half. */
    public static final ResourceKey<DamageType> VOID =
            ResourceKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("void"));

    /** A fire spell (docs/magic-roadmap.md, M6): fire, so fire protection holds and the fireproof feel nothing. */
    public static final ResourceKey<DamageType> SPELL_FIRE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("spell_fire"));
    /** A fire spell turned around: freezing cold. */
    public static final ResourceKey<DamageType> SPELL_FROST =
            ResourceKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("spell_frost"));
    /** An earth spell: a blunt blow of stone, which armour takes as it takes a hammer. */
    public static final ResourceKey<DamageType> SPELL_STRIKE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("spell_strike"));

    public static final TagKey<DamageType> IS_SUFFOCATING =
            TagKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("is_suffocating"));

    private ModDamageTypes() { }
}
