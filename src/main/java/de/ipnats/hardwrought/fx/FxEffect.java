package de.ipnats.hardwrought.fx;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The effects the client knows how to play, by name. The server only needs to know how an effect is
 * aimed; what it looks like lives entirely on the client ({@code FxPresets}).
 */
public enum FxEffect {
    NOVA(Aim.AREA, 0xFF6A1E),
    FIREBALL(Aim.RAY, 0xFF7A26),
    ARCANE_BOLT(Aim.RAY, 0xB45CFF),
    LIGHTNING(Aim.AREA, 0x9FD8FF),
    CHAIN_LIGHTNING(Aim.RAY, 0x9FD8FF),
    BEAM(Aim.RAY, 0xFF4FD8),
    RUNE_CIRCLE(Aim.AREA, 0x57E1FF),
    VORTEX(Aim.AREA, 0x8A3CFF),
    FROST_NOVA(Aim.AREA, 0xA8E8FF),
    SHIELD(Aim.SELF, 0x6FB8FF),
    HEAL(Aim.SELF, 0x7CFF8A),
    SPARKLE(Aim.AREA, 0xFFE38A),
    /** A hovering mote of light that lights its surroundings for a while: the plain Light rune. */
    LIGHT_ORB(Aim.AREA, 0xFFF0B0),
    /** A step through nothing: drawn in at the start, a streak through the air, a burst at the end. */
    TELEPORT(Aim.RAY, 0x9A6AFF),
    /** A rune glowing faintly on the ground for a while, scale seconds long: a trap, a field. */
    SIGIL(Aim.AREA, 0xD8D0C0),
    /** A gout of flame from the hand to the target. */
    FLAME_SPRAY(Aim.RAY, 0xFF6A2A),
    /** A gush of water from the hand, splashing where it lands. */
    WATER_SPRAY(Aim.RAY, 0x3C8CFF),
    /** A gust of wind: streaks of air rushing forward and a ring where it strikes. */
    WIND_GUST(Aim.RAY, 0xB8F0E0),
    /** Stone flung from the hand and bursting up from the ground where it lands. */
    EARTH_SPIKES(Aim.RAY, 0x9A7A4A),
    /** A ray of light from the hand, glinting, flaring where it ends. */
    LIGHT_RAY(Aim.RAY, 0xFFF0B0),
    /** A billow of darkness rolling forward and swelling where it ends. */
    DARK_CLOUD(Aim.RAY, 0x7A4ADC),
    /** Shadow drawn in around a body as it vanishes: the veil. */
    VEIL(Aim.SELF, 0x3A1F5C),
    /** Embers circling a body that has taken on a skin of fire. */
    EMBER_SKIN(Aim.SELF, 0xFF6A2A),
    /** One pulse of a healing ring on the ground; the scale is its radius in blocks. */
    HEAL_RING(Aim.AREA, 0xFFF0B0),
    /** One pulse of a shroud of darkness; the scale is its radius. */
    SHROUD(Aim.AREA, 0x7A4ADC),
    /** One pulse of a sucking mire; the scale is its radius. */
    MIRE(Aim.AREA, 0x6A5030),
    /** One pulse of scalding steam; the scale is its radius. */
    STEAM(Aim.AREA, 0xE8F0F4),
    /** A burst of fire whirled up by wind. */
    FIRESTORM(Aim.AREA, 0xFF6A2A),
    /** A wave through the ground, throwing dust and stones up as it runs outward. */
    SHOCKWAVE(Aim.AREA, 0x9A7A4A),
    SHOWCASE(Aim.SELF, 0xFFFFFF),
    SHAKE(Aim.SELF, 0xFFFFFF),
    FLASH(Aim.SELF, 0xFFFFFF),
    CLEAR(Aim.SELF, 0xFFFFFF);

    /** How the effect is placed when a player starts it without giving positions. */
    public enum Aim {
        /** At the block the player looks at. */
        AREA,
        /** From the player's hand to the block the player looks at. */
        RAY,
        /** On the player. */
        SELF
    }

    private final Aim aim;
    private final int defaultColor;

    FxEffect(Aim aim, int defaultColor) {
        this.aim = aim;
        this.defaultColor = defaultColor;
    }

    public Aim aim() {
        return aim;
    }

    public int defaultColor() {
        return defaultColor;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<FxEffect> byId(String id) {
        return Arrays.stream(values()).filter(effect -> effect.id().equals(id)).findFirst();
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(FxEffect::id).toList();
    }
}
