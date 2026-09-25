package de.ipnats.hardwrought.environment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The air at one place, as volume fractions: what is left of the oxygen, and the three gases that
 * exist as blocks in the world (see {@link Gases#sample}). Values are bounded on every operation, so a
 * reading can never drift into a state the rest of the game would have to guess about.
 */
public record GasMixture(double oxygen, double carbonDioxide, double methane, double carbonMonoxide) {
    // Each field uses a bounded codec, so a saved or datapack value outside its range is rejected
    // during load while the compact constructor keeps every in-memory operation inside the bounds.
    public static final double OUTDOOR_OXYGEN = 0.209;
    public static final double OUTDOOR_CARBON_DIOXIDE = 0.0004;
    public static final double MAX_OXYGEN = 0.25;
    public static final double MAX_CARBON_DIOXIDE = 0.15;
    public static final double MAX_METHANE = 0.25;
    public static final double MAX_CARBON_MONOXIDE = 0.01;

    /** Section 18.1: below this an occupant is impaired, and the reserve starts to matter. */
    public static final double OXYGEN_IMPAIRED = 0.160;
    public static final double OXYGEN_DANGEROUS = 0.120;
    public static final double OXYGEN_LETHAL = 0.085;
    /** Section 18.2: carbon dioxide is odourless, so these thresholds are symptoms, not warnings. */
    public static final double CARBON_DIOXIDE_NOTICEABLE = 0.010;
    public static final double CARBON_DIOXIDE_SEVERE = 0.040;
    public static final double CARBON_DIOXIDE_LETHAL = 0.080;
    /** Section 18.3: the real flammability window of methane in air. */
    public static final double METHANE_EXPLOSIVE_MIN = 0.050;
    public static final double METHANE_EXPLOSIVE_MAX = 0.150;
    /**
     * Carbon monoxide: a headache at two hundred parts per million, dizziness and collapse at eight
     * hundred, death within the hour at sixteen hundred. It gives no warning of its own at all.
     */
    public static final double CARBON_MONOXIDE_NOTICEABLE = 0.0002;
    public static final double CARBON_MONOXIDE_SEVERE = 0.0008;
    public static final double CARBON_MONOXIDE_LETHAL = 0.0016;

    public static final GasMixture OUTDOOR = new GasMixture(OUTDOOR_OXYGEN, OUTDOOR_CARBON_DIOXIDE, 0, 0);

    public static final Codec<GasMixture> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(0, MAX_OXYGEN).fieldOf("oxygen").forGetter(GasMixture::oxygen),
            Codec.doubleRange(0, MAX_CARBON_DIOXIDE).fieldOf("carbon_dioxide").forGetter(GasMixture::carbonDioxide),
            Codec.doubleRange(0, MAX_METHANE).fieldOf("methane").forGetter(GasMixture::methane),
            Codec.doubleRange(0, MAX_CARBON_MONOXIDE).fieldOf("carbon_monoxide").forGetter(GasMixture::carbonMonoxide)
    ).apply(instance, GasMixture::new));

    public GasMixture {
        oxygen = clamp(oxygen, 0, MAX_OXYGEN);
        carbonDioxide = clamp(carbonDioxide, 0, MAX_CARBON_DIOXIDE);
        methane = clamp(methane, 0, MAX_METHANE);
        carbonMonoxide = clamp(carbonMonoxide, 0, MAX_CARBON_MONOXIDE);
    }

    public GasMixture add(double oxygenDelta, double carbonDioxideDelta, double methaneDelta,
                          double carbonMonoxideDelta) {
        return new GasMixture(oxygen + oxygenDelta, carbonDioxide + carbonDioxideDelta,
                methane + methaneDelta, carbonMonoxide + carbonMonoxideDelta);
    }

    public boolean breathable() {
        return oxygen >= OXYGEN_IMPAIRED && carbonDioxide < CARBON_DIOXIDE_NOTICEABLE
                && carbonMonoxide < CARBON_MONOXIDE_NOTICEABLE;
    }

    /** Section 18.3: only a concentration inside the flammability window can ignite. */
    public boolean explosive() {
        return methane >= METHANE_EXPLOSIVE_MIN && methane <= METHANE_EXPLOSIVE_MAX;
    }

    /** 0 at the outdoor baseline, 1 at the lethal threshold. Used for bounded, monotone effects. */
    public double oxygenStress() {
        if (oxygen >= OXYGEN_IMPAIRED) return 0;
        return clamp((OXYGEN_IMPAIRED - oxygen) / (OXYGEN_IMPAIRED - OXYGEN_LETHAL), 0, 1);
    }

    public double carbonDioxideStress() {
        if (carbonDioxide <= CARBON_DIOXIDE_NOTICEABLE) return 0;
        return clamp((carbonDioxide - CARBON_DIOXIDE_NOTICEABLE)
                / (CARBON_DIOXIDE_LETHAL - CARBON_DIOXIDE_NOTICEABLE), 0, 1);
    }

    public double carbonMonoxideStress() {
        if (carbonMonoxide < CARBON_MONOXIDE_NOTICEABLE) return 0;
        return clamp(carbonMonoxide / CARBON_MONOXIDE_LETHAL, 0, 1);
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
