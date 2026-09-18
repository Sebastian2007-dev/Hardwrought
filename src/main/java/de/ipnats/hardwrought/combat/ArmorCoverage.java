package de.ipnats.hardwrought.combat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * The armor a defender is wearing, reduced to the values one hit needs. Coverage weights are a
 * coarse area share of the four armor slots; they are not the hit zones of specification section 35,
 * which need their own system.
 */
public record ArmorCoverage(double slashResistance, double pierceResistance, double bluntResistance,
                            double staminaDrain, double insulation) {
    public static final ArmorCoverage NONE = new ArmorCoverage(0, 0, 0, 0, 0);
    /** Insulation used for a worn piece whose material has no profile yet. */
    public static final double DEFAULT_INSULATION = 0.12;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final double[] COVERAGE = {0.15, 0.40, 0.30, 0.15};

    public ArmorCoverage {
        if (!finite(slashResistance, pierceResistance, bluntResistance, staminaDrain, insulation)
                || slashResistance < 0 || pierceResistance < 0 || bluntResistance < 0
                || staminaDrain < 0 || insulation < 0) {
            throw new IllegalArgumentException("Invalid armor coverage");
        }
    }

    public static ArmorCoverage of(LivingEntity entity, Map<Identifier, ArmorProfile> profiles) {
        double slash = 0;
        double pierce = 0;
        double blunt = 0;
        double drain = 0;
        double insulation = 0;
        for (int index = 0; index < ARMOR_SLOTS.length; index++) {
            ItemStack piece = entity.getItemBySlot(ARMOR_SLOTS[index]);
            if (piece.isEmpty()) continue;
            double coverage = COVERAGE[index];
            ArmorProfile profile = profiles.get(BuiltInRegistries.ITEM.getKey(piece.getItem()));
            if (profile == null) {
                insulation += DEFAULT_INSULATION;
                continue;
            }
            slash += coverage * profile.slashResistance();
            pierce += coverage * profile.pierceResistance();
            blunt += coverage * profile.bluntResistance();
            drain += profile.staminaDrain();
            insulation += profile.insulation().orElse(DEFAULT_INSULATION);
        }
        return new ArmorCoverage(slash, pierce, blunt, drain, insulation);
    }

    public double resistance(CombatDamageType type) {
        return switch (type) {
            case SLASH -> slashResistance;
            case PIERCE -> pierceResistance;
            case BLUNT -> bluntResistance;
            default -> 0;
        };
    }

    /**
     * Section 29: armor penetration removes part of the material advantage instead of ignoring the
     * vanilla armor points, which stay in charge of the base reduction.
     */
    public double effectiveResistance(CombatDamageType type, double armorPenetration) {
        return resistance(type) * (1.0 - Math.max(0, Math.min(1, armorPenetration)));
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
