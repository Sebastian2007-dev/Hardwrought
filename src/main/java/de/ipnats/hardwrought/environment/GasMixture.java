package de.ipnats.hardwrought.environment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The atmosphere of one environmental cell, as volume fractions. Smoke is a density rather than a
 * gas fraction because it is particulate. Values are bounded on every operation, so a cell can never
 * drift into a state the rest of the simulation would have to guess about.
 */
public record GasMixture(double oxygen, double carbonDioxide, double methane, double smoke) {
    // Each field uses a bounded codec, so a saved or datapack value outside its range is rejected
    // during load while the compact constructor keeps every in-memory operation inside the bounds.
    public static final double OUTDOOR_OXYGEN = 0.209;
    public static final double OUTDOOR_CARBON_DIOXIDE = 0.0004;
    public static final double MAX_OXYGEN = 0.25;
    public static final double MAX_CARBON_DIOXIDE = 0.15;
    public static final double MAX_METHANE = 0.25;

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
    public static final double SMOKE_CHOKING = 0.30;

    public static final GasMixture OUTDOOR = new GasMixture(OUTDOOR_OXYGEN, OUTDOOR_CARBON_DIOXIDE, 0, 0);

    public static final Codec<GasMixture> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(0, MAX_OXYGEN).fieldOf("oxygen").forGetter(GasMixture::oxygen),
            Codec.doubleRange(0, MAX_CARBON_DIOXIDE).fieldOf("carbon_dioxide").forGetter(GasMixture::carbonDioxide),
            Codec.doubleRange(0, MAX_METHANE).fieldOf("methane").forGetter(GasMixture::methane),
            Codec.doubleRange(0, 1).fieldOf("smoke").forGetter(GasMixture::smoke)
    ).apply(instance, GasMixture::new));

    public GasMixture {
        oxygen = clamp(oxygen, 0, MAX_OXYGEN);
        carbonDioxide = clamp(carbonDioxide, 0, MAX_CARBON_DIOXIDE);
        methane = clamp(methane, 0, MAX_METHANE);
        smoke = clamp(smoke, 0, 1);
    }

    public GasMixture add(double oxygenDelta, double carbonDioxideDelta, double methaneDelta, double smokeDelta) {
        return new GasMixture(oxygen + oxygenDelta, carbonDioxide + carbonDioxideDelta,
                methane + methaneDelta, smoke + smokeDelta);
    }

    /** Ventilation against sea-level air, which is what a room at an ordinary height breathes. */
    public GasMixture ventilate(double rate) {
        return ventilate(rate, OUTDOOR);
    }

    /**
     * Ventilation: the mixture moves toward the air outside by the given fraction per step.
     *
     * <p>Which air that is depends on where the room stands. Section 43 makes the outside thin at
     * altitude, and a room can only ever be aired out with what is outside it — opening a window at
     * Y 800 does not produce sea-level air.
     */
    public GasMixture ventilate(double rate, GasMixture outside) {
        double amount = clamp(rate, 0, 1);
        if (outside == null) throw new IllegalArgumentException("Ventilation needs air to exchange with");
        return new GasMixture(
                oxygen + (outside.oxygen - oxygen) * amount,
                carbonDioxide + (outside.carbonDioxide - carbonDioxide) * amount,
                methane + (outside.methane - methane) * amount,
                smoke + (OUTDOOR.smoke - smoke) * amount);
    }

    /** Returns a copy with one component replaced; the bounds are applied as usual. */
    public GasMixture with(String component, double value) {
        return switch (component) {
            case "oxygen" -> new GasMixture(value, carbonDioxide, methane, smoke);
            case "carbon_dioxide" -> new GasMixture(oxygen, value, methane, smoke);
            case "methane" -> new GasMixture(oxygen, carbonDioxide, value, smoke);
            case "smoke" -> new GasMixture(oxygen, carbonDioxide, methane, value);
            default -> throw new IllegalArgumentException("Unknown gas component: " + component);
        };
    }

    /**
     * How strongly the heavy and light gases separate between the floor and the ceiling of a space.
     * A cell stores one average mixture; this is the local sample taken at a height inside it, so
     * nothing is created or destroyed by asking.
     */
    public static final double STRATIFICATION = 0.6;

    /**
     * The air actually breathed at a height in a space: 0 at the floor, 1 at the ceiling.
     *
     * <p>Gases do not mix evenly. Carbon dioxide is half again as heavy as air (44 against 29 g/mol)
     * and pools in the low places of a mine, which is why blackdamp kills people in shafts and
     * cellars. Methane is far lighter (16 g/mol) and collects against the roof, which is why firedamp
     * is found there and why a safety lamp is held up. Oxygen (32 g/mol) is close enough to air to
     * stay even, and smoke rises with the heat that makes it.
     */
    public GasMixture at(double heightFraction) {
        double height = clamp(heightFraction, 0, 1);
        double low = 1.0 + STRATIFICATION * (1.0 - 2.0 * height);
        double high = 1.0 + STRATIFICATION * (2.0 * height - 1.0);
        return new GasMixture(oxygen, carbonDioxide * low, methane * high, smoke * high);
    }

    public boolean breathable() {
        return oxygen >= OXYGEN_IMPAIRED && carbonDioxide < CARBON_DIOXIDE_NOTICEABLE && smoke < SMOKE_CHOKING;
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

    public double smokeStress() {
        return clamp(smoke / SMOKE_CHOKING, 0, 1);
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
