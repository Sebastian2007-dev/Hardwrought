package de.ipnats.hardwrought.environment;

import java.util.Locale;

/**
 * The vertical zones of specification sections 42 and 49, from the abyss to extreme altitude.
 *
 * <p>Section 41 asks for a world from Y −256 to Y +1024 and adds the condition that decides whether
 * the milestone is worth anything: <em>the extra height and depth must matter mechanically</em>. The
 * zones are where that is written down. They are named bands rather than magic numbers scattered
 * through the systems, so air pressure, geothermal heat, diagnostics and everything the later
 * milestones add all cut the world at the same places.
 *
 * <pre>
 *   Y +1024  ┌ extreme altitude   thin air, gales, fire barely burns
 *            ├ high mountains
 *            ├ alpine
 *            ├ normal surface     what a vanilla world is
 *            ├ shallow caves
 *            ├ deep caves
 *            ├ lower caverns      warm, badly ventilated
 *   Y −256   └ abyss              hot, airless, no way out but the way in
 * </pre>
 *
 * <p>A zone knows only where it begins. What it does to a player comes from {@link Altitude}, which
 * is a continuous curve — a band boundary is a name for a stretch of it, never a step.
 */
public enum VerticalZone {
    ABYSS("abyss", Integer.MIN_VALUE),
    LOWER_CAVERNS("lower_caverns", -180),
    DEEP_CAVES("deep_caves", -100),
    SHALLOW_CAVES("shallow_caves", -20),
    NORMAL_SURFACE("normal_surface", 50),
    ALPINE("alpine", 200),
    HIGH_MOUNTAINS("high_mountains", 400),
    EXTREME_ALTITUDE("extreme_altitude", 700);

    /** Section 41: the range the world is built for. */
    public static final int WORLD_FLOOR_Y = -256;
    public static final int WORLD_CEILING_Y = 1024;

    private final String serializedName;
    private final int floorY;

    VerticalZone(String serializedName, int floorY) {
        this.serializedName = serializedName;
        this.floorY = floorY;
    }

    public String serializedName() {
        return serializedName;
    }

    /** The lowest Y that belongs to this zone. */
    public int floorY() {
        return floorY;
    }

    public boolean underground() {
        return ordinal() <= SHALLOW_CAVES.ordinal();
    }

    /** True where the air is thin enough that section 43 has something to say about it. */
    public boolean thinAir() {
        return ordinal() >= ALPINE.ordinal();
    }

    public static VerticalZone at(int y) {
        VerticalZone found = ABYSS;
        for (VerticalZone zone : values()) {
            if (y >= zone.floorY) found = zone;
        }
        return found;
    }

    public static VerticalZone byName(String name) {
        if (name == null) return null;
        for (VerticalZone zone : values()) {
            if (zone.serializedName.equals(name.toLowerCase(Locale.ROOT))) return zone;
        }
        return null;
    }
}
