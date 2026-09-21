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

    /**
     * How much of a mixture the worse water has to make up before the whole of it counts as spoiled.
     * A quarter: a bucket of seawater ruins a barrel, and a cup of it does not.
     */
    public static final double CONTAMINATION_SHARE = 0.25;

    /** The less useful of two waters. The declaration order is the order of usefulness. */
    public static WaterQuality worse(WaterQuality first, WaterQuality second) {
        if (first == null || second == null) throw new IllegalArgumentException("Nothing to compare");
        return first.ordinal() >= second.ordinal() ? first : second;
    }

    /**
     * What comes out when water is poured into water. Contamination travels the easy way and
     * cleanliness the hard way: the worse of the two decides as soon as it makes up a quarter of the
     * mixture, so a pool is spoiled by far less than it takes to clean it again.
     *
     * <p>Pure arithmetic on two amounts, which is what makes the rule checkable without a world.
     */
    public static WaterQuality mix(WaterQuality present, int presentAmount,
                                   WaterQuality incoming, int incomingAmount) {
        if (present == null || incoming == null) throw new IllegalArgumentException("Nothing to mix");
        if (presentAmount < 0 || incomingAmount < 0) {
            throw new IllegalArgumentException("Cannot mix a negative amount of water");
        }
        if (presentAmount == 0) return incoming;
        if (incomingAmount == 0 || present == incoming) return present;
        WaterQuality worse = worse(present, incoming);
        int worseAmount = worse == present ? presentAmount : incomingAmount;
        long total = (long) presentAmount + incomingAmount;
        if (worseAmount >= total * CONTAMINATION_SHARE) return worse;
        return worse == present ? incoming : present;
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
