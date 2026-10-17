package de.ipnats.hardwrought.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Harm made for the end of the game, against the best armour there is: the bosses' own damage types
 * bypass armour and enchantments, and this works out how much of each still holds. Armour counts for a
 * given share of itself; protective runes for a given share of what they would hold against magic, at
 * most what vanilla allows them. A magic shield still stops it, as it stops anything from outside.
 */
public final class Rending {
    private Rending() { }

    /**
     * Hurts a victim with harm of this type.
     *
     * @param armour how much of their armour holds, 0 to 1
     * @param runes  how much of their protective runes holds, 0 to 1
     */
    public static boolean rend(ServerLevel level, LivingEntity victim, ResourceKey<DamageType> type, Entity direct, Entity causing,
                               float amount, float armour, float runes) {
        DamageSource magic = level.damageSources().indirectMagic(direct, causing);
        float protection = Math.min(20, EnchantmentHelper.getDamageProtection(level, victim, magic)) * runes;
        float dealt = CombatRules.getDamageAfterMagicAbsorb(amount, protection);
        if (armour > 0) {
            dealt = CombatRules.getDamageAfterAbsorb(victim, dealt, magic, victim.getArmorValue() * armour,
                    (float) victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS) * armour);
        }
        DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), direct, causing);
        return victim.hurtServer(level, source, dealt);
    }
}
