package de.ipnats.hardwrought.environment;

/** Broad climate bands used where a raw biome temperature would be needlessly fragile. */
public enum BiomeClimate {
    COLD("cold", 0.45),
    NEUTRAL("neutral", 1.00),
    WARM("warm", 1.45);

    private static final float COLD_MAX_TEMPERATURE = 0.30f;
    private static final float WARM_MIN_TEMPERATURE = 0.90f;

    private final String serializedName;
    private final double evaporationFactor;

    BiomeClimate(String serializedName, double evaporationFactor) {
        this.serializedName = serializedName;
        this.evaporationFactor = evaporationFactor;
    }

    public static BiomeClimate fromBaseTemperature(float temperature) {
        if (!Float.isFinite(temperature)) throw new IllegalArgumentException("Non-finite biome temperature");
        if (temperature <= COLD_MAX_TEMPERATURE) return COLD;
        if (temperature >= WARM_MIN_TEMPERATURE) return WARM;
        return NEUTRAL;
    }

    public String serializedName() {
        return serializedName;
    }

    public double evaporationFactor() {
        return evaporationFactor;
    }
}
