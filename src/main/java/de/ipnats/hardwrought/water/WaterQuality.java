package de.ipnats.hardwrought.water;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

/**
 * The water categories of specification section 23.2. The order is the order of usefulness, so a
 * purification step can only ever move water toward {@link #PURIFIED}.
 *
 * <p>Only purified water is wholly safe. Water out of the world — even a spring or the rain — carries
 * a little risk, and the better it is cleaned, the less: boiled in a bowl over a fire early on,
 * through filters and machines later.
 *
 * @param hydration    what one drink of this water is worth, as a share of clean water. Salt water
 *                     is negative: drinking it costs the body more water than it supplies.
 * @param illnessRisk  chance per drink of falling ill from it, 0 for water that is safe
 */
public enum WaterQuality {
    /** Boiled, filtered or distilled: clean, and a little more refreshing than anything raw. Rarely a spring. */
    PURIFIED(1.10, 0.00, 0x6FD6F7, 0x5CCBF2, 0.70f),
    /** Spring, rain and meltwater: clear, but not quite safe until it has been cleaned. */
    FRESH(1.00, 0.04, 0x3F76E4, 0, 0.0f),
    /** Running water: carries less than a lake, but keeps itself moving. */
    RIVER(0.90, 0.12, 0x3D8BC0, 0x3D8BA8, 0.20f),
    /** Standing, warm, organic water. Drinkable only in the sense that it is wet. */
    SWAMP(0.65, 0.45, 0x6B6A33, 0x5E5B2C, 0.60f),
    /** Section 23.2: seawater takes more water out of the body than it puts in. */
    SALT(-0.60, 0.05, 0x4F8C8A, 0x3C6E70, 0.35f);

    public static final Codec<WaterQuality> CODEC = Codec.STRING.comapFlatMap(name -> {
        WaterQuality value = byName(name);
        return value == null ? DataResult.error(() -> "Unknown water quality: " + name) : DataResult.success(value);
    }, WaterQuality::serializedName);

    private final double hydration;
    private final double illnessRisk;
    private final int color;
    private final int worldTint;
    private final float worldBlend;

    WaterQuality(double hydration, double illnessRisk, int color, int worldTint, float worldBlend) {
        this.hydration = hydration;
        this.illnessRisk = illnessRisk;
        this.color = color;
        this.worldTint = worldTint;
        this.worldBlend = worldBlend;
    }

    /** The water as a bucket, a bottle, a bowl or a bar shows it, as RGB. */
    public int color() {
        return color;
    }

    /** The colour water of this kind is pulled towards in the world, as RGB, and how far (0 to 1). */
    public int worldTint() {
        return worldTint;
    }

    public float worldBlend() {
        return worldBlend;
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
     * Section 23.2: boiling makes water drinkable. It kills what lives in the water, so it purifies
     * swamp, river and spring water alike — but boiling seawater only concentrates the salt, which is
     * why distillation is listed separately there and belongs to the later purification chain.
     */
    public WaterQuality boiled() {
        return this == SALT ? SALT : PURIFIED;
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
