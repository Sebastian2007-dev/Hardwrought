package de.ipnats.hardwrought.client.fx.sound;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * A synthesized sound played through Minecraft's own sound engine, so it obeys the volume sliders,
 * sits in the world and fades with distance. Its samples come from {@link FxSynth} instead of a
 * file: the {@code hardwrought:fx.synth} sound event is only a placeholder marked as streaming, and
 * {@link #getAudioStream} hands the engine the rendered buffer.
 *
 * <p>A sound can follow a moving source, and it can loop: an intro plays once, then the loop until
 * the sound has lived its time and faded out.
 */
public final class FxSoundInstance extends AbstractTickableSoundInstance {
    public static final Identifier EVENT = Hardwrought.id("fx.synth");

    private final Supplier<float[]> intro;
    private final Supplier<float[]> loop;
    private final Supplier<Vec3> position;
    private final float loudness;
    private final int life;
    private final int fadeTicks;
    private int age;

    FxSoundInstance(Supplier<Vec3> position, float range, float loudness, Supplier<float[]> intro, Supplier<float[]> loop,
                    int life, int fadeTicks) {
        super(SoundEvent.createVariableRangeEvent(EVENT), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.position = position;
        this.intro = intro;
        this.loop = loop;
        this.loudness = loudness;
        this.life = life;
        this.fadeTicks = Math.max(1, fadeTicks);
        this.attenuation = Attenuation.LINEAR;
        // The volume at the start sets how far the sound carries (16 blocks per unit); after that it
        // only sets the loudness, so it drops to the loudness on the first tick.
        this.volume = Math.max(1, range / 16f);
        Vec3 at = position.get();
        this.x = at.x;
        this.y = at.y;
        this.z = at.z;
    }

    @Override
    public void tick() {
        age++;
        Vec3 at = position.get();
        x = at.x;
        y = at.y;
        z = at.z;
        float fade = 1;
        if (loop != null && life > 0) {
            fade = Mth.clamp((life + fadeTicks - age) / (float) fadeTicks, 0, 1);
            if (age >= life + fadeTicks) stop();
        }
        volume = loudness * fade;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary library, Identifier id, boolean repeatInstantly) {
        return CompletableFuture.supplyAsync(() -> new PcmStream(intro == null ? null : intro.get(),
                loop == null ? null : loop.get()), Util.backgroundExecutor());
    }

    /** Plays an intro once, then a loop forever (or nothing, and ends). */
    private static final class PcmStream implements AudioStream {
        private static final AudioFormat FORMAT = new AudioFormat(FxSynth.RATE, 16, 1, true, false);
        private final float[] intro;
        private final float[] loop;
        private int position;
        private boolean looping;

        PcmStream(float[] intro, float[] loop) {
            this.intro = intro == null ? new float[0] : intro;
            this.loop = loop != null && loop.length > 0 ? loop : null;
        }

        @Override
        public AudioFormat getFormat() {
            return FORMAT;
        }

        @Override
        public ByteBuffer read(int expectedSize) {
            int samples = Math.max(1, expectedSize / 2);
            ByteBuffer buffer = BufferUtils.createByteBuffer(samples * 2);
            int written = 0;
            while (written < samples) {
                float[] source = looping ? loop : intro;
                if (position >= source.length) {
                    if (loop == null) break;
                    looping = true;
                    position = 0;
                    continue;
                }
                float sample = source[position++];
                buffer.putShort((short) Mth.clamp(Math.round(sample * 32767), -32768, 32767));
                written++;
            }
            if (written == 0) return null;
            buffer.flip();
            return buffer;
        }

        @Override
        public void close() { }
    }
}
