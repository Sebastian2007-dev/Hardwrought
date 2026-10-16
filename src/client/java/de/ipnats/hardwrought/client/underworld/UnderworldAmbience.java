package de.ipnats.hardwrought.client.underworld;

import de.ipnats.hardwrought.client.environment.DynamicLight;
import de.ipnats.hardwrought.underworld.Underworld;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * What the Underworld sounds like, besides silence. Each player hears their own; none of it is real, and
 * all of it goes through the reverb and the rock like any other sound.
 *
 * <ul>
 *   <li><b>Dripping water</b> from the roof somewhere close — often in the Dripstone Depths, into the
 *   water in the Flooded Halls.</li>
 *   <li><b>Rock settling</b> a long way off: a trickle of gravel, a crack.</li>
 *   <li><b>A low wind</b> through the passages, rising and falling, louder where the rock closes in.</li>
 *   <li><b>The earth groaning</b>, deep and far below, very rarely.</li>
 *   <li><b>Something falling</b> in a distant hall: a stalactite letting go, the crash.</li>
 *   <li><b>Whispers</b> at the edge of hearing in the Graveyard and the Ossuary (and near the ascent
 *   ruins, from the shaft itself).</li>
 *   <li><b>Footsteps that are not yours</b>, now and then, some way behind: walking when you walk,
 *   stopping a beat after you stop, gone the moment you turn round.</li>
 *   <li><b>Breathing</b> close behind, when standing still in the dark for a long time.</li>
 * </ul>
 *
 * Nothing in the Mossy Grove but the drips: it is the one place down here to rest.
 */
public final class UnderworldAmbience {
    private record Pending(int at, Runnable sound) { }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static int tick;
    private static Wind wind;

    // Footsteps that follow.
    private static int followingUntil;
    private static double walked;
    private static Vec3 lastPosition;
    // Breathing.
    private static int stillFor;

    private UnderworldAmbience() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(UnderworldAmbience::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void reset() {
        PENDING.clear();
        wind = null;
        followingUntil = 0;
        stillFor = 0;
        lastPosition = null;
    }

    private static void tick(Minecraft client) {
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null || level.dimension() != Underworld.LEVEL || client.isPaused()) {
            if (level == null || level.dimension() != Underworld.LEVEL) reset();
            return;
        }
        tick++;
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending pending = it.next();
            if (pending.at() > tick) continue;
            it.remove();
            pending.sound().run();
        }
        String biome = level.getBiome(player.blockPosition()).unwrapKey().map(key -> key.identifier().getPath()).orElse("");
        boolean grove = biome.equals("mossy_grove");
        Vec3 eyes = player.getEyePosition();

        if (wind == null || wind.isStopped()) {
            wind = new Wind();
            client.getSoundManager().play(wind);
        }

        int dripEvery = switch (biome) {
            case "dripstone_depths" -> 14;
            case "flooded_halls" -> 22;
            case "echo_chasm" -> 70;
            default -> 45;
        };
        if (RANDOM.nextInt(dripEvery) == 0) drip(level, eyes, biome.equals("flooded_halls"));
        if (grove) {
            stillFor = 0;
            followingUntil = 0;
            lastPosition = player.position();
            return;
        }
        if (RANDOM.nextInt(900) == 0) settle(level, eyes);
        if (RANDOM.nextInt(5000) == 0) groan(level, eyes);
        if (RANDOM.nextInt(2400) == 0) distantFall(level, eyes);
        if ((biome.equals("graveyard") || biome.equals("ossuary")) && RANDOM.nextInt(600) == 0) whisper(level, eyes, 3, 8);
        follow(level, player);
        breathe(level, player);
        lastPosition = player.position();
    }

    // ------------------------------------------------------------------------------------ sounds

    private static void play(ClientLevel level, Vec3 at, SoundEvent sound, SoundSource source, float volume, float pitch) {
        level.playLocalSound(at.x, at.y, at.z, sound, source, volume, pitch, false);
    }

    /** A drop from the roof somewhere within a few blocks. */
    private static void drip(ClientLevel level, Vec3 eyes, boolean intoWater) {
        Vec3 at = roofAbove(level, eyes.add(RANDOM.nextInt(17) - 8, 0, RANDOM.nextInt(17) - 8));
        if (at == null) return;
        Vec3 floor = floorBelow(level, at);
        boolean water = intoWater && floor != null && level.getFluidState(BlockPos.containing(floor)).isSource();
        play(level, floor != null ? floor : at, water ? SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON : SoundEvents.POINTED_DRIPSTONE_DRIP_WATER,
                SoundSource.AMBIENT, 0.25f + RANDOM.nextFloat() * 0.4f, 0.8f + RANDOM.nextFloat() * 0.35f);
    }

    /** Rock settling far off: a trickle of gravel, or a crack. */
    private static void settle(ClientLevel level, Vec3 eyes) {
        Vec3 at = around(eyes, 12, 32);
        if (RANDOM.nextBoolean()) {
            for (int i = 0; i < 3 + RANDOM.nextInt(4); i++) {
                later(i * (2 + RANDOM.nextInt(3)), () -> play(level, at, SoundEvents.GRAVEL_FALL, SoundSource.AMBIENT, 0.5f, 0.55f + RANDOM.nextFloat() * 0.2f));
            }
        } else {
            play(level, at, SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.AMBIENT, 0.9f, 0.45f + RANDOM.nextFloat() * 0.15f);
        }
    }

    /** The earth groaning, deep below. */
    private static void groan(ClientLevel level, Vec3 eyes) {
        Vec3 at = eyes.add(RANDOM.nextInt(41) - 20, -18, RANDOM.nextInt(41) - 20);
        play(level, at, SoundEvents.AMBIENT_BASALT_DELTAS_MOOD.value(), SoundSource.AMBIENT, 1.6f, 0.5f);
        later(30, () -> play(level, at, SoundEvents.DEEPSLATE_BREAK, SoundSource.AMBIENT, 1.2f, 0.3f));
    }

    /** A stalactite letting go in a hall the player has not been to, and the crash. */
    private static void distantFall(ClientLevel level, Vec3 eyes) {
        Vec3 at = around(eyes, 24, 44);
        play(level, at.add(0, 6, 0), SoundEvents.POINTED_DRIPSTONE_BREAK, SoundSource.AMBIENT, 1.2f, 0.6f);
        later(14 + RANDOM.nextInt(6), () -> {
            play(level, at, SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.AMBIENT, 2.0f, 0.8f + RANDOM.nextFloat() * 0.2f);
            play(level, at, SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.AMBIENT, 1.5f, 0.6f);
        });
    }

    /** Whispers, close and to one side. Also called by the dim ascent shafts. */
    public static void whisper(ClientLevel level, Vec3 near, int min, int max) {
        Vec3 at = around(near, min, max).add(0, RANDOM.nextInt(3) - 1, 0);
        play(level, at, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS.value(), SoundSource.AMBIENT, 0.3f, 0.65f + RANDOM.nextFloat() * 0.2f);
    }

    // ------------------------------------------------------------------------------------ unease

    /**
     * Now and then, for a while, steps some way behind: one for each of the player's own, a beat late,
     * on whatever ground is back there. Turning round to look ends it at once.
     */
    private static void follow(ClientLevel level, LocalPlayer player) {
        if (followingUntil <= tick) {
            if (player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() > 0.002 && RANDOM.nextInt(3600) == 0) {
                followingUntil = tick + 300 + RANDOM.nextInt(300);
                walked = 0;
            }
            return;
        }
        Vec3 back = player.position().subtract(player.getLookAngle().multiply(1, 0, 1).normalize().scale(5 + RANDOM.nextDouble()));
        Vec3 toward = back.subtract(player.position()).normalize();
        if (player.getLookAngle().multiply(1, 0, 1).normalize().dot(toward) > 0.3) {
            followingUntil = 0;
            PENDING.removeIf(pending -> pending.sound() instanceof Step);
            return;
        }
        if (lastPosition == null || !player.onGround()) return;
        walked += player.position().subtract(lastPosition).horizontalDistance();
        if (walked < 1.7) return;
        walked = 0;
        BlockPos ground = BlockPos.containing(back.x, player.getY() + 1, back.z);
        for (int down = 0; down < 5 && level.getBlockState(ground.below()).isAir(); down++) ground = ground.below();
        BlockState floor = level.getBlockState(ground.below());
        if (floor.isAir()) return;
        SoundType type = floor.getSoundType();
        Vec3 at = Vec3.atBottomCenterOf(ground);
        later(6 + RANDOM.nextInt(3), new Step(() -> play(level, at, type.getStepSound(), SoundSource.HOSTILE, type.getVolume() * 0.3f, type.getPitch() * 0.85f)));
    }

    private record Step(Runnable sound) implements Runnable {
        @Override
        public void run() {
            sound.run();
        }
    }

    /** Standing still in the dark for twenty seconds and more: something breathing, close behind. */
    private static void breathe(ClientLevel level, LocalPlayer player) {
        boolean still = lastPosition != null && player.position().distanceToSqr(lastPosition) < 0.0004;
        int light = Math.max(level.getBrightness(LightLayer.BLOCK, BlockPos.containing(player.getEyePosition())), DynamicLight.ownLevel());
        stillFor = still && light <= 3 ? stillFor + 1 : 0;
        if (stillFor < 400 || stillFor % 85 != 0) return;
        Vec3 behind = player.getEyePosition().subtract(player.getLookAngle().multiply(1, 0, 1).normalize().scale(1.6));
        play(level, behind, SoundEvents.PLAYER_BREATH, SoundSource.HOSTILE, 0.22f, 0.7f + RANDOM.nextFloat() * 0.08f);
    }

    // ------------------------------------------------------------------------------------ helpers

    private static void later(int ticks, Runnable sound) {
        PENDING.add(new Pending(tick + ticks, sound));
    }

    /** A point some way off in a random direction, at about the same height. */
    private static Vec3 around(Vec3 center, double min, double max) {
        double angle = RANDOM.nextDouble() * Math.PI * 2, distance = Mth.lerp(RANDOM.nextDouble(), min, max);
        return center.add(Math.cos(angle) * distance, RANDOM.nextInt(9) - 4, Math.sin(angle) * distance);
    }

    /** Just under the roof over a point, if there is a roof within reach and air below it. */
    private static Vec3 roofAbove(ClientLevel level, Vec3 from) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(from).mutable();
        if (!level.getBlockState(pos).isAir()) return null;
        for (int up = 0; up < 24; up++) {
            pos.move(Direction.UP);
            if (!level.getBlockState(pos).isAir()) return Vec3.atBottomCenterOf(pos).subtract(0, 0.1, 0);
        }
        return null;
    }

    private static Vec3 floorBelow(ClientLevel level, Vec3 from) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(from).mutable();
        for (int down = 0; down < 32; down++) {
            pos.move(Direction.DOWN);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) return Vec3.atCenterOf(pos).add(0, 0.5, 0);
        }
        return null;
    }

    /** The wind in the passages: a slow swell and fall, never quite gone. */
    private static final class Wind extends AbstractTickableSoundInstance {
        private float phase = RANDOM.nextFloat() * 100;

        Wind() {
            super(SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.01f;
            this.pitch = 0.45f;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            Minecraft client = Minecraft.getInstance();
            if (client.level == null || client.level.dimension() != Underworld.LEVEL) {
                stop();
                return;
            }
            phase += 0.01f;
            float swell = (float) (0.5 + 0.5 * Math.sin(phase) * Math.sin(phase * 0.37 + 1.3));
            // Where the rock is close all round, the air is squeezed through: louder, higher.
            float tight = (float) Mth.clamp(1 - UnderworldReverb.farthest() / 48, 0, 1);
            float target = 0.03f + 0.09f * swell + 0.05f * tight;
            volume += (target - volume) * 0.05f;
            pitch = 0.4f + 0.12f * swell + 0.08f * tight;
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
