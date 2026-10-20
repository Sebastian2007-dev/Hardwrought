package de.ipnats.hardwrought.client.end;

import de.ipnats.hardwrought.client.fx.FxDecal;
import de.ipnats.hardwrought.client.fx.FxEngine;
import de.ipnats.hardwrought.client.fx.FxParticle;
import de.ipnats.hardwrought.client.fx.FxScreen;
import de.ipnats.hardwrought.client.fx.sound.FxSounds;
import de.ipnats.hardwrought.end.BrokenIsland;
import de.ipnats.hardwrought.fx.FxEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import static de.ipnats.hardwrought.client.end.DragonFx.BLACK;
import static de.ipnats.hardwrought.client.end.DragonFx.DEEP;
import static de.ipnats.hardwrought.client.end.DragonFx.MAGENTA;
import static de.ipnats.hardwrought.client.end.DragonFx.PALE;
import static de.ipnats.hardwrought.client.end.DragonFx.VIOLET;

/**
 * The dragon raised again, as it is seen (see {@code DragonRising}): the void stirring and a heart
 * beating in it, the pillars called up out of the void one by one in lightning and thunder, every
 * crystal pouring into one point until the void there swells black, and the dragon bursting out of it.
 * While it goes on the sky tears, more and more ({@link #tear}).
 */
final class DragonRiseFx {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final RandomSource RANDOM = RandomSource.create();

    /** How torn the sky is to be for the raising; it eases back on its own once nothing holds it up. */
    private static float tear;

    private DragonRiseFx() { }

    /** How torn the sky is for the raising this tick, easing back. */
    static float tear() {
        float now = tear;
        tear = Math.max(0, tear - 0.006f);
        return now;
    }

    private static void holdTear(float at) {
        tear = Math.max(tear, at);
    }

    static void play(FxEngine fx, FxEffect effect, Vec3 pos, Vec3 target, float s, Entity follow) {
        int ticks = Math.max(1, (int) Math.round(target.y - pos.y));
        switch (effect) {
            case DRAGON_RISE_CALL -> call(fx, pos, ticks);
            case DRAGON_RISE_PILLAR -> pillar(fx, pos, ticks);
            case DRAGON_RISE_PILLAR_BURST -> pillarBurst(fx, pos, s);
            case DRAGON_RISE_SUMMON -> summon(fx, pos, ticks);
            case DRAGON_RISE_BIRTH -> birth(fx, pos, follow);
            default -> { }
        }
    }

    // ------------------------------------------------------------------------------------ the call

    /**
     * The four crystals call: motes of light are drawn in from all round to the point they shine at, a
     * heart begins to beat there — each beat a ring of bent air and a thud, quicker and quicker — a sigil
     * draws itself across the island beneath, and the sky begins to tear.
     */
    private static void call(FxEngine fx, Vec3 focus, int ticks) {
        Vec3 ground = new Vec3(focus.x, BrokenIsland.MIDDLE_TOP + 1.1, focus.z);
        fx.decal(FxDecal.Kind.HEX_SIGIL, ground, UP).radius(BrokenIsland.MIDDLE_RADIUS + 4).color(VIOLET).alpha(0.85f).spin(0.01f).reveal(ticks)
                .life(ticks + 520).fade(0.02f, 0.9f);
        fx.decal(FxDecal.Kind.HEX_SIGIL, ground.add(0, 0.05, 0), UP).radius(BrokenIsland.MIDDLE_FLAT).color(MAGENTA).alpha(0.7f).spin(-0.025f)
                .reveal(ticks / 2).life(ticks + 520).fade(0.05f, 0.9f);
        FxDecal heart = fx.decal(FxDecal.Kind.GLOW, focus, null).radius(0.5f, 4).grow(ticks).color(VIOLET).alpha(0.8f).life(ticks + 10);
        fx.light(focus, VIOLET, 40, ticks + 10).intensity(1.2f).flicker(0.2f);
        fx.light(ground.add(0, 2, 0), MAGENTA, 30, ticks + 520).intensity(0.8f).flicker(0.15f).fadeFrom(0.9f);
        FxSounds.vanilla(SoundEvents.BEACON_ACTIVATE, ground, 4, 0.5f);
        FxSounds.play(focus, 160, 0.8f, FxSounds::vortex);
        FxScreen.vignette(DEEP, 0.4f, ticks * 50);
        fx.task((engine, age) -> {
            float progress = age / (float) ticks;
            holdTear(0.15f + 0.3f * progress);
            // Motes drawn in, more and more.
            for (int i = 0; i < 2 + progress * 8; i++) {
                Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.5, RANDOM.nextGaussian()).normalize();
                Vec3 from = focus.add(dir.scale(18 + RANDOM.nextDouble() * 22));
                Vec3 v = focus.subtract(from).scale(1 / 24.0);
                Vec3 swirl = v.cross(UP).scale(0.35);
                engine.particle(from).shape(FxParticle.Shape.SPARK).velocity(v.x + swirl.x, v.y, v.z + swirl.z).life(24).size(0.12f, 0.03f)
                        .color(RANDOM.nextInt(3) == 0 ? PALE : MAGENTA, VIOLET).drag(1.0f).stretch(2f).hot(0.9f);
            }
            // A heartbeat, quicker the nearer the end of the call: two beats, a pause.
            int every = Math.max(10, 34 - (int) (progress * 24));
            int beat = age % every;
            if (beat == 0 || beat == 5) {
                float strength = beat == 0 ? 1 : 0.6f;
                engine.decal(FxDecal.Kind.WARP_RING, focus, null).radius(1, 14 + 10 * progress).life(14).param(0.5f * strength + 0.3f * progress);
                engine.decal(FxDecal.Kind.HEX_WAVE, focus, UP).radius(1, 10 + 16 * progress).life(16).color(MAGENTA).alpha(0.6f * strength).fade(0, 0.5f);
                heart.alpha(1.0f);
                FxSounds.vanilla(SoundEvents.WARDEN_HEARTBEAT, camera(), 1.0f + strength, 0.55f);
                FxScreen.shake(focus, 0.12f + 0.2f * progress * strength, 160);
            } else {
                heart.alpha(0.55f + 0.25f * (float) Math.exp(-beat * 0.3));
            }
            return age < ticks;
        });
    }

    // ------------------------------------------------------------------------------------ the pillars

    /**
     * A pillar called up: lightning from the point over the island to its top, a column of light rising
     * out of the void beneath it, sparks running up it, the air over it twisting.
     */
    private static void pillar(FxEngine fx, Vec3 top, int ticks) {
        Vec3 focus = de.ipnats.hardwrought.end.dragon.DragonRising.FOCUS;
        Vec3 deep = top.add(0, -BrokenIsland.PILLAR_DEPTH - 30, 0);
        fx.beam(deep, top.add(0, 70, 0)).width(1.6f).life(ticks + 6).color(MAGENTA).alpha(0.55f).energy(0.4f, 6).fade(0.4f, 0.8f);
        fx.beam(deep, top.add(0, 70, 0)).width(0.5f).life(ticks + 6).color(PALE).alpha(0.8f).energy(0.2f, 10).fade(0.5f, 0.8f);
        fx.decal(FxDecal.Kind.WARP_SWIRL, top.add(0, 3, 0), null).radius(2, 7).life(ticks).param(0.6f).fade(0.2f, 0.8f);
        fx.decal(FxDecal.Kind.HEX_SIGIL, top.add(0, 0.2, 0), UP).radius(6).color(MAGENTA).alpha(0.8f).spin(0.08f).reveal(ticks).life(ticks + 4)
                .fade(0.05f, 0.85f);
        fx.light(top, MAGENTA, 30, ticks + 6).intensity(1.6f).flicker(0.3f).fadeFrom(0.85f);
        FxSounds.play(top, 160, 0.7f, FxSounds::beamStart);
        fx.task((engine, age) -> {
            holdTear(0.45f);
            if (age % 4 == 0) {
                engine.beam(focus, top).lightning(2.2f, 2, 3).width(0.25f).life(5).color(RANDOM.nextInt(3) == 0 ? PALE : MAGENTA).alpha(0.9f)
                        .fade(0, 0.5f);
            }
            for (int i = 0; i < 4; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2, r = 3 + RANDOM.nextDouble() * 2;
                Vec3 from = top.add(Math.cos(a) * r, -RANDOM.nextDouble() * BrokenIsland.PILLAR_DEPTH, Math.sin(a) * r);
                engine.particle(from).shape(FxParticle.Shape.SPARK).velocity(0, 0.9 + RANDOM.nextDouble() * 0.6, 0).life(30).size(0.12f, 0)
                        .color(PALE, MAGENTA).stretch(4f).drag(1.0f).hot(1.0f);
            }
            return age < ticks;
        });
    }

    /** The pillar stands again: a burst of light at its top, a ring running out, thunder, falling embers. */
    private static void pillarBurst(FxEngine fx, Vec3 top, float radius) {
        fx.decal(FxDecal.Kind.GLOW, top, null).radius(2, 22).life(16).color(PALE).fade(0, 0.25f);
        fx.decal(FxDecal.Kind.HEX_WAVE, top, UP).radius(2, 45).life(26).color(MAGENTA).fade(0, 0.6f);
        fx.decal(FxDecal.Kind.WARP_RING, top, null).radius(2, 30).life(18).param(0.9f);
        fx.light(top, PALE, 50, 24).intensity(3).fadeFrom(0.1f);
        for (int i = 0; i < 140; i++) {
            Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
            double speed = 0.4 + RANDOM.nextDouble() * 1.2;
            fx.particle(top.add(dir.scale(radius))).shape(FxParticle.Shape.SPARK).velocity(dir.x * speed, dir.y * speed, dir.z * speed)
                    .life(30 + RANDOM.nextInt(30)).size(0.12f, 0).color(i % 3 == 0 ? PALE : MAGENTA, VIOLET).drag(0.94f).gravity(0.02f).stretch(2f)
                    .hot(1.0f);
        }
        for (int i = 0; i < 30; i++) {
            Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.3, RANDOM.nextGaussian()).normalize();
            fx.particle(top.add(dir.scale(2))).shape(FxParticle.Shape.SMOKE).velocity(dir.x * 0.3, dir.y * 0.3, dir.z * 0.3).life(50)
                    .size(1.5f, 4f).color(DEEP, BLACK).alpha(0.6f).drag(0.93f).fade(0.05f, 0.4f);
        }
        FxSounds.playDistant(top, 200, 1.0f, FxSounds::thunder);
        FxSounds.vanilla(SoundEvents.LIGHTNING_BOLT_THUNDER, top, 6, 0.7f);
        FxScreen.shake(top, 0.5f, 120);
        FxScreen.flash(top, MAGENTA, 0.25f, 300, 140);
    }

    // ------------------------------------------------------------------------------------ the summoning

    /**
     * Every crystal pours into the point: lightning from each, rings closing in on it quicker and quicker,
     * the void drawn in until it swells black inside a cracking shell of light, the view bent towards it
     * and the sky torn wide.
     */
    private static void summon(FxEngine fx, Vec3 focus, int ticks) {
        FxDecal shell = fx.decal(FxDecal.Kind.HEX_WARD, focus, null).radius(1, 7).grow(ticks).color(MAGENTA).alpha(0.85f).param(0)
                .life(ticks + 2);
        FxDecal glow = fx.decal(FxDecal.Kind.GLOW, focus, null).radius(3, 12).grow(ticks).color(MAGENTA).alpha(0.7f).life(ticks + 2);
        fx.decal(FxDecal.Kind.WARP_LENS, focus, null).radius(6, 26).grow(ticks).life(ticks + 2).param(0.5f).fade(0.1f, 0.95f);
        fx.light(focus, MAGENTA, 90, ticks + 2).intensity(2).flicker(0.35f);
        FxSounds.loop(() -> focus, 200, 1.0f, FxSounds::vortex, FxSounds::beamLoop, ticks, 10);
        FxSounds.vanilla(SoundEvents.ENDER_DRAGON_AMBIENT, focus, 8, 0.5f);
        fx.task((engine, age) -> {
            float progress = age / (float) ticks;
            holdTear(0.5f + 0.5f * progress);
            shell.param(progress);
            glow.alpha(0.5f + 0.4f * progress + 0.1f * RANDOM.nextFloat());
            // Lightning out of every crystal still shining into it.
            if (age % 3 == 0 && Minecraft.getInstance().level != null) {
                AABB round = new AABB(focus, focus).inflate(120);
                for (EndCrystal crystal : Minecraft.getInstance().level.getEntitiesOfClass(EndCrystal.class, round, Entity::isAlive)) {
                    if (RANDOM.nextFloat() > 0.35f + 0.5f * progress) continue;
                    engine.beam(crystal.position().add(0, 1, 0), focus).lightning(2.5f, 2, 2).width(0.15f + 0.25f * progress).life(4)
                            .color(RANDOM.nextInt(3) == 0 ? PALE : MAGENTA).alpha(0.9f).fade(0, 0.5f);
                }
            }
            // The void drawn in and swelling at its heart.
            for (int i = 0; i < 6 + progress * 16; i++) {
                Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
                Vec3 from = focus.add(dir.scale(10 + RANDOM.nextDouble() * 16));
                Vec3 v = focus.subtract(from).scale(1 / 16.0);
                engine.particle(from).shape(FxParticle.Shape.SMOKE).velocity(v.x, v.y, v.z).life(16).size(1.4f, 0.4f).color(DEEP, BLACK).alpha(0.75f)
                        .drag(1.0f);
            }
            for (int i = 0; i < 2 + progress * 5; i++) {
                Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
                engine.particle(focus.add(dir.scale(RANDOM.nextDouble() * (1 + 4 * progress)))).shape(FxParticle.Shape.SMOKE)
                        .velocity(RANDOM.nextGaussian() * 0.01, RANDOM.nextGaussian() * 0.01, RANDOM.nextGaussian() * 0.01).life(20)
                        .size(1.5f + 2 * progress, 2.5f + 3 * progress).color(BLACK, BLACK).alpha(0.9f).fade(0.1f, 0.6f);
            }
            // Rings closing in, quicker and quicker.
            int every = Math.max(4, 22 - (int) (progress * 18));
            if (age % every == 0) {
                Vec3 axis = new Vec3(RANDOM.nextGaussian(), 1.5 + RANDOM.nextDouble(), RANDOM.nextGaussian()).normalize();
                engine.decal(FxDecal.Kind.HEX_WAVE, focus, axis).radius(30 - 10 * progress, 1).linear().life(every + 6).color(MAGENTA).alpha(0.8f)
                        .fade(0.1f, 0.9f);
                FxScreen.shake(focus, 0.15f + 0.45f * progress, 200);
            }
            if (age % 20 == 0) FxSounds.vanilla(SoundEvents.WARDEN_HEARTBEAT, camera(), 2, 0.5f + 0.3f * progress);
            // The last of it: the sky splits open over it.
            if (progress > 0.75f && RANDOM.nextInt(4) == 0) {
                Vec3 from = focus.add(RANDOM.nextGaussian() * 30, 40 + RANDOM.nextDouble() * 30, RANDOM.nextGaussian() * 30);
                engine.beam(from, focus).lightning(3f, 2, 3).width(0.3f).life(6).color(PALE).alpha(0.9f).fade(0, 0.5f);
                FxSounds.vanilla(SoundEvents.LIGHTNING_BOLT_IMPACT, focus, 6, 0.6f);
            }
            if (age == ticks - 12) {
                FxScreen.aberration(focus, 1.2f, 800, 200);
                FxScreen.vignette(focus, BLACK, 0.7f, 900, 200);
            }
            return age < ticks;
        });
    }

    // ------------------------------------------------------------------------------------ the birth

    /**
     * It is there: everything falls into the point for a breath, then bursts — a flash, a wave over the
     * whole island and the void, a storm of sparks and black wings of smoke flung out, and its roar.
     */
    private static void birth(FxEngine fx, Vec3 focus, Entity dragon) {
        holdTear(1);
        fx.decal(FxDecal.Kind.GLOW, focus, null).radius(3, 90).life(26).color(PALE).fade(0, 0.25f);
        fx.decal(FxDecal.Kind.HEX_WAVE, focus, UP).radius(3, 170).life(50).color(MAGENTA).fade(0, 0.6f);
        fx.decal(FxDecal.Kind.HEX_WAVE, focus, new Vec3(1, 0, 0)).radius(3, 110).life(40).color(VIOLET).fade(0, 0.6f);
        fx.decal(FxDecal.Kind.HEX_WAVE, focus, new Vec3(0, 0, 1)).radius(3, 110).life(40).color(VIOLET).fade(0, 0.6f);
        fx.decal(FxDecal.Kind.WARP_RING, focus, null).radius(3, 100).life(34).param(1.0f);
        fx.later(8, () -> fx.decal(FxDecal.Kind.WARP_RING, focus, UP).radius(3, 70).life(30).param(0.8f));
        Vec3 ground = new Vec3(focus.x, BrokenIsland.MIDDLE_TOP + 1.1, focus.z);
        fx.later(10, () -> fx.decal(FxDecal.Kind.HEX_WAVE, ground, UP).radius(2, 120).life(45).color(MAGENTA).alpha(0.8f).fade(0, 0.6f));
        fx.light(focus, MAGENTA, 160, 60).intensity(4).fadeFrom(0.1f);
        for (int i = 0; i < 600; i++) {
            Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
            double speed = 0.8 + RANDOM.nextDouble() * 2.4;
            fx.particle(focus).shape(FxParticle.Shape.SPARK).velocity(dir.x * speed, dir.y * speed, dir.z * speed).life(35 + RANDOM.nextInt(35))
                    .size(0.15f, 0).color(i % 3 == 0 ? PALE : MAGENTA, VIOLET).drag(0.95f).stretch(2.5f).hot(1.0f);
        }
        // Black wings of smoke flung out to either side.
        for (int i = 0; i < 160; i++) {
            double side = i % 2 == 0 ? 1 : -1, spread = RANDOM.nextGaussian() * 0.35;
            Vec3 dir = new Vec3(side, 0.25 + spread, RANDOM.nextGaussian() * 0.5).normalize();
            double speed = 0.4 + RANDOM.nextDouble() * 1.1;
            fx.particle(focus.add(dir.scale(2))).shape(FxParticle.Shape.SMOKE).velocity(dir.x * speed, dir.y * speed, dir.z * speed).life(70)
                    .size(2f, 6f).color(DEEP, BLACK).alpha(0.7f).drag(0.94f).turbulence(0.004f).fade(0.05f, 0.4f);
        }
        FxScreen.flash(focus, PALE, 1.0f, 1100, 300);
        FxScreen.vignette(focus, MAGENTA, 0.9f, 3500, 300);
        FxScreen.shake(focus, 1.2f, 300);
        FxScreen.aberration(focus, 2.2f, 2000, 300);
        FxScreen.saturation(focus, 0.8f, 4000, 300);
        Vec3 at = dragon != null ? dragon.position() : focus;
        FxSounds.vanilla(SoundEvents.ENDER_DRAGON_GROWL, at, 12, 0.6f);
        FxSounds.vanilla(SoundEvents.GENERIC_EXPLODE.value(), focus, 10, 0.5f);
        FxSounds.playDistant(focus, 300, 1.2f, FxSounds::thunder);
        fx.later(14, () -> FxSounds.vanilla(SoundEvents.ENDER_DRAGON_GROWL, at, 12, 0.45f));
        // The tear stays wide a while after, and closes slowly.
        fx.task((engine, age) -> {
            holdTear(1 - age / 160f);
            return age < 160;
        });
    }

    private static Vec3 camera() {
        Minecraft client = Minecraft.getInstance();
        return client.gameRenderer == null ? Vec3.ZERO : client.gameRenderer.mainCamera().position();
    }
}
