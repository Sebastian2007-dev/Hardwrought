package de.ipnats.hardwrought.machinery;

/**
 * How fast an item pipe carries: the same five metals a drill frame is built of, each one faster
 * than the last.
 *
 * <p>A pipe holds one batch at a time and hands it on to the next block after a set number of
 * ticks, so a run carries at the rate of its slowest pipe. Bronze moves a single item a second;
 * titanium moves a stack in a moment.
 */
public enum ItemPipeTier {
    BRONZE("bronze", 20, 1),
    IRON("iron", 10, 4),
    NICKEL("nickel", 8, 8),
    CHROMIUM("chromium", 4, 16),
    TITANIUM("titanium", 2, 32);

    private final String serializedName;
    private final int ticksPerBlock;
    private final int batch;

    ItemPipeTier(String serializedName, int ticksPerBlock, int batch) {
        this.serializedName = serializedName;
        this.ticksPerBlock = ticksPerBlock;
        this.batch = batch;
    }

    public String serializedName() {
        return serializedName;
    }

    /** Ticks a batch spends in one pipe before it moves on. */
    public int ticksPerBlock() {
        return ticksPerBlock;
    }

    /** How many items one pipe holds and moves at a time. */
    public int batch() {
        return batch;
    }

    /** Items a second a run of these pipes carries at most. */
    public float itemsPerSecond() {
        return batch * 20.0f / ticksPerBlock;
    }
}
