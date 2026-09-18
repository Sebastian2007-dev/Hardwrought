package de.ipnats.hardwrought.water;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/**
 * The water categories of specification section 23.2. The order is the order of usefulness, so a
 * purification step can only ever move water toward {@link #FRESH}.
 *
 * @param hydration    what one drink of this water is worth, as a share of clean water. Salt water
 *                     is negative: drinking it costs the body more water than it supplies.
 * @param illnessRisk  chance per drink of falling ill from it, 0 for water that is safe
 */
public enum WaterQuality {
    /** Spring, rain and meltwater, or anything that has been boiled. */
    FRESH(1.00, 0.00),
    /** Running water: carries less than a lake, but keeps itself moving. */
    RIVER(0.90, 0.12),
    /** Standing, warm, organic water. Drinkable only in the sense that it is wet. */
    SWAMP(0.65, 0.45),
    /** Section 23.2: seawater takes more water out of the body than it puts in. */
    SALT(-0.60, 0.05);

    public static final Codec<WaterQuality> CODEC = Codec.STRING.comapFlatMap(name -> {
        WaterQuality value = byName(name);
        return value == null ? DataResult.error(() -> "Unknown water quality: " + name) : DataResult.success(value);
    }, WaterQuality::serializedName);

    private final double hydration;
    private final double illnessRisk;

    WaterQuality(double hydration, double illnessRisk) {
        this.hydration = hydration;
        this.illnessRisk = illnessRisk;
    }

    public double hydrationFactor() {
        return hydration;
    }

    public double illnessRisk() {
        return illnessRisk;
    }

    public boolean safeToDrink() {
        return illnessRisk <= 0 && hydration > 0;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Section 23.2: boiling makes water drinkable. It kills what lives in the water, so it clears
     * swamp and river water — but boiling seawater only concentrates the salt, which is why
     * distillation is listed separately there and belongs to the later purification chain.
     */
    public WaterQuality boiled() {
        return this == SALT ? SALT : FRESH;
    }

    public static WaterQuality byName(String name) {
        if (name == null) return null;
        for (WaterQuality value : values()) {
            if (value.serializedName().equals(name.toLowerCase(Locale.ROOT))) return value;
        }
        return null;
    }

    public static WaterQuality byOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            throw new IllegalArgumentException("Unknown water quality: " + ordinal);
        }
        return values()[ordinal];
    }
}
