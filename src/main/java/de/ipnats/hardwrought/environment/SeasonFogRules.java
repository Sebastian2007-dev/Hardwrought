package de.ipnats.hardwrought.environment;

/**
 * What a fog does besides being looked at. Specification section 24.
 *
 * <p>An autumn fog is not only a shorter view. It is a morning where the sun never properly
 * arrives, so the things that are afraid of it are not, and a player who goes out in one meets
 * what they would otherwise have met at night. That is the whole point of putting it in autumn:
 * the season gets a character — a wet, grey, dangerous quarter — out of one rolled number.
 *
 * <p>Both rules are keyed to the same threshold so they arrive together. A thin fog is weather; a
 * thick one is an event, and a player can tell which they are standing in by looking.
 */
public final class SeasonFogRules {
    /**
     * How thick the fog has to be before it starts keeping the sun off. Set so that the ramp in and
     * out of a fog has a stretch at either end that is merely murky, and a middle that is not.
     */
    public static final float SMOTHERING = 0.45f;

    private SeasonFogRules() { }

    /** Whether the fog is currently thick enough to keep the sun off an undead. */
    public static boolean sunIsSmothered() {
        return SeasonState.fog() >= SMOTHERING;
    }

    /**
     * Whether a monster may spawn here in daylight. In a fog this thick the light the spawn rules
     * are looking at is not reaching the ground anyway.
     */
    public static boolean darkEnoughForMonsters() {
        return sunIsSmothered();
    }
}
