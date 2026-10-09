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

    public static final TagKey<DamageType> IS_SUFFOCATING =
            TagKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("is_suffocating"));

    private ModDamageTypes() { }
}
