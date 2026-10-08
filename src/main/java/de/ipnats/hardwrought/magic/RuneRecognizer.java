package de.ipnats.hardwrought.magic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Which rune a drawn stroke is, and how well it was drawn.
 *
 * <p>A template matcher in the manner of the "$1 recognizer": the stroke is resampled to evenly
 * spaced points, moved so its centre sits at the origin and scaled so its larger side is one long,
 * then compared point by point with each rune's ideal shape. Unlike $1 it is <em>not</em> turned to
 * a standard angle first, since a rune's orientation is part of it. The stroke is compared forwards
 * and backwards, since which end a rune is begun from does not matter.
 *
 * <p>The same code runs on the client, to colour the stroke as it is drawn, and on the server, which
 * alone decides what was cast: a client cannot claim a better rune than it drew.
 */
public final class RuneRecognizer {
    /** Points a stroke is resampled to before it is compared. */
    static final int POINTS = 48;
    /**
     * The mean distance between stroke and template, in units of the stroke's size, at which a
     * drawing no longer counts as that rune at all. Accuracy falls linearly from 1 to 0 up to it.
     */
    private static final double MISS = 0.34;
    /** Below this accuracy a stroke is not read as any rune: the magic does not answer it. */
    public static final double NO_RUNE = 0.25;
    /** A stroke shorter than this, in the units it was drawn in, is a slip of the hand. */
    public static final double MIN_LENGTH = 12;

    /** Each rune's ideal shape, and the other ways it may rightly be drawn: Lightning zigzags either way. */
    private static final Map<Rune, List<double[][]>> TEMPLATES = new EnumMap<>(Rune.class);

    static {
        for (Rune rune : Rune.values()) {
            List<double[][]> shapes = new ArrayList<>();
            shapes.add(normalize(toArray(rune.shape())));
            if (rune == Rune.LIGHTNING) {
                List<float[]> mirrored = new ArrayList<>();
                for (float[] point : rune.shape()) mirrored.add(new float[] {1200 - point[0], point[1]});
                shapes.add(normalize(toArray(mirrored)));
            }
            TEMPLATES.put(rune, shapes);
        }
    }

    private RuneRecognizer() { }

    /**
     * How a stroke reads.
     *
     * @param rune the rune it is most like, or {@code null} when it is like none of them
     * @param accuracy how close it is to that rune, from 0 to 1
     * @param runnerUp the rune it is next most like, which a badly drawn rune is mistaken for
     */
    public record Reading(Rune rune, double accuracy, Rune runnerUp, double runnerUpAccuracy) {
        public boolean recognized() {
            return rune != null;
        }
    }

    /** Reads a stroke given as alternating x and y coordinates, y downward. */
    public static Reading read(float[] xs, float[] ys) {
        if (xs.length < 2 || xs.length != ys.length || length(xs, ys) < MIN_LENGTH) {
            return new Reading(null, 0, null, 0);
        }
        double[][] stroke = normalize(resample(xs, ys));
        Rune best = null, second = null;
        double bestScore = 0, secondScore = 0;
        for (Map.Entry<Rune, List<double[][]>> entry : TEMPLATES.entrySet()) {
            double score = 0;
            for (double[][] template : entry.getValue()) score = Math.max(score, accuracy(stroke, template));
            if (score > bestScore) {
                second = best;
                secondScore = bestScore;
                best = entry.getKey();
                bestScore = score;
            } else if (score > secondScore) {
                second = entry.getKey();
                secondScore = score;
            }
        }
        if (bestScore < NO_RUNE) return new Reading(null, bestScore, best, bestScore);
        return new Reading(best, bestScore, second, secondScore);
    }

    /** How closely a stroke follows one particular rune, from 0 to 1. */
    public static double accuracy(float[] xs, float[] ys, Rune rune) {
        if (xs.length < 2 || length(xs, ys) < MIN_LENGTH) return 0;
        double[][] stroke = normalize(resample(xs, ys));
        double best = 0;
        for (double[][] template : TEMPLATES.get(rune)) best = Math.max(best, accuracy(stroke, template));
        return best;
    }

    private static double accuracy(double[][] stroke, double[][] template) {
        double forward = 0, backward = 0;
        for (int i = 0; i < POINTS; i++) {
            forward += distance(stroke[i], template[i]);
            backward += distance(stroke[i], template[POINTS - 1 - i]);
        }
        double mean = Math.min(forward, backward) / POINTS;
        return Math.max(0, 1 - mean / MISS);
    }

    private static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1];
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double[][] toArray(List<float[]> shape) {
        float[] xs = new float[shape.size()], ys = new float[shape.size()];
        for (int i = 0; i < shape.size(); i++) {
            xs[i] = shape.get(i)[0];
            ys[i] = shape.get(i)[1];
        }
        return resample(xs, ys);
    }

    static double length(float[] xs, float[] ys) {
        double total = 0;
        for (int i = 1; i < xs.length; i++) total += Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
        return total;
    }

    /** The stroke as POINTS points spaced evenly along its length. */
    static double[][] resample(float[] xs, float[] ys) {
        return resample(xs, ys, POINTS);
    }

    /** The stroke as n points spaced evenly along its length. */
    static double[][] resample(float[] xs, float[] ys, int n) {
        final int POINTS = n;
        double total = length(xs, ys);
        double interval = total / (POINTS - 1);
        List<double[]> points = new ArrayList<>(POINTS);
        points.add(new double[] {xs[0], ys[0]});
        double carried = 0;
        double px = xs[0], py = ys[0];
        for (int i = 1; i < xs.length && interval > 0; i++) {
            double qx = xs[i], qy = ys[i];
            double segment = Math.hypot(qx - px, qy - py);
            while (carried + segment >= interval && points.size() < POINTS) {
                double t = (interval - carried) / segment;
                px += (qx - px) * t;
                py += (qy - py) * t;
                points.add(new double[] {px, py});
                segment = Math.hypot(qx - px, qy - py);
                carried = 0;
            }
            carried += segment;
            px = qx;
            py = qy;
        }
        while (points.size() < POINTS) points.add(new double[] {xs[xs.length - 1], ys[ys.length - 1]});
        return points.toArray(new double[0][]);
    }

    /** Centred on its middle and scaled so that its larger side is one long, keeping its proportions. */
    static double[][] normalize(double[][] points) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double cx = 0, cy = 0;
        for (double[] p : points) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
            cx += p[0];
            cy += p[1];
        }
        cx /= points.length;
        cy /= points.length;
        double size = Math.max(Math.max(maxX - minX, maxY - minY), 1e-6);
        double[][] result = new double[points.length][];
        for (int i = 0; i < points.length; i++) {
            result[i] = new double[] {(points[i][0] - cx) / size, (points[i][1] - cy) / size};
        }
        return result;
    }
}
