package de.ipnats.hardwrought.client.magic;

import de.ipnats.hardwrought.magic.CastSpellPayload;
import de.ipnats.hardwrought.magic.Rune;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * Ink on the GUI: strokes drawn as chains of small squares, which is all the GUI draws without a
 * pipeline of its own, and is plenty for a pen line.
 */
final class RuneInk {
    /**
     * The colours that tell one rune of a spell from the next (magic specification section 9). They
     * are only a help to the eye and say nothing about the element.
     */
    static final int[] SEPARATION = {0xFFD23C3C, 0xFF3C6CD2, 0xFF3CA850, 0xFFD2B43C, 0xFF9A50D2, 0xFF3CB4C8,
            0xFFE07830, 0xFFD250A0};

    private RuneInk() { }

    static int separation(int index) {
        return SEPARATION[index % SEPARATION.length];
    }

    /** A polyline through the points, offset by (ox, oy) and scaled, in a pen of the given width. */
    static void stroke(GuiGraphicsExtractor graphics, float[] xs, float[] ys, int count, float ox, float oy, float scale,
                       int width, int color) {
        if (count == 0) return;
        int half = width / 2;
        float px = ox + xs[0] * scale, py = oy + ys[0] * scale;
        dot(graphics, px, py, half, width, color);
        for (int i = 1; i < count; i++) {
            float qx = ox + xs[i] * scale, qy = oy + ys[i] * scale;
            float length = (float) Math.hypot(qx - px, qy - py);
            int steps = Math.max(1, (int) (length / Math.max(1, width * 0.6f)));
            for (int s = 1; s <= steps; s++) {
                float t = s / (float) steps;
                dot(graphics, px + (qx - px) * t, py + (qy - py) * t, half, width, color);
            }
            px = qx;
            py = qy;
        }
    }

    /** A rune's ideal shape, fitted into a square at (x, y) of the given size. */
    static void shape(GuiGraphicsExtractor graphics, Rune rune, int x, int y, int size, int width, int color) {
        List<float[]> points = rune.shape();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] p : points) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        float extent = Math.max(maxX - minX, maxY - minY);
        float scale = (size - width * 2) / extent;
        float ox = x + (size - (maxX - minX) * scale) / 2 - minX * scale;
        float oy = y + (size - (maxY - minY) * scale) / 2 - minY * scale;
        float[] xs = new float[points.size()], ys = new float[points.size()];
        for (int i = 0; i < points.size(); i++) {
            xs[i] = points.get(i)[0];
            ys[i] = points.get(i)[1];
        }
        stroke(graphics, xs, ys, xs.length, ox, oy, scale, width, color);
    }

    private static void dot(GuiGraphicsExtractor graphics, float x, float y, int half, int width, int color) {
        int ix = Math.round(x) - half, iy = Math.round(y) - half;
        graphics.fill(ix, iy, ix + width, iy + width, color);
    }

    /** At most {@link CastSpellPayload#MAX_POINTS} points, evenly picked, the last always kept. */
    static CastSpellPayload.Stroke thin(float[] xs, float[] ys, int count, int millis) {
        int max = CastSpellPayload.MAX_POINTS;
        if (count <= max) {
            return new CastSpellPayload.Stroke(java.util.Arrays.copyOf(xs, count), java.util.Arrays.copyOf(ys, count), millis);
        }
        float[] tx = new float[max], ty = new float[max];
        for (int i = 0; i < max; i++) {
            int from = (int) Math.round(i * (count - 1) / (double) (max - 1));
            tx[i] = xs[from];
            ty[i] = ys[from];
        }
        return new CastSpellPayload.Stroke(tx, ty, millis);
    }
}
