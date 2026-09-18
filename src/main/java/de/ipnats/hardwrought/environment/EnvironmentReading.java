package de.ipnats.hardwrought.environment;

/**
 * What the environment simulation reports about one position, as typed values. Other systems read
 * this record; the diagnostic strings of the debug HUD are never a gameplay data interface.
 *
 * @param temperature room temperature in degrees Celsius
 * @param wind        0 for still air, 1 for a fully exposed gale
 * @param sealed      true when the position sits in a closed space with its own atmosphere
 * @param volume      size of that space in blocks, or the cell budget when it is open air
 * @param insulation  0 for a bare metal shell, 1 for a fully insulated one
 */
public record EnvironmentReading(GasMixture gases, double temperature, double wind, boolean sealed,
                                 int volume, double insulation) {
    public EnvironmentReading {
        if (gases == null || !Double.isFinite(temperature) || !Double.isFinite(wind)
                || !Double.isFinite(insulation) || volume < 0) {
            throw new IllegalArgumentException("Invalid environment reading");
        }
    }

    public static EnvironmentReading outdoor(double temperature, double wind) {
        return new EnvironmentReading(GasMixture.OUTDOOR, temperature, wind, false, RoomScan.MAX_VOLUME, 0);
    }
}
