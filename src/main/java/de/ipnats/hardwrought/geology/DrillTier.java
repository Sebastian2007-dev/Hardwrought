package de.ipnats.hardwrought.geology;

import java.util.Locale;

/**
 * How deep a drill reaches into the rock it stands on — the five tiers of the ore drill, one for
 * each metal its frame can be built of.
 *
 * <p>A mine runs out; the rock does not. A drill works the whole thickness of ground under a chunk
 * rather than a seam, so it keeps producing — slowly, and only what that chunk actually holds. That
 * is the endless supply the late game needs without turning a finite world into an infinite one: the
 * rate is the limit, not the amount.
 *
 * <p>What a drill can bring up is gated by its tier, and the tier of an ore is stated per deposit in
 * the rock profile:
 * <ol>
 *   <li>bronze — coal and iron, what everything after is built with;</li>
 *   <li>iron — copper, tin, zinc and lead as well;</li>
 *   <li>nickel — gold, redstone, lapis, manganese, magnesium, aluminium, nickel;</li>
 *   <li>chromium — diamond, emerald, cobalt, chromium, mercury;</li>
 *   <li>titanium — titanium, tungsten, uranium, thorium, platinum: everything.</li>
 * </ol>
 */
public enum DrillTier {
    BRONZE("bronze", 1, 600),
    IRON("iron", 2, 500),
    NICKEL("nickel", 3, 400),
    CHROMIUM("chromium", 4, 320),
    TITANIUM("titanium", 5, 240);

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

    /** Ticks between two pieces of ore out of bare rock at the drill's rated speed. A body under the chunk is faster. */
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
