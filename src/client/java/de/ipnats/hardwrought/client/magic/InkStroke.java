package de.ipnats.hardwrought.client.magic;

import de.ipnats.hardwrought.magic.CastSpellPayload;
import de.ipnats.hardwrought.magic.RuneRecognizer;
import net.minecraft.util.Util;

import java.util.Arrays;

/** One stroke as it is being drawn: its points, when it began and ended, and what it reads as. */
final class InkStroke {
    float[] xs = new float[64];
    float[] ys = new float[64];
    int count;
    final long started = Util.getMillis();
    long finished;
    RuneRecognizer.Reading reading;

    void add(float x, float y) {
        if (count > 0 && Math.abs(xs[count - 1] - x) < 0.5f && Math.abs(ys[count - 1] - y) < 0.5f) return;
        if (count == xs.length) {
            xs = Arrays.copyOf(xs, count * 2);
            ys = Arrays.copyOf(ys, count * 2);
        }
        xs[count] = x;
        ys[count] = y;
        count++;
    }

    /** Ends the stroke and reads it, just as the server will. */
    void finish() {
        finished = Util.getMillis();
        reading = RuneRecognizer.read(Arrays.copyOf(xs, count), Arrays.copyOf(ys, count));
    }

    int millis() {
        return (int) Math.min(Integer.MAX_VALUE, (finished == 0 ? Util.getMillis() : finished) - started);
    }

    CastSpellPayload.Stroke toPayload() {
        return RuneInk.thin(xs, ys, count, millis());
    }
}
