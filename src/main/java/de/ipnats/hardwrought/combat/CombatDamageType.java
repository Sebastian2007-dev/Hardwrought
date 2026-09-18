package de.ipnats.hardwrought.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import de.ipnats.hardwrought.core.registry.ModDamageTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

import java.util.Locale;

/**
 * Damage types of specification section 28, plus suffocation. Section 28 lists the types a weapon or
 * an element deals; damage that takes the breath away is neither, and reporting it as a blunt impact
 * would be simply wrong, so it has its own type.
 *
 * <p>Only the three physical types interact with the material of worn armor; the remaining types
 * pass the armor stage unchanged until the environmental, chemical and magic systems define their
 * own interactions.
 */
public enum CombatDamageType {
    SLASH, PIERCE, BLUNT, FIRE, COLD, ELECTRIC, EXPLOSION, MAGIC, POISON, SUFFOCATION;

    public static final Codec<CombatDamageType> CODEC = Codec.STRING.comapFlatMap(name -> {
        CombatDamageType value = byName(name);
        return value == null ? DataResult.error(() -> "Unknown damage type: " + name) : DataResult.success(value);
    }, CombatDamageType::serializedName);

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean physical() {
        return this == SLASH || this == PIERCE || this == BLUNT;
    }

    public static CombatDamageType byName(String name) {
        if (name == null) return null;
        for (CombatDamageType value : values()) {
            if (value.serializedName().equals(name.toLowerCase(Locale.ROOT))) return value;
        }
        return null;
    }

    /**
     * Classification for damage that no weapon profile describes. Unclassified impacts count as
     * blunt; POISON is reserved for the later disease system and no vanilla source produces it.
     */
    public static CombatDamageType classify(DamageSource source) {
        // Checked first: spent air, smoke, drowning and being crushed inside a block are all
        // suffocation, and the tag is what later systems extend rather than this method.
        if (source.is(ModDamageTypes.IS_SUFFOCATING) || source.is(DamageTypeTags.IS_DROWNING)) {
            return SUFFOCATION;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) return FIRE;
        if (source.is(DamageTypeTags.IS_FREEZING)) return COLD;
        if (source.is(DamageTypeTags.IS_LIGHTNING)) return ELECTRIC;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return EXPLOSION;
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypes.WITHER) || source.is(DamageTypes.SONIC_BOOM)) {
            return MAGIC;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return PIERCE;
        return BLUNT;
    }
}
