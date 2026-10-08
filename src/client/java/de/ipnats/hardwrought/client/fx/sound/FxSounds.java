package de.ipnats.hardwrought.client.fx.sound;

import de.ipnats.hardwrought.client.fx.FxClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import static de.ipnats.hardwrought.client.fx.sound.FxSynth.Filter.BAND;
import static de.ipnats.hardwrought.client.fx.sound.FxSynth.Filter.HIGH;
import static de.ipnats.hardwrought.client.fx.sound.FxSynth.Filter.LOW;
import static de.ipnats.hardwrought.client.fx.sound.FxSynth.Wave.SAW;
import static de.ipnats.hardwrought.client.fx.sound.FxSynth.Wave.SINE;

/**
 * The sounds of the FX presets. Each is a recipe for {@link FxSynth}, rendered anew with a random
 * seed every time it plays, so no two casts sound quite alike. Big blasts arrive late with distance,
 * as sound does: lightning a kilometre away is seen before it is heard.
 */
public final class FxSounds {
    /** Blocks per second. */
    private static final double SPEED_OF_SOUND = 340;

    private FxSounds() { }

    // --- Playing --------------------------------------------------------------------------------

    /** Plays a sound once, where it happens. */
    public static void play(Vec3 at, float range, float loudness, LongFunction<float[]> recipe) {
        long seed = ThreadLocalRandom.current().nextLong();
        start(new FxSoundInstance(() -> at, range, loudness, () -> recipe.apply(seed), null, 0, 1));
    }

    /** Plays a sound once, reaching the listener only after the time sound takes to travel. */
    public static void playDistant(Vec3 at, float range, float loudness, LongFunction<float[]> recipe) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        int delay = (int) (camera.distanceTo(at) / SPEED_OF_SOUND * 20);
        if (delay <= 0) {
            play(at, range, loudness, recipe);
        } else {
            FxClient.engine().later(delay, () -> play(at, range, loudness, recipe));
        }
    }

    /** A sound that follows its source, plays its intro and then loops for {@code life} ticks before fading. */
    public static FxSoundInstance loop(Supplier<Vec3> at, float range, float loudness, LongFunction<float[]> intro,
                                       LongFunction<float[]> loop, int life, int fadeTicks) {
        long seed = ThreadLocalRandom.current().nextLong();
        FxSoundInstance sound = new FxSoundInstance(at, range, loudness,
                intro == null ? null : () -> intro.apply(seed), () -> loop.apply(seed ^ 0x5DEECE66DL), life, fadeTicks);
        start(sound);
        return sound;
    }

    /** A vanilla sound layered under the synthesized one, for grit. */
    public static void vanilla(SoundEvent event, Vec3 at, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(event, SoundSource.PLAYERS, volume, pitch,
                SoundInstance.createUnseededRandom(), at.x, at.y, at.z));
    }

    private static void start(FxSoundInstance sound) {
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    // --- Recipes --------------------------------------------------------------------------------

    /** An explosion of fire: a thump that drops away, a roar that darkens, crackle, a long tail. */
    public static float[] nova(long seed) {
        return new FxSynth(4.0, seed)
                .boom(0, 75, 26, 0.9, 1.0)
                .noise(0, 3.0, 0.004, 0.75, LOW, 9000, 220, 0.7, 0.95)
                .noise(0, 0.6, 0.002, 0.18, BAND, 1600, 380, 1.2, 0.55)
                .crackle(0.05, 2.6, 55, 0.4)
                .reverb(0.38, 1.1)
                .render(0.92f);
    }

    /** A fireball leaving the hand: a rushing whoosh over a low roar of flame. */
    public static float[] fireCast(long seed) {
        return new FxSynth(1.4, seed)
                .noise(0, 1.0, 0.06, 0.35, BAND, 350, 2600, 1.8, 0.8)
                .noise(0, 1.2, 0.03, 0.5, LOW, 900, 500, 0.7, 0.6)
                .crackle(0.05, 0.9, 30, 0.25)
                .reverb(0.2, 0.8)
                .render(0.8f);
    }

    /** Arcane energy gathered and released: a rising, shimmering FM chime and a bright whoosh. */
    public static float[] arcaneCast(long seed) {
        return new FxSynth(1.6, seed)
                .fm(0, 1.0, 520, 1.5, 4.5, 0.4, 0.01, 0.45, 0.45)
                .tone(0, 0.55, 280, 1300, 0.02, 0.2, SINE, 7, 0.01, 0.35)
                .noise(0, 0.5, 0.02, 0.15, HIGH, 2200, 7000, 0.8, 0.35)
                .glints(0.05, 0.7, 9, 2000, 6000, 0.18)
                .reverb(0.42, 1.1)
                .render(0.8f);
    }

    /** Arcane energy breaking on a surface: a deep bell, a thump and a spray of glints. */
    public static float[] arcaneImpact(long seed) {
        return new FxSynth(3.2, seed)
                .bell(0, 330, 2.4, 0.6)
                .bell(0.004, 494, 1.9, 0.35)
                .boom(0, 130, 55, 0.3, 0.55)
                .noise(0, 0.25, 0.001, 0.07, BAND, 3200, 1800, 1.4, 0.5)
                .glints(0.02, 1.3, 16, 1800, 6500, 0.18)
                .reverb(0.45, 1.25)
                .render(0.9f);
    }

    /** A thunderbolt: a crack that tears the air, a second strike, then thunder rolling away. */
    public static float[] thunder(long seed) {
        return new FxSynth(5.0, seed)
                .noise(0, 0.12, 0.0008, 0.05, HIGH, 2500, 1500, 0.8, 1.0)
                .noise(0, 0.5, 0.001, 0.22, LOW, 6000, 1200, 0.9, 0.75)
                .noise(0.2, 0.3, 0.001, 0.08, HIGH, 2200, 1500, 0.8, 0.55)
                .boom(0, 90, 35, 0.6, 0.6)
                .rumble(0.08, 4.8, 0.25, 1.6, 320, 1.2)
                .reverb(0.5, 1.45)
                .render(0.95f);
    }

    /** Electricity arcing: a harsh buzz full of crackle. */
    public static float[] zap(long seed) {
        return new FxSynth(1.2, seed)
                .noise(0, 0.08, 0.0008, 0.04, HIGH, 3000, 2000, 0.8, 0.8)
                .tone(0, 0.95, 92, 88, 0.005, 0.15, SAW, 31, 0.06, 0.3)
                .tone(0, 0.95, 184, 176, 0.005, 0.15, SAW, 43, 0.05, 0.18)
                .noise(0, 0.95, 0.005, 0.8, BAND, 3200, 2400, 1.5, 0.35)
                .crackle(0, 0.95, 140, 0.5)
                .reverb(0.2, 0.7)
                .render(0.85f);
    }

    /** A ray switching on: a quick rising swell. */
    public static float[] beamStart(long seed) {
        return new FxSynth(0.6, seed)
                .tone(0, 0.5, 120, 420, 0.02, 0.1, SAW, 0, 0, 0.3)
                .noise(0, 0.5, 0.02, 0.2, BAND, 500, 3500, 1.5, 0.45)
                .render(0.8f);
    }

    /** A ray held: a thick, wavering hum of detuned saws over a hiss, looping without a seam. */
    public static float[] beamLoop(long seed) {
        return new FxSynth(3.4, seed)
                .tone(0, 3.4, 110, 110, 0.001, 0.001, SAW, 5.8, 0.012, 0.22)
                .tone(0, 3.4, 110.9, 110.9, 0.001, 0.001, SAW, 6.3, 0.012, 0.22)
                .tone(0, 3.4, 220.6, 220.6, 0.001, 0.001, SAW, 4.1, 0.01, 0.12)
                .tone(0, 3.4, 331, 331, 0.001, 0.001, SINE, 7.7, 0.02, 0.16)
                .noise(0, 3.4, 0.001, 100, BAND, 2600, 2600, 1.2, 0.2)
                .crackle(0, 3.4, 20, 0.2)
                .renderLoop(0.75f, 0.4);
    }

    /**
     * A magic circle: a choir of soft sines that swells as it is drawn, glints rising, and at the end
     * the whoosh and boom of the pillar of light.
     */
    public static float[] runeCircle(long seed) {
        FxSynth synth = new FxSynth(7.0, seed);
        double[] chord = {220, 261.6, 329.6, 440};
        for (double note : chord) {
            synth.tone(0, 3.9, note * 0.997, note * 0.997, 1.3, 1.2, SINE, 4.5, 0.004, 0.13);
            synth.tone(0, 3.9, note * 1.003, note * 1.003, 1.3, 1.2, SINE, 5.1, 0.004, 0.13);
        }
        return synth.glints(0.4, 3.2, 22, 1500, 5000, 0.08)
                .noise(3.3, 0.6, 0.35, 0.2, BAND, 300, 5000, 1.4, 0.5)
                .boom(3.62, 85, 38, 0.8, 0.8)
                .bell(3.62, 440, 2.4, 0.3)
                .bell(3.62, 660, 2.0, 0.22)
                .bell(3.62, 880, 1.6, 0.16)
                .reverb(0.5, 1.5)
                .render(0.9f);
    }

    /**
     * A rift opening: a drone that climbs as space is pulled in, a swirl of wind, a sucking rush and
     * then the burst.
     */
    public static float[] vortex(long seed) {
        return new FxSynth(7.5, seed)
                .tone(0, 4.95, 55, 110, 0.8, 0.05, SAW, 0.8, 0.02, 0.22)
                .tone(0, 4.95, 82.5, 165, 0.8, 0.05, SAW, 1.1, 0.02, 0.16)
                .tone(0.3, 4.65, 90, 420, 1.5, 0.05, SINE, 6, 0.01, 0.2)
                .noise(0, 4.95, 1.2, 100, BAND, 400, 2800, 2.5, 0.4)
                .noise(4.55, 0.4, 0.38, 0.02, HIGH, 800, 6000, 0.8, 0.7)
                .boom(4.95, 70, 24, 1.1, 1.0)
                .noise(4.95, 2.2, 0.003, 0.6, LOW, 7000, 250, 0.7, 0.8)
                .glints(5.0, 1.2, 14, 1500, 5500, 0.14)
                .reverb(0.45, 1.35)
                .render(0.92f);
    }

    /** Frost breaking out: a burst of shattering ice, a hiss, and cold wind. */
    public static float[] frost(long seed) {
        return new FxSynth(3.5, seed)
                .shatter(0, 45, 0.25)
                .noise(0, 0.9, 0.002, 0.35, HIGH, 5000, 3500, 0.8, 0.45)
                .boom(0, 95, 50, 0.3, 0.5)
                .noise(0.05, 2.8, 0.3, 1.1, BAND, 1400, 500, 1.6, 0.3)
                .reverb(0.45, 1.2)
                .render(0.88f);
    }

    /** A shield rising: a resonant sweep upward and a soft bell. */
    public static float[] shieldUp(long seed) {
        return new FxSynth(1.8, seed)
                .tone(0, 0.35, 150, 440, 0.01, 0.12, SINE, 0, 0, 0.5)
                .fm(0, 1.2, 220, 2.0, 3.0, 0.2, 0.02, 0.5, 0.3)
                .bell(0.12, 660, 1.2, 0.25)
                .reverb(0.4, 1.0)
                .render(0.8f);
    }

    /** A shield held: a quiet, beating hum. */
    public static float[] shieldLoop(long seed) {
        return new FxSynth(4.4, seed)
                .tone(0, 4.4, 200, 200, 0.001, 0.001, SINE, 0.5, 0.003, 0.3)
                .tone(0, 4.4, 300.8, 300.8, 0.001, 0.001, SINE, 0.7, 0.003, 0.2)
                .tone(0, 4.4, 401.2, 401.2, 0.001, 0.001, SINE, 0.3, 0.003, 0.12)
                .noise(0, 4.4, 0.001, 100, BAND, 1800, 1800, 3, 0.05)
                .renderLoop(0.5f, 0.4);
    }

    /** A shield breaking: glass. */
    public static float[] shieldBreak(long seed) {
        return new FxSynth(2.2, seed)
                .shatter(0, 35, 0.3)
                .noise(0, 0.5, 0.001, 0.15, HIGH, 4500, 3000, 0.8, 0.4)
                .reverb(0.4, 1.1)
                .render(0.8f);
    }

    /** Healing: a rising arpeggio of bells over a warm swell. */
    public static float[] heal(long seed) {
        FxSynth synth = new FxSynth(3.5, seed);
        double[] notes = {523.3, 659.3, 784.0, 1046.5, 1318.5};
        for (int i = 0; i < notes.length; i++) synth.bell(i * 0.12, notes[i], 1.4, 0.3);
        return synth.tone(0, 2.0, 523.3, 523.3, 0.6, 1.0, SINE, 4, 0.004, 0.15)
                .tone(0, 2.0, 784, 784, 0.6, 1.0, SINE, 4.5, 0.004, 0.1)
                .glints(0.2, 1.5, 12, 2500, 6000, 0.1)
                .reverb(0.5, 1.3)
                .render(0.75f);
    }

    /** A twinkle. */
    public static float[] sparkle(long seed) {
        return new FxSynth(1.8, seed)
                .glints(0, 0.5, 12, 2500, 7500, 0.3)
                .bell(0, 1760, 0.8, 0.25)
                .reverb(0.4, 1.0)
                .render(0.7f);
    }
}
