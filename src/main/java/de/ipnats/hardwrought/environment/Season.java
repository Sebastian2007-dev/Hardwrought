package de.ipnats.hardwrought.environment;

/**
 * The four parts of Hardwrought's calendar. The values live here, next to the shared climate model,
 * so temperature, water, farming and ecology all read one calendar instead of inventing their own.
 *
 * <p>The temperature offset is not one number per season, and that is the whole design. A season
 * that moved every biome by the same amount would give a world where the tundra is always worse
 * than the plains by a fixed margin and nothing ever changes character. Real continents do not work
 * that way: the tropics barely notice the year while the far north swings forty degrees across it.
 * So each season carries an offset per climate band, and the consequence is the one worth having —
 * a cold biome is somewhere you can go in summer and somewhere that will kill you in winter, and
 * the desert is the other way round.
 */
public enum Season {
    /** The biome's own climate, near enough, and everything growing at once. */
    SPRING("spring", 2.0, 0.0, 0.0, 0.80, 1.25, 1.00, 0.00),
    /** Hot, dry and long. The one season a cold biome is hospitable, and the one the desert is not. */
    SUMMER("summer", 14.0, 7.0, 5.0, 1.60, 1.45, 0.55, 0.00),
    /** Spring's temperature without spring's growth, and the wettest, greyest quarter of the year. */
    AUTUMN("autumn", 2.0, -1.0, 0.0, 0.55, 1.00, 1.45, 0.35),
    /** Cold enough to be a problem everywhere and a death sentence where it was already cold. */
    WINTER("winter", -22.0, -14.0, -6.0, 0.12, 0.50, 1.00, 0.00);

    private final String serializedName;
    private final double coldOffset;
    private final double neutralOffset;
    private final double warmOffset;
    private final double evaporationFactor;
    private final double growthFactor;
    private final double rainFactor;
    private final double fogChance;

    Season(String serializedName, double coldOffset, double neutralOffset, double warmOffset,
           double evaporationFactor, double growthFactor, double rainFactor, double fogChance) {
        this.serializedName = serializedName;
        this.coldOffset = coldOffset;
        this.neutralOffset = neutralOffset;
        this.warmOffset = warmOffset;
        this.evaporationFactor = evaporationFactor;
        this.growthFactor = growthFactor;
        this.rainFactor = rainFactor;
        this.fogChance = fogChance;
    }

    public String serializedName() {
        return serializedName;
    }

    /** What a player is shown this season called, in their own language. */
    public String translationKey() {
        return "season.hardwrought." + serializedName;
    }

    /**
     * Degrees Celsius this season adds to the biome's own temperature, which depends on what sort of
     * place it is. A frozen one swings hardest; a hot one hardly notices the year.
     */
    public double temperatureOffset(BiomeClimate climate) {
        return switch (climate) {
            case COLD -> coldOffset;
            case NEUTRAL -> neutralOffset;
            case WARM -> warmOffset;
        };
    }

    /** Relative drying strength after temperature has already been considered. */
    public double evaporationFactor() {
        return evaporationFactor;
    }

    /**
     * What a season does to everything that grows. Spring is the push, summer is the long one,
     * autumn is merely ordinary, and in winter half of what would have ripened does not.
     */
    public double growthFactor() {
        return growthFactor;
    }

    /** How much more or less often it rains than it otherwise would. */
    public double rainFactor() {
        return rainFactor;
    }

    /** The chance, rolled once a day, that this season puts a morning fog on the world. */
    public double fogChance() {
        return fogChance;
    }
}
