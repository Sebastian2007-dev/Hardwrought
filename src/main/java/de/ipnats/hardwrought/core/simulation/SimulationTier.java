package de.ipnats.hardwrought.core.simulation;

/** Intervals are measured in game ticks, never wall-clock time. */
public enum SimulationTier {
    CRITICAL(1), FAST(5), MEDIUM(20), SLOW(200);

    private final int interval;

    SimulationTier(int interval) {
        this.interval = interval;
    }

    public int interval() {
        return interval;
    }
}
