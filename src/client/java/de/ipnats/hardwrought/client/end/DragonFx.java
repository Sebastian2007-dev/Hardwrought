package de.ipnats.hardwrought.client.end;

import de.ipnats.hardwrought.client.fx.FxClient;
import de.ipnats.hardwrought.client.fx.FxDecal;
import de.ipnats.hardwrought.client.fx.FxEngine;
import de.ipnats.hardwrought.client.fx.FxLight;
import de.ipnats.hardwrought.client.fx.FxParticle;
import de.ipnats.hardwrought.client.fx.FxScreen;
import de.ipnats.hardwrought.end.dragon.CrystalWards;
import de.ipnats.hardwrought.end.dragon.VoidBolt;
import de.ipnats.hardwrought.end.dragon.VoidDragon;
import de.ipnats.hardwrought.fx.FxEffect;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Everything seen of the End's dragon beyond its own model (see {@code VoidDragon}): void fire trailing
 * from it, its bolts, the clouds it leaves in the air, the wind of its wings, where it marks a strike,
 * the ground shaking under it, the wards round its crystals, the sky torn open while it rages, and its
 * death.
 */
public final class DragonFx {
    static final int VIOLET = 0xFFB04CFF, DEEP = 0xFF2A0838, MAGENTA = 0xFFFF3CC8, PALE = 0xFFF2DCFF, BLACK = 0xFF07020C, BLOOD = 0xFFFF2A6A;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final RandomSource RANDOM = RandomSource.create();

    /** What follows a bolt, a crystal or the torn sky, by entity id. */
    private static final Map<Integer, FxDecal> BOLTS = new HashMap<>(), WARDS = new HashMap<>();
    private static final Map<Integer, FxLight> LIGHTS = new HashMap<>();
    private static FxDecal tear, tearGlow;
    private static FxLight tearLight;

    private DragonFx() { }

    public static void initialize() {
        EntityRendererRegistry.register(VoidBolt.TYPE, NoopRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(DragonFx::tick);
    }

    // ------------------------------------------------------------------------------------ every tick

    private static void tick(Minecraft client) {
        ClientLevel level = client.level;
        FxEngine fx = FxClient.engine();
        if (level == null || client.player == null || fx == null || client.isPaused()) return;
        if (level.dimension() != Level.END) {
            clearTear();
            de.ipnats.hardwrought.client.fx.FxPost.voidTear(0);
            return;
        }
        AABB near = client.player.getBoundingBox().inflate(256);
        boolean raging = false;
        float torn = 0;
        for (EnderDragon dragon : level.getEntitiesOfClass(EnderDragon.class, near)) {
            if (dragon.isAlive()) {
                aura(fx, dragon);
                raging |= VoidDragon.share(dragon) < VoidDragon.RAGE_AT;
                // The void torn open: more of it the nearer the dragon is to its end.
                if (VoidDragon.share(dragon) < VoidDragon.RAGE_AT) torn = Math.max(torn, 0.65f + 0.35f * (1 - VoidDragon.share(dragon) / VoidDragon.RAGE_AT));
            } else {
                dying(fx, dragon);
                // Dying, the void tears wide open round it.
                torn = 1;
            }
        }
        for (VoidBolt bolt : level.getEntitiesOfClass(VoidBolt.class, near, Entity::isAlive)) bolt(fx, bolt);
        for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, near, Entity::isAlive)) ward(fx, crystal);
        de.ipnats.hardwrought.client.fx.FxPost.voidTear(torn);
        if (raging) tornSky(fx, client);
        else clearTear();
        prune(level);
    }

    private static void prune(ClientLevel level) {
        for (Map<Integer, FxDecal> map : java.util.List.of(BOLTS, WARDS)) {
            map.entrySet().removeIf(entry -> {
                Entity entity = level.getEntity(entry.getKey());
                boolean gone = entity == null || !entity.isAlive() || !entry.getValue().alive()
                        || entity instanceof EndCrystal crystal && CrystalWards.ward(crystal) <= 0;
                if (gone) entry.getValue().kill();
                return gone;
            });
        }
        LIGHTS.entrySet().removeIf(entry -> {
            boolean gone = level.getEntity(entry.getKey()) == null;
            if (gone) entry.getValue().kill();
            return gone;
        });
    }

    /** Void fire trailing from its body and wings: violet, magenta and sparking while it rages, paler raised again. */
    private static void aura(FxEngine fx, EnderDragon dragon) {
        boolean rage = VoidDragon.share(dragon) < VoidDragon.RAGE_AT;
        boolean risen = dragon.entityTags().contains(VoidDragon.RISEN);
        EnderDragonPart[] parts = dragon.getSubEntities();
        for (int i = 0; i < (rage ? 5 : 3); i++) {
            EnderDragonPart part = parts[RANDOM.nextInt(parts.length)];
            Vec3 at = part.position().add(RANDOM.nextGaussian() * part.getBbWidth() * 0.4, RANDOM.nextDouble() * part.getBbHeight(),
                    RANDOM.nextGaussian() * part.getBbWidth() * 0.4);
            fx.particle(at).shape(FxParticle.Shape.SMOKE).velocity(RANDOM.nextGaussian() * 0.02, 0.02, RANDOM.nextGaussian() * 0.02)
                    .life(30 + RANDOM.nextInt(20)).size(0.5f, 1.4f).color(rage ? MAGENTA : VIOLET, BLACK).alpha(0.55f).drag(0.94f)
                    .turbulence(0.004f).fade(0.05f, 0.4f);
        }
        if (rage || risen || RANDOM.nextInt(3) == 0) {
            EnderDragonPart part = parts[RANDOM.nextInt(parts.length)];
            fx.particle(part.position().add(0, part.getBbHeight() * 0.5, 0)).shape(FxParticle.Shape.SPARK)
                    .velocity(RANDOM.nextGaussian() * 0.06, RANDOM.nextGaussian() * 0.06, RANDOM.nextGaussian() * 0.06).life(18).size(0.09f, 0)
                    .color(risen ? PALE : MAGENTA, VIOLET).hot(1.0f).drag(0.95f);
        }
        // Its eyes and its maw light what is near, magenta when it rages.
        Vec3 head = dragon.head.position().add(0, dragon.head.getBbHeight() * 0.5, 0);
        FxLight light = LIGHTS.computeIfAbsent(dragon.getId(), id -> fx.light(head, VIOLET, 12, 40).intensity(1.6f).flicker(0.25f));
        light.moveTo(head).color(rage ? MAGENTA : VIOLET).extend(5);
    }

    /**
     * Dying, the void takes it back: tendrils of void lightning clawing at it from all round, black ash
     * and smoke drawn into it, its body shedding embers that fall away, rings closing in on it ever faster
     * while the sky tears wide — and at the end everything falls into one point, and bursts outwards in a
     * wave of magenta and light that runs over the whole island.
     */
    private static void dying(FxEngine fx, EnderDragon dragon) {
        int t = dragon.dragonDeathTime;
        Vec3 middle = dragon.position().add(0, 2, 0);
        float progress = Math.min(1, t / 185f);
        if (t < 185) {
            // Tendrils of void clawing at it.
            if (RANDOM.nextFloat() < 0.2f + progress * 0.7f) {
                Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.6, RANDOM.nextGaussian()).normalize();
                Vec3 from = middle.add(dir.scale(10 + RANDOM.nextDouble() * 22));
                fx.beam(from, middle).lightning(1.4f, 2, 2).width(0.1f + 0.25f * progress).life(5 + RANDOM.nextInt(4))
                        .color(RANDOM.nextInt(3) == 0 ? PALE : MAGENTA).alpha(0.85f).fade(0, 0.5f);
            }
            // Ash and smoke drawn in.
            for (int i = 0; i < 3 + progress * 10; i++) {
                Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
                Vec3 from = middle.add(dir.scale(6 + RANDOM.nextDouble() * 10));
                Vec3 v = middle.subtract(from).scale(1 / 14.0);
                fx.particle(from).shape(FxParticle.Shape.SMOKE).velocity(v.x, v.y, v.z).life(14).size(1.0f, 0.2f).color(BLACK, DEEP)
                        .alpha(0.75f).drag(1.0f);
            }
            // Its body shedding embers that fall into the void.
            EnderDragonPart[] parts = dragon.getSubEntities();
            for (int i = 0; i < 2 + progress * 4; i++) {
                EnderDragonPart part = parts[RANDOM.nextInt(parts.length)];
                fx.particle(part.position().add(RANDOM.nextGaussian() * 0.6, part.getBbHeight() * RANDOM.nextDouble(), RANDOM.nextGaussian() * 0.6))
                        .shape(FxParticle.Shape.SPARK).velocity(RANDOM.nextGaussian() * 0.03, -0.05, RANDOM.nextGaussian() * 0.03).life(40)
                        .size(0.08f, 0).color(MAGENTA, BLACK).gravity(0.01f).hot(1.0f);
            }
            // Rings closing in on it, ever faster.
            int every = Math.max(6, 28 - (int) (progress * 22));
            if (t % every == 0) {
                Vec3 axis = new Vec3(RANDOM.nextGaussian(), 1.5 + RANDOM.nextDouble(), RANDOM.nextGaussian()).normalize();
                fx.decal(FxDecal.Kind.HEX_WAVE, middle, axis).radius(18 + 14 * progress, 0.5f).life(every + 4).color(MAGENTA).alpha(0.8f)
                        .fade(0.1f, 0.9f);
                fx.decal(FxDecal.Kind.WARP_RING, middle, axis).radius(14, 0.5f).life(every).param(0.6f + 0.4f * progress);
                FxScreen.shake(middle, 0.15f + 0.5f * progress, 160);
            }
        } else if (t == 185) {
            // Everything falls into one point.
            fx.decal(FxDecal.Kind.GLOW, middle, null).radius(16, 0.3f).life(8).color(PALE).fade(0, 0.9f);
            fx.decal(FxDecal.Kind.WARP_RING, middle, null).radius(20, 0.5f).life(8).param(1.0f);
            fx.light(middle, PALE, 30, 10).intensity(3);
            FxScreen.aberration(middle, 1.5f, 500, 200);
        } else if (t == 193) {
            // And bursts.
            fx.decal(FxDecal.Kind.GLOW, middle, null).radius(2, 70).life(22).color(PALE).fade(0, 0.25f);
            fx.decal(FxDecal.Kind.HEX_WAVE, middle, UP).radius(2, 130).life(45).color(MAGENTA).fade(0, 0.6f);
            fx.decal(FxDecal.Kind.HEX_WAVE, middle, new Vec3(1, 0, 0)).radius(2, 90).life(35).color(VIOLET).fade(0, 0.6f);
            fx.decal(FxDecal.Kind.WARP_RING, middle, UP).radius(2, 80).life(30).param(1.0f);
            fx.light(middle, MAGENTA, 120, 50).intensity(3.5f).fadeFrom(0.1f);
            FxScreen.flash(middle, PALE, 0.9f, 900, 250);
            FxScreen.vignette(middle, MAGENTA, 0.8f, 2500, 250);
            FxScreen.shake(middle, 1.0f, 250);
            FxScreen.aberration(middle, 2.0f, 1500, 250);
            for (int i = 0; i < 400; i++) {
                Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
                double speed = 0.6 + RANDOM.nextDouble() * 1.8;
                fx.particle(middle).shape(FxParticle.Shape.SPARK).velocity(dir.x * speed, dir.y * speed, dir.z * speed).life(30 + RANDOM.nextInt(30))
                        .size(0.12f, 0).color(i % 3 == 0 ? PALE : MAGENTA, VIOLET).drag(0.95f).stretch(2f).hot(1.0f);
            }
            for (int i = 0; i < 80; i++) {
                Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.4, RANDOM.nextGaussian()).normalize();
                fx.particle(middle.add(dir.scale(3))).shape(FxParticle.Shape.SMOKE).velocity(dir.x * 0.5, dir.y * 0.5, dir.z * 0.5).life(60)
                        .size(1.5f, 5f).color(DEEP, BLACK).alpha(0.6f).drag(0.94f).fade(0.05f, 0.4f);
            }
        }
    }

    /** A bolt: a black core in a magenta shell, smoke trailing behind it. */
    private static void bolt(FxEngine fx, VoidBolt bolt) {
        Vec3 at = bolt.position().add(0, bolt.getBbHeight() / 2, 0);
        FxDecal shell = BOLTS.computeIfAbsent(bolt.getId(), id -> fx.decal(FxDecal.Kind.HEX_WARD, at, null).radius(0.55f).color(MAGENTA).alpha(0.9f)
                .param(0.2f).life(10));
        shell.center(at).keep(3);
        fx.particle(at).life(2).size(0.5f, 0.45f).color(PALE).hot(1.0f).alpha(0.8f);
        Vec3 back = bolt.getDeltaMovement().scale(-0.4);
        fx.particle(at.add(back)).shape(FxParticle.Shape.SMOKE).velocity(back.x * 0.1, back.y * 0.1, back.z * 0.1).life(20).size(0.35f, 0.9f)
                .color(DEEP, BLACK).alpha(0.7f).drag(0.9f);
        if (RANDOM.nextInt(2) == 0) {
            fx.particle(at).shape(FxParticle.Shape.SPARK).velocity(RANDOM.nextGaussian() * 0.05, RANDOM.nextGaussian() * 0.05, RANDOM.nextGaussian() * 0.05)
                    .life(10).size(0.06f, 0).color(MAGENTA, VIOLET).hot(1.0f);
        }
    }

    /** A crystal's ward: a shell of light round it, cracking as it is worn down. */
    private static void ward(FxEngine fx, EndCrystal crystal) {
        float ward = CrystalWards.ward(crystal);
        if (ward <= 0) return;
        Vec3 at = crystal.position().add(0, 1.0, 0);
        float cracked = 1 - Math.min(1, ward / CrystalWards.WARD);
        FxDecal shell = WARDS.computeIfAbsent(crystal.getId(), id -> fx.decal(FxDecal.Kind.HEX_WARD, at, null).radius(0.3f, 2.2f).grow(12)
                .color(MAGENTA).alpha(0.75f).fade(0.05f, 0.98f).life(40));
        shell.center(at).param(cracked).alpha((float) (0.75 * Math.clamp((camera().distanceTo(at) - 2.2) / 3, 0, 1))).keep(5);
        if (RANDOM.nextInt(4) == 0) {
            Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
            fx.particle(at.add(dir.scale(2.2))).velocity(-dir.x * 0.03, -dir.y * 0.03, -dir.z * 0.03).life(20).size(0.08f, 0)
                    .color(PALE, MAGENTA).hot(0.9f);
        }
    }

    /**
     * Raging: a rift torn high in the sky over the island, dark at its heart, its light falling on the
     * fragments below, now and then a crack of lightning out of it, and the view darkened at its edges.
     */
    private static void tornSky(FxEngine fx, Minecraft client) {
        Vec3 at = new Vec3(0, 170, 0);
        if (tear == null || !tear.alive()) {
            tear = fx.decal(FxDecal.Kind.HEX_RIFT, at, null).radius(26).color(VIOLET).alpha(0.7f).life(40).fade(0.1f, 1);
            tearGlow = fx.decal(FxDecal.Kind.GLOW, at, null).radius(4, 14).grow(40).color(DEEP).alpha(0.35f).life(40);
            tearLight = fx.light(at.add(0, -40, 0), MAGENTA, 120, 40).intensity(0.6f).flicker(0.3f);
        }
        tear.keep(5);
        tearGlow.keep(5);
        tearLight.extend(5);
        if (RANDOM.nextInt(90) == 0) {
            Vec3 from = at.add(RANDOM.nextGaussian() * 4, -RANDOM.nextDouble() * 20, RANDOM.nextGaussian() * 4);
            Vec3 to = from.add(RANDOM.nextGaussian() * 30, -25 - RANDOM.nextDouble() * 35, RANDOM.nextGaussian() * 30);
            fx.beam(from, to).lightning(1.2f, 2, 2).width(0.18f).life(6).color(PALE).alpha(0.7f).fade(0, 0.5f);
        }
        if (client.player.tickCount % 20 == 0) FxScreen.vignette(DEEP, 0.3f, 1400);
    }

    private static Vec3 camera() {
        Minecraft client = Minecraft.getInstance();
        return client.gameRenderer == null ? Vec3.ZERO : client.gameRenderer.mainCamera().position();
    }

    private static void clearTear() {
        if (tear != null) tear.kill();
        if (tearGlow != null) tearGlow.kill();
        if (tearLight != null) tearLight.kill();
        tear = tearGlow = null;
        tearLight = null;
    }

    // ------------------------------------------------------------------------------------ effects the server plays

    /** One of the dragon's effects, from the FX module. */
    public static void play(FxEngine fx, FxEffect effect, Vec3 pos, Vec3 target, int color, float s, Entity follow) {
        int ticks = Math.max(1, (int) Math.round(target.y - pos.y));
        switch (effect) {
            case DRAGON_SHIELDED -> {
                fx.decal(FxDecal.Kind.HEX_WARD, pos, null).radius(4, 6).life(8).color(MAGENTA).alpha(0.8f).param(0.4f).fade(0, 0.3f);
                burst(fx, pos, 20, 0.4, MAGENTA);
            }
            case DRAGON_GUST_CHARGE -> fx.task((engine, age) -> {
                Vec3 head = follow instanceof EnderDragon dragon ? dragon.head.position() : pos;
                for (int i = 0; i < 4; i++) {
                    Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.5, RANDOM.nextGaussian()).normalize();
                    Vec3 from = head.add(dir.scale(10 + RANDOM.nextDouble() * 6));
                    Vec3 v = head.subtract(from).scale(1 / 10.0);
                    engine.particle(from).shape(FxParticle.Shape.SPARK).velocity(v.x, v.y, v.z).life(10).size(0.08f, 0.03f).color(PALE, VIOLET)
                            .drag(1.0f).stretch(2f).hot(0.8f);
                }
                if (age == ticks - 2) FxScreen.shake(head, 0.25f, 40);
                return age < ticks;
            });
            case DRAGON_GUST -> gust(fx, pos, target);
            case DRAGON_LOCK -> lock(fx, pos, s, ticks);
            case DRAGON_STOMP -> {
                fx.decal(FxDecal.Kind.HEX_WAVE, pos.add(0, 0.1, 0), UP).radius(0.5f, s * 1.4f).life(18).color(VIOLET).fade(0, 0.6f);
                fx.decal(FxDecal.Kind.WARP_RING, pos.add(0, 1, 0), UP).radius(0.5f, s * 1.2f).life(14).param(0.7f);
                fx.light(pos.add(0, 1, 0), VIOLET, s * 2, 14).intensity(2).fadeFrom(0.2f);
                dust(fx, pos, s, 80);
                FxScreen.shake(pos, 0.7f, s * 4);
                FxScreen.aberration(pos, 0.5f, 300, s * 3);
            }
            case DRAGON_LAND -> {
                fx.decal(FxDecal.Kind.HEX_WAVE, pos.add(0, 0.1, 0), UP).radius(1, s * 2).life(26).color(MAGENTA).fade(0, 0.6f);
                fx.decal(FxDecal.Kind.HEX_SIGIL, pos.add(0, 0.08, 0), UP).radius(s).color(VIOLET).alpha(0.8f).spin(0.03f).reveal(10).life(60)
                        .fade(0.05f, 0.7f);
                dust(fx, pos, s, 160);
                FxScreen.shake(pos, 1.0f, 80);
                FxScreen.flash(pos, VIOLET, 0.35f, 400, 60);
            }
            case DRAGON_ROAR -> {
                fx.decal(FxDecal.Kind.WARP_RING, pos, null).radius(1, 30 * s).life(24).param(0.9f);
                fx.decal(FxDecal.Kind.GLOW, pos, null).radius(1, 6 * s).life(12).color(MAGENTA).fade(0, 0.3f);
                FxScreen.shake(pos, 0.5f * s, 90);
                FxScreen.aberration(pos, 0.8f, 600, 90);
                if (s > 2) {
                    FxScreen.flash(pos, MAGENTA, 0.5f, 600, 200);
                    FxScreen.saturation(pos, 0.7f, 3000, 200);
                }
            }
            case VOID_CLOUD -> cloud(fx, pos, s, ticks);
            case VOID_PULL -> {
                Vec3 at = follow != null ? follow.position() : pos;
                for (int i = 0; i < 24; i++) {
                    Vec3 from = at.add(RANDOM.nextGaussian() * 1.2, 1 + RANDOM.nextDouble() * 2, RANDOM.nextGaussian() * 1.2);
                    fx.particle(from).shape(FxParticle.Shape.SPARK).velocity(0, -0.6 - RANDOM.nextDouble() * 0.4, 0).life(14).size(0.07f, 0)
                            .color(VIOLET, BLACK).stretch(3f);
                }
                if (follow == Minecraft.getInstance().player) {
                    FxScreen.vignette(BLACK, 0.7f, 900);
                    FxScreen.shake(0.25f);
                }
            }
            case FRAGMENT_CRUMBLE -> {
                fx.task((engine, age) -> {
                    for (int i = 0; i < 6; i++) {
                        double a = RANDOM.nextDouble() * Math.PI * 2, d = RANDOM.nextDouble() * s * 1.2;
                        Vec3 at = pos.add(Math.cos(a) * d, -RANDOM.nextDouble() * s, Math.sin(a) * d);
                        engine.particle(at).shape(FxParticle.Shape.SMOKE).velocity(0, -0.05, 0).life(30 + RANDOM.nextInt(20)).size(0.6f, 2.0f)
                                .color(0xFF6A6450, 0xFF201A28).alpha(0.5f).turbulence(0.004f).fade(0.1f, 0.5f);
                    }
                    if (age % 6 == 0) {
                        double a = RANDOM.nextDouble() * Math.PI * 2;
                        Vec3 at = pos.add(Math.cos(a) * s * 0.8, 0.2, Math.sin(a) * s * 0.8);
                        engine.decal(FxDecal.Kind.GLOW, at, null).radius(0.5f, 2.5f).life(8).color(MAGENTA).fade(0, 0.3f);
                    }
                    return age < ticks;
                });
                FxScreen.shake(pos, 0.45f, 70);
            }
            case CRYSTAL_WARD_HIT -> {
                fx.decal(FxDecal.Kind.HEX_WARD, pos, null).radius(2.4f).color(PALE).alpha(0.9f).param(1 - s).life(6).fade(0, 0.2f);
                burst(fx, pos, 14, 0.25, MAGENTA);
            }
            case CRYSTAL_WARD_BREAK -> {
                for (int i = 0; i < 60; i++) {
                    Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
                    fx.particle(pos.add(dir.scale(2.2))).shape(FxParticle.Shape.SPARK).velocity(dir.x * 0.5, dir.y * 0.5, dir.z * 0.5).life(26)
                            .size(0.1f, 0).color(PALE, MAGENTA).drag(0.93f).gravity(0.01f);
                }
                fx.decal(FxDecal.Kind.GLOW, pos, null).radius(1, 6).life(10).color(PALE).fade(0, 0.3f);
                fx.light(pos, MAGENTA, 14, 16).intensity(2).fadeFrom(0.2f);
                FxScreen.shake(pos, 0.3f, 30);
            }
            default -> { }
        }
    }

    /** The wind of its wings: a cone of streaks rushing out, a ring of bent air running ahead of them. */
    private static void gust(FxEngine fx, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        double reach = dir.length();
        dir = dir.normalize();
        Vec3 side = dir.cross(UP).lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : dir.cross(UP).normalize();
        Vec3 up = side.cross(dir).normalize();
        for (int i = 0; i < 140; i++) {
            double spread = Math.tan(Math.toRadians(38)) * RANDOM.nextDouble();
            double a = RANDOM.nextDouble() * Math.PI * 2;
            Vec3 way = dir.add(side.scale(Math.cos(a) * spread)).add(up.scale(Math.sin(a) * spread)).normalize();
            double speed = 1.2 + RANDOM.nextDouble() * 1.2;
            fx.particle(from.add(way.scale(RANDOM.nextDouble() * 3))).shape(FxParticle.Shape.SPARK)
                    .velocity(way.x * speed, way.y * speed, way.z * speed).life((int) (reach / speed) + 4).size(0.09f, 0.02f)
                    .color(PALE, VIOLET).alpha(0.8f).stretch(3f).drag(0.98f);
        }
        Vec3 along = dir;
        for (int i = 0; i < 3; i++) {
            Vec3 at = from.add(dir.scale(reach * (0.25 + 0.3 * i)));
            float wide = 6 + 4 * i;
            fx.later(i * 3, () -> fx.decal(FxDecal.Kind.WARP_RING, at, along).radius(1, wide).life(14).param(0.8f));
        }
        fx.decal(FxDecal.Kind.HEX_WAVE, from.add(dir.scale(4)), dir).radius(1, 10).life(14).color(VIOLET).alpha(0.7f).fade(0, 0.5f);
        FxScreen.shake(from, 0.55f, 60);
    }

    /** Where it will strike: a sigil drawing itself there and a pillar of light over it, so it can be seen from far off. */
    private static void lock(FxEngine fx, Vec3 at, float radius, int ticks) {
        fx.decal(FxDecal.Kind.HEX_SIGIL, at, UP).radius(radius).color(MAGENTA).alpha(0.9f).spin(0.15f).reveal(ticks).life(ticks + 4)
                .fade(0.05f, 0.85f);
        fx.decal(FxDecal.Kind.HEX_SIGIL, at.add(0, 0.05, 0), UP).radius(radius * 0.4f).color(PALE).alpha(0.7f).spin(-0.3f).reveal(ticks / 2)
                .life(ticks + 4).fade(0.05f, 0.85f);
        fx.beam(at, at.add(0, 40, 0)).width(0.12f * radius).life(ticks + 4).color(MAGENTA).alpha(0.6f).energy(0.3f, 4).fade(0.2f, 0.85f);
        fx.light(at, MAGENTA, 4 + radius * 2, ticks + 4).intensity(1.6f).fadeFrom(0.85f);
        fx.task((engine, age) -> {
            for (int i = 0; i < 2; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2;
                Vec3 from = at.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
                Vec3 v = at.subtract(from).scale(1 / 12.0);
                engine.particle(from).shape(FxParticle.Shape.SPARK).velocity(v.x, v.y, v.z).life(12).size(0.07f, 0).color(PALE, MAGENTA).hot(0.9f);
            }
            return age < ticks;
        });
    }

    /** A cloud of void hanging in the air: black smoke turning slowly in a shell of faint light, sparks in it. */
    private static void cloud(FxEngine fx, Vec3 at, float radius, int ticks) {
        FxDecal shell = fx.decal(FxDecal.Kind.HEX_WARD, at, null).radius(radius * 0.3f, radius).grow(10).color(VIOLET).alpha(0.28f).param(0.6f)
                .life(ticks).fade(0.05f, 0.85f);
        fx.light(at, MAGENTA, radius * 2.5f, ticks).intensity(0.9f).flicker(0.3f).fadeFrom(0.85f);
        fx.task((engine, age) -> {
            float left = 1 - age / (float) ticks;
            // Seen from inside, the shell would fill the view: it fades as the eye comes near, and inside the edges darken instead.
            double eye = camera().distanceTo(at);
            shell.alpha((float) (0.28 * Math.clamp((eye - radius) / 3, 0, 1)));
            if (eye < radius && age % 8 == 0) FxScreen.vignette(BLACK, 0.75f, 500);
            for (int i = 0; i < 3; i++) {
                Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
                Vec3 p = at.add(dir.scale(RANDOM.nextDouble() * radius * 0.85));
                Vec3 swirl = dir.cross(UP).scale(0.02);
                engine.particle(p).shape(FxParticle.Shape.SMOKE).velocity(swirl.x, 0.005, swirl.z).life(26).size(0.8f, 1.6f)
                        .color(DEEP, BLACK).alpha(0.65f * Math.min(1, left * 4)).turbulence(0.003f).fade(0.15f, 0.5f);
            }
            if (RANDOM.nextInt(2) == 0) {
                Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
                engine.particle(at.add(dir.scale(RANDOM.nextDouble() * radius))).shape(FxParticle.Shape.SPARK)
                        .velocity(RANDOM.nextGaussian() * 0.03, RANDOM.nextGaussian() * 0.03, RANDOM.nextGaussian() * 0.03).life(12).size(0.06f, 0)
                        .color(MAGENTA, VIOLET).hot(1.0f);
            }
            return age < ticks && shell.alive();
        });
    }

    private static void dust(FxEngine fx, Vec3 at, float radius, int count) {
        for (int i = 0; i < count; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2, speed = 0.2 + RANDOM.nextDouble() * 0.5;
            fx.particle(at.add(Math.cos(a) * 1.5, 0.3, Math.sin(a) * 1.5)).shape(i % 3 == 0 ? FxParticle.Shape.SPARK : FxParticle.Shape.SMOKE)
                    .velocity(Math.cos(a) * speed, 0.05 + RANDOM.nextDouble() * 0.15, Math.sin(a) * speed).life(22 + RANDOM.nextInt(18))
                    .size(i % 3 == 0 ? 0.08f : 0.6f, i % 3 == 0 ? 0 : 1.6f).color(i % 3 == 0 ? PALE : 0xFF8A8270, i % 3 == 0 ? VIOLET : 0xFF2A2430)
                    .alpha(i % 3 == 0 ? 1 : 0.55f).drag(0.9f);
        }
    }

    private static void burst(FxEngine fx, Vec3 at, int count, double speed, int color) {
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize().scale(speed * (0.4 + RANDOM.nextDouble()));
            fx.particle(at).shape(FxParticle.Shape.SPARK).velocity(dir.x, dir.y, dir.z).life(16 + RANDOM.nextInt(10)).size(0.07f, 0)
                    .color(PALE, color).drag(0.9f).hot(0.9f);
        }
    }
}
