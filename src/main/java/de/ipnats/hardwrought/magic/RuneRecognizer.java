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
     * Set so that strokes score as they did before the alignment became elastic, which brings a
     * stroke closer to its template: a careful hand still draws about 0.9, a hurried one about 0.7.
     */
    private static final double MISS = 0.28;
    /** Below this accuracy a stroke is not read as any rune: the magic does not answer it. */
    public static final double NO_RUNE = 0.25;
    /** A stroke shorter than this, in the units it was drawn in, is a slip of the hand. */
    public static final double MIN_LENGTH = 12;

    /**
     * Each rune's ideal shape, and the other ways it may rightly be drawn: Lightning zigzags either
     * way, and Water's wave may run on half a wave longer, down again at its end.
     */
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
            if (rune == Rune.WATER) {
                // The sheet's wave in the same space, carried on to one and a half waves.
                List<float[]> longer = new ArrayList<>();
                for (int i = 0; i <= 40; i++) {
                    double t = i / 40.0;
                    longer.add(new float[] {(float) (205 + 850 * t), (float) (624 - 96 * Math.sin(2 * Math.PI * 1.5 * t + 0.4))});
                }
                shapes.add(normalize(toArray(longer)));
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
        double[][] stroke = prepare(xs, ys);
        Rune best = null, second = null;
        Match bestMatch = null, secondMatch = null;
        for (Map.Entry<Rune, List<double[][]>> entry : TEMPLATES.entrySet()) {
            Match match = match(stroke, entry.getValue());
            if (bestMatch == null || match.cost() < bestMatch.cost()) {
                second = best;
                secondMatch = bestMatch;
                best = entry.getKey();
                bestMatch = match;
            } else if (secondMatch == null || match.cost() < secondMatch.cost()) {
                second = entry.getKey();
                secondMatch = match;
            }
        }
        double accuracy = bestMatch.accuracy(), secondAccuracy = secondMatch == null ? 0 : secondMatch.accuracy();
        if (accuracy < NO_RUNE) return new Reading(null, accuracy, best, accuracy);
        return new Reading(best, accuracy, second, secondAccuracy);
    }

    /** How closely a stroke follows one particular rune, from 0 to 1. */
    public static double accuracy(float[] xs, float[] ys, Rune rune) {
        if (xs.length < 2 || length(xs, ys) < MIN_LENGTH) return 0;
        return match(prepare(xs, ys), TEMPLATES.get(rune)).accuracy();
    }

    /** The stroke as it is compared: without the hooks at its ends, resampled, centred and scaled. */
    static double[][] prepare(float[] xs, float[] ys) {
        double[][] fine = dehook(resample(xs, ys, FINE));
        float[] fx = new float[fine.length], fy = new float[fine.length];
        for (int i = 0; i < fine.length; i++) {
            fx[i] = (float) fine[i][0];
            fy[i] = (float) fine[i][1];
        }
        return normalize(resample(fx, fy));
    }

    /** Points a stroke is resampled to while its hooks are looked for. */
    private static final int FINE = 96;
    /** A hook is at most this share of the stroke's length. */
    private static final double HOOK_LENGTH = 0.1;
    /** And turns away from the rest of the stroke by at least this many degrees. */
    private static final double HOOK_TURN = 60;

    /**
     * The stroke without the little hooks a hand makes as the button goes down or comes up: a short
     * piece at either end that sets off in quite another direction than the stroke then takes. They
     * are no part of any rune, and left in they drag the whole stroke's alignment with its template.
     */
    static double[][] dehook(double[][] p) {
        int n = p.length;
        int limit = (int) (HOOK_LENGTH * (n - 1));
        int span = Math.max(3, limit);
        int start = 0, end = n - 1;
        double bestStart = HOOK_TURN, bestEnd = HOOK_TURN;
        for (int c = 2; c <= limit; c++) {
            double turnStart = turn(p[0], p[c], p[Math.min(n - 1, c + span)]);
            if (turnStart >= bestStart) {
                bestStart = turnStart;
                start = c;
            }
            double turnEnd = turn(p[n - 1], p[n - 1 - c], p[Math.max(0, n - 1 - c - span)]);
            if (turnEnd >= bestEnd) {
                bestEnd = turnEnd;
                end = n - 1 - c;
            }
        }
        if (start == 0 && end == n - 1) return p;
        return java.util.Arrays.copyOfRange(p, start, end + 1);
    }

    /** How sharply a path from a through b turns on towards c, in degrees. */
    private static double turn(double[] a, double[] b, double[] c) {
        double ux = b[0] - a[0], uy = b[1] - a[1], vx = c[0] - b[0], vy = c[1] - b[1];
        double lu = Math.hypot(ux, uy), lv = Math.hypot(vx, vy);
        if (lu < 1e-9 || lv < 1e-9) return 0;
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (ux * vx + uy * vy) / (lu * lv)))));
    }

    /**
     * How a stroke lies against a rune: the cost that decides which rune it is, and the accuracy it
     * was drawn with.
     */
    private record Match(double cost, double accuracy) { }

    /** How far apart two strokes may slide along each other while they are aligned, in points. */
    static final int BAND = 6;
    /**
     * How much a difference in the way the pen is heading counts against a difference in where it is,
     * in deciding which rune a stroke is. Position alone mistakes a crescent for an upright line, and a
     * wave for a notched level line; the turn of the pen tells them apart.
     */
    static final double DIRECTION = 0.2;

    /** The best of a rune's ideal shapes, run forwards or backwards. */
    private static Match match(double[][] stroke, List<double[][]> templates) {
        Match best = null;
        for (double[][] template : templates) {
            for (boolean backward : new boolean[] {false, true}) {
                Match match = warped(stroke, template, backward);
                if (best == null || match.cost() < best.cost()) best = match;
            }
        }
        return best;
    }

    /** Unit tangent at every point. */
    private static double[][] tangents(double[][] p) {
        int n = p.length;
        double[][] t = new double[n][];
        for (int i = 0; i < n; i++) {
            double[] a = p[Math.max(0, i - 2)], b = p[Math.min(n - 1, i + 2)];
            double dx = b[0] - a[0], dy = b[1] - a[1], l = Math.hypot(dx, dy);
            t[i] = l < 1e-9 ? new double[] {0, 0} : new double[] {dx / l, dy / l};
        }
        return t;
    }

    /**
     * A stroke against one template. Point i of the stroke is not held to point i of the template:
     * they are aligned elastically (dynamic time warping, within a band of {@value #BAND} points), so a
     * notch drawn a little off centre or a wave with one hump wider than the other still lines up with
     * its rune. The alignment weighs where the pen is and which way it heads; the accuracy is how far,
     * along that alignment, the stroke lies from the template.
     */
    private static Match warped(double[][] stroke, double[][] template, boolean backward) {
        int n = POINTS;
        double[][] ts = tangents(stroke), tt = tangents(template);
        double sign = backward ? -1 : 1;
        double[][] cost = new double[n][n], apart = new double[n][n];
        int[][] steps = new int[n][n];
        for (double[] row : cost) java.util.Arrays.fill(row, Double.MAX_VALUE);
        for (int i = 0; i < n; i++) {
            for (int j = Math.max(0, i - BAND); j <= Math.min(n - 1, i + BAND); j++) {
                int k = backward ? n - 1 - j : j;
                double distance = distance(stroke[i], template[k]);
                double d = distance + DIRECTION * Math.hypot(ts[i][0] - sign * tt[k][0], ts[i][1] - sign * tt[k][1]) / 2;
                int fromI = -1, fromJ = -1;
                if (i > 0 && cost[i - 1][j] < Double.MAX_VALUE) {
                    fromI = i - 1;
                    fromJ = j;
                }
                if (j > 0 && cost[i][j - 1] < Double.MAX_VALUE && (fromI < 0 || cost[i][j - 1] < cost[fromI][fromJ])) {
                    fromI = i;
                    fromJ = j - 1;
                }
                if (i > 0 && j > 0 && cost[i - 1][j - 1] < Double.MAX_VALUE
                        && (fromI < 0 || cost[i - 1][j - 1] <= cost[fromI][fromJ])) {
                    fromI = i - 1;
                    fromJ = j - 1;
                }
                if (fromI < 0) {
                    if (i > 0 || j > 0) continue;
                    cost[i][j] = d;
                    apart[i][j] = distance;
                    steps[i][j] = 1;
                    continue;
                }
                cost[i][j] = cost[fromI][fromJ] + d;
                apart[i][j] = apart[fromI][fromJ] + distance;
                steps[i][j] = steps[fromI][fromJ] + 1;
            }
        }
        int count = steps[n - 1][n - 1];
        return new Match(cost[n - 1][n - 1] / count, Math.max(0, 1 - apart[n - 1][n - 1] / count / MISS));
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
