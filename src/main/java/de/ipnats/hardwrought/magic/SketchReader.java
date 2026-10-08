package de.ipnats.hardwrought.magic;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a whole drawing: which strokes are element runes, which are auxiliary runes, and how they
 * belong together — a rune inside a circle, a line struck through a rune, a shaft and the head that
 * makes it an arrow.
 *
 * <p>Element runes are matched against their templates ({@link RuneRecognizer}). Auxiliary runes are
 * read by their geometry instead, since an arrow may point anywhere and a circle may be any size: a
 * closed stroke with no corners is a circle, with three a triangle, with four a square; a stroke
 * that turns round more than once and a half is a spiral; a straight shaft that ends in a hooked
 * head is an arrow, whether drawn in one stroke or as a line and a head.
 *
 * <p>One stroke may also hold two things, since in action casting the pen is never lifted: a
 * straight stem that turns into a rune is that rune on an arrow, pointing the way the stem was
 * drawn; a loop that closes and runs on into a rune is that rune with its circle.
 *
 * <p>A circle means what lies in it: a rune wholly inside gives a shield its element; a rune that
 * runs out over the edge acts on the caster's own body; nothing at all is a plain shield.
 *
 * <p>A circle an arrow starts from is a gate instead: the caster steps out along the arrow. With a
 * second circle at the arrow's tip the step goes far, to a place the caster names; any rune drawn
 * across the arrow's shaft steadies the way (see {@link Gate}).
 *
 * <p>Like the recognizer it runs on both sides, and only the server's reading counts.
 */
public final class SketchReader {
    private static final int N = 64;
    /** A stroke whose ends are closer than this share of its length is closed. */
    private static final double CLOSED = 0.18;
    /** A turn sharper than this, in degrees, over a few points is a corner. */
    private static final double CORNER = 55;
    /** Straighter than this (chord over length) is a straight line. */
    private static final double STRAIGHT = 0.94;

    private SketchReader() { }

    /**
     * One meaningful thing on the parchment.
     *
     * @param stroke the stroke it was drawn with (the first, for a two-stroke arrow); decides its order
     * @param rune the element rune, for a rune — or the nearest guess, for a stroke like nothing
     * @param sign the auxiliary rune, for one
     * @param recognized whether a rune was clearly read; {@code false} for a scrawl
     * @param accuracy how well it was drawn, 0 to 1
     * @param runnerUp the rune a badly drawn rune is next most like
     * @param inverted for a rune: struck through
     * @param enclosed for a rune: drawn in or across a circle; for a circle: a rune runs over its edge,
     *                 so it acts on the caster rather than shielding them
     * @param dx for an arrow: its direction on the parchment, rightward
     * @param dy for an arrow: its direction on the parchment, downward
     * @param reach for an arrow: its length as a share of the parchment
     * @param partners the other strokes that belong to it — an arrow's head, or the halves of its head,
     *                 drawn apart
     * @param group which spell of the drawing it belongs to: every shape with runes inside is a spell of
     *              its own, and the runes outside any shape are one more
     * @param gate what it is in a gate, if it is part of one
     */
    public record Part(int stroke, Rune rune, Sign sign, boolean recognized, double accuracy, Rune runnerUp,
                       boolean inverted, boolean enclosed, float dx, float dy, float reach, List<Integer> partners, int group,
                       Gate gate) {
        public Part {
            if (gate == null) gate = Gate.NONE;
        }

        public Part(int stroke, Rune rune, Sign sign, boolean recognized, double accuracy, Rune runnerUp, boolean inverted,
                    boolean enclosed, float dx, float dy, float reach, List<Integer> partners, int group) {
            this(stroke, rune, sign, recognized, accuracy, runnerUp, inverted, enclosed, dx, dy, reach, partners, group, Gate.NONE);
        }

        public boolean isRune() {
            return rune != null && sign == null;
        }

        public Glyph glyph() {
            return sign != null ? Glyph.of(sign) : Glyph.of(rune);
        }
    }

    /**
     * What a mark is in a gate: circle, arrow, circle is a far step, a circle and an arrow a short one,
     * and a rune across the shaft holds the way steady.
     */
    public enum Gate {
        NONE,
        /** The circle the arrow starts from: where the caster stands. */
        FROM,
        /** The circle at the arrow's tip: somewhere far, named once the gate is open. */
        TO,
        /** An arrow out of a circle and into nothing: a short step the way it points. */
        SHORT,
        /** An arrow from circle to circle: a far step. */
        LONG,
        /** A rune drawn across the shaft of a gate's arrow: it steadies the way rather than acting. */
        ANCHOR
    }

    /** How far from a circle's middle, in radii, an arrow's end still starts from it or ends in it. */
    private static final double GATE = 1.35;

    /** What a stroke's shape alone says about it, before the strokes are seen together. */
    private static final class Shape {
        int index;
        /** Orders the shapes cut from one stroke after each other. */
        int order;
        final float[] xs, ys;
        final double[][] p;
        /** The points as drawn, unsmoothed: a polygon's corners are counted on these. */
        final double[][] raw;
        final double length, chord, size, cx, cy;
        final List<Integer> corners = new ArrayList<>();
        double turning;
        boolean closed, junctionCorner;
        Kind kind = Kind.RUNE;
        double accuracy;
        // For an arrow: tail and tip.
        double tailX, tailY, tipX, tipY;
        // For a head: its point, and the middle of its two ends.
        double vertexX, vertexY, armsX, armsY;
        boolean consumed, inverted, enclosed;
        final List<Integer> partners = new ArrayList<>();
        int group;
        Gate gate = Gate.NONE;
        /** For an anchor: the arrow it lies across. */
        Shape owner;

        Shape(int index, float[] xs, float[] ys) {
            this.index = index;
            this.xs = xs;
            this.ys = ys;
            // Smoothed a little, so that a hand's tremor and the pixel steps of the mouse are not read as corners.
            this.raw = RuneRecognizer.resample(xs, ys, N);
            this.p = smooth(raw);
            double travelled = 0;
            for (int i = 1; i < N; i++) travelled += Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]);
            this.length = travelled;
            this.chord = Math.hypot(p[N - 1][0] - p[0][0], p[N - 1][1] - p[0][1]);
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            double sx = 0, sy = 0;
            for (double[] q : p) {
                minX = Math.min(minX, q[0]);
                maxX = Math.max(maxX, q[0]);
                minY = Math.min(minY, q[1]);
                maxY = Math.max(maxY, q[1]);
                sx += q[0];
                sy += q[1];
            }
            this.size = Math.max(maxX - minX, maxY - minY);
            this.cx = sx / N;
            this.cy = sy / N;
        }
    }

    private enum Kind { RUNE, LINE, HEAD, ARROW, CIRCLE, TRIANGLE, SQUARE, SPIRAL, STRIKE, NOISE }

    /** Reads a drawing drawn on the parchment, where every stroke is one thing. */
    public static List<Part> read(List<float[]> xs, List<float[]> ys, float size) {
        return read(xs, ys, size, false);
    }

    /**
     * Reads a drawing.
     *
     * @param xs every stroke's x coordinates, y downward
     * @param ys every stroke's y coordinates
     * @param size the side of the parchment it was drawn on, in the same units; an arrow's length is
     *             read against it
     * @param action whether it was drawn in action casting, where the pen is never lifted and one
     *               stroke may hold two things; on the parchment every stroke is one thing
     */
    public static List<Part> read(List<float[]> xs, List<float[]> ys, float size, boolean action) {
        List<Shape> shapes = new ArrayList<>();
        for (int i = 0; i < xs.size(); i++) {
            if (xs.get(i).length < 2 || RuneRecognizer.length(xs.get(i), ys.get(i)) < RuneRecognizer.MIN_LENGTH) continue;
            List<Shape> pieces = action ? pieces(i, xs.get(i), ys.get(i)) : List.of(shape(i, xs.get(i), ys.get(i)));
            for (int k = 0; k < pieces.size(); k++) {
                Shape shape = pieces.get(k);
                shape.order = i * 4 + k;
                shapes.add(shape);
            }
        }
        gateArrows(shapes);
        joinArrows(shapes);
        // A head no shaft took is perhaps a stem bent into a rune after all.
        for (int i = 0; action && i < shapes.size(); i++) {
            Shape head = shapes.get(i);
            if (head.kind != Kind.HEAD || head.consumed) continue;
            List<Shape> split = stemSplit(head);
            if (split == null) continue;
            split.get(0).order = head.order;
            split.get(1).order = head.order + 1;
            shapes.set(i, split.get(0));
            shapes.add(i + 1, split.get(1));
        }
        strikeThrough(shapes);
        List<Shape> enclosures = new ArrayList<>();
        List<Shape> runes = new ArrayList<>();
        for (Shape shape : shapes) {
            if (shape.consumed) continue;
            switch (shape.kind) {
                case CIRCLE, TRIANGLE, SQUARE -> enclosures.add(shape);
                case LINE, HEAD, RUNE, NOISE -> {
                    shape.kind = Kind.RUNE;
                    runes.add(shape);
                }
                default -> { }
            }
        }
        List<Shape> gates = gates(shapes, runes);
        runes.removeIf(rune -> rune.gate == Gate.ANCHOR);
        group(shapes, enclosures, runes);
        regroupGates(shapes, gates);
        shapes.sort((a, b) -> Integer.compare(a.order, b.order));
        List<Part> parts = new ArrayList<>();
        for (Shape shape : shapes) {
            if (shape.consumed) continue;
            Part part = switch (shape.kind) {
                case RUNE -> {
                    RuneRecognizer.Reading reading = RuneRecognizer.read(shape.xs, shape.ys);
                    Rune rune = reading.rune() != null ? reading.rune() : reading.runnerUp();
                    if (rune == null) yield null;
                    yield new Part(shape.index, rune, null, reading.recognized(), reading.accuracy(),
                            reading.rune() != null ? reading.runnerUp() : null, shape.inverted, shape.enclosed, 0, 0, 0,
                            List.of(), shape.group, shape.gate);
                }
                case ARROW -> {
                    double dx = shape.tipX - shape.tailX, dy = shape.tipY - shape.tailY;
                    double length = Math.max(1e-6, Math.hypot(dx, dy));
                    yield new Part(shape.index, null, Sign.ARROW, true, shape.accuracy, null, false, false,
                            (float) (dx / length), (float) (dy / length), (float) Math.min(1.5, length / Math.max(1, size)),
                            List.copyOf(shape.partners), shape.group, shape.gate);
                }
                case CIRCLE -> new Part(shape.index, null, Sign.CIRCLE, true, shape.accuracy, null, false, shape.enclosed,
                        0, 0, 0, List.of(), shape.group, shape.gate);
                case TRIANGLE -> sign(shape, Sign.TRIANGLE);
                case SQUARE -> sign(shape, Sign.SQUARE);
                case SPIRAL -> sign(shape, Sign.SPIRAL);
                case STRIKE -> sign(shape, Sign.STRIKE);
                default -> null;
            };
            if (part != null) parts.add(part);
        }
        return parts;
    }

    /**
     * Sorts a drawing into its spells. Every circle, triangle or square with runes in it is a spell of
     * its own, made of those runes — a circle's shield or self, a square's trap, a triangle's burst —
     * and the runes outside any shape are one more. A shape with nothing in it is no spell by itself:
     * an empty triangle or square shapes the runes outside, as it always did; an empty circle is a
     * plain shield. Arrows and spirals belong to the spell nearest them.
     *
     * <p>Group 0 is the runes outside; the shapes' spells are numbered from 1.
     */
    private static void group(List<Shape> shapes, List<Shape> enclosures, List<Shape> runes) {
        // Each rune goes to the smallest shape it lies in, or across, for a circle.
        java.util.Map<Shape, List<Shape>> members = new java.util.HashMap<>();
        for (Shape rune : runes) {
            Shape best = null;
            boolean across = false;
            for (Shape shape : enclosures) {
                double share = inside(shape, rune);
                boolean in = share >= 0.92;
                boolean crossing = shape.kind == Kind.CIRCLE && share >= 0.15 && !in;
                if ((in || crossing) && (best == null || shape.size < best.size)) {
                    best = shape;
                    across = crossing;
                }
            }
            if (best == null) continue;
            rune.enclosed = true;
            if (across) best.enclosed = true;
            members.computeIfAbsent(best, k -> new ArrayList<>()).add(rune);
        }
        int next = 1;
        List<double[]> centres = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        for (Shape shape : enclosures) {
            List<Shape> inside = members.get(shape);
            if (inside != null) {
                shape.group = next;
                for (Shape rune : inside) rune.group = next;
                centres.add(new double[] {shape.cx, shape.cy});
                ids.add(next);
                next++;
            } else if (shape.kind == Kind.CIRCLE) {
                // An empty circle: a plain shield, a spell of its own.
                shape.group = next++;
            } else {
                shape.group = 0;
            }
        }
        double fx = 0, fy = 0;
        int free = 0;
        for (Shape rune : runes) {
            if (rune.enclosed) continue;
            rune.group = 0;
            fx += rune.cx;
            fy += rune.cy;
            free++;
        }
        if (free > 0) {
            centres.add(new double[] {fx / free, fy / free});
            ids.add(0);
        }
        for (Shape shape : shapes) {
            if (shape.consumed || (shape.kind != Kind.ARROW && shape.kind != Kind.SPIRAL && shape.kind != Kind.STRIKE)) continue;
            double mx = shape.kind == Kind.ARROW ? (shape.tailX + shape.tipX) / 2 : shape.cx;
            double my = shape.kind == Kind.ARROW ? (shape.tailY + shape.tipY) / 2 : shape.cy;
            int best = 0;
            double nearest = Double.MAX_VALUE;
            for (int i = 0; i < centres.size(); i++) {
                double d = Math.hypot(centres.get(i)[0] - mx, centres.get(i)[1] - my);
                if (d < nearest) {
                    nearest = d;
                    best = ids.get(i);
                }
            }
            shape.group = best;
        }
    }

    /**
     * A stroke that starts at a circle and runs out of it with a hook at its end is an arrow, even when
     * its shaft wavers more than a free arrow's may: next to a circle it can hardly be anything else.
     */
    private static void gateArrows(List<Shape> shapes) {
        for (Shape s : shapes) {
            if (s.kind != Kind.RUNE && s.kind != Kind.NOISE && s.kind != Kind.HEAD) continue;
            for (Shape circle : shapes) {
                if (circle == s || circle.kind != Kind.CIRCLE) continue;
                if (!atCircle(circle, s.p[0][0], s.p[0][1]) || atCircle(circle, s.p[N - 1][0], s.p[N - 1][1])) continue;
                Kind before = s.kind;
                if (singleArrow(s, 0.7) && !atCircle(circle, s.tipX, s.tipY)) break;
                s.kind = before;
            }
        }
    }

    private static boolean atCircle(Shape circle, double x, double y) {
        return Math.hypot(x - circle.cx, y - circle.cy) <= GATE * meanRadius(circle);
    }

    /**
     * Finds the gates: every arrow that starts at a circle, with the circle at its tip if it has one,
     * and the runes across its shaft. Returns the arrows.
     */
    private static List<Shape> gates(List<Shape> shapes, List<Shape> runes) {
        List<Shape> arrows = new ArrayList<>();
        for (Shape arrow : shapes) {
            if (arrow.consumed || arrow.kind != Kind.ARROW) continue;
            Shape from = null, to = null;
            double fromGap = Double.MAX_VALUE, toGap = Double.MAX_VALUE;
            for (Shape circle : shapes) {
                if (circle.consumed || circle.kind != Kind.CIRCLE || circle.gate != Gate.NONE) continue;
                boolean tail = atCircle(circle, arrow.tailX, arrow.tailY), tip = atCircle(circle, arrow.tipX, arrow.tipY);
                double r = Math.max(1e-6, meanRadius(circle));
                if (tail && !tip) {
                    double gap = Math.hypot(arrow.tailX - circle.cx, arrow.tailY - circle.cy) / r;
                    if (gap < fromGap) {
                        fromGap = gap;
                        from = circle;
                    }
                } else if (tip && !tail) {
                    double gap = Math.hypot(arrow.tipX - circle.cx, arrow.tipY - circle.cy) / r;
                    if (gap < toGap) {
                        toGap = gap;
                        to = circle;
                    }
                }
            }
            if (from == null) continue;
            from.gate = Gate.FROM;
            from.owner = arrow;
            if (to != null) {
                to.gate = Gate.TO;
                to.owner = arrow;
            }
            arrow.gate = to != null ? Gate.LONG : Gate.SHORT;
            arrows.add(arrow);
            double[] tail = {arrow.tailX, arrow.tailY}, tip = {arrow.tipX, arrow.tipY};
            for (Shape rune : runes) {
                if (rune.gate != Gate.NONE || !crossesSegment(rune, tail, tip)) continue;
                // A rune inside one of the gate's circles is that circle's, not the shaft's.
                if (inside(from, rune) >= 0.5 || (to != null && inside(to, rune) >= 0.5)) continue;
                rune.gate = Gate.ANCHOR;
                rune.owner = arrow;
            }
        }
        return arrows;
    }

    /** A gate is one spell: its circles, its arrow, what lies in its circles and across its shaft. */
    private static void regroupGates(List<Shape> shapes, List<Shape> arrows) {
        int next = 1;
        for (Shape shape : shapes) next = Math.max(next, shape.group + 1);
        for (Shape arrow : arrows) {
            int id = next++;
            java.util.Set<Integer> old = new java.util.HashSet<>();
            for (Shape shape : shapes) {
                if ((shape.gate == Gate.FROM || shape.gate == Gate.TO) && shape.owner == arrow && shape.group > 0) {
                    old.add(shape.group);
                }
            }
            for (Shape shape : shapes) {
                if (shape.consumed) continue;
                if (shape == arrow || shape.owner == arrow || (shape.group > 0 && old.contains(shape.group))) shape.group = id;
            }
        }
    }

    private static boolean crossesSegment(Shape stroke, double[] a, double[] b) {
        for (int i = 1; i < N; i++) {
            if (intersects(a, b, stroke.p[i - 1], stroke.p[i])) return true;
        }
        return false;
    }

    /** How much of a stroke lies inside a closed shape, 0 to 1. */
    private static double inside(Shape shape, Shape stroke) {
        int count = 0;
        if (shape.kind == Kind.CIRCLE) {
            double radius = meanRadius(shape) * 1.12;
            for (double[] q : stroke.p) if (Math.hypot(q[0] - shape.cx, q[1] - shape.cy) <= radius) count++;
        } else {
            for (double[] q : stroke.p) if (contains(shape.p, q)) count++;
        }
        return count / (double) N;
    }

    /** Whether a point lies inside a closed outline (even-odd rule). */
    private static boolean contains(double[][] outline, double[] q) {
        boolean in = false;
        for (int i = 0, j = outline.length - 1; i < outline.length; j = i++) {
            double[] a = outline[i], b = outline[j];
            if ((a[1] > q[1]) != (b[1] > q[1]) && q[0] < (b[0] - a[0]) * (q[1] - a[1]) / (b[1] - a[1]) + a[0]) in = !in;
        }
        return in;
    }

    /**
     * A stroke as the shapes it holds: usually one, but a loop that runs on into a rune is a circle
     * and the rune, and a straight stem that turns into a rune is an arrow and the rune.
     */
    private static List<Shape> pieces(int index, float[] xs, float[] ys) {
        Shape whole = shape(index, xs, ys);
        // A head may yet belong to a shaft drawn in another stroke; that is decided once all are read.
        if (whole.kind == Kind.SPIRAL || whole.kind == Kind.ARROW || whole.kind == Kind.HEAD) return List.of(whole);
        double[][] p = whole.p;
        // A loop at the start (or the end): the pen comes back to where it began, after going round, and goes on.
        // A clean circle on its own is no loop with something after it.
        for (boolean reversed : round(whole) ? new boolean[0] : new boolean[] {false, true}) {
            int close = loopEnd(p, whole.length, reversed);
            if (close < 0) continue;
            double fraction = (double) close / (N - 1);
            float[][] first = cut(xs, ys, 0, fraction), second = cut(xs, ys, fraction, 1);
            Shape a = shape(index, first[0], first[1]), b = shape(index, second[0], second[1]);
            Shape loop = reversed ? b : a, rest = reversed ? a : b;
            if (loop.kind == Kind.CIRCLE && rest.length >= Math.max(RuneRecognizer.MIN_LENGTH, 0.15 * whole.length)) {
                // The pen has to get from the circle to the rune: a straight lead-in is not part of the rune.
                Shape rune = reversed ? rest : trimmed(index, rest);
                return reversed ? List.of(rune, loop) : List.of(loop, rune);
            }
        }
        // A closed figure or a straight line holds no stem: what looks like a bend in it is a corner or a tremor.
        if (whole.closed || whole.kind == Kind.LINE) return List.of(whole);
        List<Shape> stem = stemSplit(whole);
        return stem != null ? stem : List.of(whole);
    }

    /** A stem at the start: straight, then a bend, then a rune; the stem is an arrow pointing the way it was drawn. */
    private static List<Shape> stemSplit(Shape whole) {
        double[][] p = whole.p;
        float[] xs = whole.xs, ys = whole.ys;
        int index = whole.index;
        int corner = firstBend(p);
        if (corner > 0) {
            double stem = 0;
            for (int i = 1; i <= corner; i++) stem += Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]);
            double straight = Math.hypot(p[corner][0] - p[0][0], p[corner][1] - p[0][1]) / Math.max(1e-6, stem);
            // The bend must hold over the strokes as a whole, not only over a few points of tremor.
            int ahead = Math.min(N - 1, corner + Math.max(4, (N - corner) / 3));
            double turn = Math.abs(Math.toDegrees(wrap(direction(p, corner, ahead) - direction(p, 0, corner))));
            if (straight >= 0.95 && turn >= 30 && stem >= 0.15 * whole.length && stem <= 0.7 * whole.length) {
                double fraction = (double) corner / (N - 1);
                float[][] head = cut(xs, ys, 0, fraction), rest = cut(xs, ys, fraction, 1);
                RuneRecognizer.Reading whole0 = RuneRecognizer.read(xs, ys);
                double alone = whole0.accuracy();
                // Lightning begins with a straight stroke and a sharp bend of its own: it is no stem.
                if (whole0.rune() == Rune.LIGHTNING && alone >= 0.55) return null;
                RuneRecognizer.Reading reading = RuneRecognizer.read(rest[0], rest[1]);
                // A long clear stem is a stem even when the whole stroke happens to look a little like a rune too.
                boolean clearStem = stem >= 0.3 * whole.length && alone < 0.85;
                if (reading.recognized() && reading.accuracy() >= 0.45
                        && (reading.accuracy() >= alone - 0.05 || clearStem)) {
                    Shape arrow = shape(index, head[0], head[1]);
                    arrow.kind = Kind.ARROW;
                    arrow.tailX = p[0][0];
                    arrow.tailY = p[0][1];
                    arrow.tipX = p[corner][0];
                    arrow.tipY = p[corner][1];
                    arrow.accuracy = clamp(0.6 + (straight - 0.95) * 8);
                    return List.of(arrow, shape(index, rest[0], rest[1]));
                }
            }
        }
        return null;
    }

    /** Where a stroke first bends by at least 35 degrees, at the sharpest point of that bend; else -1. */
    private static int firstBend(double[][] p) {
        int k = 3;
        for (int i = k; i < N - k; i++) {
            if (bend(p, i, k) < 35) continue;
            int best = i;
            for (int j = i + 1; j < Math.min(N - k, i + 2 * k); j++) if (bend(p, j, k) > bend(p, best, k)) best = j;
            return best;
        }
        return -1;
    }

    private static double bend(double[][] p, int i, int k) {
        return Math.abs(Math.toDegrees(wrap(direction(p, i, i + k) - direction(p, i - k, i))));
    }

    /** A rune without the straight lead-in it began with, if it reads better without it. */
    private static Shape trimmed(int index, Shape rune) {
        int bend = firstBend(rune.p);
        if (bend <= 0) return rune;
        double fraction = (double) bend / (N - 1);
        if (fraction > 0.45) return rune;
        float[][] rest = cut(rune.xs, rune.ys, fraction, 1);
        if (RuneRecognizer.read(rest[0], rest[1]).accuracy() <= RuneRecognizer.read(rune.xs, rune.ys).accuracy()) return rune;
        return shape(index, rest[0], rest[1]);
    }

    private static Shape shape(int index, float[] xs, float[] ys) {
        Shape shape = new Shape(index, xs, ys);
        measure(shape);
        classify(shape);
        return shape;
    }

    /** Where a loop at the start of a stroke (or at its end) closes, as a point index, or -1 if it has none. */
    private static int loopEnd(double[][] p, double length, boolean reversed) {
        double travelled = 0;
        int best = -1;
        double bestGap = Double.MAX_VALUE;
        int start = reversed ? N - 1 : 0;
        for (int k = 1; k < N; k++) {
            int i = reversed ? N - 1 - k : k, previous = reversed ? N - k : k - 1;
            travelled += Math.hypot(p[i][0] - p[previous][0], p[i][1] - p[previous][1]);
            if (travelled < 0.35 * length || travelled > 0.9 * length) continue;
            double gap = Math.hypot(p[i][0] - p[start][0], p[i][1] - p[start][1]);
            if (gap < 0.12 * travelled && gap < bestGap) {
                bestGap = gap;
                best = i;
            }
        }
        return best;
    }

    /** The part of a stroke between two shares of its length. */
    private static float[][] cut(float[] xs, float[] ys, double from, double to) {
        double total = RuneRecognizer.length(xs, ys);
        double a = from * total, b = to * total;
        List<float[]> points = new ArrayList<>();
        double travelled = 0;
        for (int i = 0; i < xs.length; i++) {
            if (i > 0) travelled += Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
            if (travelled >= a - 1e-6 && travelled <= b + 1e-6) points.add(new float[] {xs[i], ys[i]});
        }
        if (points.size() < 2) {
            points = List.of(new float[] {xs[0], ys[0]}, new float[] {xs[xs.length - 1], ys[ys.length - 1]});
        }
        float[] cx = new float[points.size()], cy = new float[points.size()];
        for (int i = 0; i < points.size(); i++) {
            cx[i] = points.get(i)[0];
            cy[i] = points.get(i)[1];
        }
        return new float[][] {cx, cy};
    }

    private static double meanRadius(Shape s) {
        double mean = 0;
        for (double[] q : s.p) mean += Math.hypot(q[0] - s.cx, q[1] - s.cy);
        return mean / N;
    }

    private static Part sign(Shape shape, Sign sign) {
        return new Part(shape.index, null, sign, true, shape.accuracy, null, false, false, 0, 0, 0, List.of(), shape.group);
    }

    /** Corners, total turning, closure. */
    private static void measure(Shape s) {
        double[][] p = s.p;
        s.closed = s.length > 0 && s.chord / s.length < CLOSED;
        double total = 0;
        double previous = Double.NaN;
        // Over every third point, so a tremor does not add up to turns the stroke never made.
        for (int i = 3; i < N; i += 3) {
            double dx = p[i][0] - p[i - 3][0], dy = p[i][1] - p[i - 3][1];
            if (dx * dx + dy * dy < 1e-9) continue;
            double angle = Math.atan2(dy, dx);
            if (!Double.isNaN(previous)) total += wrap(angle - previous);
            previous = angle;
        }
        s.turning = Math.toDegrees(total);
        int k = 3;
        double[] turn = new double[N];
        for (int i = k; i < N - k; i++) turn[i] = Math.abs(Math.toDegrees(wrap(direction(p, i, i + k) - direction(p, i - k, i))));
        for (int i = k; i < N - k; i++) {
            if (turn[i] < CORNER) continue;
            boolean peak = true;
            for (int j = Math.max(k, i - 2 * k); j <= Math.min(N - k - 1, i + 2 * k); j++) {
                if (turn[j] > turn[i] || (turn[j] == turn[i] && j < i)) {
                    peak = false;
                    break;
                }
            }
            if (peak) s.corners.add(i);
        }
        if (s.closed) {
            double end = direction(p, N - 1 - k, N - 1), start = direction(p, 0, k);
            s.junctionCorner = Math.abs(Math.toDegrees(wrap(start - end))) >= CORNER;
        }
    }

    private static void classify(Shape s) {
        int corners = s.corners.size() + (s.junctionCorner ? 1 : 0);
        double closure = s.chord / s.length;
        if (Math.abs(s.turning) >= 500 && spiralling(s)) {
            s.kind = Kind.SPIRAL;
            s.accuracy = clamp(0.6 + (Math.abs(s.turning) - 500) / 1000);
            return;
        }
        if (s.closed) {
            if (corners <= 1 || (corners >= 5 && radialSpread(s) < 0.22)) {
                s.kind = Kind.CIRCLE;
                s.accuracy = clamp(1 - radialSpread(s) * 3 - Math.max(0, closure - 0.06) * 3);
            } else if (corners <= 3) {
                s.kind = Kind.TRIANGLE;
                s.accuracy = clamp(1 - closure * 2 - (corners == 3 ? 0 : 0.2));
            } else {
                s.kind = Kind.SQUARE;
                s.accuracy = clamp(1 - closure * 2 - (corners == 4 ? 0 : 0.2));
            }
            return;
        }
        if (Math.abs(s.turning) >= 500) {
            s.kind = Kind.SPIRAL;
            s.accuracy = clamp(0.6 + (Math.abs(s.turning) - 500) / 1000);
            return;
        }
        if (singleArrow(s, 0.85)) return;
        if (closure >= STRAIGHT || deviation(s.p, 0, N - 1) < 0.05) {
            s.kind = Kind.LINE;
            s.accuracy = clamp((closure - 0.85) / 0.15);
            return;
        }
        if (head(s)) return;
        s.kind = Kind.RUNE;
    }

    /** Whether a stroke winds outward (or inward) as it turns, rather than going round the same way twice. */
    private static boolean spiralling(Shape s) {
        double first = 0, last = 0;
        int quarter = N / 4;
        for (int i = 0; i < quarter; i++) {
            first += Math.hypot(s.p[i][0] - s.cx, s.p[i][1] - s.cy);
            last += Math.hypot(s.p[N - 1 - i][0] - s.cx, s.p[N - 1 - i][1] - s.cy);
        }
        return Math.max(first, last) > 1.6 * Math.max(1e-6, Math.min(first, last));
    }

    /**
     * An arrow in one stroke: a shaft out to the tip, then a short hook back from it. How straight the
     * shaft must be is given: a hand drawing quickly wavers.
     */
    private static boolean singleArrow(Shape s, double straight) {
        double[][] p = s.p;
        double furthest = 0;
        for (int i = 0; i < N; i++) furthest = Math.max(furthest, Math.hypot(p[i][0] - p[0][0], p[i][1] - p[0][1]));
        // The first time the pen gets there: a head drawn back and forth comes back to the tip again.
        int tip = 0;
        while (tip < N - 1 && Math.hypot(p[tip][0] - p[0][0], p[tip][1] - p[0][1]) < 0.98 * furthest) tip++;
        double far = Math.hypot(p[tip][0] - p[0][0], p[tip][1] - p[0][1]);
        // A short arrow drawn with a head back and forth has more stroke in its head than in its shaft.
        if (tip < N / 4 || tip > N - 4) return false;
        double shaft = 0;
        for (int i = 1; i <= tip; i++) shaft += Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]);
        double head = s.length - shaft;
        double straightness = far / Math.max(1e-6, shaft);
        if (straightness < straight || head < 0.1 * shaft || head > 2.2 * shaft) return false;
        double ux = (p[tip][0] - p[0][0]) / far, uy = (p[tip][1] - p[0][1]) / far;
        int behind = 0, total = 0;
        for (int i = tip + 2; i < N; i++) {
            total++;
            if ((p[i][0] - p[tip][0]) * ux + (p[i][1] - p[tip][1]) * uy < 0) behind++;
        }
        if (total == 0 || behind < 0.7 * total) return false;
        s.kind = Kind.ARROW;
        s.tailX = p[0][0];
        s.tailY = p[0][1];
        s.tipX = p[tip][0];
        s.tipY = p[tip][1];
        s.accuracy = clamp(0.6 + (straightness - Math.min(0.9, straight)) * 4);
        return true;
    }

    /**
     * A head on its own: two straight arms meeting at one point. The point is where the stroke lies
     * furthest from the line between its ends, so a tremor along the arms does not count as corners.
     */
    private static boolean head(Shape s) {
        double[][] p = s.p;
        int c = -1;
        double furthest = 0;
        for (int i = 1; i < N - 1; i++) {
            double d = Math.abs(cross(p[0], p[N - 1], p[i])) / Math.max(1e-6, s.chord);
            if (d > furthest) {
                furthest = d;
                c = i;
            }
        }
        if (c < N / 5 || c > N - 1 - N / 5) return false;
        if (deviation(p, 0, c) > 0.15 || deviation(p, c, N - 1) > 0.15) return false;
        double armA = Math.hypot(p[c][0] - p[0][0], p[c][1] - p[0][1]);
        double armB = Math.hypot(p[N - 1][0] - p[c][0], p[N - 1][1] - p[c][1]);
        if (armA < 1e-6 || armB < 1e-6 || armA / armB > 2.5 || armB / armA > 2.5) return false;
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1,
                ((p[0][0] - p[c][0]) * (p[N - 1][0] - p[c][0]) + (p[0][1] - p[c][1]) * (p[N - 1][1] - p[c][1])) / (armA * armB)))));
        if (angle < 25 || angle > 130) return false;
        s.kind = Kind.HEAD;
        s.vertexX = p[c][0];
        s.vertexY = p[c][1];
        s.armsX = (p[0][0] + p[N - 1][0]) / 2;
        s.armsY = (p[0][1] + p[N - 1][1]) / 2;
        s.accuracy = 0.85;
        return true;
    }

    /** A head whose point sits at the end of a line, pointing on along it, makes the line an arrow. */
    private static void joinArrows(List<Shape> shapes) {
        joinHeads(shapes);
        joinBarbs(shapes);
    }

    /**
     * Half a head drawn as a stroke of its own — a short line from an arrow's tip slanting back, or two
     * of them at the end of a plain line — belongs to the arrow, and is no rune of its own.
     */
    private static void joinBarbs(List<Shape> shapes) {
        // A plain line with a barb at one end is an arrow pointing to that end.
        for (Shape line : shapes) {
            if (line.kind != Kind.LINE || line.consumed) continue;
            double[] a = line.p[0], b = line.p[N - 1];
            for (int end = 0; end < 2 && line.kind == Kind.LINE; end++) {
                double[] tip = end == 0 ? a : b, tail = end == 0 ? b : a;
                for (Shape barb : shapes) {
                    if (barb == line || barb.consumed || barb.kind != Kind.LINE) continue;
                    // Much shorter than the line, or it is a rune of its own that happens to touch it.
                    if (barb.chord > 0.5 * line.chord || !barbOf(barb, tip, tail, line.chord)) continue;
                    line.kind = Kind.ARROW;
                    line.tailX = tail[0];
                    line.tailY = tail[1];
                    line.tipX = tip[0];
                    line.tipY = tip[1];
                    line.accuracy = clamp((line.accuracy + 0.85) / 2);
                    break;
                }
            }
        }
        // Every barb at an arrow's tip is part of it.
        for (Shape arrow : shapes) {
            if (arrow.kind != Kind.ARROW || arrow.consumed) continue;
            double shaft = Math.hypot(arrow.tipX - arrow.tailX, arrow.tipY - arrow.tailY);
            for (Shape barb : shapes) {
                if (barb == arrow || barb.consumed || barb.kind != Kind.LINE) continue;
                if (!barbOf(barb, new double[] {arrow.tipX, arrow.tipY}, new double[] {arrow.tailX, arrow.tailY}, shaft)) continue;
                barb.consumed = true;
                arrow.partners.add(barb.index);
            }
        }
    }

    /** Whether a short line starts at a tip and slants back towards the tail, as half an arrow's head. */
    private static boolean barbOf(Shape barb, double[] tip, double[] tail, double shaft) {
        if (barb.chord > 0.7 * shaft || barb.chord < 0.08 * shaft) return false;
        double[] a = barb.p[0], b = barb.p[N - 1];
        double reach = Math.max(0.2 * shaft, barb.chord * 0.5);
        double[] near, far;
        if (Math.hypot(a[0] - tip[0], a[1] - tip[1]) <= reach) {
            near = a;
            far = b;
        } else if (Math.hypot(b[0] - tip[0], b[1] - tip[1]) <= reach) {
            near = b;
            far = a;
        } else {
            return false;
        }
        double bx = far[0] - near[0], by = far[1] - near[1], bl = Math.hypot(bx, by);
        double ux = tail[0] - tip[0], uy = tail[1] - tip[1], ul = Math.hypot(ux, uy);
        if (bl < 1e-6 || ul < 1e-6) return false;
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (bx * ux + by * uy) / (bl * ul)))));
        return angle >= 12 && angle <= 80;
    }

    private static void joinHeads(List<Shape> shapes) {
        for (Shape head : shapes) {
            if (head.kind != Kind.HEAD || head.consumed) continue;
            for (Shape line : shapes) {
                if (line.kind != Kind.LINE || line.consumed) continue;
                double[] a = line.p[0], b = line.p[N - 1];
                for (int end = 0; end < 2; end++) {
                    double[] tip = end == 0 ? a : b, tail = end == 0 ? b : a;
                    double near = Math.hypot(head.vertexX - tip[0], head.vertexY - tip[1]);
                    // The arms open back towards the tail.
                    double back = (head.armsX - head.vertexX) * (tail[0] - tip[0]) + (head.armsY - head.vertexY) * (tail[1] - tip[1]);
                    if (near < 0.3 * line.chord && back > 0) {
                        line.kind = Kind.ARROW;
                        line.tailX = tail[0];
                        line.tailY = tail[1];
                        line.tipX = tip[0];
                        line.tipY = tip[1];
                        line.accuracy = (line.accuracy + head.accuracy) / 2;
                        line.partners.add(Math.max(line.index, head.index));
                        line.index = Math.min(line.index, head.index);
                        head.consumed = true;
                        break;
                    }
                }
                if (head.consumed) break;
            }
        }
    }

    /** A straight line drawn across an earlier rune strikes it through. */
    private static void strikeThrough(List<Shape> shapes) {
        for (Shape line : shapes) {
            if (line.kind != Kind.LINE || line.consumed) continue;
            for (Shape rune : shapes) {
                if (rune == line || rune.index >= line.index || rune.consumed || rune.inverted) continue;
                if (rune.kind != Kind.RUNE && rune.kind != Kind.LINE && rune.kind != Kind.HEAD) continue;
                if (line.chord < 0.5 * rune.size || !crosses(line, rune)) continue;
                rune.inverted = true;
                line.kind = Kind.STRIKE;
                break;
            }
        }
    }

    private static boolean crosses(Shape line, Shape other) {
        double[] a = line.p[0], b = line.p[N - 1];
        for (int i = 1; i < N; i++) {
            if (intersects(a, b, other.p[i - 1], other.p[i])) return true;
        }
        return false;
    }

    private static boolean intersects(double[] a, double[] b, double[] c, double[] d) {
        double d1 = cross(c, d, a), d2 = cross(c, d, b), d3 = cross(a, b, c), d4 = cross(a, b, d);
        return d1 * d2 < 0 && d3 * d4 < 0;
    }

    private static double cross(double[] o, double[] p, double[] q) {
        return (p[0] - o[0]) * (q[1] - o[1]) - (p[1] - o[1]) * (q[0] - o[0]);
    }

    /** How unevenly far a closed stroke's points lie from its middle: 0 for a true circle. */
    private static double radialSpread(Shape s) {
        double mean = 0;
        double[] r = new double[N];
        for (int i = 0; i < N; i++) {
            r[i] = Math.hypot(s.p[i][0] - s.cx, s.p[i][1] - s.cy);
            mean += r[i];
        }
        mean /= N;
        double variance = 0;
        for (double v : r) variance += (v - mean) * (v - mean);
        return Math.sqrt(variance / N) / Math.max(1e-6, mean);
    }

    /** Each point moved to the average of itself and its two neighbours on either side; the ends stay. */
    private static double[][] smooth(double[][] p) {
        double[][] out = new double[p.length][];
        for (int i = 0; i < p.length; i++) {
            if (i < 2 || i > p.length - 3) {
                out[i] = p[i].clone();
                continue;
            }
            double x = 0, y = 0;
            for (int d = -2; d <= 2; d++) {
                x += p[i + d][0];
                y += p[i + d][1];
            }
            out[i] = new double[] {x / 5, y / 5};
        }
        return out;
    }

    /** How far a stretch of a stroke strays from the straight line between its ends, as a share of its length. */
    private static double deviation(double[][] p, int from, int to) {
        double length = Math.hypot(p[to][0] - p[from][0], p[to][1] - p[from][1]);
        if (length < 1e-6) return 1;
        double worst = 0;
        for (int i = from + 1; i < to; i++) worst = Math.max(worst, Math.abs(cross(p[from], p[to], p[i])) / length);
        return worst / length;
    }

    private static double direction(double[][] p, int from, int to) {
        return Math.atan2(p[to][1] - p[from][1], p[to][0] - p[from][0]);
    }

    private static double wrap(double angle) {
        while (angle > Math.PI) angle -= 2 * Math.PI;
        while (angle < -Math.PI) angle += 2 * Math.PI;
        return angle;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    // --- Sigils ----------------------------------------------------------------------------------

    /**
     * Reads a drawing as a sigil, or {@code null} if it is none: a sigil is two circles, one inside
     * the other. See {@link SigilDesign} for what the rest of it means.
     */
    public static SigilDesign readSigil(List<float[]> xs, List<float[]> ys, float size) {
        return readSigil(xs, ys, size, false);
    }

    /** Reads a drawing as a sigil; in action casting one stroke may hold two things, as for spells. */
    public static SigilDesign readSigil(List<float[]> xs, List<float[]> ys, float size, boolean action) {
        List<Shape> shapes = new ArrayList<>();
        for (int i = 0; i < xs.size(); i++) {
            if (xs.get(i).length < 2 || RuneRecognizer.length(xs.get(i), ys.get(i)) < RuneRecognizer.MIN_LENGTH) continue;
            if (action) shapes.addAll(pieces(i, xs.get(i), ys.get(i)));
            else shapes.add(shape(i, xs.get(i), ys.get(i)));
        }
        joinArrows(shapes);
        // The two circles: round, closed, one well inside the other, sharing a middle.
        Shape outer = null, inner = null;
        for (Shape a : shapes) {
            if (!round(a)) continue;
            for (Shape b : shapes) {
                if (b == a || !round(b)) continue;
                double ra = meanRadius(a), rb = meanRadius(b);
                if (rb > 0.75 * ra || Math.hypot(a.cx - b.cx, a.cy - b.cy) > 0.3 * ra) continue;
                if (outer == null || ra > meanRadius(outer)) {
                    outer = a;
                    inner = b;
                }
            }
        }
        if (outer == null) return null;
        double big = meanRadius(outer), small = meanRadius(inner);
        List<Double> accuracies = new ArrayList<>();
        accuracies.add(roundness(outer));
        accuracies.add(roundness(inner));
        outer.consumed = true;
        inner.consumed = true;

        // The stabilising figure: the largest closed figure with corners inside the inner circle.
        Shape figure = null;
        int corners = 0;
        for (Shape shape : shapes) {
            if (shape.consumed || !shape.closed || Math.hypot(shape.cx - inner.cx, shape.cy - inner.cy) > small) continue;
            int n = polygonCorners(shape);
            if (n < 3 || n > SigilDesign.MAX_CORNERS) continue;
            if (figure == null || shape.size > figure.size) {
                figure = shape;
                corners = n;
            }
        }
        if (figure != null) {
            figure.consumed = true;
            accuracies.add(clamp(1 - figure.chord / Math.max(1e-6, figure.length) * 2));
        }

        // The element: the best-read rune inside the inner circle; a line across it turns it around.
        Shape elementShape = null;
        RuneRecognizer.Reading element = null;
        int noise = 0;
        List<Shape> insideLines = new ArrayList<>();
        for (Shape shape : shapes) {
            if (shape.consumed || Math.hypot(shape.cx - inner.cx, shape.cy - inner.cy) > small) continue;
            if (shape.kind == Kind.LINE) insideLines.add(shape);
            RuneRecognizer.Reading reading = RuneRecognizer.read(shape.xs, shape.ys);
            if (reading.recognized() && (element == null || reading.accuracy() > element.accuracy())) {
                element = reading;
                elementShape = shape;
            }
        }
        boolean inverted = false;
        if (elementShape != null) {
            elementShape.consumed = true;
            accuracies.add(element.accuracy());
            for (Shape line : insideLines) {
                if (line == elementShape) continue;
                line.consumed = true;
                if (crosses(line, elementShape)) inverted = true;
                else noise++;
            }
        }
        for (Shape shape : shapes) {
            if (!shape.consumed && Math.hypot(shape.cx - inner.cx, shape.cy - inner.cy) <= small) {
                shape.consumed = true;
                noise++;
            }
        }

        // The ring: the instructions.
        double ring = big - small;
        double sumX = 0, sumY = 0;
        int arrows = 0, strength = 0, cost = 0;
        boolean presence = false, pulse = false;
        SigilDesign.Shape effect = SigilDesign.Shape.POINT;
        List<Glyph> glyphs = new ArrayList<>();
        if (element != null) glyphs.add(Glyph.of(element.rune()));
        List<Shape> arrowShapes = new ArrayList<>();
        for (Shape shape : shapes) {
            if (shape.consumed || shape.kind != Kind.ARROW) continue;
            shape.consumed = true;
            double dx = shape.tipX - shape.tailX, dy = shape.tipY - shape.tailY, l = Math.max(1e-6, Math.hypot(dx, dy));
            sumX += dx / l;
            sumY += dy / l;
            arrows++;
            cost++;
            accuracies.add(shape.accuracy);
            arrowShapes.add(shape);
        }
        if (arrows > 0) glyphs.add(Glyph.ARROW);
        for (Shape shape : shapes) {
            if (shape.consumed) continue;
            shape.consumed = true;
            if (shape.kind == Kind.LINE) {
                // A short stroke at the foot of an arrow is part of the arrow; anywhere else it is strength.
                if (foot(shape, arrowShapes, ring)) continue;
                strength++;
                cost++;
                accuracies.add(shape.accuracy);
                continue;
            }
            if (shape.kind == Kind.SPIRAL) {
                pulse = true;
                cost++;
                accuracies.add(shape.accuracy);
                glyphs.add(Glyph.SPIRAL);
                continue;
            }
            if (shape.closed) {
                int n = polygonCorners(shape);
                if (n < 3 && round(shape)) {
                    effect = SigilDesign.Shape.SPHERE;
                    glyphs.add(Glyph.CIRCLE);
                } else if (n == 3) {
                    effect = SigilDesign.Shape.BURST;
                    glyphs.add(Glyph.TRIANGLE);
                } else if (n == 4) {
                    presence = true;
                    glyphs.add(Glyph.SQUARE);
                } else {
                    noise++;
                    continue;
                }
                cost++;
                accuracies.add(clamp(1 - shape.chord / Math.max(1e-6, shape.length) * 2));
                continue;
            }
            noise++;
        }

        // The drawing as it will lie on the ground: centred on the outer circle, its radius one.
        List<float[]> lines = new ArrayList<>();
        for (int i = 0; i < xs.size(); i++) {
            float[] x = xs.get(i), y = ys.get(i);
            if (x.length < 2) continue;
            int count = Math.min(32, x.length);
            float[] line = new float[count * 2];
            for (int k = 0; k < count; k++) {
                int from = (int) Math.round(k * (x.length - 1) / (double) Math.max(1, count - 1));
                line[k * 2] = (float) ((x[from] - outer.cx) / big);
                line[k * 2 + 1] = (float) ((y[from] - outer.cy) / big);
            }
            lines.add(line);
        }
        return new SigilDesign(element == null ? null : element.rune(), inverted, corners,
                arrows == 0 ? 0 : (float) (sumX / arrows), arrows == 0 ? 0 : (float) (sumY / arrows), arrows, effect,
                presence, pulse, strength, cost, glyphs, accuracies, noise, lines);
    }

    /** A closed, round stroke without corners: a circle of any size. */
    private static boolean round(Shape s) {
        return s.closed && radialSpread(s) < 0.25 && polygonCorners(s) < 3;
    }

    private static double roundness(Shape s) {
        return clamp(1 - radialSpread(s) * 3 - Math.max(0, s.chord / Math.max(1e-6, s.length) - 0.06) * 3);
    }

    /**
     * How many corners a closed figure has, an octagon's included. The figure is simplified to the
     * fewest straight sides that keep within a few percent of it (Douglas and Peucker); a circle
     * simplifies too, but its sides stay bowed, so it is told apart by how far it strays from them.
     */
    static int polygonCorners(Shape s) {
        if (!s.closed) return 0;
        double[][] p = s.raw;
        double eps = 0.06 * Math.max(1e-6, s.size);
        // Split the ring at the two points furthest apart, and simplify each half.
        int a = 0, b = 0;
        double far = -1;
        for (int i = 0; i < N - 1; i++) {
            for (int j = i + 1; j < N - 1; j++) {
                double d = Math.hypot(p[i][0] - p[j][0], p[i][1] - p[j][1]);
                if (d > far) {
                    far = d;
                    a = i;
                    b = j;
                }
            }
        }
        List<double[]> ring = new ArrayList<>();
        for (int i = 0; i < N - 1; i++) ring.add(p[(a + i) % (N - 1)]);
        int split = Math.floorMod(b - a, N - 1);
        List<double[]> first = ring.subList(0, split + 1);
        List<double[]> second = new ArrayList<>(ring.subList(split, ring.size()));
        second.add(ring.getFirst());
        List<double[]> corners = new ArrayList<>();
        simplify(first, eps, corners);
        corners.removeLast();
        simplify(second, eps, corners);
        corners.removeLast();
        // Drop corners that are hardly corners: sides that run on nearly straight.
        boolean changed = true;
        while (changed && corners.size() > 3) {
            changed = false;
            for (int i = 0; i < corners.size(); i++) {
                double[] prev = corners.get(Math.floorMod(i - 1, corners.size())), here = corners.get(i),
                        next = corners.get((i + 1) % corners.size());
                double turn = Math.abs(Math.toDegrees(wrap(Math.atan2(next[1] - here[1], next[0] - here[0])
                        - Math.atan2(here[1] - prev[1], here[0] - prev[0]))));
                if (turn < 20) {
                    corners.remove(i);
                    changed = true;
                    break;
                }
            }
        }
        int n = corners.size();
        if (n >= 5) {
            // A true polygon lies on its sides; a circle bulges out between them.
            double[] off = new double[ring.size()];
            for (int k = 0; k < ring.size(); k++) {
                double best = Double.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    best = Math.min(best, segmentDistance(ring.get(k), corners.get(i), corners.get((i + 1) % n)));
                }
                off[k] = best;
            }
            java.util.Arrays.sort(off);
            if (off[(int) (off.length * 0.9)] > 0.025 * s.size) return 0;
        }
        return n;
    }

    /** Douglas-Peucker: the points of a polyline that keep it within eps; appends all but nothing twice. */
    private static void simplify(List<double[]> line, double eps, List<double[]> out) {
        if (line.size() < 2) return;
        double[] a = line.getFirst(), b = line.getLast();
        int worst = -1;
        double distance = 0;
        for (int i = 1; i < line.size() - 1; i++) {
            double d = segmentDistance(line.get(i), a, b);
            if (d > distance) {
                distance = d;
                worst = i;
            }
        }
        if (worst < 0 || distance <= eps) {
            out.add(a);
            out.add(b);
            return;
        }
        simplify(line.subList(0, worst + 1), eps, out);
        out.removeLast();
        simplify(line.subList(worst, line.size()), eps, out);
    }

    private static double segmentDistance(double[] q, double[] a, double[] b) {
        double dx = b[0] - a[0], dy = b[1] - a[1];
        double l2 = dx * dx + dy * dy;
        double t = l2 < 1e-12 ? 0 : Math.max(0, Math.min(1, ((q[0] - a[0]) * dx + (q[1] - a[1]) * dy) / l2));
        return Math.hypot(q[0] - a[0] - t * dx, q[1] - a[1] - t * dy);
    }

    /** Whether a short stroke sits across the tail of one of the arrows, as its foot. */
    private static boolean foot(Shape line, List<Shape> arrows, double ring) {
        if (line.chord > 0.6 * ring) return false;
        double mx = (line.p[0][0] + line.p[N - 1][0]) / 2, my = (line.p[0][1] + line.p[N - 1][1]) / 2;
        for (Shape arrow : arrows) {
            if (Math.hypot(mx - arrow.tailX, my - arrow.tailY) < 0.35 * ring) return true;
        }
        return false;
    }
}
