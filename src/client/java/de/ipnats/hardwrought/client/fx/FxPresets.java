package de.ipnats.hardwrought.client.fx;

import de.ipnats.hardwrought.client.fx.sound.FxSounds;
import de.ipnats.hardwrought.fx.FxEffect;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.List;
import java.util.function.Supplier;

/**
 * The named effects, built from the engine's primitives. Each is a small choreography: a flash, a
 * shockwave, sparks that bounce off the floor, smoke that lingers, light that fills the room and a
 * jolt of the camera, each at its own moment.
 *
 * <p>These are showpieces for trying out the module; the magic system will compose its own.
 */
public final class FxPresets {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private FxPresets() { }

    /** Plays an effect sent by the server. A colour of 0 was replaced by the effect's own already. */
    public static void play(String id, Vec3 pos, Vec3 target, int color, float scale, int entityId) {
        FxEngine fx = FxClient.engine();
        if (fx.level() == null) return;
        Entity follow = entityId >= 0 ? fx.level().getEntity(entityId) : null;
        FxEffect effect = FxEffect.byId(id).orElse(null);
        if (effect == null) return;
        play(fx, effect, pos, target, color == 0 ? effect.defaultColor() : color, scale, follow);
    }

    public static void play(FxEngine fx, FxEffect effect, Vec3 pos, Vec3 target, int color, float s, Entity follow) {
        switch (effect) {
            case NOVA -> nova(fx, pos, color, s);
            case FIREBALL -> projectile(fx, pos, target, color, s, true);
            case ARCANE_BOLT -> projectile(fx, pos, target, color, s, false);
            case LIGHTNING -> {
                RandomSource r = fx.random();
                strike(fx, pos.add(range(r, -5, 5), 30 * s, range(r, -5, 5)), pos, color, s);
            }
            case CHAIN_LIGHTNING -> chainLightning(fx, pos, target, color, s);
            case BEAM -> beam(fx, pos, target, color, s);
            case RUNE_CIRCLE -> runeCircle(fx, pos, color, s);
            case VORTEX -> vortex(fx, pos, color, s);
            case FROST_NOVA -> frostNova(fx, pos, color, s);
            case SHIELD -> shield(fx, pos, color, s, follow);
            case HEAL -> heal(fx, pos, color, s, follow);
            case SPARKLE -> sparkle(fx, pos, color, s);
            case LIGHT_ORB -> lightOrb(fx, pos, color, s);
            case TELEPORT -> teleport(fx, pos, target, color, s);
            case FLAME_SPRAY, WATER_SPRAY, WIND_GUST, EARTH_SPIKES, LIGHT_RAY, DARK_CLOUD -> spray(fx, effect, pos, target, color, s);
            case SIGIL -> sigil(fx, pos, color, s);
            case SHOWCASE -> showcase(fx, follow != null ? follow : Minecraft.getInstance().player);
            case SHAKE -> FxScreen.shake(0.5f * s);
            case FLASH -> {
                FxScreen.flash(color, Math.min(1, 0.7f * s), 450);
                FxScreen.vignette(color, Math.min(1, 0.8f * s), 1200);
            }
            case CLEAR -> {
                fx.clear();
                FxScreen.clear();
            }
        }
    }

    // --- The effects -----------------------------------------------------------------------------

    /** An explosion of fire: a white-hot flash, a fireball, sparks that rain down and bounce, smoke. */
    static void nova(FxEngine fx, Vec3 p, int color, float s) {
        RandomSource r = fx.random();
        int hot = lighter(color, 0.75f), deep = darker(color, 0.6f);
        fx.decal(FxDecal.Kind.GLOW, p, null).radius(0.6f * s, 6f * s).life(9).color(hot).param(1.3f).fade(0, 0.15f);
        fx.decal(FxDecal.Kind.SHIELD, p, null).radius(0.4f * s, 4.4f * s).life(11).color(color).alpha(0.7f).param(0).fade(0, 0.3f);
        fx.decal(FxDecal.Kind.RING, p.add(0, 0.06, 0), UP).radius(0.3f * s, 7.5f * s).life(18).color(color).param(0.14f).fade(0, 0.35f);
        fx.decal(FxDecal.Kind.RING, p.add(0, 0.07, 0), UP).radius(0.2f * s, 4.5f * s).life(24).color(hot).alpha(0.6f).param(0.3f).fade(0, 0.2f);

        for (int i = 0; i < 26 * s; i++) {
            Vec3 v = unit(r).scale(range(r, 0.05f, 0.22f) * s);
            fx.particle(p).velocity(v.x, v.y, v.z).life(rangeI(r, 10, 20)).size(range(r, 0.9f, 1.5f) * s, 0.2f * s)
                    .color(hot, deep).hot(1).drag(0.84f).fade(0, 0.3f);
        }
        for (int i = 0; i < 170 * s; i++) {
            Vec3 d = unit(r);
            d = new Vec3(d.x, Math.abs(d.y) * 0.8 + 0.12, d.z).normalize().scale(range(r, 0.3f, 0.95f) * s);
            fx.particle(p).shape(FxParticle.Shape.SPARK).velocity(d.x, d.y, d.z).life(rangeI(r, 22, 48))
                    .size(range(r, 0.045f, 0.08f)).color(hot, deep).hot(1.1f).gravity(0.035f).drag(0.95f)
                    .collide(0.4f).stretch(1.6f).fade(0, 0.6f);
        }
        for (int i = 0; i < 60 * s; i++) {
            Vec3 at = p.add(unit(r).scale(range(r, 0, 1.5f) * s));
            Vec3 v = unit(r).scale(0.03).add(0, 0.02, 0);
            fx.particle(at).velocity(v.x, v.y, v.z).life(rangeI(r, 50, 100)).size(0.07f, 0).color(hot, color)
                    .twinkle(0.6f).turbulence(0.008f).gravity(-0.0015f).hot(0.8f);
        }
        // The smoke rises out of the fire once the flash has passed, lit from within by it at first.
        fx.later(3, () -> smoke(fx, p, 36 * s, 0.5f * s, darker(color, 0.55f), 0x1A1816, 0.38f, s, 0.9f));
        fx.light(p.add(0, 1, 0), lighter(color, 0.35f), 16 * s, 34).intensity(2.6f).flicker(0.25f).fadeFrom(0.1f);
        // The blast bends the air as it passes, and the fire leaves it shimmering.
        fx.decal(FxDecal.Kind.WARP_RING, p, null).radius(0.5f * s, 9f * s).life(16).param(0.4f).fade(0, 0.3f);
        fx.decal(FxDecal.Kind.WARP_RING, p.add(0, 0.1, 0), UP).radius(0.5f * s, 8f * s).life(18).param(0.3f).fade(0, 0.3f);
        for (int i = 0; i < 18 * s; i++) {
            Vec3 at = p.add(range(r, -1, 1) * s, range(r, 0, 1.2f) * s, range(r, -1, 1) * s);
            fx.particle(at).heat(0.3f).velocity(0, range(r, 0.03f, 0.07f), 0).drag(0.98f).size(1.2f * s, 2.6f * s)
                    .life(rangeI(r, 30, 55)).fade(0.1f, 0.4f);
        }
        FxScreen.shake(p, 0.65f * Math.min(1, s), 28 * s);
        FxScreen.flash(p, hot, 0.35f, 260, 20 * s);
        FxScreen.vignette(p, color, 0.55f, 900, 32 * s);
        FxScreen.aberration(p, 0.9f, 550, 28 * s);
        FxScreen.saturation(p, 0.35f, 900, 32 * s);
        FxSounds.playDistant(p, 72 * s, 1, FxSounds::nova);
        FxSounds.vanilla(SoundEvents.GENERIC_EXPLODE.value(), p, 0.5f * s, 0.65f);
    }

    /**
     * A projectile flying to its target: a glowing head with a trail, and an impact where it hits a
     * block or arrives. Fire leaves smoke and falling sparks and ends in a nova; arcane magic leaves a
     * spiral of glints and ends in a flare of runes.
     */
    static void projectile(FxEngine fx, Vec3 from, Vec3 to, int color, float s, boolean fire) {
        Vec3 path = to.subtract(from);
        if (path.lengthSqr() < 0.01) path = new Vec3(20, 0, 0);
        double length = path.length();
        Vec3 dir = path.normalize();
        double speed = fire ? 0.9 : 1.3;
        int hot = lighter(color, 0.7f);
        Vec3[] basis = basis(dir);

        FxDecal head = fx.decal(FxDecal.Kind.GLOW, from, null).radius(0.55f * s).life(10000).color(hot).param(1.3f).fade(0, 1);
        FxDecal aura = fx.decal(FxDecal.Kind.GLOW, from, null).radius(1.4f * s).life(10000).color(color).alpha(0.35f).param(0).fade(0, 1);
        FxLight light = fx.light(from, lighter(color, 0.3f), 9 * s, 10000).intensity(1.6f).flicker(fire ? 0.3f : 0.1f);
        fx.task((engine, age) -> {
            RandomSource r = engine.random();
            double d0 = age * speed, d1 = Math.min(length, (age + 1) * speed);
            Vec3 a = from.add(dir.scale(d0)), b = from.add(dir.scale(d1));
            BlockHitResult hit = engine.level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    CollisionContext.empty()));
            boolean blocked = hit.getType() == HitResult.Type.BLOCK;
            Vec3 end = blocked ? hit.getLocation() : b;
            head.center(end);
            aura.center(end);
            light.moveTo(end);

            int steps = 6;
            for (int i = 0; i < steps; i++) {
                Vec3 q = a.lerp(end, (i + 1) / (double) steps);
                // The trail grows in over the first blocks, or it blinds the caster at their own hand.
                float near = (float) Math.min(1, q.distanceTo(from) / 3);
                Vec3 jitter = unit(r).scale(0.05);
                engine.particle(q.add(jitter)).velocity(jitter.x * 0.2, jitter.y * 0.2, jitter.z * 0.2)
                        .life(rangeI(r, 8, 14)).size(0.36f * s * near, 0).color(hot, color).hot(0.9f).drag(0.9f).fade(0, 0.2f);
                if (fire) {
                    if (r.nextFloat() < 0.35f) {
                        engine.particle(q).shape(FxParticle.Shape.SPARK).velocity(range(r, -0.05f, 0.05f), range(r, 0, 0.08f), range(r, -0.05f, 0.05f))
                                .life(rangeI(r, 12, 24)).size(0.04f).color(hot, darker(color, 0.5f)).gravity(0.02f).drag(0.96f)
                                .collide(0.4f).stretch(2);
                    }
                } else {
                    double angle = (age * steps + i) * 0.9;
                    for (int k = 0; k < 2; k++) {
                        double phase = angle + k * Math.PI;
                        Vec3 offset = basis[0].scale(Math.cos(phase) * 0.28 * s).add(basis[1].scale(Math.sin(phase) * 0.28 * s));
                        engine.particle(q.add(offset)).velocity(offset.x * 0.03, offset.y * 0.03, offset.z * 0.03)
                                .life(rangeI(r, 14, 24)).size(0.08f * s, 0).color(lighter(color, 0.4f), color)
                                .glint(0.8f).twinkle(0.4f).hot(1);
                    }
                }
            }
            if (fire) engine.particle(end).heat(0.25f).velocity(0, 0.03, 0).size(0.5f * s, 1.1f * s).life(18).fade(0.1f, 0.4f);
            if (fire && age % 2 == 0) {
                engine.particle(end).shape(FxParticle.Shape.SMOKE).velocity(0, 0.02, 0).life(rangeI(r, 30, 50))
                        .size(0.35f * s, 1.3f * s).color(0x2E2622, 0x141312).alpha(0.28f).drag(0.94f).turbulence(0.003f)
                        .fade(0.2f, 0.3f).emissive(0.5f);
            }

            if (blocked || d1 >= length || age > 300) {
                head.kill();
                aura.kill();
                light.kill();
                Vec3 normal = blocked ? hit.getDirection().getUnitVec3() : dir.scale(-1);
                if (fire) {
                    nova(engine, end.add(normal.scale(0.2)), color, 0.75f * s);
                } else {
                    arcaneImpact(engine, end, normal, color, s);
                }
                return false;
            }
            return true;
        });
        FxScreen.vignette(from, color, 0.2f, 400, 4);
        FxSounds.play(from, 28, 1, fire ? FxSounds::fireCast : FxSounds::arcaneCast);
    }

    static void arcaneImpact(FxEngine fx, Vec3 p, Vec3 normal, int color, float s) {
        RandomSource r = fx.random();
        int hot = lighter(color, 0.7f);
        fx.decal(FxDecal.Kind.GLOW, p, null).radius(0.5f * s, 3.4f * s).life(8).color(hot).param(1.3f).fade(0, 0.1f);
        fx.decal(FxDecal.Kind.RING, p.add(normal.scale(0.05)), normal).radius(0.2f * s, 3.6f * s).life(14).color(color).param(0.12f);
        fx.decal(FxDecal.Kind.RUNE, p.add(normal.scale(0.04)), normal).radius(1.5f * s).grow(4).life(32).color(color)
                .alpha(0.9f).spin(0.09f).fade(0, 0.3f);
        for (int i = 0; i < 70 * s; i++) {
            Vec3 v = hemisphere(r, normal).scale(range(r, 0.25f, 0.7f) * s);
            fx.particle(p).shape(FxParticle.Shape.SPARK).velocity(v.x, v.y, v.z).life(rangeI(r, 12, 26))
                    .size(0.05f).color(hot, color).drag(0.9f).gravity(0.01f).collide(0.5f).stretch(2).hot(1);
        }
        for (int i = 0; i < 30 * s; i++) {
            Vec3 v = unit(r).scale(range(r, 0.01f, 0.05f));
            fx.particle(p.add(unit(r).scale(0.6 * s))).velocity(v.x, v.y, v.z).life(rangeI(r, 30, 55)).size(0.1f, 0)
                    .color(hot, color).glint(0.9f).twinkle(0.6f).drag(0.95f).hot(1);
        }
        fx.light(p.add(normal.scale(0.5)), lighter(color, 0.3f), 12 * s, 22).intensity(2.2f).fadeFrom(0.1f);
        fx.decal(FxDecal.Kind.WARP_RING, p, null).radius(0.3f * s, 4.5f * s).life(12).param(0.35f).fade(0, 0.3f);
        FxScreen.shake(p, 0.3f, 20);
        FxScreen.vignette(p, color, 0.35f, 600, 16);
        FxScreen.aberration(p, 0.5f, 400, 18);
        FxSounds.playDistant(p, 48 * s, 1, FxSounds::arcaneImpact);
    }

    /** A bolt from the sky: a forked strike that flickers, strikes again, and scorches the ground. */
    static void strike(FxEngine fx, Vec3 from, Vec3 to, int color, float s) {
        RandomSource r = fx.random();
        int hot = lighter(color, 0.8f);
        fx.beam(from, to).lightning(0.9f, 2, 4).width(0.45f * s).life(12).color(hot).fade(0, 0.5f).energy(0.5f, 6);
        fx.beam(from, to).lightning(1.1f, 3, 2).width(1.0f * s).life(10).color(color).alpha(0.45f).fade(0, 0.4f);
        fx.later(4, () -> fx.beam(from, to).lightning(0.8f, 2, 3).width(0.35f * s).life(8).color(hot));

        fx.decal(FxDecal.Kind.GLOW, to, null).radius(0.6f * s, 4.2f * s).life(7).color(hot).param(1.5f).fade(0, 0.1f);
        fx.decal(FxDecal.Kind.RING, to.add(0, 0.06, 0), UP).radius(0.3f * s, 5.5f * s).life(12).color(color).param(0.1f);
        for (int i = 0; i < 90 * s; i++) {
            Vec3 v = hemisphere(r, UP).scale(range(r, 0.2f, 0.75f) * s);
            fx.particle(to.add(0, 0.1, 0)).shape(FxParticle.Shape.SPARK).velocity(v.x, v.y, v.z).life(rangeI(r, 14, 32))
                    .size(0.045f).color(0xFFFFFF, color).gravity(0.03f).drag(0.95f).collide(0.45f).stretch(1.5f).hot(1.2f);
        }
        smoke(fx, to, 12 * s, 0.3f * s, 0x4A4A55, 0x1C1C22, 0.3f, s * 0.7f, 0.6f);
        fx.light(to.add(0, 1, 0), hot, 20 * s, 14).intensity(3).flicker(0.5f).fadeFrom(0.1f);
        fx.light(from.lerp(to, 0.5), hot, 22 * s, 6).intensity(2).flicker(0.6f).fadeFrom(0.1f);
        fx.decal(FxDecal.Kind.WARP_RING, to.add(0, 0.8, 0), null).radius(0.4f * s, 7f * s).life(10).param(0.45f).fade(0, 0.3f);
        fx.decal(FxDecal.Kind.WARP_RING, to.add(0, 0.1, 0), UP).radius(0.4f * s, 6f * s).life(12).param(0.3f).fade(0, 0.3f);
        FxScreen.flash(to, hot, 0.55f, 220, 48);
        FxScreen.shake(to, 0.55f, 36);
        FxScreen.vignette(to, color, 0.4f, 700, 40);
        FxScreen.aberration(to, 1.2f, 350, 40);
        FxScreen.saturation(to, 0.3f, 500, 40);
        FxSounds.playDistant(to, 160 * s, 1, FxSounds::thunder);
        FxSounds.vanilla(SoundEvents.LIGHTNING_BOLT_IMPACT, to, 0.6f, 0.8f);
    }

    /** A crackling arc from the hand to the target that holds for a moment. */
    static void chainLightning(FxEngine fx, Vec3 from, Vec3 to, int color, float s) {
        Vec3 end = impactPoint(fx, from, to);
        int hot = lighter(color, 0.8f);
        int life = 18;
        fx.beam(from, end).lightning(0.6f, 1, 2).width(0.28f * s).life(life).color(hot).fade(0, 0.7f);
        fx.light(from, hot, 7, life).intensity(1.2f).flicker(0.6f);
        fx.light(end, hot, 11 * s, life).intensity(2).flicker(0.6f);
        fx.task((engine, age) -> {
            RandomSource r = engine.random();
            for (int i = 0; i < 5 * s; i++) {
                Vec3 v = unit(r).scale(range(r, 0.15f, 0.45f));
                engine.particle(end).shape(FxParticle.Shape.SPARK).velocity(v.x, v.y, v.z).life(rangeI(r, 6, 14))
                        .size(0.04f).color(0xFFFFFF, color).gravity(0.02f).collide(0.5f).stretch(1.5f).hot(1.2f);
            }
            engine.particle(end).life(3).size(0.9f * s, 0.4f * s).color(hot).hot(1.3f).alpha(0.8f);
            engine.particle(from).life(2).size(0.35f * s).color(hot).hot(1.2f).alpha(0.8f);
            return age < life;
        });
        FxScreen.shake(end, 0.25f, 20);
        FxScreen.flash(end, hot, 0.2f, 150, 24);
        FxScreen.aberration(end, 0.5f, 300, 24);
        FxSounds.play(from.lerp(end, 0.5), 32, 1, FxSounds::zap);
    }

    /** A sustained ray: a streaming ribbon with a white core, sparks spraying where it hits. */
    static void beam(FxEngine fx, Vec3 from, Vec3 to, int color, float s) {
        BlockHitResult hit = fx.level().clip(new ClipContext(from, to.add(to.subtract(from).normalize().scale(0.5)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 end = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : to;
        Vec3 normal = hit.getType() == HitResult.Type.BLOCK ? hit.getDirection().getUnitVec3()
                : from.subtract(to).normalize();
        int hot = lighter(color, 0.75f);
        int life = 70;
        fx.beam(from, end).width(0.45f * s).life(life).color(color).alpha(0.7f).energy(0.9f, 3.5f).fade(0.08f, 0.85f);
        fx.beam(from, end).width(0.13f * s).life(life).color(hot).energy(0.3f, 6).fade(0.08f, 0.85f);
        fx.light(end.add(normal.scale(0.5)), lighter(color, 0.3f), 11 * s, life).intensity(2).flicker(0.2f).fadeFrom(0.85f);
        fx.light(from, color, 7, life).intensity(1).fadeFrom(0.85f);
        fx.task((engine, age) -> {
            RandomSource r = engine.random();
            float fade = age > life * 0.85f ? 1 - (age - life * 0.85f) / (life * 0.15f) : 1;
            for (int i = 0; i < 6 * s * fade; i++) {
                Vec3 v = hemisphere(r, normal).scale(range(r, 0.2f, 0.5f));
                engine.particle(end).shape(FxParticle.Shape.SPARK).velocity(v.x, v.y, v.z).life(rangeI(r, 8, 16))
                        .size(0.045f).color(hot, color).gravity(0.025f).drag(0.94f).collide(0.4f).stretch(1.6f).hot(1.1f);
            }
            engine.particle(end).life(3).size(1.0f * s, 0.4f * s).color(hot).hot(1.2f).alpha(0.8f * fade);
            engine.particle(from).life(2).size(0.45f * s).color(hot).hot(1.2f).alpha(0.8f * fade);
            if (age % 10 == 0) {
                engine.decal(FxDecal.Kind.RING, end.add(normal.scale(0.04)), normal).radius(0.2f * s, 1.4f * s).life(10)
                        .color(color).alpha(0.7f * fade).param(0.2f);
            }
            if (age % 3 == 0 && r.nextBoolean()) {
                engine.particle(end).shape(FxParticle.Shape.SMOKE).velocity(normal.x * 0.03, 0.03, normal.z * 0.03)
                        .life(rangeI(r, 25, 45)).size(0.3f, 1.2f).color(0x302830, 0x151215).alpha(0.35f).turbulence(0.003f)
                        .fade(0.1f, 0.3f);
            }
            FxScreen.shake(end, 0.035f, 14);
            engine.particle(end.add(normal.scale(0.3))).heat(0.3f * fade).velocity(normal.x * 0.02, 0.03, normal.z * 0.02)
                    .size(0.6f * s, 1.3f * s).life(16).fade(0.1f, 0.4f);
            return age < life;
        });
        FxScreen.vignette(from, color, 0.35f, 900, 6);
        FxScreen.aberration(from, 0.4f, 900, 8);
        FxSounds.loop(() -> from, 32, 0.9f, FxSounds::beamStart, FxSounds::beamLoop, life - 8, 10);
    }

    /**
     * A magic circle that draws itself on the ground and turns, with motes rising from it, and at
     * the end sends a pillar of light into the sky.
     */
    static void runeCircle(FxEngine fx, Vec3 pos, int color, float s) {
        Vec3 c = pos.add(0, 0.03, 0);
        int light = lighter(color, 0.35f), hot = lighter(color, 0.7f);
        float radius = 2.8f * s;
        fx.decal(FxDecal.Kind.RUNE, c, UP).radius(radius).life(110).color(color).reveal(24).spin(0.018f).fade(0, 0.82f);
        fx.later(6, () -> fx.decal(FxDecal.Kind.RUNE, c.add(0, 0.01, 0), UP).radius(1.25f * s).life(98).color(light)
                .alpha(0.85f).reveal(30).spin(-0.045f).fade(0.05f, 0.8f));
        FxSounds.play(c, 44, 1, FxSounds::runeCircle);
        fx.light(c.add(0, 0.5, 0), color, 11 * s, 110).intensity(1.9f).fadeFrom(0.75f);
        fx.task((engine, age) -> {
            RandomSource r = engine.random();
            if (age < 100) {
                for (int i = 0; i < 5 * s; i++) {
                    double angle = r.nextDouble() * Math.PI * 2, dist = radius * Math.sqrt(r.nextDouble());
                    engine.particle(c.add(Math.cos(angle) * dist, 0.05, Math.sin(angle) * dist))
                            .velocity(0, range(r, 0.03f, 0.09f), 0).turbulence(0.002f).drag(0.98f)
                            .life(rangeI(r, 25, 55)).size(0.08f, 0).color(light, color)
                            .glint(r.nextFloat() < 0.25f ? 1 : 0).twinkle(0.5f).hot(0.8f);
                }
            }
            if (age % 20 == 0 && age < 90) {
                engine.decal(FxDecal.Kind.RING, c.add(0, 0.02, 0), UP).radius(radius, radius * 1.22f).life(20).color(color)
                        .alpha(0.5f).param(0.08f).linear();
            }
            if (age == 72) {
                Vec3 top = c.add(0, 16 * s, 0);
                engine.beam(c, top).width(0.8f * s).life(32).color(light).energy(0.8f, 5).fade(0.05f, 0.5f);
                engine.beam(c, top).width(0.28f * s).life(32).color(0xFFFFFF).energy(0.3f, 7).fade(0.05f, 0.5f);
                engine.decal(FxDecal.Kind.RING, c.add(0, 0.05, 0), UP).radius(0.5f * s, 6.5f * s).life(18).color(hot).param(0.12f);
                engine.decal(FxDecal.Kind.GLOW, c.add(0, 0.6, 0), null).radius(0.8f * s, 3.2f * s).life(10).color(hot).param(1.2f).fade(0, 0.2f);
                for (int i = 0; i < 70 * s; i++) {
                    engine.particle(c.add(range(r, -0.4f, 0.4f) * s, 0.2, range(r, -0.4f, 0.4f) * s))
                            .shape(FxParticle.Shape.SPARK).velocity(range(r, -0.08f, 0.08f), range(r, 0.4f, 0.95f), range(r, -0.08f, 0.08f))
                            .life(rangeI(r, 24, 40)).size(0.055f).color(0xFFFFFF, color).gravity(0.012f).drag(0.965f).stretch(1.4f).hot(1);
                }
                engine.light(c.add(0, 1, 0), hot, 18 * s, 30).intensity(2.6f).fadeFrom(0.3f);
                engine.decal(FxDecal.Kind.WARP_RING, c.add(0, 0.1, 0), UP).radius(0.5f * s, 7f * s).life(16).param(0.35f).fade(0, 0.3f);
                engine.decal(FxDecal.Kind.WARP_HEAT, c.add(0, 3 * s, 0), null).radius(2.5f * s).life(30).param(0.15f).fade(0.1f, 0.5f);
                FxScreen.flash(c, hot, 0.3f, 300, 24);
                FxScreen.shake(c, 0.35f, 28);
                FxScreen.vignette(c, color, 0.5f, 1000, 30);
                FxScreen.aberration(c, 0.7f, 600, 30);
                FxScreen.saturation(c, 0.3f, 900, 30);
            }
            return age < 110;
        });
    }

    /** A rift that drags light in from all around, spinning around a dark heart, and then bursts. */
    static void vortex(FxEngine fx, Vec3 pos, int color, float s) {
        Vec3 c = pos.add(0, 1.4 * s, 0);
        int light = lighter(color, 0.5f), hot = lighter(color, 0.75f);
        int life = 100;
        fx.decal(FxDecal.Kind.RUNE, pos.add(0, 0.03, 0), UP).radius(3.2f * s).life(life).color(darker(color, 0.15f))
                .alpha(0.55f).spin(-0.03f).reveal(30).fade(0, 0.9f);
        fx.decal(FxDecal.Kind.SHIELD, c, null).radius(0.2f * s, 0.95f * s).grow(20).life(life).color(color)
                .alpha(0.75f).param(0.25f).fade(0.05f, 0.9f);
        fx.decal(FxDecal.Kind.GLOW, c, null).radius(0.3f * s, 1.8f * s).grow(20).life(life).color(color)
                .alpha(0.45f).param(0).fade(0.05f, 0.9f);
        FxSounds.play(c, 60, 1, FxSounds::vortex);
        FxLight glow = fx.light(c, lighter(color, 0.2f), 11 * s, life).intensity(2.1f).flicker(0.2f);
        // Space itself turns around the rift and sinks into its heart.
        fx.decal(FxDecal.Kind.WARP_SWIRL, c, null).radius(0.5f * s, 4.5f * s).grow(25).life(life).param(0.3f).fade(0.1f, 0.9f);
        fx.decal(FxDecal.Kind.WARP_LENS, c, null).radius(0.3f * s, 1.8f * s).grow(20).life(life).param(0.4f).fade(0.1f, 0.9f);
        fx.task((engine, age) -> {
            RandomSource r = engine.random();
            float ramp = Math.min(1, age / 20f);
            if (age < life - 8) {
                for (int i = 0; i < 12 * s * ramp; i++) {
                    double angle = r.nextDouble() * Math.PI * 2, dist = range(r, 3.2f, 4.4f) * s;
                    engine.particle(c.add(Math.cos(angle) * dist, range(r, -0.7f, 0.7f) * s, Math.sin(angle) * dist))
                            .attract(c.x, c.y, c.z, 0.012f * s, 0.055f * s).drag(0.92f).life(rangeI(r, 35, 55))
                            .size(0.1f * s, 0.02f).color(light, color).hot(0.7f).fade(0.1f, 0.7f);
                }
                Vec3 v = unit(r).scale(0.02);
                engine.particle(c.add(unit(r).scale(0.3 * s))).shape(FxParticle.Shape.SMOKE).velocity(v.x, v.y, v.z)
                        .life(20).size(0.8f * s, 0.2f * s).color(0x08030C).alpha(0.75f).fade(0.2f, 0.4f);
                if (age % 12 == 0) {
                    engine.decal(FxDecal.Kind.RING, c, UP).radius(4.4f * s, 0.3f * s).linear().life(18).color(color)
                            .alpha(0.55f).param(0.06f);
                }
            }
            if (age == life - 8) {
                engine.decal(FxDecal.Kind.GLOW, c, null).radius(3.5f * s, 0.2f * s).linear().life(8).color(hot).param(1.2f).fade(0.5f, 0.9f);
            }
            if (age == life - 1) {
                engine.decal(FxDecal.Kind.GLOW, c, null).radius(0.5f * s, 5f * s).life(10).color(hot).param(1.4f).fade(0, 0.15f);
                engine.decal(FxDecal.Kind.RING, c, UP).radius(0.3f * s, 7f * s).life(16).color(color).param(0.12f);
                engine.decal(FxDecal.Kind.RING, c, null).radius(0.3f * s, 5f * s).life(14).color(hot).alpha(0.6f).param(0.1f);
                for (int i = 0; i < 110 * s; i++) {
                    Vec3 v = unit(r).scale(range(r, 0.3f, 0.9f) * s);
                    engine.particle(c).shape(FxParticle.Shape.SPARK).velocity(v.x, v.y, v.z).life(rangeI(r, 18, 34))
                            .size(0.05f).color(0xFFFFFF, color).drag(0.93f).gravity(0.01f).collide(0.5f).stretch(1.8f).hot(1.1f);
                }
                engine.light(c, hot, 18 * s, 16).intensity(3).fadeFrom(0.2f);
                engine.decal(FxDecal.Kind.WARP_RING, c, null).radius(0.3f * s, 9f * s).life(14).param(0.5f).fade(0, 0.3f);
                FxScreen.flash(c, hot, 0.35f, 280, 28);
                FxScreen.shake(c, 0.55f, 32);
                FxScreen.vignette(c, color, 0.5f, 800, 32);
                FxScreen.aberration(c, 1.2f, 600, 32);
                FxScreen.saturation(c, 0.4f, 800, 32);
            }
            glow.moveTo(c);
            return age < life;
        });
    }

    /** A burst of cold: ice shards flying low, mist rolling out along the ground, glints of snow. */
    static void frostNova(FxEngine fx, Vec3 p, int color, float s) {
        RandomSource r = fx.random();
        int hot = lighter(color, 0.6f);
        fx.decal(FxDecal.Kind.RING, p.add(0, 0.06, 0), UP).radius(0.3f * s, 6.5f * s).life(20).color(color).param(0.22f);
        fx.decal(FxDecal.Kind.RING, p.add(0, 0.07, 0), UP).radius(0.2f * s, 4f * s).life(26).color(hot).alpha(0.5f).param(0.05f);
        fx.decal(FxDecal.Kind.GLOW, p, null).radius(0.5f * s, 3.6f * s).life(8).color(hot).param(1).fade(0, 0.1f);
        for (int i = 0; i < 120 * s; i++) {
            Vec3 d = unit(r);
            d = new Vec3(d.x, range(r, -0.1f, 0.35f), d.z).normalize().scale(range(r, 0.35f, 0.8f) * s);
            fx.particle(p.add(0, 0.3, 0)).shape(FxParticle.Shape.SPARK).velocity(d.x, d.y, d.z).life(rangeI(r, 16, 30))
                    .size(range(r, 0.05f, 0.07f)).color(0xFFFFFF, color).hot(0.4f).drag(0.88f).gravity(0.004f)
                    .collide(0.2f).stretch(1.2f);
        }
        for (int i = 0; i < 40 * s; i++) {
            double angle = r.nextDouble() * Math.PI * 2;
            Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            float speed = range(r, 0.06f, 0.18f) * s;
            fx.particle(p.add(out.scale(0.5)).add(0, 0.2, 0)).shape(FxParticle.Shape.SMOKE)
                    .velocity(out.x * speed, 0.005, out.z * speed).life(rangeI(r, 50, 90)).size(0.7f * s, 2.4f * s)
                    .color(0xDDF4FF, 0xA8D8F0).alpha(0.35f).drag(0.95f).fade(0.1f, 0.3f);
        }
        for (int i = 0; i < 70 * s; i++) {
            double angle = r.nextDouble() * Math.PI * 2, dist = 5 * s * Math.sqrt(r.nextDouble());
            fx.particle(p.add(Math.cos(angle) * dist, range(r, 2, 4), Math.sin(angle) * dist))
                    .velocity(0, -range(r, 0.02f, 0.05f), 0).turbulence(0.002f).drag(0.99f).life(rangeI(r, 60, 100))
                    .size(0.06f).color(hot).glint(1).twinkle(0.7f).hot(1);
        }
        fx.light(p.add(0, 0.5, 0), lighter(color, 0.3f), 12 * s, 24).intensity(1.8f).fadeFrom(0.2f);
        fx.decal(FxDecal.Kind.WARP_RING, p.add(0, 0.1, 0), UP).radius(0.3f * s, 7f * s).life(20).param(0.3f).fade(0, 0.3f);
        fx.decal(FxDecal.Kind.WARP_RING, p.add(0, 0.6, 0), null).radius(0.3f * s, 5f * s).life(12).param(0.3f).fade(0, 0.3f);
        FxScreen.shake(p, 0.25f, 20);
        FxScreen.vignette(p, color, 0.4f, 900, 20);
        FxScreen.aberration(p, 0.5f, 450, 20);
        FxSounds.playDistant(p, 44 * s, 1, FxSounds::frost);
    }

    /** A sphere of force around its owner that shimmers, ripples now and then, and shatters at the end. */
    static void shield(FxEngine fx, Vec3 pos, int color, float s, Entity follow) {
        int hot = lighter(color, 0.7f);
        int life = 140;
        float radius = 1.7f * s;
        Supplier<Vec3> center = () -> follow != null && follow.isAlive()
                ? follow.position().add(0, follow.getBbHeight() * 0.5, 0) : pos.add(0, 1, 0);
        FxDecal lens = fx.decal(FxDecal.Kind.WARP_LENS, center.get(), null).radius(0.3f * s, radius).grow(8).life(life)
                .param(-0.12f).fade(0.03f, 0.9f);
        fx.decal(FxDecal.Kind.WARP_RING, center.get(), null).radius(0.3f * s, radius * 1.6f).life(10).param(0.3f).fade(0, 0.3f);
        FxDecal bubble = fx.decal(FxDecal.Kind.SHIELD, center.get(), null).radius(0.3f * s, radius).grow(8).life(life)
                .color(color).param(1).fade(0.03f, 0.9f);
        fx.decal(FxDecal.Kind.RING, center.get(), null).radius(0.3f * s, radius * 1.3f).life(10).color(hot).param(0.15f);
        FxLight light = fx.light(center.get(), color, 6 * s, life).intensity(0.9f);
        FxSounds.loop(center, 20, 0.7f, FxSounds::shieldUp, FxSounds::shieldLoop, life - 6, 6);
        fx.task((engine, age) -> {
            RandomSource r = engine.random();
            Vec3 c = center.get();
            bubble.center(c);
            lens.center(c);
            light.moveTo(c);
            if (age < life - 6) {
                for (int i = 0; i < 2 * s; i++) {
                    Vec3 n = unit(r);
                    Vec3 tangent = n.cross(UP).normalize().scale(0.02);
                    engine.particle(c.add(n.scale(radius))).velocity(tangent.x, tangent.y, tangent.z).life(16).size(0.07f, 0)
                            .color(hot).glint(0.9f).twinkle(0.4f).hot(1);
                }
                if (age % 25 == 12) {
                    engine.decal(FxDecal.Kind.RING, c, null).radius(radius, radius * 1.18f).life(12).color(color)
                            .alpha(0.45f).param(0.1f).linear();
                }
                if (follow == Minecraft.getInstance().getCameraEntity() && age % 20 == 0) {
                    FxScreen.vignette(color, 0.3f, 1400);
                }
            }
            if (age == life - 6) {
                for (int i = 0; i < 60 * s; i++) {
                    Vec3 n = unit(r);
                    Vec3 v = n.scale(range(r, 0.1f, 0.3f));
                    engine.particle(c.add(n.scale(radius))).shape(FxParticle.Shape.SPARK).velocity(v.x, v.y, v.z)
                            .life(rangeI(r, 12, 22)).size(0.05f).color(0xFFFFFF, color).gravity(0.02f).drag(0.94f).stretch(1.5f).hot(1);
                }
                engine.decal(FxDecal.Kind.RING, c, null).radius(radius, radius * 1.8f).life(10).color(hot).param(0.15f);
                FxScreen.shake(c, 0.15f, 10);
                FxSounds.play(c, 24, 1, FxSounds::shieldBreak);
            }
            return age < life;
        });
    }

    /** Healing light spiralling up around its target, with glints rising from the ground. */
    static void heal(FxEngine fx, Vec3 pos, int color, float s, Entity follow) {
        int light = lighter(color, 0.6f);
        int life = 50;
        Supplier<Vec3> base = () -> follow != null && follow.isAlive() ? follow.position() : pos;
        float height = follow != null ? follow.getBbHeight() * 1.2f : 2.2f;
        FxLight glow = fx.light(base.get().add(0, 1, 0), color, 7 * s, life + 10).intensity(1.2f).fadeFrom(0.6f);
        fx.decal(FxDecal.Kind.GLOW, base.get().add(0, height * 0.5, 0), null).radius(1.0f * s, 2.4f * s).life(12)
                .color(light).alpha(0.5f).param(0.5f).fade(0, 0.2f);
        fx.task((engine, age) -> {
            RandomSource r = engine.random();
            Vec3 b = base.get();
            glow.moveTo(b.add(0, 1, 0));
            if (age < life) {
                float progress = age / (float) life;
                for (int k = 0; k < 3; k++) {
                    double angle = age * 0.42 + k * Math.PI * 2 / 3;
                    double radius = 0.8 * s * (1 - progress * 0.6);
                    engine.particle(b.add(Math.cos(angle) * radius, progress * height, Math.sin(angle) * radius))
                            .velocity(0, 0.01, 0).life(26).size(0.2f * s, 0).color(light, color).hot(0.8f).glint(0.4f).twinkle(0.3f);
                }
                for (int i = 0; i < 2 * s; i++) {
                    double angle = r.nextDouble() * Math.PI * 2, dist = r.nextDouble() * 1.1 * s;
                    engine.particle(b.add(Math.cos(angle) * dist, 0.05, Math.sin(angle) * dist))
                            .velocity(0, range(r, 0.04f, 0.08f), 0).drag(0.97f).life(rangeI(r, 22, 34)).size(0.06f, 0)
                            .color(light).glint(1).twinkle(0.5f).hot(1);
                }
                if (age % 16 == 0) {
                    engine.decal(FxDecal.Kind.RING, b.add(0, 0.05, 0), UP).radius(0.4f * s, 1.6f * s).life(16).color(color)
                            .alpha(0.5f).param(0.15f);
                }
            }
            return age < life;
        });
        if (follow == Minecraft.getInstance().getCameraEntity()) FxScreen.vignette(color, 0.35f, 1400);
        FxSounds.play(base.get().add(0, 1, 0), 20, 0.9f, FxSounds::heal);
    }

    /**
     * A mote of light hanging in the air, bobbing a little, that lights what is around it for half a
     * minute — longer for a stronger spell — and then dims away.
     */
    static void lightOrb(FxEngine fx, Vec3 p, int color, float s) {
        int life = (int) (600 * Math.max(0.5f, Math.min(2f, s)));
        int hot = lighter(color, 0.6f);
        FxDecal core = fx.decal(FxDecal.Kind.GLOW, p, null).radius(0.18f, 0.22f).life(life).color(hot).param(1.2f)
                .fade(0.02f, 0.1f);
        FxDecal halo = fx.decal(FxDecal.Kind.GLOW, p, null).radius(0.6f, 0.7f).life(life).color(color).alpha(0.3f)
                .param(0).fade(0.02f, 0.1f);
        FxLight light = fx.light(p, color, 10 * Math.max(0.6f, Math.min(1.5f, s)), life).intensity(1.3f).fadeFrom(0.9f);
        fx.task((engine, age) -> {
            Vec3 at = p.add(0, Math.sin(age * 0.08) * 0.08, 0);
            core.center(at);
            halo.center(at);
            light.moveTo(at);
            if (age % 6 == 0) {
                RandomSource r = engine.random();
                engine.particle(at.add(unit(r).scale(0.25))).velocity(0, 0.01, 0).life(rangeI(r, 16, 28)).size(0.05f, 0)
                        .color(hot).glint(0.8f).twinkle(0.5f).hot(1);
            }
            return age < life;
        });
        FxSounds.play(p, 16, 0.6f, FxSounds::sparkle);
    }

    /**
     * A step through nothing. Where the caster stood, the air is drawn in to a column of light that
     * pinches shut; a streak of sparks crosses the gap; where they arrive, a ring runs out over the
     * ground and glints rise around them. from and to are where their feet were and are.
     */
    static void teleport(FxEngine fx, Vec3 from, Vec3 to, int color, float s) {
        RandomSource r = fx.random();
        int hot = lighter(color, 0.7f);
        Vec3 a = from.add(0, 1, 0), b = to.add(0, 1, 0);
        // Departure: drawn in, then gone.
        fx.decal(FxDecal.Kind.GLOW, a, null).radius(1.6f * s, 0.1f).linear().life(10).color(hot).param(1.2f).fade(0, 0.6f);
        fx.decal(FxDecal.Kind.WARP_LENS, a, null).radius(2.2f * s, 0.2f).linear().life(10).param(0.6f).fade(0, 0.5f);
        for (int i = 0; i < 70 * s; i++) {
            Vec3 start = a.add(unit(r).scale(range(r, 1.2f, 2.2f) * s)).add(0, range(r, -0.8f, 0.8f), 0);
            fx.particle(start).attract(a.x, a.y, a.z, 0.04f * s, 0.2f * s).drag(0.85f).life(rangeI(r, 8, 12))
                    .size(0.09f * s, 0.01f).color(hot, color).hot(1).fade(0.1f, 0.6f);
        }
        for (int i = 0; i < 24; i++) {
            double y = i / 12.0;
            fx.particle(from.add(range(r, -0.25f, 0.25f), y, range(r, -0.25f, 0.25f))).velocity(0, 0.02, 0)
                    .life(rangeI(r, 10, 18)).size(0.16f * s, 0).color(hot).glint(0.8f).hot(1).fade(0, 0.3f);
        }
        fx.light(a, color, 8 * s, 12).intensity(2).fadeFrom(0.1f);
        // The gap: a streak of sparks along the way.
        Vec3 way = b.subtract(a);
        int steps = (int) Math.max(4, way.length() * 3);
        for (int i = 0; i <= steps; i++) {
            Vec3 q = a.add(way.scale(i / (double) steps));
            Vec3 v = way.normalize().scale(range(r, 0.05f, 0.2f));
            fx.particle(q.add(unit(r).scale(0.15))).shape(FxParticle.Shape.SPARK).velocity(v.x, v.y, v.z)
                    .life(rangeI(r, 6, 12)).size(0.05f).color(0xFFFFFF, color).drag(0.9f).stretch(2.5f).hot(1.1f);
        }
        // Arrival.
        fx.decal(FxDecal.Kind.RING, to.add(0, 0.05, 0), UP).radius(0.3f, 2.8f * s).life(14).color(color).param(0.12f);
        fx.decal(FxDecal.Kind.GLOW, b, null).radius(0.4f, 2.2f * s).life(10).color(hot).param(1.3f).fade(0, 0.2f);
        fx.decal(FxDecal.Kind.WARP_RING, b, null).radius(0.3f, 3.5f * s).life(12).param(0.35f).fade(0, 0.3f);
        fx.light(b, lighter(color, 0.3f), 10 * s, 18).intensity(2.4f).fadeFrom(0.15f);
        fx.task((engine, age) -> {
            RandomSource rr = engine.random();
            for (int i = 0; i < 4 * s; i++) {
                double angle = rr.nextDouble() * Math.PI * 2, dist = range(rr, 0.2f, 0.9f) * s;
                engine.particle(to.add(Math.cos(angle) * dist, 0.1, Math.sin(angle) * dist))
                        .velocity(0, range(rr, 0.05f, 0.12f), 0).drag(0.95f).life(rangeI(rr, 16, 28)).size(0.07f, 0)
                        .color(hot, color).glint(1).twinkle(0.5f).hot(1);
            }
            return age < 14;
        });
        FxScreen.vignette(b, color, 0.3f, 500, 6);
        FxScreen.aberration(b, 0.6f, 300, 8);
        FxSounds.play(a, 24, 1.2f, FxSounds::zap);
        FxSounds.play(b, 24, 0.8f, FxSounds::arcaneImpact);
    }

    /**
     * An element thrown from the hand at what is in front of it: for a few ticks a stream of its
     * stuff rushes from pos to target, and where it lands it does what that element does — a puff of
     * fire, a splash, a ring of wind, stone bursting up, a flare, a swelling cloud.
     */
    static void spray(FxEngine fx, FxEffect kind, Vec3 from, Vec3 to, int color, float s) {
        Vec3 path = to.subtract(from);
        double length = Math.max(0.5, path.length());
        Vec3 dir = path.scale(1 / length);
        int hot = lighter(color, 0.65f);
        int life = 7;
        fx.light(from.lerp(to, 0.4), lighter(color, 0.2f), 7 * s, life + 10).intensity(1.6f).fadeFrom(0.4f);
        fx.task((engine, age) -> {
            RandomSource r = engine.random();
            if (age < life) {
                for (int i = 0; i < 14 * s; i++) {
                    // Each bit set off from the hand to arrive at the target within the stream's span.
                    float speed = (float) (length / range(r, 6f, 10f));
                    Vec3 spread = unit(r).scale(range(r, 0.0f, 0.09f) * s);
                    Vec3 v = dir.scale(speed).add(spread);
                    Vec3 start = from.add(unit(r).scale(0.08));
                    switch (kind) {
                        case FLAME_SPRAY -> engine.particle(start).velocity(v.x, v.y, v.z).life(rangeI(r, 7, 11))
                                .size(0.12f * s, 0.55f * s).color(0xFFF0B0, color).hot(1).drag(0.93f).turbulence(0.004f);
                        case WATER_SPRAY -> engine.particle(start).velocity(v.x, v.y + 0.04, v.z).life(rangeI(r, 8, 12))
                                .size(0.09f * s, 0.04f).color(hot, color).glint(0.6f).gravity(0.025f).collide(0.3f);
                        case WIND_GUST -> engine.particle(start).shape(FxParticle.Shape.SPARK).velocity(v.x * 1.3, v.y * 1.3, v.z * 1.3)
                                .life(rangeI(r, 6, 9)).size(0.05f * s).color(0xFFFFFF, color).alpha(0.7f).stretch(4).drag(0.95f);
                        case EARTH_SPIKES -> {
                            if (i % 3 == 0) {
                                engine.particle(start).shape(FxParticle.Shape.SPARK).velocity(v.x, v.y + 0.05, v.z)
                                        .life(rangeI(r, 8, 12)).size(0.07f * s).color(darker(color, 0.2f), darker(color, 0.5f))
                                        .gravity(0.03f).collide(0.4f).stretch(1.2f);
                            } else {
                                engine.particle(start).shape(FxParticle.Shape.SMOKE).velocity(v.x * 0.7, v.y * 0.7, v.z * 0.7)
                                        .life(rangeI(r, 12, 20)).size(0.2f * s, 0.6f * s).color(darker(color, 0.1f), darker(color, 0.4f))
                                        .alpha(0.45f).drag(0.9f).fade(0.1f, 0.5f);
                            }
                        }
                        case LIGHT_RAY -> engine.particle(start).velocity(v.x * 1.4, v.y * 1.4, v.z * 1.4).life(rangeI(r, 5, 8))
                                .size(0.1f * s, 0).color(0xFFFFFF, color).glint(1).twinkle(0.4f).hot(1);
                        case DARK_CLOUD -> engine.particle(start).shape(FxParticle.Shape.SMOKE).velocity(v.x * 0.8, v.y * 0.8, v.z * 0.8)
                                .life(rangeI(r, 14, 22)).size(0.25f * s, 0.9f * s).color(0x1A0E26, 0x050208).alpha(0.75f)
                                .drag(0.9f).turbulence(0.004f).fade(0.1f, 0.5f);
                        default -> { }
                    }
                }
            }
            if (age == life) {
                switch (kind) {
                    case FLAME_SPRAY -> nova(engine, to, color, 0.45f * s);
                    case WATER_SPRAY -> {
                        frostNova(engine, to, color, 0.4f * s);
                        for (int i = 0; i < 40 * s; i++) {
                            Vec3 v = hemisphere(r, UP).scale(range(r, 0.08f, 0.25f));
                            engine.particle(to).velocity(v.x, v.y, v.z).life(rangeI(r, 12, 22)).size(0.07f, 0.03f)
                                    .color(hot, color).glint(0.6f).gravity(0.03f).collide(0.3f);
                        }
                    }
                    case WIND_GUST -> {
                        engine.decal(FxDecal.Kind.RING, to, dir).radius(0.2f, 2.2f * s).life(10).color(lighter(color, 0.4f)).param(0.1f);
                        engine.decal(FxDecal.Kind.WARP_RING, to, null).radius(0.2f, 2.6f * s).life(10).param(0.3f).fade(0, 0.3f);
                    }
                    case EARTH_SPIKES -> {
                        for (int i = 0; i < 30 * s; i++) {
                            Vec3 v = hemisphere(r, UP).scale(range(r, 0.1f, 0.35f));
                            engine.particle(to).shape(FxParticle.Shape.SPARK).velocity(v.x, v.y + 0.1, v.z).life(rangeI(r, 14, 24))
                                    .size(0.08f).color(darker(color, 0.1f), darker(color, 0.5f)).gravity(0.04f).collide(0.4f).stretch(1.3f);
                        }
                        engine.decal(FxDecal.Kind.RING, to.add(0, 0.05, 0), UP).radius(0.3f, 2.0f * s).life(12).color(color).param(0.15f);
                        FxScreen.shake(to, 0.25f * s, 10);
                    }
                    case LIGHT_RAY -> {
                        engine.decal(FxDecal.Kind.GLOW, to, null).radius(0.4f, 2.0f * s).life(10).color(0xFFFFFF).param(1.3f).fade(0, 0.2f);
                        engine.light(to, color, 9 * s, 20).intensity(2.5f).fadeFrom(0.2f);
                    }
                    case DARK_CLOUD -> smoke(engine, to, 24 * s, 1.0f * s, 0x1A0E26, 0x050208, 0.8f, s, 0);
                    default -> { }
                }
            }
            return age < life + 1;
        });
        FxSounds.play(from, 20, 0.9f, switch (kind) {
            case FLAME_SPRAY -> FxSounds::fireCast;
            case WATER_SPRAY, WIND_GUST -> FxSounds::frost;
            case EARTH_SPIKES -> FxSounds::nova;
            case LIGHT_RAY -> FxSounds::sparkle;
            default -> FxSounds::vortex;
        });
    }

    /** A rune glowing faintly on the ground, s seconds long: where a trap lies or a field holds. */
    static void sigil(FxEngine fx, Vec3 p, int color, float s) {
        int life = Math.max(10, (int) (s * 20));
        fx.decal(FxDecal.Kind.RUNE, p.add(0, 0.04, 0), UP).radius(1.1f).life(life).color(color).alpha(0.4f)
                .spin(0.01f).fade(0.1f, 0.8f);
        fx.light(p.add(0, 0.3, 0), color, 3, life).intensity(0.5f).fadeFrom(0.8f);
    }

    /** A little burst of twinkling glints. */
    static void sparkle(FxEngine fx, Vec3 p, int color, float s) {
        RandomSource r = fx.random();
        int light = lighter(color, 0.5f);
        fx.decal(FxDecal.Kind.GLOW, p, null).radius(0.3f * s, 1.4f * s).life(8).color(light).param(1).fade(0, 0.2f);
        for (int i = 0; i < 40 * s; i++) {
            Vec3 v = unit(r).scale(range(r, 0.01f, 0.05f) * s);
            fx.particle(p.add(unit(r).scale(0.6 * s * r.nextFloat()))).velocity(v.x, v.y, v.z).drag(0.94f)
                    .life(rangeI(r, 20, 40)).size(0.1f * s, 0).color(light, color).glint(1).twinkle(0.8f).hot(1);
        }
        fx.light(p, lighter(color, 0.4f), 6 * s, 12).intensity(1.3f);
        FxSounds.play(p, 16, 0.8f, FxSounds::sparkle);
    }

    /** Every effect in turn, in an arc in front of the player. */
    static void showcase(FxEngine fx, Entity player) {
        if (player == null) return;
        Vec3 origin = player.position();
        Vec3 look = player.getViewVector(1);
        Vec3 forward = new Vec3(look.x, 0, look.z);
        forward = forward.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : forward.normalize();
        Vec3 side = forward.cross(UP).normalize();
        Vec3 eye = player.getEyePosition();
        Vec3 hand = eye.add(forward.scale(0.6)).add(side.scale(0.35)).add(0, -0.25, 0);

        List<FxEffect> order = List.of(FxEffect.NOVA, FxEffect.ARCANE_BOLT, FxEffect.LIGHTNING, FxEffect.RUNE_CIRCLE,
                FxEffect.FROST_NOVA, FxEffect.BEAM, FxEffect.VORTEX, FxEffect.CHAIN_LIGHTNING, FxEffect.FIREBALL,
                FxEffect.SHIELD, FxEffect.HEAL, FxEffect.SPARKLE);
        int delay = 0;
        for (int i = 0; i < order.size(); i++) {
            FxEffect effect = order.get(i);
            double offset = (i % 3 - 1) * 5;
            Vec3 spot = ground(fx, origin.add(forward.scale(11)).add(side.scale(offset)));
            int start = delay;
            fx.later(start, () -> {
                switch (effect.aim()) {
                    case RAY -> play(fx, effect, hand, spot.add(0, 0.8, 0), effect.defaultColor(), 1, null);
                    case SELF -> play(fx, effect, spot, spot, effect.defaultColor(), 1, null);
                    default -> play(fx, effect, spot, spot, effect.defaultColor(), 1, null);
                }
            });
            delay += switch (effect) {
                case RUNE_CIRCLE, VORTEX, BEAM -> 100;
                case SHIELD -> 80;
                default -> 50;
            };
        }
    }

    // --- Helpers ---------------------------------------------------------------------------------

    static void smoke(FxEngine fx, Vec3 p, float count, float spread, int from, int to, float alpha, float s, float emissive) {
        RandomSource r = fx.random();
        for (int i = 0; i < count; i++) {
            Vec3 v = unit(r).scale(range(r, 0.04f, 0.14f) * s).add(0, 0.03, 0);
            fx.particle(p.add(unit(r).scale(spread))).shape(FxParticle.Shape.SMOKE).velocity(v.x, v.y, v.z)
                    .size(0.8f * s, 2.6f * s).color(from, to).alpha(alpha).life(rangeI(r, 45, 90)).drag(0.93f)
                    .gravity(-0.003f).turbulence(0.004f).fade(0.2f, 0.35f).emissive(emissive);
        }
    }

    /** Where the ray would really end, stopping at the first block. */
    static Vec3 impactPoint(FxEngine fx, Vec3 from, Vec3 to) {
        BlockHitResult hit = fx.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : to;
    }

    /** The ground below or around a point, so showcase effects stand on it. */
    static Vec3 ground(FxEngine fx, Vec3 p) {
        BlockHitResult hit = fx.level().clip(new ClipContext(p.add(0, 6, 0), p.add(0, -12, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : p;
    }

    static int lighter(int color, float t) {
        return ARGB.srgbLerp(t, color, 0xFFFFFF) & 0xFFFFFF;
    }

    static int darker(int color, float t) {
        return ARGB.srgbLerp(t, color, 0x000000) & 0xFFFFFF;
    }

    static float range(RandomSource r, float a, float b) {
        return a + r.nextFloat() * (b - a);
    }

    static int rangeI(RandomSource r, int a, int b) {
        return a + r.nextInt(b - a + 1);
    }

    static Vec3 unit(RandomSource r) {
        double theta = r.nextDouble() * Math.PI * 2, u = r.nextDouble() * 2 - 1;
        double s = Math.sqrt(1 - u * u);
        return new Vec3(s * Math.cos(theta), u, s * Math.sin(theta));
    }

    /** A random direction away from a surface, leaning towards its normal. */
    static Vec3 hemisphere(RandomSource r, Vec3 normal) {
        Vec3 v = unit(r);
        if (v.dot(normal) < 0) v = v.scale(-1);
        return v.add(normal.scale(0.6)).normalize();
    }

    /** Two unit vectors perpendicular to the direction and to each other. */
    static Vec3[] basis(Vec3 dir) {
        Vec3 helper = Math.abs(dir.y) < 0.9 ? UP : new Vec3(1, 0, 0);
        Vec3 a = dir.cross(helper).normalize();
        return new Vec3[] {a, dir.cross(a).normalize()};
    }
}
