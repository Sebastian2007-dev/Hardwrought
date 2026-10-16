package de.ipnats.hardwrought.client.underworld;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Sounds coming back. In the Echo Chasm every sound returns twice, from the far wall and again from
 * further off, late by the time sound takes to cross the chasm and back. Where the sound driver has no
 * reverb ({@link UnderworldReverb#supported()}), every sound in the Underworld gets one quiet, short
 * copy instead, so the place still never sounds dry.
 */
public final class UnderworldEcho {
    private static final double SPEED_OF_SOUND = 343;
    private static final int MAX_PENDING = 24;

    private record Pending(int at, Echo echo) { }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static int tick;

    private UnderworldEcho() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tick++;
            if (PENDING.isEmpty()) return;
            if (!UnderworldReverb.active()) {
                PENDING.clear();
                return;
            }
            for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
                Pending pending = it.next();
                if (pending.at() > tick) continue;
                it.remove();
                client.getSoundManager().play(pending.echo());
            }
        });
        // The sound manager exists only once the client has started.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(client ->
                client.getSoundManager().addListener(UnderworldEcho::heard));
    }

    /** Every sound that starts; queued here, played from the tick, never from inside the sound engine. */
    private static void heard(SoundInstance sound, WeighedSoundEvents event, float range) {
        if (!UnderworldReverb.active() || sound instanceof Echo || sound.isRelative() || sound.isLooping()) return;
        if (sound.getSource() == SoundSource.MUSIC || sound.getSource() == SoundSource.RECORDS || sound.getSource() == SoundSource.UI
                || sound.getSource() == SoundSource.MASTER || PENDING.size() >= MAX_PENDING) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || sound.getSound() == null) return;
        boolean chasm = client.level.getBiome(BlockPos.containing(sound.getX(), sound.getY(), sound.getZ())).unwrapKey()
                .map(key -> key.identifier().getPath().equals("echo_chasm")).orElse(false);
        if (!chasm && UnderworldReverb.supported()) return;
        double far = UnderworldReverb.farthest();
        if (chasm) {
            int delay = ticks(Mth.clamp(2 * far / SPEED_OF_SOUND, 0.3, 0.9));
            PENDING.add(new Pending(tick + delay, new Echo(sound, 0.38f)));
            PENDING.add(new Pending(tick + delay * 2 + 2, new Echo(sound, 0.16f)));
        } else {
            PENDING.add(new Pending(tick + ticks(Mth.clamp(2 * far / SPEED_OF_SOUND, 0.1, 0.35)), new Echo(sound, 0.22f)));
        }
    }

    private static int ticks(double seconds) {
        return Math.max(2, (int) Math.round(seconds * 20));
    }

    /** The same sound, the very same sample, quieter, from where it was made. */
    static final class Echo extends AbstractSoundInstance {
        private final @Nullable Sound original;
        private final @Nullable WeighedSoundEvents originalEvent;

        Echo(SoundInstance sound, float share) {
            super(sound.getIdentifier(), sound.getSource(), RandomSource.create());
            this.original = sound.getSound();
            this.originalEvent = sound.getSoundEvent();
            this.volume = sound.getVolume() * share;
            this.pitch = sound.getPitch();
            this.x = sound.getX();
            this.y = sound.getY();
            this.z = sound.getZ();
            this.attenuation = sound.getAttenuation();
        }

        @Override
        public @Nullable WeighedSoundEvents getOrResolve(SoundManager manager) {
            WeighedSoundEvents event = super.getOrResolve(manager);
            if (original != null) this.sound = original;
            return originalEvent != null ? originalEvent : event;
        }

        @Override
        public float getVolume() {
            return volume;
        }

        @Override
        public float getPitch() {
            return pitch;
        }
    }
}
