package de.ipnats.hardwrought.magic;

import de.ipnats.hardwrought.fx.Fx;
import de.ipnats.hardwrought.fx.FxEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * What spells do in the world. Three questions, answered separately: what the core element does to
 * whatever it reaches ({@link #payload}), where the spell acts (its placement), and in what shape —
 * once, bursting, lingering, lying in wait. Every combination of the three then works without being
 * written down.
 */
final class SpellEffects {
    /** Spells sent along an arrow fly this many blocks a tick, as fast as the effect the clients draw. */
    private static final double SPEED = 1.1;

    private SpellEffects() { }

    /**
     * A spell on its way: what it is made of, how strong it came out, and where it goes.
     *
     * @param core the element that acts, which a medium failure may have changed; {@code null} for a bare shield
     * @param inverted whether the core is turned around
     * @param direction where an arrow sends it, already turned into the world; or the gaze
     * @param distance how far an arrow sends it
     * @param power the final strength, quality and failure included
     * @param area radius where it lands
     * @param duration how much longer lasting effects last than plain
     * @param blend the other elements of the spell, each of which acts too, at {@link #BLEND} of its
     *              strength: Fire with Water both burns and splashes
     */
    record Release(ServerLevel level, ServerPlayer caster, Rune core, boolean inverted, Spell.Placement placement,
                   Vec3 direction, double distance, boolean burst, boolean linger, boolean trap, boolean shield,
                   double power, double area, double duration, List<Rune> blend) {
        Release {
            blend = blend == null ? List.of() : List.copyOf(blend);
        }

        Release(ServerLevel level, ServerPlayer caster, Rune core, boolean inverted, Spell.Placement placement,
                Vec3 direction, double distance, boolean burst, boolean linger, boolean trap, boolean shield,
                double power, double area, double duration) {
            this(level, caster, core, inverted, placement, direction, distance, burst, linger, trap, shield, power, area,
                    duration, List.of());
        }

        Release weaker(double factor) {
            return new Release(level, caster, core, inverted, placement, direction, distance, burst, linger, trap, shield,
                    power * factor, area, duration, blend);
        }

        /** One of the blended elements, acting on its own at its share of the strength. */
        Release as(Rune element) {
            return new Release(level, caster, element, false, placement, direction, distance, burst, linger, trap, shield,
                    power * BLEND, area, duration, List.of());
        }
    }

    /** How strongly an element mixed into another's spell acts beside it. */
    static final double BLEND = 0.4;

    /** Sends the spell out. */
    static void release(Release r) {
        if (r.shield()) {
            Vec3 at = null;
            if (r.linger()) {
                // A spiral holds the shield where it is raised: where the arrow ends, or where the caster stands.
                at = r.placement() == Spell.Placement.ARROW
                        ? aim(r, r.caster().getEyePosition(), r.direction(), r.distance())
                        : r.caster().position().add(0, r.caster().getBbHeight() / 2, 0);
            }
            MagicShields.raise(r.level(), r.caster(), r.power(), r.duration(), r.core(), r.inverted(), at);
            return;
        }
        if (r.core() == null && r.placement() != Spell.Placement.SHIFT) return;
        Vec3 eye = r.caster().getEyePosition();
        Vec3 hand = hand(r.caster());
        switch (r.placement()) {
            case FRONT -> {
                Vec3 look = r.caster().getLookAngle();
                Vec3 at = aim(r, eye, look, r.distance());
                spray(r, hand, at);
                if (r.burst() || r.linger() || r.trap()) {
                    land(r, at);
                    return;
                }
                stream(r, eye, look, at);
            }
            case ARROW -> {
                Vec3 at = aim(r, eye, r.direction(), r.distance());
                FxEffect travel = r.inverted() ? FxEffect.ARCANE_BOLT : switch (r.core()) {
                    case FIRE -> FxEffect.FIREBALL;
                    case LIGHTNING -> FxEffect.CHAIN_LIGHTNING;
                    default -> FxEffect.ARCANE_BOLT;
                };
                Fx.play(r.level(), travel, hand, at, color(r), (float) Math.min(1.6, 0.6 + 0.3 * r.power()), null);
                if (travel == FxEffect.CHAIN_LIGHTNING) {
                    // Lightning does not fly; it is there.
                    MagicTasks.later(r.level(), 2, () -> land(r, at));
                    return;
                }
                MagicTasks.later(r.level(), (int) Math.ceil(hand.distanceTo(at) / SPEED), () -> land(r, at));
            }
            case SELF -> {
                if (r.burst() || r.linger() || r.trap()) land(r, r.caster().position().add(0, 1, 0));
                else self(r);
            }
            case PORTAL -> { }
            case SHIFT -> {
                Vec3 to = shift(r);
                if (r.core() == null) return;
                if (r.burst() || r.linger() || r.trap()) land(r, to.add(0, 1, 0));
                else if (r.core() != Rune.WIND) strike(r.weaker(0.5), to.add(0, 1, 0), 1.5, false);
            }
        }
    }

    /** The spell arrives at a point and acts there in its shape. */
    static void land(Release r, Vec3 at) {
        if (r.trap()) {
            MagicFields.trap(r, ground(r.level(), at));
            return;
        }
        if (r.linger()) {
            MagicFields.linger(r, at);
            return;
        }
        if (r.burst()) {
            burst(r, at);
            return;
        }
        if (r.placement() != Spell.Placement.FRONT) impactEffect(r, at, 1.5);
        strike(r, at, 1.0 + 0.5 * (r.area() - 2), false);
        if (r.core() == Rune.FIRE && !r.inverted()) ignite(r.level(), at, r.direction());
    }

    /** A burst all around the point; the caster at its heart is spared. */
    static void burst(Release r, Vec3 at) {
        double radius = r.area() + 1;
        impactEffect(r, at, radius);
        if (r.core() == Rune.FIRE && !r.inverted() && r.power() >= 1.5) {
            // A fire burst strong enough is an explosion (section 15's example); blocks are spared.
            r.level().explode(r.caster(), damage(r), null, at, (float) Math.min(4, r.power()), false,
                    Level.ExplosionInteraction.NONE);
        }
        strike(r, at, radius, false);
    }

    /** The core element acting on everything within radius of a point. */
    static void strike(Release r, Vec3 at, double radius, boolean includeCaster) {
        AABB box = new AABB(at, at).inflate(radius + 1);
        for (LivingEntity target : r.level().getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity.isAlive() && (includeCaster || entity != r.caster()))) {
            Vec3 middle = target.position().add(0, target.getBbHeight() / 2, 0);
            double d = middle.distanceTo(at);
            if (d > radius + target.getBbWidth()) continue;
            payload(r, target, 1 - 0.5 * Math.min(1, d / Math.max(radius, 1)), at);
        }
        if (r.core() == Rune.WATER && !r.inverted()) douse(r.level(), at, radius);
    }

    /** What the core element does to one living thing; from is where it came from, for pushes and pulls. */
    static void payload(Release r, LivingEntity target, double share, Vec3 from) {
        double p = r.power() * share;
        DamageSource source = damage(r);
        Vec3 away = target.position().subtract(from).multiply(1, 0, 1);
        away = away.lengthSqr() < 1e-4 ? r.direction().multiply(1, 0, 1).add(1e-3, 0, 0).normalize() : away.normalize();
        int ticks = (int) (60 * r.duration() * Math.max(0.5, p));
        for (Rune element : r.blend()) payload(r.as(element), target, share, from);
        if (r.inverted()) {
            inverse(r, target, p, away, ticks, source);
            return;
        }
        switch (r.core()) {
            case FIRE -> {
                target.hurtServer(r.level(), source, (float) (3 * p));
                target.igniteForSeconds((float) (3 * p * r.duration()));
            }
            case WATER -> {
                target.clearFire();
                // Endermen, blazes and striders are hurt by water; everything else is only pushed.
                target.hurtServer(r.level(), source, (float) ((target.isSensitiveToWater() ? 4 : 0.5) * p));
                push(target, away.scale(0.5 * p).add(0, 0.15, 0));
            }
            case WIND -> push(target, away.scale(1.0 * p).add(0, 0.35 * Math.min(p, 2), 0));
            case EARTH -> {
                target.hurtServer(r.level(), source, (float) (4 * p));
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) (50 * r.duration()), p >= 1.5 ? 1 : 0),
                        r.caster());
            }
            case LIGHT -> {
                if (target.isInvertedHealAndHarm()) {
                    target.hurtServer(r.level(), source, (float) (4 * p));
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) (100 * r.duration())), r.caster());
                } else {
                    target.heal((float) (3 * p));
                }
            }
            case DARK -> {
                target.hurtServer(r.level(), source, (float) (1.5 * p));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks), r.caster());
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks), r.caster());
            }
            case LIGHTNING -> chain(r, target, p, source);
        }
    }

    /**
     * Lightning strikes, stuns for a moment, and leaps on to whatever living thing stands nearest,
     * weaker at every leap: two leaps, and one more for a strong spell or a Lightning amplifier.
     * Wet targets — in water, or under rain — take half again as much.
     */
    private static void chain(Release r, LivingEntity first, double p, DamageSource source) {
        java.util.Set<LivingEntity> struck = new java.util.HashSet<>();
        LivingEntity target = first;
        Vec3 from = target.position().add(0, target.getBbHeight() / 2, 0);
        int leaps = 2 + (p >= 1.5 ? 1 : 0) + (r.area() > 2.25 ? 1 : 0);
        double strength = p;
        for (int i = 0; i <= leaps && target != null; i++) {
            struck.add(target);
            double wet = target.isInWaterOrRain() ? 1.5 : 1;
            target.hurtServer(r.level(), source, (float) (3.5 * strength * wet));
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 3), r.caster());
            Vec3 at = target.position().add(0, target.getBbHeight() / 2, 0);
            if (i > 0) Fx.play(r.level(), FxEffect.CHAIN_LIGHTNING, from, at, Rune.LIGHTNING.color(), 0.6f, null);
            from = at;
            strength *= 0.6;
            Vec3 here = at;
            target = r.level().getEntitiesOfClass(LivingEntity.class, new AABB(here, here).inflate(5),
                            e -> e.isAlive() && !struck.contains(e) && e != r.caster() && !e.isSpectator()).stream()
                    .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(here))).orElse(null);
        }
    }

    /**
     * A rune struck through does the opposite of itself: fire takes heat away, water dries, wind
     * draws in, earth lets go of the ground, light harms the living, darkness lays everything bare.
     */
    private static void inverse(Release r, LivingEntity target, double p, Vec3 away, int ticks, DamageSource source) {
        switch (r.core()) {
            case FIRE -> {
                target.clearFire();
                target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 140, target.getTicksFrozen() + (int) (120 * p)));
                target.hurtServer(r.level(), source, (float) (1.5 * p));
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 1), r.caster());
            }
            case WATER -> {
                target.hurtServer(r.level(), source, (float) ((target.isSensitiveToWater() ? 0 : 1.5) * p));
                target.addEffect(new MobEffectInstance(MobEffects.HUNGER, ticks * 2), r.caster());
            }
            case WIND -> push(target, away.scale(-0.9 * p).add(0, 0.1, 0));
            case EARTH -> target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, (int) (30 * r.duration() * Math.min(p, 2))),
                    r.caster());
            case LIGHT -> {
                if (target.isInvertedHealAndHarm()) target.heal((float) (3 * p));
                else target.hurtServer(r.level(), source, (float) (3 * p));
            }
            case DARK -> {
                target.removeEffect(MobEffects.INVISIBILITY);
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks * 3), r.caster());
            }
            // Lightning turned around charges instead of striking: quicker feet and hands.
            case LIGHTNING -> {
                target.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks * 2, p >= 1.5 ? 1 : 0), r.caster());
                target.addEffect(new MobEffectInstance(MobEffects.HASTE, ticks * 2), r.caster());
            }
        }
    }

    /** Runes inside a circle, worked on the caster; every element mixed in works on them too. */
    private static void self(Release r) {
        for (Rune element : r.blend()) self(r.as(element));
        ServerPlayer caster = r.caster();
        Vec3 at = caster.position();
        if (r.inverted()) {
            switch (r.core()) {
                case FIRE -> {
                    caster.clearFire();
                    Fx.play(r.level(), FxEffect.HEAL, at, at, 0xA8E8FF, 0.8f, caster);
                }
                case DARK -> {
                    caster.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, (int) (600 * r.duration())));
                    Fx.play(r.level(), FxEffect.SPARKLE, at.add(0, 1.6, 0), at, Rune.LIGHT.color(), 0.6f, null);
                }
                case WIND -> {
                    caster.setDeltaMovement(Vec3.ZERO);
                    caster.needsSync = true;
                    caster.resetFallDistance();
                    caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, (int) (60 * r.duration())));
                }
                // Lightning turned around charges the caster, as it would anyone.
                case LIGHTNING -> {
                    caster.addEffect(new MobEffectInstance(MobEffects.SPEED, (int) (200 * r.duration()), r.power() >= 1.5 ? 1 : 0));
                    caster.addEffect(new MobEffectInstance(MobEffects.HASTE, (int) (200 * r.duration())));
                    Fx.play(r.level(), FxEffect.SPARKLE, at.add(0, 1, 0), at, Rune.LIGHTNING.color(), 1, null);
                }
                // Light and Water turned around do to the caster what they would do to anyone.
                default -> strike(r, at.add(0, 1, 0), 0.5, true);
            }
            return;
        }
        switch (r.core()) {
            case FIRE -> {
                caster.clearFire();
                caster.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, (int) (200 * r.duration() * r.power())));
                Fx.play(r.level(), FxEffect.HEAL, at, at, Rune.FIRE.color(), 0.8f, caster);
            }
            case WATER -> {
                caster.clearFire();
                caster.removeEffect(MobEffects.POISON);
                Fx.play(r.level(), FxEffect.HEAL, at, at, Rune.WATER.color(), 0.8f, caster);
            }
            case WIND -> {
                push(caster, caster.getLookAngle().scale(0.9 * Math.min(r.power(), 2.5)).add(0, 0.5, 0));
                caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, (int) (40 * r.duration())));
                Fx.play(r.level(), FxEffect.SPARKLE, at, at, Rune.WIND.color(), 1, null);
            }
            case EARTH -> {
                caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, (int) (200 * r.duration()), r.power() >= 1.5 ? 1 : 0));
                Fx.play(r.level(), FxEffect.HEAL, at, at, Rune.EARTH.color(), 0.8f, caster);
            }
            case LIGHT -> {
                caster.heal((float) (4 * r.power()));
                Fx.play(r.level(), FxEffect.HEAL, at, at, Rune.LIGHT.color(), 1, caster);
            }
            case DARK -> {
                caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, (int) (160 * r.duration() * Math.min(r.power(), 2))));
                Fx.play(r.level(), FxEffect.VORTEX, at, at, Rune.DARK.color(), 0.5f, null);
            }
            // Lightning through one's own body: it does what lightning does.
            case LIGHTNING -> strike(r, at.add(0, 1, 0), 0.5, true);
        }
    }

    /** The caster steps through nothing to where the arrow ends, and arrives there. */
    private static Vec3 shift(Release r) {
        ServerPlayer caster = r.caster();
        Vec3 from = caster.position();
        Vec3 step = r.direction().normalize().scale(0.25);
        Vec3 to = from;
        // Walked a quarter block at a time, so the step never passes through a wall or ends inside one.
        for (double travelled = 0; travelled < r.distance(); travelled += 0.25) {
            Vec3 next = to.add(step);
            if (!r.level().noCollision(caster, caster.getBoundingBox().move(next.subtract(from)))) {
                // Aimed a little down into the ground or up against a ceiling: slide along it instead.
                next = to.add(step.x, 0, step.z);
                if (step.horizontalDistanceSqr() < 1e-4
                        || !r.level().noCollision(caster, caster.getBoundingBox().move(next.subtract(from)))) break;
            }
            to = next;
        }
        caster.teleportTo(r.level(), to.x, to.y, to.z, Set.<Relative>of(), caster.getYRot(), caster.getXRot(), true);
        caster.resetFallDistance();
        Fx.play(r.level(), FxEffect.TELEPORT, from, to, color(r), 1, null);
        r.level().playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.4f);
        return to;
    }

    /** The first living thing or block along a direction, or the end of the distance. */
    static Vec3 aim(Release r, Vec3 eye, Vec3 direction, double distance) {
        Vec3 end = eye.add(direction.normalize().scale(distance));
        BlockHitResult block = r.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                r.caster()));
        Vec3 stop = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : end;
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(r.level(), r.caster(),
                eye, stop, new AABB(eye, stop).inflate(1), e -> e instanceof LivingEntity && e != r.caster() && e.isAlive(), 0.3f);
        if (hit != null) return hit.getLocation();
        if (block.getType() == HitResult.Type.BLOCK) {
            // Just in front of the face that was hit, so the effect sits in the air beside it.
            return stop.add(block.getDirection().getUnitVec3().scale(0.3));
        }
        return stop;
    }

    /** The ground under a point: where a trap is laid. */
    private static Vec3 ground(ServerLevel level, Vec3 at) {
        BlockHitResult down = level.clip(new ClipContext(at, at.add(0, -6, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return down.getType() == HitResult.Type.BLOCK ? down.getLocation().add(0, 0.05, 0) : at;
    }

    static DamageSource damage(Release r) {
        // A sigil's magic may outlast its maker's presence; then it is nobody's.
        return r.caster() == null ? r.level().damageSources().magic()
                : r.level().damageSources().indirectMagic(r.caster(), r.caster());
    }

    static void push(Entity entity, Vec3 velocity) {
        entity.push(velocity);
        entity.needsSync = true;
    }

    /** The colour a spell shows: its element's, paler when it is turned around. */
    static int color(Release r) {
        if (r.core() == null) return Rune.EARTH.color();
        if (!r.inverted()) return r.core().color();
        int c = r.core().color();
        return (((255 - (c >> 16 & 255)) / 2 + 100) << 16) | (((255 - (c >> 8 & 255)) / 2 + 100) << 8) | ((255 - (c & 255)) / 2 + 100);
    }

    /** A small flame catches on the block in front of it, as a flint would light it. */
    private static void ignite(ServerLevel level, Vec3 at, Vec3 look) {
        BlockPos pos = BlockPos.containing(at);
        if (level.getBlockState(pos).isAir() && BaseFireBlock.canBePlacedAt(level, pos, net.minecraft.core.Direction.getApproximateNearest(look))) {
            level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
        }
    }

    /** Water puts out fire around it. */
    private static void douse(ServerLevel level, Vec3 at, double radius) {
        BlockPos center = BlockPos.containing(at);
        int r = (int) Math.ceil(radius);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.FIRE) && pos.distSqr(center) <= radius * radius) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.4f);
            }
        }
    }

    /** What a spell cast just in front looks like: section 33's flame, light and push. */
    /**
     * A spell without auxiliary runes: the element pours from the hand at what is in front, hits
     * everything along the way, and does its work where it lands — sets fire, puts it out, leaves a
     * light hanging there.
     */
    private static void stream(Release r, Vec3 eye, Vec3 look, Vec3 at) {
        double length = eye.distanceTo(at) + 0.5;
        java.util.Set<LivingEntity> hit = new java.util.HashSet<>();
        for (LivingEntity target : r.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, at).inflate(1.5),
                e -> e.isAlive() && e != r.caster() && !e.isSpectator())) {
            Vec3 middle = target.position().add(0, target.getBbHeight() / 2, 0);
            Vec3 to = middle.subtract(eye);
            double along = to.dot(look);
            if (along < 0.3 || along > length) continue;
            // A stream widens a little as it goes: within half a block near the hand, a block and a half far out.
            double off = to.subtract(look.scale(along)).length();
            if (off > 0.5 + along * 0.15 + target.getBbWidth() / 2) continue;
            if (!r.caster().hasLineOfSight(target)) continue;
            hit.add(target);
            payload(r, target, 1, eye);
        }
        for (LivingEntity target : r.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(1.5),
                e -> e.isAlive() && e != r.caster() && !hit.contains(e))) {
            payload(r, target, 0.7, at);
        }
        if (!r.inverted()) {
            switch (r.core()) {
                case FIRE -> ignite(r.level(), at, look);
                case WATER -> douse(r.level(), at, 1.5);
                case LIGHT -> Fx.play(r.level(), FxEffect.LIGHT_ORB, at, at, Rune.LIGHT.color(), (float) r.power(), null);
                default -> { }
            }
        }
        for (Rune element : r.blend()) {
            if (element == Rune.FIRE) ignite(r.level(), at, look);
            if (element == Rune.WATER) douse(r.level(), at, 1);
        }
    }

    /** What a stream looks like: the element's own spray, and a smaller one of every element mixed in. */
    static void spray(Release r, Vec3 from, Vec3 to) {
        float scale = (float) Math.max(0.7, Math.min(2, 0.7 + 0.3 * r.power()));
        Fx.play(r.level(), sprayOf(r.core()), from, to, color(r), scale, null);
        for (Rune element : r.blend()) Fx.play(r.level(), sprayOf(element), from, to, element.color(), scale * 0.55f, null);
    }

    private static FxEffect sprayOf(Rune element) {
        return switch (element) {
            case FIRE -> FxEffect.FLAME_SPRAY;
            case WATER -> FxEffect.WATER_SPRAY;
            case WIND -> FxEffect.WIND_GUST;
            case EARTH -> FxEffect.EARTH_SPIKES;
            case LIGHT -> FxEffect.LIGHT_RAY;
            case DARK -> FxEffect.DARK_CLOUD;
            case LIGHTNING -> FxEffect.CHAIN_LIGHTNING;
        };
    }

    private static void touchEffect(Release r, Vec3 at) {
        if (r.inverted()) {
            Fx.play(r.level(), FxEffect.SPARKLE, at, at, color(r), 0.8f, null);
            return;
        }
        switch (r.core()) {
            case FIRE -> Fx.play(r.level(), FxEffect.NOVA, at, at, Rune.FIRE.color(), 0.3f, null);
            case LIGHT -> Fx.play(r.level(), FxEffect.LIGHT_ORB, at, at, Rune.LIGHT.color(), (float) r.power(), null);
            default -> Fx.play(r.level(), FxEffect.SPARKLE, at, at, r.core().color(), 0.8f, null);
        }
    }

    static void impactEffect(Release r, Vec3 at, double radius) {
        if (r.core() == null) return;
        float scale = (float) Math.max(0.4, Math.min(2.5, radius / 3));
        int color = color(r);
        if (r.inverted()) {
            Fx.play(r.level(), FxEffect.FROST_NOVA, at, at, color, scale, null);
            return;
        }
        switch (r.core()) {
            case FIRE -> Fx.play(r.level(), FxEffect.NOVA, at, at, color, scale, null);
            case WATER, WIND -> Fx.play(r.level(), FxEffect.FROST_NOVA, at, at, color, scale, null);
            case LIGHT -> Fx.play(r.level(), FxEffect.HEAL, at, at, color, scale, null);
            case DARK -> Fx.play(r.level(), FxEffect.VORTEX, at, at, color, scale * 0.6f, null);
            case LIGHTNING -> Fx.play(r.level(), FxEffect.LIGHTNING, at, at, color, Math.max(0.6f, scale * 0.7f), null);
            case EARTH -> Fx.play(r.level(), FxEffect.NOVA, at, at, color, scale * 0.8f, null);
        }
    }

    /** Roughly where the wand is: a little forward, right and down from the eyes. */
    static Vec3 hand(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        return player.getEyePosition().add(look.scale(0.6)).add(right.scale(0.35)).add(0, -0.3, 0);
    }
}
