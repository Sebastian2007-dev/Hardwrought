package de.ipnats.hardwrought.client.ritual;

import de.ipnats.hardwrought.client.fx.FxDecal;
import de.ipnats.hardwrought.client.fx.FxEngine;
import de.ipnats.hardwrought.client.fx.FxParticle;
import de.ipnats.hardwrought.client.fx.FxScreen;
import de.ipnats.hardwrought.fx.FxEffect;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * What is seen of the ritual at the end of the game (see {@code Ritual}): pedestals kindling and beaming
 * their light to the altar, rings of light rising over it, a pillar of light into the sky, light streaming
 * in from all round, the offerings burning up, and the last great wave of light.
 */
public final class RitualFx {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int PALE = 0xFFFFF6E0, WHITE = 0xFFFFFFFF;
    private static final RandomSource RANDOM = RandomSource.create();

    private RitualFx() { }

    public static void play(FxEngine fx, FxEffect effect, Vec3 pos, Vec3 target, int color, float s) {
        int c = 0xFF000000 | color;
        switch (effect) {
            case RITUAL_IGNITE -> {
                fx.decal(FxDecal.Kind.HEX_SIGIL, pos.add(0, -0.35, 0), UP).radius(1.2f * s).color(c).alpha(0.9f).spin(0.05f).reveal(10).life(60)
                        .fade(0.05f, 0.7f);
                fx.decal(FxDecal.Kind.GLOW, pos, null).radius(0.3f * s, 2.2f * s).life(14).color(PALE).fade(0, 0.3f);
                fx.light(pos, c, 6 * s, 30).intensity(2).fadeFrom(0.3f);
                for (int i = 0; i < 40 * s; i++) {
                    Vec3 v = new Vec3(RANDOM.nextGaussian() * 0.08, 0.1 + RANDOM.nextDouble() * 0.2, RANDOM.nextGaussian() * 0.08);
                    fx.particle(pos).shape(FxParticle.Shape.SPARK).velocity(v.x, v.y, v.z).life(24 + RANDOM.nextInt(16)).size(0.07f, 0)
                            .color(PALE, c).drag(0.95f).hot(1.0f);
                }
            }
            case RITUAL_BEAM -> {
                int life = Math.max(20, (int) s);
                fx.beam(pos, target).width(0.16f).life(life).color(c).alpha(0.8f).energy(0.4f, 5).fade(0.03f, 0.95f);
                fx.beam(pos, target).width(0.05f).life(life).color(WHITE).energy(0.2f, 8).fade(0.03f, 0.95f);
                fx.light(pos, c, 5, life).intensity(1.2f).fadeFrom(0.95f);
                fx.task((engine, age) -> {
                    if (age % 3 == 0) {
                        Vec3 v = target.subtract(pos).scale(1 / 20.0);
                        engine.particle(pos).velocity(v.x, v.y, v.z).life(20).size(0.14f, 0.05f).color(PALE, c).hot(1.0f).drag(1.0f);
                    }
                    return age < life;
                });
            }
            case RITUAL_RING -> {
                FxDecal ring = fx.decal(FxDecal.Kind.HEX_SIGIL, pos, UP).radius(s).color(c).alpha(0.85f).spin(0.08f).reveal(8).life(90).fade(0.05f, 0.6f);
                FxDecal wave = fx.decal(FxDecal.Kind.HEX_WAVE, pos, UP).radius(s * 0.5f, s * 1.3f).life(30).color(PALE).alpha(0.6f).fade(0, 0.6f);
                fx.task((engine, age) -> {
                    ring.center(pos.add(0, age * 0.12, 0));
                    return age < 90 && ring.alive();
                });
            }
            case RITUAL_SKYBEAM -> {
                int life = Math.max(20, (int) s);
                fx.beam(pos, target).width(1.8f).life(life).color(c).alpha(0.75f).energy(0.8f, 4).fade(0.02f, 0.95f);
                fx.beam(pos, target).width(0.6f).life(life).color(WHITE).energy(0.3f, 7).fade(0.02f, 0.95f);
                for (int i = 0; i < 4; i++) fx.light(pos.add(0, 8 + i * 12, 0), c, 18, life).intensity(1.6f).fadeFrom(0.95f);
                fx.decal(FxDecal.Kind.HEX_WAVE, pos.add(0, -3, 0), UP).radius(1, 40).life(30).color(c).fade(0, 0.6f);
                FxScreen.flash(pos, PALE, 0.5f, 600, 120);
                FxScreen.shake(pos, 0.6f, 120);
                fx.task((engine, age) -> {
                    // Light spiralling up the pillar.
                    for (int k = 0; k < 3; k++) {
                        double a = age * 0.25 + k * Math.PI * 2 / 3;
                        engine.particle(pos.add(Math.cos(a) * 1.4, 0, Math.sin(a) * 1.4)).velocity(-Math.sin(a) * 0.05, 0.9, Math.cos(a) * 0.05)
                                .life(40).size(0.18f, 0.05f).color(PALE, c).hot(1.0f).drag(1.0f);
                    }
                    return age < life;
                });
            }
            case RITUAL_CONVERGE -> {
                int life = Math.max(20, (int) s);
                fx.task((engine, age) -> {
                    for (int i = 0; i < 6; i++) {
                        Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.6, RANDOM.nextGaussian()).normalize();
                        Vec3 from = pos.add(dir.scale(18 + RANDOM.nextDouble() * 12));
                        Vec3 v = pos.subtract(from).scale(1 / 24.0);
                        engine.particle(from).shape(FxParticle.Shape.SPARK).velocity(v.x, v.y, v.z).life(24).size(0.1f, 0.03f).color(c, PALE)
                                .stretch(2f).hot(1.0f).drag(1.0f);
                    }
                    if (age % 20 == 0) FxScreen.shake(pos, 0.1f + 0.3f * age / life, 60);
                    return age < life;
                });
                fx.light(pos, c, 20, life).intensity(1.5f).flicker(0.3f).fadeFrom(0.9f);
            }
            case RITUAL_DISSOLVE -> {
                fx.decal(FxDecal.Kind.GLOW, pos, null).radius(0.4f, 3).life(12).color(PALE).fade(0, 0.3f);
                for (int i = 0; i < 40; i++) {
                    Vec3 v = target.subtract(pos).scale(1 / (18.0 + RANDOM.nextInt(10))).add(RANDOM.nextGaussian() * 0.03, RANDOM.nextGaussian() * 0.03,
                            RANDOM.nextGaussian() * 0.03);
                    fx.particle(pos).velocity(v.x, v.y, v.z).life(24).size(0.16f, 0.04f).color(PALE, c).hot(1.0f).drag(1.0f);
                }
            }
            case RITUAL_FINALE -> {
                fx.decal(FxDecal.Kind.GLOW, pos, null).radius(2, 90).life(30).color(WHITE).fade(0, 0.25f);
                fx.decal(FxDecal.Kind.HEX_WAVE, pos.add(0, -3.5, 0), UP).radius(2, 180).life(60).color(c).fade(0, 0.6f);
                fx.decal(FxDecal.Kind.HEX_WAVE, pos, UP).radius(2, 120).life(45).color(PALE).alpha(0.7f).fade(0, 0.6f);
                fx.decal(FxDecal.Kind.WARP_RING, pos, UP).radius(2, 100).life(40).param(1.0f);
                fx.decal(FxDecal.Kind.HEX_SIGIL, pos.add(0, -3.5, 0), UP).radius(14).color(c).alpha(0.9f).spin(0.03f).reveal(10).life(200)
                        .fade(0.05f, 0.7f);
                fx.light(pos, PALE, 140, 80).intensity(3.5f).fadeFrom(0.2f);
                FxScreen.flash(pos, WHITE, 1.0f, 1500, 300);
                FxScreen.vignette(pos, c, 0.6f, 4000, 300);
                FxScreen.shake(pos, 1.0f, 300);
                FxScreen.aberration(pos, 1.5f, 1500, 300);
                FxScreen.saturation(pos, 0.8f, 5000, 300);
                for (int i = 0; i < 700; i++) {
                    Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.8 + 0.3, RANDOM.nextGaussian()).normalize();
                    double speed = 0.6 + RANDOM.nextDouble() * 2.2;
                    fx.particle(pos).shape(FxParticle.Shape.SPARK).velocity(dir.x * speed, dir.y * speed, dir.z * speed).life(40 + RANDOM.nextInt(40))
                            .size(0.14f, 0).color(i % 3 == 0 ? WHITE : PALE, c).drag(0.96f).stretch(2f).hot(1.0f).gravity(0.004f);
                }
            }
            case RITUAL_WARD -> ward(fx, pos, target.subtract(pos), c);
            case RITUAL_APOTHEOSIS -> apotheosis(fx, pos, c);
            default -> { }
        }
    }

    /** The ward at the chamber's wall or under its roof, met by someone who would leave: a sigil flaring there, rippling out. */
    private static void ward(FxEngine fx, Vec3 pos, Vec3 facing, int c) {
        fx.decal(FxDecal.Kind.HEX_SIGIL, pos, facing).radius(1.8f).color(c).alpha(0.9f).spin(0.12f).reveal(4).life(26).fade(0, 0.5f);
        fx.decal(FxDecal.Kind.HEX_WAVE, pos, facing).radius(0.5f, 4.5f).life(18).color(PALE).alpha(0.7f).fade(0, 0.4f);
        fx.decal(FxDecal.Kind.WARP_RING, pos, facing).radius(0.5f, 3.5f).life(16).param(0.5f);
        fx.light(pos, c, 6, 16).intensity(1.5f).fadeFrom(0.2f);
        for (int i = 0; i < 24; i++) {
            Vec3 v = facing.scale(0.1 + RANDOM.nextDouble() * 0.1).add(RANDOM.nextGaussian() * 0.08, RANDOM.nextGaussian() * 0.08, RANDOM.nextGaussian() * 0.08);
            fx.particle(pos).shape(FxParticle.Shape.SPARK).velocity(v.x, v.y, v.z).life(14 + RANDOM.nextInt(10)).size(0.06f, 0).color(PALE, c)
                    .drag(0.9f).stretch(1.5f).hot(1.0f);
        }
    }

    /**
     * The height of the ascension: light drawn into the risen player from all round, then breaking out of
     * them — a white-gold blast, a wave of gold out over the land and the sky bent with it, a pillar of light
     * up from them, a crown of rays turning round them and a halo over their head, and gold raining down
     * long after.
     */
    private static void apotheosis(FxEngine fx, Vec3 pos, int c) {
        int gold = 0xFFFFD24A, white = WHITE;
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.world.entity.player.Player risen = client.level == null ? null
                : client.level.getNearestPlayer(pos.x, pos.y, pos.z, 4, false);
        FxDecal glow = fx.decal(FxDecal.Kind.GLOW, pos, null).radius(0.2f, 2.5f).life(14).color(PALE).fade(0.5f, 0.9f);
        fx.decal(FxDecal.Kind.WARP_LENS, pos, null).radius(6, 1).life(14).param(1.0f);
        fx.task((engine, age) -> {
            Vec3 at = risen != null ? risen.position().add(0, 1, 0) : pos;
            if (age < 14) {
                // Drawn in: light from all round streaming into them.
                glow.center(at);
                for (int i = 0; i < 18; i++) {
                    Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
                    Vec3 from = at.add(dir.scale(7 + RANDOM.nextDouble() * 5));
                    Vec3 v = at.subtract(from).scale(1 / 10.0);
                    engine.particle(from).shape(FxParticle.Shape.SPARK).velocity(v.x, v.y, v.z).life(10).size(0.09f, 0.02f).color(gold, white)
                            .stretch(2f).hot(1.0f).drag(1.0f);
                }
                if (age == 4) FxScreen.vignette(at, gold, 0.5f, 900, 200);
                return true;
            }
            if (age == 14) burst(engine, at, c, gold);
            return false;
        });
    }

    /** The light breaking out of them. */
    private static void burst(FxEngine fx, Vec3 at, int c, int gold) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.world.entity.player.Player risen = client.level == null ? null
                : client.level.getNearestPlayer(at.x, at.y, at.z, 4, false);
        FxScreen.flash(at, 0xFFF2C0, 0.7f, 900, 400);
        FxScreen.shake(at, 1.2f, 400);
        FxScreen.aberration(at, 2.0f, 1600, 400);
        FxScreen.saturation(at, 1.0f, 6000, 400);
        fx.decal(FxDecal.Kind.GLOW, at, null).radius(1, 30).life(26).color(WHITE).fade(0, 0.3f);
        fx.decal(FxDecal.Kind.SPHERE, at, null).radius(0.5f, 16).life(22).color(gold).alpha(0.55f).fade(0, 0.3f);
        // The wave out over the land, flat, twice, and the sky bent with it.
        fx.decal(FxDecal.Kind.HEX_WAVE, at, UP).radius(1, 240).life(80).color(gold).fade(0, 0.6f);
        fx.decal(FxDecal.Kind.HEX_WAVE, at, UP).radius(1, 150).life(55).color(PALE).alpha(0.7f).fade(0, 0.6f);
        fx.decal(FxDecal.Kind.WARP_RING, at, UP).radius(2, 120).life(45).param(1.0f);
        fx.decal(FxDecal.Kind.WARP_RING, at, null).radius(1, 40).life(25).param(1.0f);
        fx.light(at, gold, 120, 120).intensity(4.0f).fadeFrom(0.15f);
        // A pillar of light up from them into the sky.
        fx.beam(at, at.add(0, 260, 0)).width(1.4f).life(90).color(gold).alpha(0.85f).energy(0.6f, 5).fade(0.02f, 0.6f);
        fx.beam(at, at.add(0, 260, 0)).width(0.45f).life(90).color(WHITE).energy(0.2f, 9).fade(0.02f, 0.6f);
        // The halo over their head, and a great sigil of gold under their feet.
        FxDecal halo = fx.decal(FxDecal.Kind.HEX_SIGIL, at.add(0, 1.3, 0), UP).radius(0.6f).color(gold).alpha(0.95f).spin(0.06f).reveal(8).life(70)
                .fade(0.05f, 0.8f);
        FxDecal sigil = fx.decal(FxDecal.Kind.HEX_SIGIL, at.add(0, -1.4, 0), UP).radius(9).color(gold).alpha(0.8f).spin(-0.02f).reveal(14).life(120)
                .fade(0.05f, 0.7f);
        // The crown of rays: twelve, turning round them, opening out and fading.
        int rays = 12;
        com.google.common.collect.ImmutableList.Builder<de.ipnats.hardwrought.client.fx.FxBeam> built = com.google.common.collect.ImmutableList.builder();
        for (int i = 0; i < rays; i++) {
            built.add(fx.beam(at, at).width(i % 2 == 0 ? 0.22f : 0.12f).life(110).color(i % 2 == 0 ? gold : PALE).alpha(0.85f).energy(0.3f, 6)
                    .fade(0.05f, 0.55f));
        }
        var crown = built.build();
        fx.task((engine, age) -> {
            Vec3 center = risen != null ? risen.position().add(0, 1, 0) : at;
            halo.center(center.add(0, 1.3, 0));
            sigil.center(center.add(0, -1.4, 0));
            double open = 1 - Math.exp(-age / 10.0);
            for (int i = 0; i < rays; i++) {
                double a = i * Math.PI * 2 / rays + age * 0.02;
                // The rays leaning up a little, a crown rather than a wheel.
                double tilt = (i % 2 == 0 ? 0.25 : 0.55);
                Vec3 dir = new Vec3(Math.cos(a), tilt, Math.sin(a)).normalize();
                crown.get(i).endpoints(center.add(dir.scale(0.8)), center.add(dir.scale(0.8 + (i % 2 == 0 ? 16 : 10) * open)));
            }
            // Gold raining down round them, long after.
            if (age < 200 && age % 2 == 0) {
                for (int k = 0; k < 6; k++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2, r = 2 + RANDOM.nextDouble() * 22;
                    engine.particle(center.add(Math.cos(a) * r, 6 + RANDOM.nextDouble() * 8, Math.sin(a) * r)).velocity(0, -0.05, 0)
                            .life(80 + RANDOM.nextInt(40)).size(0.08f, 0.02f).color(gold, PALE).hot(1.0f).drag(1.0f);
                }
            }
            return age < 240;
        });
        for (int i = 0; i < 900; i++) {
            Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.7, RANDOM.nextGaussian()).normalize();
            double speed = 0.5 + RANDOM.nextDouble() * 2.6;
            fx.particle(at).shape(FxParticle.Shape.SPARK).velocity(dir.x * speed, dir.y * speed, dir.z * speed).life(40 + RANDOM.nextInt(50))
                    .size(0.15f, 0).color(i % 3 == 0 ? WHITE : gold, c).drag(0.95f).stretch(2.5f).hot(1.0f).gravity(0.003f);
        }
    }
}
