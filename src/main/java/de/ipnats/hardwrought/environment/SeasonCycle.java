package de.ipnats.hardwrought.environment;

import net.minecraft.server.level.ServerLevel;

/** A zero-allocation calendar derived from the world's persisted daytime clock. */
public final class SeasonCycle {
    private static final Season[] SEASONS = Season.values();
    public static final long TICKS_PER_DAY = 24_000L;
    /** Twelve Minecraft days make a season; a complete year therefore lasts 48 days. */
    public static final int DAYS_PER_SEASON = 12;
    public static final long TICKS_PER_SEASON = TICKS_PER_DAY * DAYS_PER_SEASON;
    public static final long TICKS_PER_YEAR = TICKS_PER_SEASON * SEASONS.length;

    private SeasonCycle() { }

    public static Season current(ServerLevel level) {
        return at(level.getOverworldClockTime());
    }

    public static Season at(long dayTime) {
        long tickInYear = Math.floorMod(dayTime, TICKS_PER_YEAR);
        int index = (int) (tickInYear / TICKS_PER_SEASON);
        return SEASONS[index];
    }

    /** One-based day number, useful for diagnostics and future calendars. */
    public static int dayInSeason(long dayTime) {
        long tickInYear = Math.floorMod(dayTime, TICKS_PER_YEAR);
        return (int) ((tickInYear % TICKS_PER_SEASON) / TICKS_PER_DAY) + 1;
    }
}
