package de.ipnats.hardwrought.client.fx.sound;

import java.util.Random;

/**
 * A small synthesizer for the sounds of magic, rendered into a mono buffer at {@link #RATE}: filtered
 * noise that sweeps, sinks and roars, booms that drop in pitch, bells and FM chimes, crackle and
 * glints, all mixed and finally sent through a reverb so they sound like they happen in a place.
 *
 * <p>Every method adds a layer; times are in seconds from the start of the buffer, gains roughly
 * full scale at 1. {@link #render} normalises the mix, so gains only matter relative to each other.
 */
public final class FxSynth {
    public static final int RATE = 44100;
    private static final double TAU = Math.PI * 2;

    private final float[] out;
    private final Random random;

    public FxSynth(double seconds, long seed) {
        this.out = new float[(int) (seconds * RATE)];
        this.random = new Random(seed);
    }

    public Random random() {
        return random;
    }

    private int at(double seconds) {
        return Math.max(0, Math.min(out.length, (int) (seconds * RATE)));
    }

    /** Rises over the attack, then decays exponentially: to 1/e after {@code decay} seconds. */
    private static double envelope(double t, double attack, double decay) {
        if (t < 0) return 0;
        if (t < attack) return t / attack;
        return Math.exp(-(t - attack) / decay);
    }

    // --- Layers ---------------------------------------------------------------------------------

    /** A deep thump: a sine whose pitch falls from {@code from} to {@code to} Hz as it fades. */
    public FxSynth boom(double start, double from, double to, double decay, double gain) {
        double phase = 0;
        int end = at(start + decay * 6);
        for (int i = at(start); i < end; i++) {
            double t = (double) i / RATE - start;
            double freq = to + (from - to) * Math.exp(-t / (decay * 0.35));
            phase += TAU * freq / RATE;
            double click = t < 0.004 ? (1 - t / 0.004) * 0.6 : 0;
            out[i] += (float) ((Math.sin(phase) * envelope(t, 0.003, decay) + click * (random.nextDouble() * 2 - 1)) * gain);
        }
        return this;
    }

    public enum Filter { LOW, BAND, HIGH }

    /**
     * Noise through a resonant filter whose cutoff sweeps exponentially from {@code from} to {@code to}
     * Hz over the layer's length.
     */
    public FxSynth noise(double start, double length, double attack, double decay, Filter filter,
                         double from, double to, double q, double gain) {
        Svf svf = new Svf();
        int begin = at(start), end = at(start + length);
        for (int i = begin; i < end; i++) {
            double t = (double) i / RATE - start;
            if (((i - begin) & 31) == 0) svf.tune(from * Math.pow(to / from, t / length), q);
            double sample = svf.process(random.nextDouble() * 2 - 1, filter);
            out[i] += (float) (sample * envelope(t, attack, decay) * gain * fadeOut(t, length));
        }
        return this;
    }

    /**
     * Noise whose loudness wanders slowly, as a rumble of thunder rolls: low-passed, with the
     * amplitude following a smoothed random walk.
     */
    public FxSynth rumble(double start, double length, double attack, double decay, double cutoff, double gain) {
        Svf svf = new Svf();
        svf.tune(cutoff, 0.7);
        double level = 0.5, target = 0.5;
        int begin = at(start), end = at(start + length);
        for (int i = begin; i < end; i++) {
            double t = (double) i / RATE - start;
            if (((i - begin) % (RATE / 12)) == 0) target = 0.25 + random.nextDouble() * 0.9;
            level += (target - level) * 0.0008;
            double sample = svf.process(random.nextDouble() * 2 - 1, Filter.LOW);
            out[i] += (float) (sample * level * envelope(t, attack, decay) * gain * fadeOut(t, length));
        }
        return this;
    }

    public enum Wave { SINE, SAW, TRIANGLE }

    /** A tone gliding from {@code from} to {@code to} Hz, with vibrato. */
    public FxSynth tone(double start, double length, double from, double to, double attack, double release, Wave wave,
                        double vibratoRate, double vibratoDepth, double gain) {
        double phase = random.nextDouble();
        int begin = at(start), end = at(start + length);
        for (int i = begin; i < end; i++) {
            double t = (double) i / RATE - start;
            double freq = from * Math.pow(to / from, Math.min(1, t / length)) * (1 + vibratoDepth * Math.sin(TAU * vibratoRate * t));
            phase += freq / RATE;
            phase -= Math.floor(phase);
            double value = switch (wave) {
                case SINE -> Math.sin(TAU * phase);
                case SAW -> 2 * phase - 1;
                case TRIANGLE -> 4 * Math.abs(phase - 0.5) - 1;
            };
            double env = Math.min(1, t / Math.max(attack, 1e-4)) * Math.min(1, (length - t) / Math.max(release, 1e-4));
            out[i] += (float) (value * env * gain);
        }
        return this;
    }

    /** A struck bell: inharmonic partials, the higher ones dying first. */
    public FxSynth bell(double start, double freq, double decay, double gain) {
        double[] ratios = {1.0, 2.0, 2.76, 5.40, 8.93};
        double[] levels = {1.0, 0.35, 0.5, 0.25, 0.12};
        for (int p = 0; p < ratios.length; p++) {
            double f = freq * ratios[p];
            if (f > RATE * 0.45) continue;
            double partialDecay = decay / (1 + p * 0.8);
            int end = at(start + partialDecay * 7);
            for (int i = at(start); i < end; i++) {
                double t = (double) i / RATE - start;
                out[i] += (float) (Math.sin(TAU * f * t) * envelope(t, 0.002, partialDecay) * levels[p] * gain);
            }
        }
        return this;
    }

    /** Frequency modulation: a carrier with a modulator at {@code ratio} times its pitch, the index falling over time. */
    public FxSynth fm(double start, double length, double carrier, double ratio, double indexFrom, double indexTo,
                      double attack, double decay, double gain) {
        int begin = at(start), end = at(start + length);
        for (int i = begin; i < end; i++) {
            double t = (double) i / RATE - start;
            double index = indexTo + (indexFrom - indexTo) * Math.exp(-t / (decay * 0.5));
            double value = Math.sin(TAU * carrier * t + index * Math.sin(TAU * carrier * ratio * t));
            out[i] += (float) (value * envelope(t, attack, decay) * gain * fadeOut(t, length));
        }
        return this;
    }

    /** Fire's crackle: short bursts of bright noise, thinning out over the layer. */
    public FxSynth crackle(double start, double length, double perSecond, double gain) {
        int pops = (int) (perSecond * length);
        for (int p = 0; p < pops; p++) {
            // More pops early on, fewer as the fire dies down.
            double t = start + length * Math.pow(random.nextDouble(), 1.6);
            double loud = gain * (0.3 + random.nextDouble() * 0.7) * (1 - (t - start) / length * 0.7);
            int begin = at(t), end = at(t + 0.004 + random.nextDouble() * 0.01);
            double last = 0;
            for (int i = begin; i < end; i++) {
                double env = 1 - (double) (i - begin) / Math.max(1, end - begin);
                double white = random.nextDouble() * 2 - 1;
                double bright = white - last;
                last = white;
                out[i] += (float) (bright * env * loud);
            }
        }
        return this;
    }

    /** Tiny high chimes scattered over time: sparkle. */
    public FxSynth glints(double start, double length, int count, double low, double high, double gain) {
        for (int g = 0; g < count; g++) {
            double t = start + random.nextDouble() * length;
            double freq = low * Math.pow(high / low, random.nextDouble());
            double decay = 0.05 + random.nextDouble() * 0.15;
            double loud = gain * (0.4 + random.nextDouble() * 0.6);
            int end = at(t + decay * 6);
            for (int i = at(t); i < end; i++) {
                double u = (double) i / RATE - t;
                out[i] += (float) ((Math.sin(TAU * freq * u) + 0.3 * Math.sin(TAU * freq * 2.76 * u)) * envelope(u, 0.001, decay) * loud);
            }
        }
        return this;
    }

    /** Glass breaking: a spray of high, inharmonic pings. */
    public FxSynth shatter(double start, int count, double gain) {
        for (int k = 0; k < count; k++) {
            double t = start + Math.pow(random.nextDouble(), 2) * 0.35;
            bell(t, 1400 + random.nextDouble() * 4200, 0.06 + random.nextDouble() * 0.25, gain * (0.3 + random.nextDouble() * 0.7));
        }
        return this;
    }

    // --- Space and output -----------------------------------------------------------------------

    /**
     * A Schroeder reverb: four comb filters in parallel and two all-passes after them, mixed in by
     * {@code mix}. {@code size} stretches the delays, from a room (0.6) to a hall (1.5).
     */
    public FxSynth reverb(double mix, double size) {
        int[] combs = {1557, 1617, 1491, 1422};
        int[] allpasses = {225, 556};
        float[] wet = new float[out.length];
        for (int delay : combs) {
            int d = (int) (delay * size);
            float feedback = (float) Math.min(0.9, 0.8 + size * 0.05);
            float[] line = new float[d];
            int index = 0;
            float damp = 0;
            for (int i = 0; i < out.length; i++) {
                float delayed = line[index];
                damp = delayed * 0.6f + damp * 0.4f;
                line[index] = out[i] + damp * feedback;
                wet[i] += delayed * 0.25f;
                if (++index >= d) index = 0;
            }
        }
        for (int delay : allpasses) {
            int d = (int) (delay * size);
            float[] line = new float[d];
            int index = 0;
            for (int i = 0; i < wet.length; i++) {
                float delayed = line[index];
                float input = wet[i];
                line[index] = input + delayed * 0.5f;
                wet[i] = delayed - input * 0.5f;
                if (++index >= d) index = 0;
            }
        }
        for (int i = 0; i < out.length; i++) out[i] = (float) (out[i] * (1 - mix * 0.5) + wet[i] * mix);
        return this;
    }

    /** The finished sound: normalised, softly clipped, faded at the very end so it never clicks. */
    public float[] render(float peak) {
        float max = 1e-6f;
        for (float sample : out) max = Math.max(max, Math.abs(sample));
        float scale = peak / max;
        int fade = Math.min(out.length, RATE / 50);
        for (int i = 0; i < out.length; i++) {
            double value = Math.tanh(out[i] * scale * 1.2) / Math.tanh(1.2);
            if (i >= out.length - fade) value *= (double) (out.length - i) / fade;
            out[i] = (float) value;
        }
        return out;
    }

    /**
     * A buffer that loops without a seam: the last part is cross-faded into the first, and the
     * result is that much shorter.
     */
    public float[] renderLoop(float peak, double crossfade) {
        float[] sound = render(peak);
        int fade = (int) (crossfade * RATE);
        int length = sound.length - fade;
        float[] loop = new float[length];
        System.arraycopy(sound, 0, loop, 0, length);
        for (int i = 0; i < fade; i++) {
            float w = (float) i / fade;
            loop[i] = loop[i] * w + sound[length + i] * (1 - w);
        }
        return loop;
    }

    private static double fadeOut(double t, double length) {
        double left = length - t;
        return left < 0.01 ? Math.max(0, left / 0.01) : 1;
    }

    /** A state-variable filter (topology-preserving transform), stable while its cutoff moves. */
    private static final class Svf {
        private double ic1, ic2, a1, a2, a3, k;

        void tune(double cutoff, double q) {
            double g = Math.tan(Math.PI * Math.min(cutoff, RATE * 0.45) / RATE);
            k = 1 / q;
            a1 = 1 / (1 + g * (g + k));
            a2 = g * a1;
            a3 = g * a2;
        }

        double process(double x, Filter filter) {
            double v3 = x - ic2;
            double v1 = a1 * ic1 + a2 * v3;
            double v2 = ic2 + a2 * ic1 + a3 * v3;
            ic1 = 2 * v1 - ic1;
            ic2 = 2 * v2 - ic2;
            return switch (filter) {
                case LOW -> v2;
                case BAND -> v1;
                case HIGH -> x - k * v1 - v2;
            };
        }
    }
}
