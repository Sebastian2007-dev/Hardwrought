package de.ipnats.hardwrought.client.fx;

import de.ipnats.hardwrought.client.fx.sound.FxSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

import static de.ipnats.hardwrought.client.fx.FxPresets.darker;
import static de.ipnats.hardwrought.client.fx.FxPresets.lighter;
import static de.ipnats.hardwrought.client.fx.FxPresets.range;
import static de.ipnats.hardwrought.client.fx.FxPresets.rangeI;
import static de.ipnats.hardwrought.client.fx.FxPresets.unit;

/**
 * The looks of magic's combinations: a veil drawing shadow in, rings and clouds that pulse once a
 * second for as long as their field stands, a firestorm, a shockwave. A field's preset is played on
 * every pulse with its radius as the scale and lives a little longer than a pulse, so the field
 * reads as one standing thing.
 */
final class FxComboPresets {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    /** A field pulses every second; each pulse's look outlives it a little, so they overlap. */
    private static final int PULSE_LIFE = 26;

    private FxComboPresets() { }

    /** Shadow pours in from all round and into the body, which is gone when it clears. */
    static void veil(FxEngine fx, Vec3 pos, int color, float s, Entity follow) {
        RandomSource r = fx.random();
        Supplier<Vec3> center = () -> follow != null ? follow.position().add(0, follow.getBbHeight() * 0.5, 0) : pos.add(0, 1, 0);
        Vec3 c = center.get();
        fx.decal(FxDecal.Kind.RING, pos.add(0, 0.05, 0), UP).radius(2.4f * s, 0.2f * s).linear().life(14).color(color).alpha(0.7f).param(0.12f);
        fx.decal(FxDecal.Kind.WARP_LENS, c, null).radius(1.6f * s, 0.2f * s).linear().life(14).param(0.35f).fade(0, 0.4f);
        for (int i = 0; i < 70 * s; i++) {
            Vec3 at = c.add(unit(r).scale(range(r, 1.2f, 2.2f) * s));
            fx.particle(at).shape(FxParticle.Shape.SMOKE).attract(c.x, c.y, c.z, 0.02f, 0.12f).drag(0.88f)
                    .life(rangeI(r, 12, 20)).size(0.6f * s, 0.1f).color(0x1A0E26, 0x050208).alpha(0.8f).fade(0.1f, 0.5f);
        }
        // A wisp lingers where it stood a moment after.
        fx.later(14, () -> {
            for (int i = 0; i < 16 * s; i++) {
                Vec3 v = unit(r).scale(0.02).add(0, 0.01, 0);
                fx.particle(center.get().add(unit(r).scale(0.4))).shape(FxParticle.Shape.SMOKE).velocity(v.x, v.y, v.z)
                        .life(rangeI(r, 25, 40)).size(0.4f, 1.0f).color(0x2A1840, 0x0A0510).alpha(0.4f).fade(0.1f, 0.5f);
            }
        });
        if (follow == Minecraft.getInstance().getCameraEntity()) FxScreen.vignette(color, 0.55f, 1800);
    }

    /** Embers wheeling around a body for a while, and a ring of fire at its feet as the skin takes. */
    static void emberSkin(FxEngine fx, Vec3 pos, int color, float s, Entity follow) {
        int hot = lighter(color, 0.6f);
        int life = 60;
        Supplier<Vec3> base = () -> follow != null && follow.isAlive() ? follow.position() : pos;
        float height = follow != null ? follow.getBbHeight() : 1.8f;
        fx.decal(FxDecal.Kind.RING, base.get().add(0, 0.05, 0), UP).radius(0.3f * s, 1.8f * s).life(14).color(hot).param(0.15f);
        FxLight glow = fx.light(base.get().add(0, 1, 0), color, 6 * s, life).intensity(1.2f).flicker(0.3f).fadeFrom(0.6f);
        fx.task((engine, age) -> {
            RandomSource r = engine.random();
            Vec3 b = base.get();
            glow.moveTo(b.add(0, 1, 0));
            for (int k = 0; k < 2; k++) {
                double angle = age * 0.35 + k * Math.PI;
                double y = (Math.sin(age * 0.12 + k) * 0.5 + 0.5) * height;
                engine.particle(b.add(Math.cos(angle) * 0.7 * s, y, Math.sin(angle) * 0.7 * s)).velocity(0, 0.015, 0)
                        .life(rangeI(r, 14, 22)).size(0.09f, 0).color(hot, darker(color, 0.4f)).hot(1.1f).twinkle(0.4f);
            }
            return age < life;
        });
        FxSounds.vanilla(SoundEvents.FIRECHARGE_USE, pos, 0.5f, 0.8f);
    }

    /** One pulse of a healing ring: its edge glows on the ground, a rune turns within, and motes rise from it. */
    static void healRing(FxEngine fx, Vec3 pos, int color, float radius) {
        RandomSource r = fx.random();
        Vec3 c = pos.add(0, 0.04, 0);
        int light = lighter(color, 0.5f);
        fx.decal(FxDecal.Kind.RING, c, UP).radius(radius, radius * 1.04f).life(PULSE_LIFE).color(color).alpha(0.75f).param(0.05f)
                .fade(0.1f, 0.8f);
        fx.decal(FxDecal.Kind.RUNE, c.add(0, 0.01, 0), UP).radius(radius * 0.9f).life(PULSE_LIFE).color(light).alpha(0.22f)
                .spin(0.01f).fade(0.15f, 0.8f);
        fx.decal(FxDecal.Kind.RING, c, UP).radius(radius * 0.2f, radius).life(18).color(light).alpha(0.35f).param(0.08f).linear();
        fx.light(c.add(0, 0.8, 0), color, 4 + 2 * radius, PULSE_LIFE).intensity(0.9f).fadeFrom(0.7f);
        for (int i = 0; i < 9 * radius; i++) {
            double angle = r.nextDouble() * Math.PI * 2, dist = radius * Math.sqrt(r.nextDouble());
            fx.particle(c.add(Math.cos(angle) * dist, 0.05, Math.sin(angle) * dist)).velocity(0, range(r, 0.03f, 0.07f), 0)
                    .drag(0.98f).life(rangeI(r, 25, 45)).size(0.08f, 0).color(light, color).glint(0.6f).twinkle(0.5f).hot(0.9f);
        }
        FxSounds.play(c.add(0, 1, 0), 14, 0.35f, FxSounds::heal);
    }

    /** One pulse of a shroud: darkness welling up inside its edge, thick enough to hide in. */
    static void shroud(FxEngine fx, Vec3 pos, int color, float radius) {
        RandomSource r = fx.random();
        Vec3 c = pos.add(0, 0.04, 0);
        fx.decal(FxDecal.Kind.RING, c, UP).radius(radius, radius * 1.05f).life(PULSE_LIFE).color(darker(color, 0.3f)).alpha(0.6f)
                .param(0.06f).fade(0.1f, 0.8f);
        fx.decal(FxDecal.Kind.WARP_SWIRL, c.add(0, 1.2, 0), null).radius(radius * 0.8f).life(PULSE_LIFE).param(0.08f).fade(0.2f, 0.8f);
        for (int i = 0; i < 14 * radius; i++) {
            double angle = r.nextDouble() * Math.PI * 2, dist = radius * Math.sqrt(r.nextDouble());
            Vec3 at = c.add(Math.cos(angle) * dist, range(r, 0, 2.2f), Math.sin(angle) * dist);
            Vec3 v = unit(r).scale(0.01).add(0, 0.004, 0);
            fx.particle(at).shape(FxParticle.Shape.SMOKE).velocity(v.x, v.y, v.z).life(rangeI(r, 30, 50)).size(0.9f, 1.8f)
                    .color(0x1A0E26, 0x050208).alpha(0.55f).drag(0.95f).turbulence(0.002f).fade(0.25f, 0.4f);
        }
    }

    /** One pulse of a mire: the ground goes dark and soft, and bubbles rise and burst on it. */
    static void mire(FxEngine fx, Vec3 pos, int color, float radius) {
        RandomSource r = fx.random();
        Vec3 c = pos.add(0, 0.03, 0);
        fx.decal(FxDecal.Kind.GLOW, c, UP).radius(radius).life(PULSE_LIFE).color(darker(color, 0.5f)).alpha(0.55f).param(0.2f)
                .fade(0.15f, 0.8f);
        fx.decal(FxDecal.Kind.RING, c.add(0, 0.01, 0), UP).radius(radius, radius * 1.05f).life(PULSE_LIFE).color(color).alpha(0.5f)
                .param(0.06f).fade(0.1f, 0.8f);
        for (int i = 0; i < 5 * radius; i++) {
            double angle = r.nextDouble() * Math.PI * 2, dist = radius * Math.sqrt(r.nextDouble());
            Vec3 at = c.add(Math.cos(angle) * dist, 0.05, Math.sin(angle) * dist);
            int delay = rangeI(r, 0, 18);
            fx.later(delay, () -> {
                fx.particle(at).velocity(0, 0.02, 0).life(rangeI(r, 8, 14)).size(0.12f, 0.25f).color(lighter(color, 0.2f), color)
                        .alpha(0.8f).fade(0, 0.3f);
                fx.decal(FxDecal.Kind.RING, at.add(0, 0.02, 0), UP).radius(0.05f, 0.35f).life(8).color(color).alpha(0.6f).param(0.2f);
            });
        }
        FxSounds.vanilla(SoundEvents.MUD_STEP, c, 0.4f, 0.6f);
    }

    /** One pulse of steam: white vapour boiling up out of the ground, with the air shimmering over it. */
    static void steam(FxEngine fx, Vec3 pos, int color, float radius) {
        RandomSource r = fx.random();
        Vec3 c = pos.add(0, 0.05, 0);
        fx.decal(FxDecal.Kind.WARP_HEAT, c.add(0, 1.5, 0), null).radius(radius).life(PULSE_LIFE).param(0.12f).fade(0.2f, 0.7f);
        for (int i = 0; i < 16 * radius; i++) {
            double angle = r.nextDouble() * Math.PI * 2, dist = radius * Math.sqrt(r.nextDouble());
            Vec3 at = c.add(Math.cos(angle) * dist, range(r, 0, 0.6f), Math.sin(angle) * dist);
            fx.particle(at).shape(FxParticle.Shape.SMOKE).velocity(range(r, -0.01f, 0.01f), range(r, 0.03f, 0.07f), range(r, -0.01f, 0.01f))
                    .life(rangeI(r, 30, 50)).size(0.6f, 2.0f).color(0xFFFFFF, color).alpha(0.4f).drag(0.96f).turbulence(0.003f)
                    .fade(0.15f, 0.45f);
        }
        FxSounds.vanilla(SoundEvents.FIRE_EXTINGUISH, c, 0.35f, 0.7f);
    }

    /** Fire whirled up by wind: a blast, and flames spinning outward in a wheel around it. */
    static void firestorm(FxEngine fx, Vec3 p, int color, float s) {
        FxPresets.nova(fx, p, color, s);
        RandomSource r = fx.random();
        int hot = lighter(color, 0.7f);
        fx.decal(FxDecal.Kind.WARP_SWIRL, p.add(0, 1, 0), null).radius(0.5f * s, 6f * s).grow(10).life(40).param(0.35f).fade(0.1f, 0.6f);
        fx.task((engine, age) -> {
            if (age < 30) {
                for (int i = 0; i < 14 * s; i++) {
                    double angle = r.nextDouble() * Math.PI * 2, dist = range(r, 0.5f, 3.5f) * s;
                    Vec3 at = p.add(Math.cos(angle) * dist, range(r, 0, 2.5f) * s, Math.sin(angle) * dist);
                    // Tangent to the wheel, and out a little: a whirl, not a blast.
                    Vec3 v = new Vec3(-Math.sin(angle), 0.03, Math.cos(angle)).scale(0.25 * s).add(Math.cos(angle) * 0.06, 0, Math.sin(angle) * 0.06);
                    engine.particle(at).velocity(v.x, v.y, v.z).life(rangeI(r, 10, 18)).size(range(r, 0.4f, 0.8f) * s, 0.1f)
                            .color(hot, darker(color, 0.5f)).hot(1).drag(0.9f).fade(0, 0.3f);
                }
            }
            return age < 30;
        });
        FxSounds.vanilla(SoundEvents.BLAZE_SHOOT, p, 0.8f, 0.6f);
    }

    /** A wave running out through the ground, throwing dust and stones up as it passes. */
    static void shockwave(FxEngine fx, Vec3 p, int color, float s) {
        RandomSource r = fx.random();
        Vec3 c = p.add(0, 0.05, 0);
        float reach = 7f * s;
        fx.decal(FxDecal.Kind.RING, c, UP).radius(0.3f * s, reach).life(16).color(lighter(color, 0.3f)).param(0.2f).fade(0, 0.4f);
        fx.decal(FxDecal.Kind.WARP_RING, c.add(0, 0.2, 0), UP).radius(0.3f * s, reach).life(16).param(0.45f).fade(0, 0.3f);
        fx.task((engine, age) -> {
            if (age < 16) {
                float front = 0.3f * s + (reach - 0.3f * s) * age / 16f;
                for (int i = 0; i < 18 * s; i++) {
                    double angle = r.nextDouble() * Math.PI * 2;
                    Vec3 at = c.add(Math.cos(angle) * front, 0.1, Math.sin(angle) * front);
                    if (r.nextFloat() < 0.4f) {
                        engine.particle(at).shape(FxParticle.Shape.SPARK).velocity(range(r, -0.05f, 0.05f), range(r, 0.25f, 0.5f), range(r, -0.05f, 0.05f))
                                .life(rangeI(r, 16, 26)).size(0.08f).color(color, darker(color, 0.5f)).gravity(0.04f).collide(0.3f);
                    } else {
                        engine.particle(at).shape(FxParticle.Shape.SMOKE).velocity(0, 0.03, 0).life(rangeI(r, 30, 50)).size(0.6f, 1.6f)
                                .color(lighter(color, 0.3f), darker(color, 0.3f)).alpha(0.45f).drag(0.94f).fade(0.1f, 0.4f);
                    }
                }
            }
            return age < 16;
        });
        FxScreen.shake(c, 0.8f * Math.min(1.2f, s), 24 * s);
        FxSounds.playDistant(c, 56 * s, 1, FxSounds::nova);
        FxSounds.vanilla(SoundEvents.GENERIC_EXPLODE.value(), c, 0.4f * s, 0.5f);
    }
}
