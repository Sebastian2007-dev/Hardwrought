package de.ipnats.hardwrought.combat;

/** The bounded set of combat results the server reports to a player for display. */
public enum CombatEvent {
    NONE, GUARD_UP, GUARD_DOWN, HIT, BLOCKED, PARRIED, GUARD_BROKEN, PARRY_LANDED;

    public static CombatEvent byOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            throw new IllegalArgumentException("Unknown combat event: " + ordinal);
        }
        return values()[ordinal];
    }
}
