package de.ipnats.hardwrought.magic;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The fundamental runes, which are also the elements all magic is built from (magic specification
 * section 3): the six of the specification, and Lightning.
 *
 * <p>Each is a single stroke. Its shape is given here as the path a pen takes, in screen
 * coordinates (y grows downward), copied from the rune sheets in {@code runeconzept}. Only the
 * shape and its orientation matter: which end the stroke starts from does not, and neither does its
 * size or where it is drawn. Orientation does — Fire is a slanted line and Light an upright one,
 * Wind an arch and Dark a crescent opening to the side.
 */
public enum Rune implements StringRepresentable {
    /** A slash rising to the right. */
    FIRE(0xFF6A2A, line(380, 940, 875, 290)),
    /** A wave lying on its side: up, down, and up again. */
    WATER(0x3C8CFF, path(205, 665, 330, 580, 470, 528, 600, 600, 700, 690, 790, 720, 900, 680, 1055, 582)),
    /** An arch, open at the bottom. */
    WIND(0xB8F0E0, arc(630, 830, 300, 425, 180, 360)),
    /** A level line with a notch pressed into its middle. */
    EARTH(0x9A7A4A, path(150, 575, 340, 568, 520, 560, 625, 760, 730, 560, 910, 566, 1105, 572)),
    /** An upright line. */
    LIGHT(0xFFF0B0, line(628, 175, 628, 970)),
    /** A crescent, open to the right. */
    DARK(0x7A4ADC, arc(726, 590, 290, 290, -67.6, -295.7)),
    /** A bolt: slanting down to the left, a short step to the right, and down to the left again. */
    LIGHTNING(0xC8E4FF, path(780, 150, 420, 560, 800, 560, 440, 980));

    public static final Codec<Rune> CODEC = StringRepresentable.fromEnum(Rune::values);

    private final int color;
    private final List<float[]> shape;

    Rune(int color, List<float[]> shape) {
        this.color = color;
        this.shape = shape;
    }

    /** The colour of the element, for its light and its particles. */
    public int color() {
        return color;
    }

    /** The ideal stroke, point by point, in the coordinates of the rune sheets. */
    public List<float[]> shape() {
        return shape;
    }

    /** Light and Dark cancel each other (section 7), and Earth grounds Lightning. */
    public Rune opposite() {
        return switch (this) {
            case LIGHT -> DARK;
            case DARK -> LIGHT;
            case EARTH -> LIGHTNING;
            case LIGHTNING -> EARTH;
            default -> null;
        };
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return id();
    }

    public String translationKey() {
        return "rune.hardwrought." + id();
    }

    public static Rune byOrdinal(int ordinal) {
        Rune[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    private static List<float[]> line(float x0, float y0, float x1, float y1) {
        return path(x0, y0, x1, y1);
    }

    private static List<float[]> path(float... xy) {
        List<float[]> points = new ArrayList<>();
        for (int i = 0; i + 1 < xy.length; i += 2) points.add(new float[] {xy[i], xy[i + 1]});
        return List.copyOf(points);
    }

    /** An elliptical arc around (cx, cy), from one angle to another in degrees, y downward. */
    private static List<float[]> arc(float cx, float cy, float rx, float ry, double fromDegrees, double toDegrees) {
        List<float[]> points = new ArrayList<>();
        int steps = 32;
        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(fromDegrees + (toDegrees - fromDegrees) * i / steps);
            points.add(new float[] {(float) (cx + rx * Math.cos(angle)), (float) (cy + ry * Math.sin(angle))});
        }
        return List.copyOf(points);
    }
}
