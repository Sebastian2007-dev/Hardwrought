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
    /** Section 18.4: smoke inhalation. */
    public static final ResourceKey<DamageType> SMOKE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("smoke"));

    public static final TagKey<DamageType> IS_SUFFOCATING =
            TagKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("is_suffocating"));

    private ModDamageTypes() { }
}
