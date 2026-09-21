package de.ipnats.hardwrought.geology;

import java.util.Locale;

/**
 * How deep a drill reaches into the rock it stands on.
 *
 * <p>A mine runs out; the rock does not. A drill works the whole thickness of ground under a chunk
 * rather than a seam, so it keeps producing — slowly, and only what that rock actually holds. That
 * is the endless supply the late game needs without turning a finite world into an infinite one: the
 * rate is the limit, not the amount.
 *
 * <p>What a drill can bring up is gated by its tier, and the tier of an ore is stated per deposit in
 * the rock profile. A first drill brings up what the early game runs on; the rare ores need the
 * machine that can reach them.
 */
public enum DrillTier {
    /** Iron-framed: coal, iron, copper — everything the early game is built from. */
    BASIC("basic", 1, 600),
    /** The working machine: gold, redstone and lapis as well. */
    REINFORCED("reinforced", 2, 400),
    /** Reaches the rock nobody digs by hand: diamond and emerald. */
    DEEP("deep", 3, 300);

    private final String serializedName;
    private final int level;
    private final int intervalTicks;

    DrillTier(String serializedName, int level, int intervalTicks) {
        this.serializedName = serializedName;
        this.level = level;
        this.intervalTicks = intervalTicks;
    }

    public String serializedName() {
        return serializedName;
    }

    /** The highest ore tier this drill can bring up. */
    public int level() {
        return level;
    }

    /** Ticks between two pieces of ore out of bare rock. A body under the chunk is faster. */
    public int intervalTicks() {
        return intervalTicks;
    }

    public boolean reaches(int oreTier) {
        return oreTier <= level;
    }

    public static DrillTier byName(String name) {
        if (name == null) return null;
        for (DrillTier tier : values()) {
            if (tier.serializedName.equals(name.toLowerCase(Locale.ROOT))) return tier;
        }
        return null;
    }

    public static DrillTier byLevel(int level) {
        for (DrillTier tier : values()) {
            if (tier.level == level) return tier;
        }
        throw new IllegalArgumentException("No drill of tier " + level);
    }
}
