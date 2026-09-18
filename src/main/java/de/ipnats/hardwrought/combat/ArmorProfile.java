package de.ipnats.hardwrought.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Armor material behaviour of specification section 36. Resistances are relative reductions applied
 * on top of the vanilla armor points of the same piece, so a material decides which damage type it
 * is good against instead of replacing the vanilla armor scale. Carried mass stays in item_weights.
 */
public record ArmorProfile(List<Identifier> items, double slashResistance, double pierceResistance,
                           double bluntResistance, double staminaDrain, Optional<Double> insulation) {
    private static final Codec<Double> RESISTANCE = Codec.doubleRange(0, 0.9);
    public static final Codec<ArmorProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.listOf().fieldOf("items").forGetter(ArmorProfile::items),
            RESISTANCE.optionalFieldOf("slash_resistance", 0.0).forGetter(ArmorProfile::slashResistance),
            RESISTANCE.optionalFieldOf("pierce_resistance", 0.0).forGetter(ArmorProfile::pierceResistance),
            RESISTANCE.optionalFieldOf("blunt_resistance", 0.0).forGetter(ArmorProfile::bluntResistance),
            Codec.doubleRange(0, 2).optionalFieldOf("stamina_drain", 0.0).forGetter(ArmorProfile::staminaDrain),
            Codec.doubleRange(0, 1).optionalFieldOf("insulation").forGetter(ArmorProfile::insulation)
    ).apply(instance, ArmorProfile::new));

    public ArmorProfile {
        if (items == null || insulation == null) throw new IllegalArgumentException("Armor profile needs items");
        if (!inRange(slashResistance) || !inRange(pierceResistance) || !inRange(bluntResistance)
                || !Double.isFinite(staminaDrain) || staminaDrain < 0 || staminaDrain > 2) {
            throw new IllegalArgumentException("Invalid armor profile values");
        }
        insulation.ifPresent(value -> {
            if (!Double.isFinite(value) || value < 0 || value > 1) {
                throw new IllegalArgumentException("Invalid armor insulation");
            }
        });
        items = List.copyOf(items);
    }

    public double resistance(CombatDamageType type) {
        return switch (type) {
            case SLASH -> slashResistance;
            case PIERCE -> pierceResistance;
            case BLUNT -> bluntResistance;
            default -> 0;
        };
    }

    private static boolean inRange(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 0.9;
    }
}
