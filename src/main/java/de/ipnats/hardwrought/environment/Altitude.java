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
 *   load         the deep presses         — on the body, more the deeper it goes
 * </pre>
 *
 * <p>Thin air and the load of the deep both wear a body down for as long as it stays in them (see
 * {@link #strain}): the heights and the depths are places to go to and come back from, not to live in.
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

    /**
     * The weight of the rock and the air over a place in the deep, relative to sea level: one more
     * atmosphere for every this many blocks below it. Three at the floor of the world.
     */
    public static final int PRESSURE_DOUBLING_DEPTH = 160;
    /** The pressure a body takes without harm, and the pressure under which it is failing as fast as it can. */
    public static final double PRESSURE_BORNE = 1.6, PRESSURE_CRUSHING = 3.0;
    /**
     * Strain a body takes on per second where the air is as thin as it gets, and where the pressure is
     * as great as it gets. Strain is counted to a hundred: the top of the highest mountains is about
     * ten minutes away from that, the floor of the world about seven.
     */
    public static final double THIN_AIR_STRAIN = 0.28, PRESSURE_STRAIN = 0.24;

    /**
     * The most of the strain of thin air, or of pressure, that being used to it takes away. Never
     * all of it: sections 44 and 48 say adaptation alone is not enough at the extremes, and must
     * never make them harmless.
     */
    public static final double HABIT_RELIEF = 0.70;
    /**
     * Seconds of being in the worst of it that make a body fully used to it, and seconds away from
     * it in which all of that is lost again. Getting used to a place takes hours of being there, in
     * many visits; it fades over many more hours of being somewhere else.
     */
    public static final double HABIT_SECONDS = 2.5 * 3600, HABIT_FADING_SECONDS = 8 * 3600;
    /** How much of a habit forms in a body that is not eating a balanced diet: the body needs the means to change. */
    public static final double HABIT_UNFED = 0.5;

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

    /**
     * How hard the deep presses on a body at this height, in atmospheres: one at sea level and above,
     * growing steadily below. This is not {@link #pressure}: that one says how much air there is to
     * breathe, and the deep has all the air a lung can use. This one is the load on the body.
     */
    public static double depthPressure(int y) {
        return y >= SEA_LEVEL ? 1.0 : 1.0 + (SEA_LEVEL - y) / (double) PRESSURE_DOUBLING_DEPTH;
    }

    /** How far the pressure at this height is beyond what a body bears, 0 to 1. Nought above about Y -32. */
    public static double pressureStress(int y) {
        return Math.clamp((depthPressure(y) - PRESSURE_BORNE) / (PRESSURE_CRUSHING - PRESSURE_BORNE), 0.0, 1.0);
    }

    /**
     * Strain per second on a body breathing air this short of oxygen at this height: from thin air
     * or from pressure, whichever is the worse. The two never meet — thin air is a matter of the
     * heights and pressure of the deep — but bad air in a deep mine counts as thin air does.
     *
     * @param oxygenStress how short of oxygen the air breathed is, 0 to 1 (see {@link GasMixture#oxygenStress})
     */
    public static double strain(double oxygenStress, int y) {
        return Math.max(oxygenStress * THIN_AIR_STRAIN, pressureStress(y) * PRESSURE_STRAIN);
    }

    /**
     * As {@link #strain(double, int)}, for a body that is used to the heights and to the deep by this
     * much, 0 to 1 each (see {@link #habit}).
     */
    public static double strain(double oxygenStress, int y, double heightHabit, double depthHabit) {
        return Math.max(oxygenStress * THIN_AIR_STRAIN * (1 - HABIT_RELIEF * Math.clamp(heightHabit, 0.0, 1.0)),
                pressureStress(y) * PRESSURE_STRAIN * (1 - HABIT_RELIEF * Math.clamp(depthHabit, 0.0, 1.0)));
    }

    /**
     * How used to thin air, or to pressure, a body is after one more second, 0 to 1.
     *
     * <p>It grows only while the body is under that stress — the more of it the faster, but a little
     * is enough to learn from, which is how living half way up a mountain prepares for its summit —
     * and it fades, far more slowly, whenever the body is out of it.
     *
     * @param habit  how used to it the body is now
     * @param stress how much of that stress it is under, 0 to 1
     * @param fed    whether its diet is balanced
     */
    public static double habit(double habit, double stress, boolean fed) {
        double next = stress > 0
                ? habit + (0.3 + 0.7 * Math.clamp(stress, 0.0, 1.0)) / HABIT_SECONDS * (fed ? 1.0 : HABIT_UNFED)
                : habit - 1.0 / HABIT_FADING_SECONDS;
        return Math.clamp(next, 0.0, 1.0);
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
