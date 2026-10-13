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

    /** One-based day number counted from the world's first morning. */
    public static long dayOfWorld(long dayTime) {
        return Math.floorDiv(dayTime, TICKS_PER_DAY) + 1;
    }

    /** One-based year. */
    public static long year(long dayTime) {
        return Math.floorDiv(dayTime, TICKS_PER_YEAR) + 1;
    }

    /** How many whole days of this season are still to come, counting today. */
    public static int daysLeftInSeason(long dayTime) {
        return DAYS_PER_SEASON - dayInSeason(dayTime) + 1;
    }

    /**
     * How far the clock has to run forward to reach this day of this season.
     *
     * <p>Always forward, and always a whole number of days. Forward because the rest of the game
     * takes a clock that only increases for granted, and whole days because the answer keeps the
     * hour of the day it was asked at — a world set to winter at dawn is still at dawn afterwards,
     * which is what makes the result possible to look at.
     *
     * @return zero if the clock is already at exactly that moment
     */
    public static long ticksUntil(long dayTime, Season season, int dayInSeason) {
        if (season == null) throw new IllegalArgumentException("A season is required");
        if (dayInSeason < 1 || dayInSeason > DAYS_PER_SEASON) {
            throw new IllegalArgumentException("Day outside the season: " + dayInSeason);
        }
        long hour = Math.floorMod(dayTime, TICKS_PER_DAY);
        long target = season.ordinal() * TICKS_PER_SEASON + (dayInSeason - 1) * TICKS_PER_DAY + hour;
        long delta = target - Math.floorMod(dayTime, TICKS_PER_YEAR);
        return delta < 0 ? delta + TICKS_PER_YEAR : delta;
    }
}
