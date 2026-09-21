package de.ipnats.hardwrought.knowledge;

import java.util.Locale;

/**
 * How well a player knows one thing, per specification section 81.
 *
 * <p>An unknown material tells a player almost nothing: a name it was told by someone, and rows of
 * question marks where the properties should be. Holding it makes it real; working with it is what
 * turns it into knowledge.
 */
public enum KnowledgeLevel {
    /** Never held, never worked with. The compendium shows the shape of an entry and nothing in it. */
    UNKNOWN("unknown"),
    /** Held at least once: the name is real, and so is the fact that it exists. */
    DISCOVERED("discovered"),
    /** Worked with: what it weighs, what it withstands, what it is for, and how it is made. */
    STUDIED("studied");

    private final String serializedName;

    KnowledgeLevel(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    /** True where the compendium may name the entry at all. */
    public boolean named() {
        return this != UNKNOWN;
    }

    /** True where properties, recipes and uses may be shown instead of question marks. */
    public boolean detailed() {
        return this == STUDIED;
    }

    public KnowledgeLevel and(KnowledgeLevel other) {
        return other == null || other.ordinal() <= ordinal() ? this : other;
    }

    public static KnowledgeLevel byName(String name) {
        if (name == null) return null;
        for (KnowledgeLevel level : values()) {
            if (level.serializedName.equals(name.toLowerCase(Locale.ROOT))) return level;
        }
        return null;
    }

    public static KnowledgeLevel byOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            throw new IllegalArgumentException("Unknown knowledge level: " + ordinal);
        }
        return values()[ordinal];
    }
}
