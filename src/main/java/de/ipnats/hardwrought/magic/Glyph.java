package de.ipnats.hardwrought.magic;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Everything a mage can know: the six element runes and the auxiliary runes. One list, so that
 * knowledge, rune stones and ruins treat them alike — an arrow has to be found just as Fire has.
 */
public enum Glyph implements StringRepresentable {
    FIRE(Rune.FIRE), WATER(Rune.WATER), WIND(Rune.WIND), EARTH(Rune.EARTH), LIGHT(Rune.LIGHT), DARK(Rune.DARK),
    ARROW(Sign.ARROW), CIRCLE(Sign.CIRCLE), TRIANGLE(Sign.TRIANGLE), SPIRAL(Sign.SPIRAL), SQUARE(Sign.SQUARE),
    STRIKE(Sign.STRIKE),
    // Added later; kept at the end, since what a player knows is stored by position in this list.
    LIGHTNING(Rune.LIGHTNING);

    private final Rune rune;
    private final Sign sign;

    Glyph(Rune rune) {
        this.rune = rune;
        this.sign = null;
    }

    Glyph(Sign sign) {
        this.rune = null;
        this.sign = sign;
    }

    /** The element rune, or {@code null} for an auxiliary one. */
    public Rune rune() {
        return rune;
    }

    /** The auxiliary rune, or {@code null} for an element rune. */
    public Sign sign() {
        return sign;
    }

    public static Glyph of(Rune rune) {
        for (Glyph glyph : values()) if (glyph.rune == rune) return glyph;
        throw new IllegalArgumentException("No glyph for " + rune);
    }

    public static Glyph of(Sign sign) {
        for (Glyph glyph : values()) if (glyph.sign == sign) return glyph;
        throw new IllegalArgumentException("No glyph for " + sign);
    }

    public String translationKey() {
        return rune != null ? rune.translationKey() : sign.translationKey();
    }

    /** The colour it is named in: its element's, or a pale stone grey for an auxiliary rune. */
    public int color() {
        return rune != null ? rune.color() : 0xD8D0C0;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
