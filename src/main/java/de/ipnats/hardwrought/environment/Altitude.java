package de.ipnats.hardwrought.environment;

/**
 * What height itself does to the air, specification sections 43, 45 and 47.
 *
 * <p>Three continuous curves, all of them pure arithmetic on one Y coordinate, so the whole vertical
 * model can be checked without a world:
 *
 * <pre>
 *   pressure     air thins going up      — less oxygen in every breath
 *   lapse        air cools going up      — and cools faster where it is thin
 *   geothermal   rock warms going down   — the deep is hot before anything burns in it
 * </pre>
 *
 * <p>Air pressure is the interesting one, because it does not need a single new rule to be felt.
 * Milestone 3 already models oxygen as a fraction of the air, and everything downstream — the
 * impaired and lethal thresholds, stamina, the safety lamp, the minimum a flame needs — reads that
 * one number. Thinning the outside air at altitude therefore reaches all of it at once: a shelter
 * built at Y 800 starts out with air a sea-level shelter only reaches after hours of breathing, and
 * somewhere above Y 700 an open fire no longer holds.
 */
public final class Altitude {
    /** Where an ordinary overworld surface sits, and the height everything here is relative to. */
    public static final int SEA_LEVEL = 64;

    /**
     * Air pressure halves every this many blocks above sea level. Chosen against the examples in
     * section 43 rather than against the real atmosphere, which is far too flat at this scale:
     * Y 300 is slightly thin, Y 600 is noticeably low and Y 900 is very low.
     */
    public static final int PRESSURE_HALVING = 960;
    /** Even the top of the world keeps this much air; vacuum is not a Minecraft altitude. */
    public static final double MIN_PRESSURE = 0.20;

    /** Degrees lost per block of height up to the knee — the ordinary atmospheric lapse rate. */
    public static final double LAPSE_LOWER = 0.0065;
    /** Above this, thin air holds heat badly and the rate nearly doubles. */
    public static final int LAPSE_KNEE = 320;
    public static final double LAPSE_UPPER = 0.012;

    /** Section 47: below this the rock starts adding its own heat. */
    public static final int GEOTHERMAL_START = 0;
    /** The bottom of the world, where the full geothermal offset applies. */
    public static final int GEOTHERMAL_FLOOR = VerticalZone.WORLD_FLOOR_Y;
    /** Degrees added at the floor. Fifteen degrees of surface plus this is the 45 °C of section 47. */
    public static final double GEOTHERMAL_MAX = 30.0;
    /** Heat rises slowly at first and steeply near the bottom, which fits the stated curve. */
    public static final double GEOTHERMAL_EXPONENT = 1.6;

    /** Where wind stops being weather and starts being altitude. */
    public static final int GALE_START_Y = 320;
    public static final int GALE_FULL_Y = 720;

    private Altitude() { }

    /**
     * Air pressure relative to sea level, 1.0 down to sea level and falling above it. Below sea
     * level it stays at 1.0: the real gain of a few percent down a mine is not worth modelling, and
     * pretending the deep is richer in oxygen would work against the hazards of section 46.
     */
    public static double pressure(int y) {
        if (y <= SEA_LEVEL) return 1.0;
        double halvings = (y - SEA_LEVEL) / (double) PRESSURE_HALVING;
        return Math.max(MIN_PRESSURE, Math.pow(0.5, halvings));
    }

    /**
     * The air outside at this height. Section 43: less <em>effective</em> oxygen, which is what a
     * fraction scaled by pressure is — the same share of a thinner atmosphere.
     */
    public static GasMixture outsideAir(int y) {
        double pressure = pressure(y);
        if (pressure >= 1.0) return GasMixture.OUTDOOR;
        return new GasMixture(GasMixture.OUTDOOR_OXYGEN * pressure,
                GasMixture.OUTDOOR_CARBON_DIOXIDE * pressure, 0, 0);
    }

    /** Degrees to take off the biome temperature for standing this high. Never negative. */
    public static double lapse(int y) {
        if (y <= SEA_LEVEL) return 0;
        if (y <= LAPSE_KNEE) return (y - SEA_LEVEL) * LAPSE_LOWER;
        return (LAPSE_KNEE - SEA_LEVEL) * LAPSE_LOWER + (y - LAPSE_KNEE) * LAPSE_UPPER;
    }

    /**
     * Section 47: degrees the rock adds at this depth. Nothing at the surface, about seven degrees
     * at Y −100, twenty at Y −200 and the full thirty at the floor of the world.
     */
    public static double geothermal(int y) {
        if (y >= GEOTHERMAL_START) return 0;
        double depth = Math.min(1.0, (GEOTHERMAL_START - y) / (double) (GEOTHERMAL_START - GEOTHERMAL_FLOOR));
        return GEOTHERMAL_MAX * Math.pow(depth, GEOTHERMAL_EXPONENT);
    }

    /**
     * Section 45: how much of a gale this height blows on its own, 0 to 1. Below the knee the
     * weather still decides; above it the wind is simply there, whatever the sky is doing.
     */
    public static double gale(int y) {
        if (y <= GALE_START_Y) return 0;
        return Math.min(1.0, (y - GALE_START_Y) / (double) (GALE_FULL_Y - GALE_START_Y));
    }

    /** True where the air is too thin for an open flame to hold, by the rule Milestone 3 already uses. */
    public static boolean tooThinToBurn(int y) {
        return outsideAir(y).oxygen() < EnvironmentSystem.FIRE_MINIMUM_OXYGEN;
    }
}
