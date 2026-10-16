package de.ipnats.hardwrought.client.witchking;

import de.ipnats.hardwrought.client.fx.FxClient;
import de.ipnats.hardwrought.client.fx.FxDecal;
import de.ipnats.hardwrought.client.fx.FxEngine;
import de.ipnats.hardwrought.client.fx.FxLight;
import de.ipnats.hardwrought.client.fx.FxParticle;
import de.ipnats.hardwrought.witchking.HexBlade;
import de.ipnats.hardwrought.witchking.SoulOrb;
import de.ipnats.hardwrought.witchking.WitchKing;
import de.ipnats.hardwrought.witchking.WitchKingContent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Everything seen of the Witch King's magic, drawn with the FX module's Witch King kinds — sigils, rifts,
 * his ward, shockwaves, swords of light — none of which anything else uses. Some of it follows him for as
 * long as it lasts (the sigil under him, his ward, the links to his crystals, every sword and soul he sends);
 * the rest answers the events he sends when something happens.
 */
public final class WitchKingFx {
    static final int VIOLET = 0xFFB25CFF, DEEP = 0xFF7A2CFF, SOUL = 0xFF5CE1FF, GOLD = 0xFFFFC94A, PALE = 0xFFF0E0FF;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final RandomSource RANDOM = RandomSource.create();

    /** What follows each king, sword or soul, by entity id. */
    private static final Map<Integer, FxDecal> UNDER = new HashMap<>(), WARD = new HashMap<>(), BLADES = new HashMap<>(), RAIN = new HashMap<>(),
            ORBS = new HashMap<>();
    private static final Map<Long, FxDecal> CRYSTAL_SIGILS = new HashMap<>();
    private static final Map<Integer, FxLight> LIGHTS = new HashMap<>();

    private WitchKingFx() { }

    public static void initialize() {
        WitchKing.CLIENT_EVENTS = WitchKingFx::event;
        ClientTickEvents.END_CLIENT_TICK.register(WitchKingFx::tick);
    }

    // ------------------------------------------------------------------------------------ every tick

    private static void tick(Minecraft client) {
        ClientLevel level = client.level;
        FxEngine fx = FxClient.engine();
        if (level == null || client.player == null || fx == null || client.isPaused()) return;
        AABB near = client.player.getBoundingBox().inflate(96);
        for (WitchKing king : level.getEntitiesOfClass(WitchKing.class, near, Entity::isAlive)) follow(fx, level, king);
        for (WitchKing king : level.getEntitiesOfClass(WitchKing.class, near, k -> k.isDeadOrDying())) dying(fx, king);
        for (HexBlade blade : level.getEntitiesOfClass(HexBlade.class, near, Entity::isAlive)) blade(fx, blade);
        for (SoulOrb orb : level.getEntitiesOfClass(SoulOrb.class, near, Entity::isAlive)) orb(fx, orb);
        prune(level);
    }

    /** Drops what follows something that is no longer there. */
    private static void prune(ClientLevel level) {
        for (Map<Integer, FxDecal> map : java.util.List.of(UNDER, WARD, BLADES, RAIN, ORBS)) {
            map.entrySet().removeIf(entry -> {
                Entity entity = level.getEntity(entry.getKey());
                boolean gone = entity == null || !entity.isAlive() || !entry.getValue().alive();
                if (gone) entry.getValue().kill();
                return gone;
            });
        }
        LIGHTS.entrySet().removeIf(entry -> {
            boolean gone = level.getEntity(entry.getKey()) == null;
            if (gone) entry.getValue().kill();
            return gone;
        });
        CRYSTAL_SIGILS.entrySet().removeIf(entry -> {
            boolean gone = !level.getBlockState(BlockPos.of(entry.getKey())).is(WitchKingContent.CRYSTAL);
            if (gone) entry.getValue().kill();
            return gone;
        });
    }

    private static void follow(FxEngine fx, ClientLevel level, WitchKing king) {
        float scale = king.getScale();
        Vec3 feet = king.position();
        Vec3 middle = feet.add(0, king.getBbHeight() * 0.55, 0);
        // His light, violet, reaching further as he grows.
        FxLight light = LIGHTS.computeIfAbsent(king.getId(), id -> fx.light(middle, VIOLET, 7, 40).flicker(0.2f));
        light.moveTo(middle).extend(5);
        // The sigil turning under him while he floats.
        if (king.phase() < 3 || king.growing()) {
            FxDecal under = UNDER.computeIfAbsent(king.getId(), id -> fx.decal(FxDecal.Kind.HEX_SIGIL, feet, UP).radius(1.7f).color(VIOLET)
                    .alpha(0.75f).spin(0.03f).reveal(20).fade(0.1f, 0.98f).life(40));
            under.center(new Vec3(feet.x, king.home().getY() + 0.03, feet.z)).radius(1.7f * scale).keep(5);
        } else {
            FxDecal under = UNDER.remove(king.getId());
            if (under != null) under.kill();
        }
        // His robe trailing light, his crown sparking.
        if (RANDOM.nextInt(2) == 0) {
            fx.particle(feet.add(RANDOM.nextGaussian() * 0.3 * scale, 0.2 * scale + RANDOM.nextDouble() * 0.3, RANDOM.nextGaussian() * 0.3 * scale))
                    .velocity(0, 0.02 + RANDOM.nextDouble() * 0.02, 0).life(30).size(0.12f * scale, 0).color(DEEP, VIOLET).alpha(0.8f).drag(0.98f);
        }
        if (RANDOM.nextInt(6) == 0) {
            Vec3 crown = feet.add(0, king.getBbHeight() + 0.1 * scale, 0);
            fx.particle(crown.add(RANDOM.nextGaussian() * 0.15 * scale, 0, RANDOM.nextGaussian() * 0.15 * scale)).shape(FxParticle.Shape.SPARK)
                    .velocity(0, 0.04, 0).life(12).size(0.05f * scale, 0).color(GOLD).hot(0.9f);
        }
        if (king.phase() == 2) warded(fx, level, king, middle);
        else {
            FxDecal ward = WARD.remove(king.getId());
            if (ward != null) ward.kill();
        }
        if (king.phase() == 3 && !king.growing() && RANDOM.nextInt(2) == 0) {
            // A giant's footfall of power: embers thrown up round him.
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            Vec3 at = feet.add(Math.cos(angle) * king.getBbWidth() * 0.7, 0.1, Math.sin(angle) * king.getBbWidth() * 0.7);
            fx.particle(at).shape(FxParticle.Shape.SPARK).velocity(Math.cos(angle) * 0.05, 0.08, Math.sin(angle) * 0.05).life(18)
                    .size(0.08f, 0).color(VIOLET).gravity(0.004f);
        }
    }

    /** Phase 2: the ward round him, cracked as his crystals fall, and the crystals' power streaming in to it. */
    private static void warded(FxEngine fx, ClientLevel level, WitchKing king, Vec3 middle) {
        float cracked = (4 - king.crystals()) / 4f;
        FxDecal ward = WARD.computeIfAbsent(king.getId(), id -> fx.decal(FxDecal.Kind.HEX_WARD, middle, null).radius(0.2f, 2.6f).grow(16)
                .color(VIOLET).alpha(0.9f).fade(0.05f, 0.98f).life(40));
        ward.center(middle).param(cracked).keep(5);
        for (BlockPos spot : WitchKing.CRYSTAL_SPOTS) {
            BlockPos at = king.home().offset(spot);
            if (!level.getBlockState(at).is(WitchKingContent.CRYSTAL)) continue;
            Vec3 crystal = Vec3.atCenterOf(at).add(0, 0.3, 0);
            CRYSTAL_SIGILS.computeIfAbsent(at.asLong(), key -> fx.decal(FxDecal.Kind.HEX_SIGIL, Vec3.atBottomCenterOf(at).add(0, 0.02, 0), UP)
                    .radius(1.4f).color(SOUL).alpha(0.8f).spin(-0.05f).reveal(16).life(40)).keep(5);
            // Power streaming from the crystal to him.
            if (RANDOM.nextInt(2) == 0) {
                Vec3 way = middle.subtract(crystal);
                double travel = 18;
                fx.particle(crystal).velocity(way.x / travel, way.y / travel + 0.02, way.z / travel).life((int) travel).size(0.18f, 0.06f)
                        .color(SOUL, VIOLET).alpha(0.9f).drag(1.0f).hot(0.8f);
            }
        }
    }

    private static void blade(FxEngine fx, HexBlade blade) {
        Vec3 at = blade.position().add(0, blade.getBbHeight() / 2, 0);
        float strength = Math.min(1, blade.tickCount / 6f);
        FxDecal sword = BLADES.computeIfAbsent(blade.getId(), id -> fx.decal(FxDecal.Kind.HEX_BLADE, at, null).radius(0.95f).color(VIOLET)
                .alpha(1).fade(0, 1).life(10));
        sword.center(at).axis(blade.pointing()).param(strength).keep(3);
        if (blade.flying()) {
            Vec3 back = blade.getDeltaMovement().scale(-0.5);
            fx.particle(at.add(back)).shape(FxParticle.Shape.SPARK).velocity(back.x * 0.1, back.y * 0.1, back.z * 0.1).life(8).size(0.06f, 0)
                    .color(PALE, VIOLET).hot(0.9f);
            FxDecal rain = RAIN.remove(blade.getId());
            if (rain != null) rain.kill();
        } else if (blade.pointing().y < -0.7) {
            // Poised above its victim: a sigil over it, out of which it will fall.
            FxDecal rain = RAIN.computeIfAbsent(blade.getId(), id -> fx.decal(FxDecal.Kind.HEX_SIGIL, at.add(0, 1.0, 0), UP).radius(1.1f)
                    .color(VIOLET).alpha(0.85f).spin(0.12f).reveal(10).life(20));
            rain.center(at.add(0, 1.0, 0)).keep(3);
        }
    }

    private static void orb(FxEngine fx, SoulOrb orb) {
        Vec3 at = orb.position().add(0, orb.getBbHeight() / 2, 0);
        FxDecal shell = ORBS.computeIfAbsent(orb.getId(), id -> fx.decal(FxDecal.Kind.HEX_WARD, at, null).radius(0.38f).color(SOUL).alpha(0.9f)
                .param(0).life(10));
        shell.center(at).keep(3);
        fx.particle(at).velocity(0, 0, 0).life(3).size(0.35f, 0.3f).color(PALE).hot(1.0f).alpha(0.9f);
        if (RANDOM.nextInt(2) == 0) {
            fx.particle(at.add(RANDOM.nextGaussian() * 0.15, RANDOM.nextGaussian() * 0.15, RANDOM.nextGaussian() * 0.15)).life(16)
                    .size(0.1f, 0).color(SOUL, DEEP).alpha(0.7f).drag(0.9f);
        }
    }

    /** Dying: rays of light breaking out of him, more and longer as he rises. */
    private static void dying(FxEngine fx, WitchKing king) {
        Vec3 middle = king.position().add(0, king.getBbHeight() * 0.5, 0);
        float progress = king.deathTime / (float) WitchKing.DEATH_TICKS;
        if (RANDOM.nextFloat() < 0.3f + progress) {
            Vec3 ray = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 0.6, RANDOM.nextGaussian()).normalize();
            float length = (1.2f + 2.5f * progress) * king.getScale();
            fx.decal(FxDecal.Kind.HEX_BLADE, middle.add(ray.scale(length)), null).axis(ray).radius(length).color(PALE).param(0.6f + 0.4f * progress)
                    .life(8).fade(0.1f, 0.4f);
        }
        if (RANDOM.nextInt(3) == 0) {
            fx.particle(middle.add(RANDOM.nextGaussian() * king.getBbWidth() * 0.4, RANDOM.nextGaussian() * king.getBbHeight() * 0.3,
                    RANDOM.nextGaussian() * king.getBbWidth() * 0.4)).velocity(0, 0.05, 0).life(30).size(0.2f, 0).color(SOUL, VIOLET).alpha(0.9f);
        }
    }

    // ------------------------------------------------------------------------------------ events

    private static void event(WitchKing king, byte event) {
        FxEngine fx = FxClient.engine();
        if (fx == null) return;
        float scale = king.getScale();
        Vec3 feet = king.position();
        Vec3 middle = feet.add(0, king.getBbHeight() * 0.55, 0);
        Vec3 floor = new Vec3(feet.x, king.home().getY() + 0.04, feet.z);
        switch (event) {
            case WitchKing.EV_BLADES -> {
                Vec3 look = king.getLookAngle().multiply(1, 0, 1).normalize();
                fx.decal(FxDecal.Kind.HEX_SIGIL, middle.subtract(look.scale(0.9 * scale)), look).radius(1.9f * scale).color(VIOLET).spin(0.1f)
                        .reveal(10).life(40).fade(0.05f, 0.7f);
                burst(fx, middle, 24, 0.12, VIOLET);
            }
            case WitchKing.EV_TELEPORT_OUT -> rift(fx, middle, king.getBbHeight() * 0.75f);
            case WitchKing.EV_TELEPORT_IN -> fx.later(2, () -> rift(fx, king.position().add(0, king.getBbHeight() * 0.55, 0), king.getBbHeight() * 0.75f));
            case WitchKing.EV_WAVE_CHARGE -> fx.decal(FxDecal.Kind.HEX_SIGIL, floor, UP).radius(4.5f * scale).color(VIOLET).spin(0.15f)
                    .reveal(WitchKing.WAVE_CHARGE).life(WitchKing.WAVE_CHARGE + 6).fade(0.05f, 0.85f);
            case WitchKing.EV_WAVE -> {
                float reach = king.phase() == 3 && scale < WitchKing.GIANT - 0.1 ? 18 : 14;
                fx.decal(FxDecal.Kind.HEX_WAVE, floor.add(0, 0.05, 0), UP).radius(0.5f, reach).life(24).color(VIOLET).fade(0, 0.6f);
                fx.decal(FxDecal.Kind.HEX_WAVE, floor.add(0, 1.2, 0), UP).radius(0.5f, reach * 0.8f).life(20).color(PALE).alpha(0.6f).fade(0, 0.5f);
                fx.decal(FxDecal.Kind.WARP_RING, floor.add(0, 1, 0), UP).radius(0.5f, reach).life(18).param(0.6f);
                fx.light(middle, VIOLET, reach, 14).fadeFrom(0.2f);
                for (int i = 0; i < 60; i++) {
                    double angle = i * Math.PI * 2 / 60;
                    fx.particle(floor.add(0, 0.2, 0)).shape(FxParticle.Shape.SPARK)
                            .velocity(Math.cos(angle) * 0.7, 0.05 + RANDOM.nextDouble() * 0.1, Math.sin(angle) * 0.7).life(20).size(0.1f, 0)
                            .color(PALE, VIOLET).drag(0.93f);
                }
            }
            case WitchKing.EV_DOOM_CHARGE -> {
                Vec3 hall = Vec3.atBottomCenterOf(king.home()).add(0, 0.05, 0);
                fx.decal(FxDecal.Kind.HEX_SIGIL, hall, UP).radius(17).color(DEEP).alpha(0.9f).spin(0.012f).reveal(WitchKing.DOOM_CHARGE)
                        .life(WitchKing.DOOM_CHARGE + 4).fade(0.02f, 0.95f);
                fx.decal(FxDecal.Kind.HEX_SIGIL, middle.add(0, king.getBbHeight() * 0.8, 0), UP).radius(3.5f * scale).color(PALE).spin(-0.06f)
                        .reveal(WitchKing.DOOM_CHARGE / 2).life(WitchKing.DOOM_CHARGE + 4);
                fx.light(middle, DEEP, 10, WitchKing.DOOM_CHARGE).fadeFrom(0.9f);
                fx.task((engine, age) -> {
                    // Souls drawn up out of the floor of the whole hall into the gathering curse.
                    for (int i = 0; i < 3; i++) {
                        double angle = RANDOM.nextDouble() * Math.PI * 2, distance = RANDOM.nextDouble() * 16;
                        Vec3 from = hall.add(Math.cos(angle) * distance, 0.1, Math.sin(angle) * distance);
                        Vec3 to = king.position().add(0, king.getBbHeight(), 0).subtract(from).scale(1 / 30.0);
                        engine.particle(from).velocity(to.x, to.y, to.z).life(30).size(0.14f, 0.04f).color(SOUL, DEEP).drag(1.0f).alpha(0.8f);
                    }
                    return age < WitchKing.DOOM_CHARGE;
                });
            }
            case WitchKing.EV_DOOM -> {
                Vec3 hall = Vec3.atBottomCenterOf(king.home());
                fx.decal(FxDecal.Kind.HEX_WAVE, hall.add(0, 0.1, 0), UP).radius(1, 40).life(30).color(DEEP).fade(0, 0.7f);
                fx.decal(FxDecal.Kind.GLOW, middle, null).radius(2, 22).life(14).color(PALE).alpha(0.8f).fade(0, 0.3f);
                fx.light(middle, PALE, 40, 16).fadeFrom(0.1f);
                for (int i = 0; i < 260; i++) {
                    double angle = RANDOM.nextDouble() * Math.PI * 2, distance = Math.sqrt(RANDOM.nextDouble()) * 18;
                    Vec3 from = hall.add(Math.cos(angle) * distance, 14 + RANDOM.nextDouble() * 6, Math.sin(angle) * distance);
                    fx.particle(from).shape(FxParticle.Shape.SPARK).velocity(0, -0.9 - RANDOM.nextDouble() * 0.5, 0).life(24)
                            .size(0.09f, 0.02f).color(PALE, DEEP).collide(0.1f);
                }
            }
            case WitchKing.EV_PHASE_TWO -> {
                burst(fx, middle, 80, 0.3, VIOLET);
                fx.decal(FxDecal.Kind.HEX_WAVE, floor, UP).radius(0.5f, 16).life(22).color(VIOLET);
                for (BlockPos spot : WitchKing.CRYSTAL_SPOTS) {
                    Vec3 at = Vec3.atBottomCenterOf(king.home().offset(spot));
                    for (int i = 0; i < 40; i++) {
                        fx.particle(at.add(RANDOM.nextGaussian() * 0.2, i * 0.35, RANDOM.nextGaussian() * 0.2)).velocity(0, 0.05, 0).life(26)
                                .size(0.2f, 0).color(PALE, SOUL).hot(0.9f);
                    }
                    fx.light(at.add(0, 1, 0), SOUL, 8, 30).fadeFrom(0.3f);
                }
            }
            case WitchKing.EV_PHASE_THREE -> {
                // The ward shatters: shards of it thrown out everywhere.
                for (int i = 0; i < 120; i++) {
                    Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize();
                    fx.particle(middle.add(dir.scale(2.6))).shape(FxParticle.Shape.SPARK).velocity(dir.x * 0.6, dir.y * 0.6, dir.z * 0.6)
                            .life(30).size(0.12f, 0).color(PALE, VIOLET).drag(0.94f).gravity(0.01f);
                }
                fx.decal(FxDecal.Kind.GLOW, middle, null).radius(1, 12).life(12).color(PALE).fade(0, 0.3f);
                fx.light(middle, VIOLET, 30, 40).fadeFrom(0.2f);
            }
            case WitchKing.EV_CRYSTAL -> {
                fx.decal(FxDecal.Kind.GLOW, middle, null).radius(1, 5).life(10).color(PALE).fade(0, 0.3f);
                for (BlockPos spot : WitchKing.CRYSTAL_SPOTS) {
                    BlockPos at = king.home().offset(spot);
                    if (king.level().getBlockState(at).is(WitchKingContent.CRYSTAL)) continue;
                    // Only the one just broken: its sigil is still there to go with it.
                    FxDecal sigil = CRYSTAL_SIGILS.remove(at.asLong());
                    if (sigil == null) continue;
                    sigil.kill();
                    burst(fx, Vec3.atCenterOf(at), 40, 0.25, SOUL);
                }
            }
            case WitchKing.EV_SUMMON -> {
                fx.decal(FxDecal.Kind.HEX_SIGIL, Vec3.atBottomCenterOf(king.home()).add(0, 0.06, 0), UP).radius(8).color(SOUL).alpha(0.7f)
                        .spin(-0.04f).reveal(20).life(60);
                burst(fx, middle, 30, 0.15, SOUL);
            }
            case WitchKing.EV_ORBS -> burst(fx, middle, 30, 0.2, SOUL);
            case WitchKing.EV_RAIN -> fx.decal(FxDecal.Kind.HEX_SIGIL, middle.add(0, king.getBbHeight() * 0.7, 0), UP).radius(2.5f).color(VIOLET)
                    .spin(0.2f).reveal(8).life(30);
            case WitchKing.EV_SLAM -> {
                Vec3 front = feet.add(king.getLookAngle().multiply(1, 0, 1).normalize().scale(king.getBbWidth()));
                fx.decal(FxDecal.Kind.HEX_WAVE, new Vec3(front.x, king.home().getY() + 0.06, front.z), UP).radius(0.5f, 5).life(14).color(VIOLET);
                burst(fx, front.add(0, 0.3, 0), 30, 0.3, PALE);
            }
            case WitchKing.EV_ENTRANCE -> {
                fx.decal(FxDecal.Kind.HEX_SIGIL, floor, UP).radius(8).color(VIOLET).spin(0.04f).reveal(40).life(WitchKing.ENTRANCE_TICKS + 20)
                        .fade(0.05f, 0.8f);
                rift(fx, middle, 2.4f);
                fx.light(middle, VIOLET, 18, WitchKing.ENTRANCE_TICKS).fadeFrom(0.6f);
            }
            case WitchKing.EV_DEATH -> {
                fx.decal(FxDecal.Kind.HEX_SIGIL, floor, UP).radius(10).color(PALE).spin(-0.08f).reveal(20).life(WitchKing.DEATH_TICKS);
                fx.later(WitchKing.DEATH_TICKS - 2, () -> {
                    Vec3 at = king.position().add(0, king.getBbHeight() * 0.5, 0);
                    fx.decal(FxDecal.Kind.HEX_WAVE, new Vec3(at.x, king.home().getY() + 0.1, at.z), UP).radius(1, 30).life(30).color(PALE);
                    fx.decal(FxDecal.Kind.GLOW, at, null).radius(2, 16).life(16).color(PALE).fade(0, 0.2f);
                    fx.light(at, PALE, 36, 30).fadeFrom(0.1f);
                    burst(fx, at, 200, 0.6, SOUL);
                });
            }
            case WitchKing.EV_WARD_HIT -> {
                fx.decal(FxDecal.Kind.HEX_WARD, middle, null).radius(2.7f).color(PALE).alpha(0.9f).param(0.5f).life(6).fade(0, 0.2f);
                burst(fx, middle, 10, 0.2, PALE);
            }
            default -> { }
        }
    }

    /** A tear in the air, opening and closing, sparks thrown off it. */
    private static void rift(FxEngine fx, Vec3 at, float height) {
        fx.decal(FxDecal.Kind.HEX_RIFT, at, null).radius(height).color(VIOLET).life(26).fade(0, 1);
        fx.light(at, VIOLET, 8, 20).fadeFrom(0.3f);
        for (int i = 0; i < 30; i++) {
            Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian() * 1.5, RANDOM.nextGaussian()).normalize();
            fx.particle(at.add(dir.scale(0.2))).shape(FxParticle.Shape.SPARK).velocity(dir.x * 0.2, dir.y * 0.2, dir.z * 0.2).life(14)
                    .size(0.07f, 0).color(PALE, VIOLET).drag(0.9f);
        }
    }

    private static void burst(FxEngine fx, Vec3 at, int count, double speed, int color) {
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian()).normalize().scale(speed * (0.4 + RANDOM.nextDouble()));
            fx.particle(at).velocity(dir.x, dir.y, dir.z).life(16 + RANDOM.nextInt(10)).size(0.14f, 0).color(PALE, color).drag(0.9f).hot(0.8f);
        }
    }
}
