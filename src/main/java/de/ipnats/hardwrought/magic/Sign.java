package de.ipnats.hardwrought.magic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The auxiliary runes: shapes drawn with and around the element runes that say not what a spell is
 * made of but how and where it acts. They are read by their geometry ({@link SketchReader}), not by
 * template, since an arrow may point anywhere and a circle may be any size.
 */
public enum Sign {
    /** The spell acts where the arrow ends: its direction is a direction from the caster, its length a distance. */
    ARROW,
    /** Runes drawn inside a circle act on the caster. A circle with nothing in it is a shield. */
    CIRCLE,
    /** A spell with a triangle bursts outward all around where it acts. */
    TRIANGLE,
    /** A spell with a spiral lingers where it acts for a while, as a field. */
    SPIRAL,
    /** A spell with a square is laid on the ground and waits for someone to step on it. */
    SQUARE,
    /** A rune struck through with a line has its element turned around. */
    STRIKE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "sign.hardwrought." + id();
    }

    /** How it looks drawn properly, as strokes in a square of side 100, y downward; for the rune lore. */
    public List<List<float[]>> strokes() {
        return switch (this) {
            case ARROW -> List.of(path(10, 90, 85, 15, 55, 18, 85, 15, 82, 45));
            case CIRCLE -> List.of(ellipse(50, 50, 40, 40, 0, 360));
            case TRIANGLE -> List.of(path(50, 10, 90, 85, 10, 85, 50, 10));
            case SPIRAL -> List.of(spiral());
            case SQUARE -> List.of(path(15, 15, 85, 15, 85, 85, 15, 85, 15, 15));
            case STRIKE -> List.of(path(50, 15, 50, 85), path(15, 75, 85, 25));
        };
    }

    private static List<float[]> path(float... xy) {
        List<float[]> points = new ArrayList<>();
        for (int i = 0; i + 1 < xy.length; i += 2) points.add(new float[] {xy[i], xy[i + 1]});
        return points;
    }

    private static List<float[]> ellipse(float cx, float cy, float rx, float ry, double from, double to) {
        List<float[]> points = new ArrayList<>();
        for (int i = 0; i <= 40; i++) {
            double a = Math.toRadians(from + (to - from) * i / 40);
            points.add(new float[] {(float) (cx + rx * Math.cos(a)), (float) (cy + ry * Math.sin(a))});
        }
        return points;
    }

    private static List<float[]> spiral() {
        List<float[]> points = new ArrayList<>();
        for (int i = 0; i <= 60; i++) {
            double t = i / 60.0;
            double a = t * Math.PI * 4.5;
            double r = 4 + 38 * t;
            points.add(new float[] {(float) (50 + r * Math.cos(a)), (float) (50 + r * Math.sin(a))});
        }
        return points;
    }
}
