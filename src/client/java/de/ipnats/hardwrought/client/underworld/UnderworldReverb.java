package de.ipnats.hardwrought.client.underworld;

import com.mojang.logging.LogUtils;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.underworld.Underworld;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.slf4j.Logger;

/**
 * Real reverb in the Underworld. Minecraft plays every sound dry; underneath it is OpenAL, whose effects
 * extension has reverb. Every sound that starts down here is sent through one reverb, whose shape follows
 * the space the player stands in, and is muffled by the rock between it and the player.
 *
 * <ul>
 *   <li><b>The space</b> is measured around the player every half second: rays in thirty directions, how
 *   far the rock is. A narrow passage gives a short, close reverb with early reflections; a great hall a
 *   long, booming one; open space in many directions makes it longer still.</li>
 *   <li><b>The biome</b> colours it: the Echo Chasm long and distinct (with {@link UnderworldEcho} on
 *   top), the Flooded Halls bright and ringing over the water, the Mossy Grove and the Overgrown Halls soft
 *   and damped, the Ossuary dry and hard among the bones.</li>
 *   <li><b>Behind rock</b> a sound loses its highs and some of its loudness, so what is round a corner
 *   sounds round a corner.</li>
 * </ul>
 *
 * Where the sound driver has no effects extension there is no reverb; {@link UnderworldEcho} then
 * gives every sound a quiet delayed copy instead. Client only: nothing here changes what the world is.
 */
public final class UnderworldReverb {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int RAYS = 30;
    private static final double REACH = 48;
    private static final int MEASURE_EVERY = 10;
    /** Rock between a sound and the ear counted up to this many blocks. */
    private static final int MAX_OCCLUSION = 8;

    private static boolean supported, eax;
    private static int slot, effect, sendFilter, directFilter;
    private static final Vec3[] DIRECTIONS = sphere(RAYS);

    /** Where the ear is, for the sound thread. */
    private static volatile Vec3 listener = Vec3.ZERO;
    private static volatile boolean active;

    private static Room target = Room.NONE, current = Room.NONE;
    private static int tick;
    /** The furthest rock in the last measurement, for the echo's delay. */
    private static volatile double farthest = 16;

    private UnderworldReverb() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
    }

    public static boolean supported() {
        return supported;
    }

    public static boolean active() {
        return active;
    }

    public static double farthest() {
        return farthest;
    }

    /** The sound engine has opened its device: build the reverb there, if the device can. */
    public static void setup(long device) {
        supported = false;
        try {
            if (device == 0 || !ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX")) {
                LOGGER.info("[{}] No OpenAL effects extension: the Underworld falls back to plain echoes", Hardwrought.MOD_ID);
                return;
            }
            slot = EXTEfx.alGenAuxiliaryEffectSlots();
            effect = EXTEfx.alGenEffects();
            EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
            eax = AL10.alGetError() == AL10.AL_NO_ERROR;
            if (!eax) EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_REVERB);
            sendFilter = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(sendFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            directFilter = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(directFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                LOGGER.warn("[{}] OpenAL effects could not be set up; the Underworld falls back to plain echoes", Hardwrought.MOD_ID);
                return;
            }
            current = Room.NONE;
            upload(Room.STONE);
            supported = true;
            LOGGER.info("[{}] Underworld reverb ready ({})", Hardwrought.MOD_ID, eax ? "EAX reverb" : "standard reverb");
        } catch (Throwable error) {
            LOGGER.warn("[{}] OpenAL effects unavailable: {}", Hardwrought.MOD_ID, error.toString());
        }
    }

    /**
     * A sound is about to start on this source. Runs on the sound thread. Outside the Underworld, and for
     * sounds that sit at the ear (the interface, music), the source is left dry.
     */
    public static void onPlay(int source) {
        if (!supported) return;
        try {
            boolean relative = AL10.alGetSourcei(source, AL10.AL_SOURCE_RELATIVE) == AL10.AL_TRUE;
            if (!active || relative) {
                AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL);
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
                return;
            }
            float[] x = new float[1], y = new float[1], z = new float[1];
            AL10.alGetSource3f(source, AL10.AL_POSITION, x, y, z);
            int rock = occlusion(new Vec3(x[0], y[0], z[0]));
            float muffle = rock / (float) MAX_OCCLUSION;
            // The reverb hears round corners better than the ear does: the room carries the sound.
            EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAIN, 1.0f - 0.35f * muffle);
            EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAINHF, 1.0f - 0.6f * muffle);
            AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, slot, 0, sendFilter);
            if (rock == 0) {
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
            } else {
                EXTEfx.alFilterf(directFilter, EXTEfx.AL_LOWPASS_GAIN, 1.0f - 0.55f * muffle);
                EXTEfx.alFilterf(directFilter, EXTEfx.AL_LOWPASS_GAINHF, (float) Math.exp(-4.0 * muffle));
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, directFilter);
            }
        } catch (Throwable ignored) {
            // A sound left dry is better than a sound engine that stops.
        }
    }

    /** How many blocks of rock lie on the straight line from the ear to the sound. */
    private static int occlusion(Vec3 sound) {
        ClientLevel level = Minecraft.getInstance().level;
        Vec3 ear = listener;
        if (level == null) return 0;
        double distance = ear.distanceTo(sound);
        if (distance < 1.5 || distance > 64) return 0;
        Vec3 step = sound.subtract(ear).scale(0.5 / distance);
        Vec3 at = ear;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long last = Long.MIN_VALUE;
        int rock = 0;
        for (double walked = 0.5; walked < distance - 0.75 && rock < MAX_OCCLUSION; walked += 0.5) {
            at = at.add(step);
            pos.set(at.x, at.y, at.z);
            if (pos.asLong() == last) continue;
            last = pos.asLong();
            if (level.getBlockState(pos).isSolidRender()) rock++;
        }
        return rock;
    }

    private static void tick(Minecraft client) {
        ClientLevel level = client.level;
        boolean down = level != null && client.player != null && level.dimension() == Underworld.LEVEL;
        active = down;
        if (!down) return;
        listener = client.gameRenderer.mainCamera().position();
        if (++tick % MEASURE_EVERY == 0) target = measure(level, listener);
        if (!supported) return;
        Room next = current.toward(target, 0.2f);
        if (!next.close(current)) upload(next);
    }

    /** The space round the ear, and what the biome makes of it. */
    private static Room measure(ClientLevel level, Vec3 ear) {
        double sum = 0, nearest = REACH, far = 0;
        int open = 0;
        for (Vec3 direction : DIRECTIONS) {
            Vec3 end = ear.add(direction.scale(REACH));
            var hit = level.clip(new ClipContext(ear, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            double distance = hit.getType() == HitResult.Type.MISS ? REACH : hit.getLocation().distanceTo(ear);
            if (hit.getType() == HitResult.Type.MISS) open++;
            sum += distance;
            nearest = Math.min(nearest, distance);
            far = Math.max(far, distance);
        }
        farthest = far;
        double mean = sum / RAYS;
        float openness = open / (float) RAYS;
        float size = (float) Mth.clamp(mean / REACH, 0, 1);
        Room room = new Room(
                Mth.clamp(0.35f + 5.5f * size + 2.5f * openness, 0.3f, 9f),
                (float) Mth.clamp(1.25 - mean / 30, 0.15, 1.6),
                Mth.clamp(0.6f + 1.8f * size, 0.6f, 2.6f),
                (float) Mth.clamp(nearest * 2 / 343, 0.002, 0.3),
                (float) Mth.clamp(mean / 343, 0.008, 0.1),
                0.82f, 0.85f, 1.0f, 0.25f, 0.0f,
                Mth.clamp(0.55f + 0.35f * (1 - openness), 0.4f, 0.9f));
        ResourceKey<Biome> biome = level.getBiome(BlockPos.containing(ear)).unwrapKey().orElse(null);
        String name = biome == null ? "" : biome.identifier().getPath();
        return switch (name) {
            case "echo_chasm" -> room.with(r -> r.decay(Math.min(12f, r.decay() * 1.6f + 1.5f)).echo(0.25f, 0.65f).gainHF(0.9f).send(0.95f));
            case "flooded_halls" -> room.with(r -> r.gainHF(0.97f).hfRatio(1.5f).decay(r.decay() * 1.25f).late(r.late() * 1.3f));
            case "mossy_grove", "overgrown_halls" -> room.with(r -> r.gainHF(0.35f).hfRatio(0.45f).decay(r.decay() * 0.5f)
                    .reflections(r.reflections() * 0.4f).send(r.send() * 0.6f));
            case "ossuary", "graveyard" -> room.with(r -> r.gainHF(0.92f).hfRatio(0.9f).decay(r.decay() * 0.8f).reflections(r.reflections() * 1.4f));
            case "dripstone_depths" -> room.with(r -> r.hfRatio(1.1f).late(r.late() * 1.15f));
            default -> room;
        };
    }

    private static void upload(Room room) {
        current = room;
        if (eax) {
            EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DENSITY, room.density());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DIFFUSION, room.diffusion());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_GAIN, 0.32f);
            EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_GAINHF, room.gainHF());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DECAY_TIME, room.decay());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DECAY_HFRATIO, room.hfRatio());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, room.reflections());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_DELAY, room.reflectionsDelay());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, room.late());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, room.lateDelay());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_ECHO_TIME, Mth.clamp(room.echoTime(), 0.075f, 0.25f));
            EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_ECHO_DEPTH, room.echoDepth());
            EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_AIR_ABSORPTION_GAINHF, 0.99f);
        } else {
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DENSITY, room.density());
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DIFFUSION, room.diffusion());
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAIN, 0.32f);
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAINHF, room.gainHF());
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DECAY_TIME, Math.min(room.decay(), 20f));
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DECAY_HFRATIO, room.hfRatio());
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_REFLECTIONS_GAIN, room.reflections());
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_REFLECTIONS_DELAY, room.reflectionsDelay());
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_LATE_REVERB_GAIN, room.late());
            EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_LATE_REVERB_DELAY, room.lateDelay());
        }
        EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);
        EXTEfx.alAuxiliaryEffectSlotf(slot, EXTEfx.AL_EFFECTSLOT_GAIN, room.send());
    }

    /** Evenly spread directions on a sphere. */
    private static Vec3[] sphere(int count) {
        Vec3[] directions = new Vec3[count];
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < count; i++) {
            double y = 1 - (i + 0.5) * 2 / count;
            double radius = Math.sqrt(1 - y * y);
            directions[i] = new Vec3(Math.cos(golden * i) * radius, y, Math.sin(golden * i) * radius);
        }
        return directions;
    }

    /** The reverb's settings, in EAX reverb's terms. */
    record Room(float decay, float reflections, float late, float reflectionsDelay, float lateDelay,
                float gainHF, float hfRatio, float density, float echoTime, float echoDepth, float send) {
        static final Room NONE = new Room(1, 0.5f, 1, 0.02f, 0.03f, 0.8f, 0.8f, 1, 0.25f, 0, 0);
        static final Room STONE = new Room(2.5f, 0.6f, 1.4f, 0.02f, 0.04f, 0.82f, 0.85f, 1, 0.25f, 0, 0.7f);

        float diffusion() {
            return 1.0f;
        }

        Room toward(Room to, float t) {
            return new Room(Mth.lerp(t, decay, to.decay), Mth.lerp(t, reflections, to.reflections), Mth.lerp(t, late, to.late),
                    Mth.lerp(t, reflectionsDelay, to.reflectionsDelay), Mth.lerp(t, lateDelay, to.lateDelay),
                    Mth.lerp(t, gainHF, to.gainHF), Mth.lerp(t, hfRatio, to.hfRatio), Mth.lerp(t, density, to.density),
                    Mth.lerp(t, echoTime, to.echoTime), Mth.lerp(t, echoDepth, to.echoDepth), Mth.lerp(t, send, to.send));
        }

        boolean close(Room other) {
            return Math.abs(decay - other.decay) < 0.02f && Math.abs(send - other.send) < 0.005f
                    && Math.abs(late - other.late) < 0.01f && Math.abs(gainHF - other.gainHF) < 0.005f
                    && Math.abs(reflections - other.reflections) < 0.01f && Math.abs(echoDepth - other.echoDepth) < 0.01f;
        }

        Room with(java.util.function.UnaryOperator<Room> change) {
            return change.apply(this);
        }

        Room decay(float value) {
            return new Room(value, reflections, late, reflectionsDelay, lateDelay, gainHF, hfRatio, density, echoTime, echoDepth, send);
        }

        Room reflections(float value) {
            return new Room(decay, Mth.clamp(value, 0, 3.16f), late, reflectionsDelay, lateDelay, gainHF, hfRatio, density, echoTime, echoDepth, send);
        }

        Room late(float value) {
            return new Room(decay, reflections, Mth.clamp(value, 0, 10f), reflectionsDelay, lateDelay, gainHF, hfRatio, density, echoTime, echoDepth, send);
        }

        Room gainHF(float value) {
            return new Room(decay, reflections, late, reflectionsDelay, lateDelay, value, hfRatio, density, echoTime, echoDepth, send);
        }

        Room hfRatio(float value) {
            return new Room(decay, reflections, late, reflectionsDelay, lateDelay, gainHF, Mth.clamp(value, 0.1f, 2f), density, echoTime, echoDepth, send);
        }

        Room echo(float time, float depth) {
            return new Room(decay, reflections, late, reflectionsDelay, lateDelay, gainHF, hfRatio, density, time, depth, send);
        }

        Room send(float value) {
            return new Room(decay, reflections, late, reflectionsDelay, lateDelay, gainHF, hfRatio, density, echoTime, echoDepth, Mth.clamp(value, 0, 1));
        }
    }
}
