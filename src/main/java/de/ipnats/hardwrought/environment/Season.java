package de.ipnats.hardwrought.environment;

/**
 * The four parts of Hardwrought's calendar.  The values live here, next to the shared climate
 * model, so water, farming and ecology can all use the same season later instead of inventing
 * separate calendars.
 */
public enum Season {
    SPRING("spring", -1.0, 0.80),
    SUMMER("summer", 6.0, 1.60),
    AUTUMN("autumn", -2.0, 0.55),
    WINTER("winter", -9.0, 0.12);

    private final String serializedName;
    private final double temperatureOffset;
    private final double evaporationFactor;

    Season(String serializedName, double temperatureOffset, double evaporationFactor) {
        this.serializedName = serializedName;
        this.temperatureOffset = temperatureOffset;
        this.evaporationFactor = evaporationFactor;
    }

    public String serializedName() {
        return serializedName;
    }

    /** Degrees Celsius added to the biome/weather temperature. */
    public double temperatureOffset() {
        return temperatureOffset;
    }

    /** Relative drying strength after temperature has already been considered. */
    public double evaporationFactor() {
        return evaporationFactor;
    }
}
