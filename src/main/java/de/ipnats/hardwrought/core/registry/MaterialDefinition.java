package de.ipnats.hardwrought.core.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.DataResult;

import java.util.Objects;
import java.util.Optional;

/** Shared material constants; tier is descriptive, never a player level or research gate.
 * Per-item craftsmanship, purity, temperature and wear belong to item/machine state.
 */
public record MaterialDefinition(int tier, double densityKgM3, Optional<Double> meltingPointC,
                                 Optional<ThermalProperties> thermal, Optional<StructuralProperties> structure) {
    public static final Codec<MaterialDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 100).optionalFieldOf("tier", 0).forGetter(MaterialDefinition::tier),
            Codec.doubleRange(1, 100_000).fieldOf("density_kg_m3").forGetter(MaterialDefinition::densityKgM3),
            Codec.doubleRange(-273.15, 10_000).optionalFieldOf("melting_point_c").forGetter(MaterialDefinition::meltingPointC),
            ThermalProperties.CODEC.optionalFieldOf("thermal").forGetter(MaterialDefinition::thermal),
            StructuralProperties.CODEC.optionalFieldOf("structure").forGetter(MaterialDefinition::structure)
    ).apply(instance, MaterialDefinition::new));

    /** Compatibility for callers defining a material with a known melting point. */
    public MaterialDefinition(int tier, double densityKgM3, double meltingPointC) {
        this(tier, densityKgM3, Optional.of(meltingPointC), Optional.empty(), Optional.empty());
    }

    public MaterialDefinition {
        Objects.requireNonNull(meltingPointC);
        Objects.requireNonNull(thermal);
        Objects.requireNonNull(structure);
        if (tier < 0 || tier > 100 || !Double.isFinite(densityKgM3) || densityKgM3 < 1 || densityKgM3 > 100_000) {
            throw new IllegalArgumentException("Invalid material properties");
        }
        meltingPointC.ifPresent(value -> {
            if (!Double.isFinite(value) || value < -273.15 || value > 10_000) {
                throw new IllegalArgumentException("Invalid melting point");
            }
        });
    }

    /** Missing thermal properties mean unknown, not zero conductivity or heat capacity. */
    public record ThermalProperties(double conductivityWmK, double specificHeatJKgK) {
        private static final Codec<Double> FINITE_POSITIVE = Codec.DOUBLE.validate(value ->
                Double.isFinite(value) && value > 0 ? DataResult.success(value)
                        : DataResult.error(() -> "Expected a finite positive value"));
        public static final Codec<ThermalProperties> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FINITE_POSITIVE.fieldOf("conductivity_w_m_k").forGetter(ThermalProperties::conductivityWmK),
                FINITE_POSITIVE.fieldOf("specific_heat_j_kg_k").forGetter(ThermalProperties::specificHeatJKgK)
        ).apply(instance, ThermalProperties::new));

        public ThermalProperties {
            if (!Double.isFinite(conductivityWmK) || conductivityWmK <= 0
                    || !Double.isFinite(specificHeatJKgK) || specificHeatJKgK <= 0) {
                throw new IllegalArgumentException("Invalid thermal properties");
            }
        }
    }

    /** Strength in MPa; support distance in blocks. Zero allows non-load-bearing materials. */
    public record StructuralProperties(double compressionStrengthMpa, double tensionStrengthMpa, double supportDistanceBlocks) {
        private static final Codec<Double> FINITE_NON_NEGATIVE = Codec.DOUBLE.validate(value ->
                Double.isFinite(value) && value >= 0 ? DataResult.success(value)
                        : DataResult.error(() -> "Expected a finite non-negative value"));
        public static final Codec<StructuralProperties> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                FINITE_NON_NEGATIVE.fieldOf("compression_strength_mpa").forGetter(StructuralProperties::compressionStrengthMpa),
                FINITE_NON_NEGATIVE.fieldOf("tension_strength_mpa").forGetter(StructuralProperties::tensionStrengthMpa),
                FINITE_NON_NEGATIVE.fieldOf("support_distance_blocks").forGetter(StructuralProperties::supportDistanceBlocks)
        ).apply(instance, StructuralProperties::new));

        public StructuralProperties {
            if (!Double.isFinite(compressionStrengthMpa) || compressionStrengthMpa < 0
                    || !Double.isFinite(tensionStrengthMpa) || tensionStrengthMpa < 0
                    || !Double.isFinite(supportDistanceBlocks) || supportDistanceBlocks < 0) {
                throw new IllegalArgumentException("Invalid structural properties");
            }
        }
    }
}
