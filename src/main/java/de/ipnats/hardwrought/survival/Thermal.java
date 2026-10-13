package de.ipnats.hardwrought.survival;

/**
 * What the world does to a body's core temperature. Specification sections 14 to 17.
 *
 * <p>Everything here is a pure function of numbers, so the whole thermal model can be read in one
 * place and tested without a world. {@code SurvivalSystem} gathers the inputs; this decides what
 * they mean.
 *
 * <p>The model has one idea behind it: the body does not follow the air, it is <em>pulled</em>
 * towards it, and what decides how hard is what the player is wearing and whether they are wet.
 * Air at a given temperature and water at the same temperature are not the same thing at all —
 * water carries heat away some twenty-five times faster, which is why a lake is a relief in the
 * desert and a death sentence in a frozen one.
 */
public final class Thermal {
    /** Core temperature a healthy body holds. */
    public static final double NORMAL_BODY = 37.0;
    /** Below this the body is losing the argument and starts taking cold damage. */
    public static final double FREEZING_BODY = 34.0;
    /** Above this it is losing the other argument. */
    public static final double OVERHEATED_BODY = 40.5;

    /**
     * The air temperature a clothed, still body neither gains nor loses heat at. Rather lower than
     * room temperature because a working body makes its own heat and has to shed it somewhere.
     */
    public static final double COMFORT_AIR = 20.0;
    /**
     * The same point in water, and it is nowhere near the same number. Still water below about
     * 33 °C takes heat off a body faster than the body can make it, which is why swimming in a warm
     * lake cools a player down rather than warming them, and why cold water is the fastest way to
     * die of cold there is.
     */
    public static final double COMFORT_WATER = 33.0;

    /**
     * The cold does not arrive in proportion to itself. Every degree below comfort costs more than
     * the one before it, because a body sheds heat faster the bigger the difference is and because
     * the things it does about that — shivering, closing down the skin — run out. The exponent is
     * what lets one rule say both <q>a cold morning is unpleasant</q> and <q>a winter tundra kills
     * you</q>; a straight line can only ever say one of them, and whichever it says it gets the
     * other badly wrong.
     */
    private static final double TRANSFER_EXPONENT = 1.5;
    /** Scale of the cold side, applied to the exponentiated distance from comfort. */
    private static final double AIR_COLD_TRANSFER = 0.0253;
    /** The hot side, which a body answers with sweat and so loses rather more slowly. */
    private static final double AIR_HEAT_TRANSFER = 0.0279;
    /** In water there is no sweat and no still layer of warm air; one figure does both directions. */
    private static final double WATER_TRANSFER = 0.0216;

    /** How much of the air's swing a body of water takes on. Water is slow and deep. */
    private static final double WATER_FOLLOWS_AIR = 0.45;
    /** What a body of water sits at when the air is neither warm nor cold. */
    private static final double WATER_BASELINE = 12.0;
    /** Liquid water gets no colder than this, and no hotter either. */
    private static final double WATER_MIN = -1.5;
    private static final double WATER_MAX = 32.0;

    /** Wet clothing is barely clothing: this is the share of the insulation that survives a soaking. */
    private static final double WET_INSULATION_FLOOR = 0.20;
    /**
     * Submerged, the water is against the skin. Only a little more than one, because the move from
     * {@link #COMFORT_AIR} to {@link #COMFORT_WATER} has already said most of what being in water
     * means; this is the rest of it.
     */
    private static final double IMMERSION_EXPOSURE = 1.15;

    /** No clothing at all is exposure 1.0; these bound what clothing can do in either direction. */
    private static final double MIN_COLD_EXPOSURE = 0.15;
    private static final double MAX_COLD_EXPOSURE = 1.80;
    private static final double MIN_HEAT_EXPOSURE = 0.80;
    private static final double MAX_HEAT_EXPOSURE = 1.80;
    /** What insulation costs on the hot side, whichever way it points: it is in the way either way. */
    private static final double HEAT_TRAPPED_BY_INSULATION = 0.35;
    /** Mass worn in the sun, per kilogramme. */
    private static final double HEAT_PER_ARMOR_KG = 0.020;

    /** Wind strips the still layer of warm air off a body; clothing is what keeps it there. */
    private static final double WIND_CHILL = 2.2;
    /** Being wet in the cold, on top of what the wind is already doing. */
    private static final double WET_CHILL = 1.6;
    /** Heat a working body makes for itself. */
    private static final double EXERTION_HEAT = 0.45;

    private Thermal() { }

    /**
     * What the water in a place sits at, given the air above it. A lake is slow: it takes on less
     * than half the air's swing, so desert water is cool rather than hot and tundra water is at
     * freezing however cold the night above it gets.
     */
    public static double waterTemperature(double airTemperature) {
        double water = WATER_BASELINE + (airTemperature - WATER_BASELINE) * WATER_FOLLOWS_AIR;
        return clamp(water, WATER_MIN, WATER_MAX);
    }

    /** The surrounding temperature at which a body in this state is in balance. */
    public static double comfort(boolean immersed) {
        return immersed ? COMFORT_WATER : COMFORT_AIR;
    }

    /**
     * What a player's clothing is worth once it is wet. Insulation works by holding still air, and
     * water in the weave puts an end to that whether it came from the rain or from the lake.
     */
    public static double wornInsulation(double insulation, double wetness) {
        double dryShare = 1.0 - (1.0 - WET_INSULATION_FLOOR) * clamp(wetness, 0, 1);
        // Clothing that conducts rather than insulates does not get better for being soaked.
        return insulation >= 0 ? insulation * dryShare : insulation;
    }

    /**
     * How much of the cold reaches the body. One is naked; less than one is clothed; more than one
     * is wearing metal, which takes heat off a body rather than keeping it there — see the armor
     * profiles, where insulation is allowed to be negative for exactly this reason.
     */
    public static double coldExposure(double insulation, boolean immersed) {
        double exposure = clamp(1.0 - insulation, MIN_COLD_EXPOSURE, MAX_COLD_EXPOSURE);
        return immersed ? exposure * IMMERSION_EXPOSURE : exposure;
    }

    /**
     * How much of the heat reaches the body. There is no good armor here: insulation traps the
     * body's own heat, metal carries the sun's in, and all of it has to be lugged about.
     */
    public static double heatExposure(double insulation, double armorMassKg, boolean immersed) {
        double exposure = 1.0 + Math.abs(insulation) * HEAT_TRAPPED_BY_INSULATION
                + Math.max(0, armorMassKg) * HEAT_PER_ARMOR_KG;
        exposure = clamp(exposure, MIN_HEAT_EXPOSURE, MAX_HEAT_EXPOSURE);
        return immersed ? exposure * IMMERSION_EXPOSURE : exposure;
    }

    /**
     * Where the body is being pulled to. It does not arrive: the metabolism pass moves the real
     * value a fixed fraction of the way each second, so a player who steps out of a blizzard into a
     * warm room warms through over a minute rather than at once.
     *
     * @param ambient     the air, or the water, the player is actually in
     * @param immersed    whether that is water — it moves the balance point and raises conduction
     * @param insulation  coverage-weighted armor insulation, negative for metal, already wetted
     * @param armorMassKg what the four armor pieces weigh
     * @param wind        0 to 1, as the environment reports it
     * @param wetness     0 to 1
     * @param lacksFat    too little fat in the diet leaves nothing between the body and the cold
     * @param exerting    sprinting or swimming
     */
    public static double targetBody(double ambient, boolean immersed, double insulation,
                                    double armorMassKg, double wind, double wetness,
                                    boolean lacksFat, boolean exerting) {
        double delta = ambient - comfort(immersed);
        double distance = Math.pow(Math.abs(delta), TRANSFER_EXPONENT);
        double target;
        if (delta < 0) {
            double transfer = immersed ? WATER_TRANSFER : AIR_COLD_TRANSFER;
            target = NORMAL_BODY - distance * transfer * coldExposure(insulation, immersed);
            // Out of the water, wind and wet clothing are what turn a cold day into a dangerous one.
            if (!immersed) {
                target -= clamp(wind, 0, 1) * (1.0 - Math.min(0.9, Math.max(0, insulation))) * WIND_CHILL;
                target -= clamp(wetness, 0, 1) * WET_CHILL;
            }
            if (lacksFat) target -= SurvivalSystem.FAT_LACK_COLD;
        } else {
            double transfer = immersed ? WATER_TRANSFER : AIR_HEAT_TRANSFER;
            target = NORMAL_BODY + distance * transfer * heatExposure(insulation, armorMassKg, immersed);
        }
        if (exerting) target += EXERTION_HEAT + Math.max(0, armorMassKg) * 0.015;
        return target;
    }

    /**
     * What a body this far out of its range loses per damage pass. Not a flat tick: the further
     * gone it is the faster it goes, so a player who walks into a winter tundra in iron is not
     * given the same quarter of an hour as one who is merely chilly.
     */
    public static float thermalDamage(double bodyTemperature) {
        if (bodyTemperature < FREEZING_BODY) {
            return (float) (1.0 + Math.floor(FREEZING_BODY - bodyTemperature));
        }
        if (bodyTemperature > OVERHEATED_BODY) {
            return (float) (1.0 + Math.floor(bodyTemperature - OVERHEATED_BODY));
        }
        return 0;
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
