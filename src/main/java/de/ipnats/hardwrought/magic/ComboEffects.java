package de.ipnats.hardwrought.magic;

import de.ipnats.hardwrought.core.registry.ModEffects;
import de.ipnats.hardwrought.fx.Fx;
import de.ipnats.hardwrought.fx.FxEffect;
import de.ipnats.hardwrought.water.Freezing;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What the combinations in {@link Combos} do. Each takes the spell as it was released — its power,
 * area and duration already judged — and the point it acts at; a field's is called once a pulse with
 * the field's weakened spell.
 */
final class ComboEffects {
    /** A veil lasts at least this long, in ticks, and at most {@link #VEIL_MOST}: long enough to get away. */
    static final int VEIL_LEAST = 100, VEIL_MOST = 200;
    /** A shroud hides those in it this long, renewed every pulse while they stay. */
    private static final int SHROUD_HOLD = 30;

    private ComboEffects() { }

    static void initialize() {
        // The ember skin answers a blow struck by hand: the hand that struck it burns.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, taken, blocked) -> {
            if (!victim.hasEffect(ModEffects.EMBER_SKIN) || blocked) return;
            if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker && attacker != victim) {
                attacker.igniteForSeconds(4);
                if (victim.level() instanceof ServerLevel level) {
                    Vec3 at = attacker.position().add(0, attacker.getBbHeight() / 2, 0);
                    Fx.play(level, FxEffect.SPARKLE, at, at, Rune.FIRE.color(), 0.5f, null);
                }
            }
        });
    }

    // --- Worked on oneself -----------------------------------------------------------------------

    /** Darkness on oneself: the veil, five seconds for a plain rune, up to ten for a strong one. */
    static void veil(SpellEffects.Release r, Vec3 at) {
        Veil.apply(r.caster(), veilTicks(r));
    }

    static int veilTicks(SpellEffects.Release r) {
        return (int) Math.max(VEIL_LEAST, Math.min(VEIL_MOST, VEIL_LEAST * r.power() * r.duration()));
    }

    /** Darkness and Wind on oneself: veiled and fleet, gone before anyone knows where. */
    static void shadowStep(SpellEffects.Release r, Vec3 at) {
        ServerPlayer caster = r.caster();
        int ticks = veilTicks(r);
        Veil.apply(caster, ticks);
        caster.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1, false, false, true));
        Vec3 look = caster.getLookAngle().multiply(1, 0, 1);
        Fx.play(r.level(), FxEffect.WIND_GUST, at.add(0, 0.5, 0), at.add(look.scale(3)).add(0, 0.5, 0), Rune.WIND.color(), 0.6f, null);
    }

    /** Fire and Earth on oneself: a skin of embers. Fire cannot hurt its bearer, blows hurt less, and a hand that strikes it burns. */
    static void emberSkin(SpellEffects.Release r, Vec3 at) {
        ServerPlayer caster = r.caster();
        int ticks = (int) (300 * r.duration() * Math.max(0.6, Math.min(1.6, r.power())));
        caster.clearFire();
        caster.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, ticks + 100));
        caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ticks, 0));
        caster.addEffect(new MobEffectInstance(ModEffects.EMBER_SKIN, ticks, 0, false, false, true));
        Fx.play(r.level(), FxEffect.EMBER_SKIN, at, at, Rune.FIRE.color(), 1, caster);
    }

    /** Water and Light on oneself: washed clean of every poison, curse and weakness, and mended. */
    static void cleansingTide(SpellEffects.Release r, Vec3 at) {
        ServerPlayer caster = r.caster();
        caster.clearFire();
        List<Holder<MobEffect>> bad = caster.getActiveEffects().stream().map(MobEffectInstance::getEffect)
                .filter(effect -> effect.value().getCategory() == MobEffectCategory.HARMFUL).toList();
        bad.forEach(caster::removeEffect);
        caster.heal((float) (3 * r.power()));
        Fx.play(r.level(), FxEffect.HEAL, at, at, Rune.WATER.color(), 1, caster);
        Fx.play(r.level(), FxEffect.SPARKLE, at.add(0, 1, 0), at, Rune.LIGHT.color(), 0.8f, null);
    }

    // --- Lingering, once every pulse -------------------------------------------------------------

    /**
     * Light lingering: a ring on the ground that mends everyone on the caster's side standing in it,
     * the caster too, and burns the undead. Cast inside a circle, it goes along with its caster.
     */
    static void healingRing(SpellEffects.Release r, Vec3 at) {
        double radius = r.area() + 1;
        Vec3 ground = SpellEffects.ground(r.level(), at);
        Fx.play(r.level(), FxEffect.HEAL_RING, ground, ground, Rune.LIGHT.color(), (float) radius, null);
        for (LivingEntity target : within(r, at, radius)) {
            if (target.isInvertedHealAndHarm()) {
                target.hurtServer(r.level(), SpellEffects.damage(r), (float) (3 * r.power()));
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40), r.caster());
            } else if (Allies.ally(r.caster(), target) && target.getHealth() < target.getMaxHealth()) {
                target.heal((float) (4 * r.power()));
                Vec3 middle = target.position().add(0, target.getBbHeight() * 0.6, 0);
                Fx.play(r.level(), FxEffect.SPARKLE, middle, middle, Rune.LIGHT.color(), 0.35f, null);
            }
        }
    }

    /**
     * Darkness lingering: a shroud. Whoever is on the caster's side within it is veiled for as long as
     * they stay; anyone else in it is blinded.
     */
    static void shroud(SpellEffects.Release r, Vec3 at) {
        double radius = r.area() + 1;
        Vec3 ground = SpellEffects.ground(r.level(), at);
        Fx.play(r.level(), FxEffect.SHROUD, ground, ground, Rune.DARK.color(), (float) radius, null);
        for (LivingEntity target : within(r, at, radius)) {
            if (Allies.ally(r.caster(), target)) {
                Veil.apply(target, SHROUD_HOLD);
            } else {
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40), r.caster());
                target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40), r.caster());
            }
        }
    }

    /** Earth and Water lingering: a mire that holds the feet of everyone not on the caster's side. */
    static void mire(SpellEffects.Release r, Vec3 at) {
        double radius = r.area() + 1;
        Vec3 ground = SpellEffects.ground(r.level(), at);
        Fx.play(r.level(), FxEffect.MIRE, ground, ground, Rune.EARTH.color(), (float) radius, null);
        for (LivingEntity target : foes(r, at, radius)) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 3), r.caster());
            // Sucked down: no leaping clear of it.
            Vec3 v = target.getDeltaMovement();
            if (v.y > 0) SpellEffects.push(target, new Vec3(-v.x * 0.5, -v.y, -v.z * 0.5));
        }
    }

    /** Fire and Water lingering: scalding steam, which blinds and burns everyone not on the caster's side, and puts fires out. */
    static void steam(SpellEffects.Release r, Vec3 at) {
        double radius = r.area() + 1;
        Vec3 ground = SpellEffects.ground(r.level(), at);
        Fx.play(r.level(), FxEffect.STEAM, ground, ground, 0xE8F0F4, (float) radius, null);
        for (LivingEntity target : foes(r, at, radius)) {
            target.hurtServer(r.level(), SpellEffects.damage(r), (float) (3 * r.power()));
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30), r.caster());
        }
        SpellEffects.douse(r.level(), at, radius);
    }

    // --- Bursting --------------------------------------------------------------------------------

    /** Fire and Wind bursting: a firestorm, wider than any fire alone, that sets everyone in it alight and flings them out. */
    static void firestorm(SpellEffects.Release r, Vec3 at) {
        double radius = r.area() + 3;
        Fx.play(r.level(), FxEffect.FIRESTORM, at, at, Rune.FIRE.color(), (float) (radius / 3), null);
        for (LivingEntity target : others(r, at, radius)) {
            double share = share(target, at, radius);
            target.hurtServer(r.level(), SpellEffects.damage(r), (float) (3 * r.power() * share));
            target.igniteForSeconds((float) (5 * r.duration()));
            SpellEffects.push(target, outward(target, at).scale(0.9 * r.power() * share).add(0, 0.3, 0));
        }
    }

    /** Earth and Wind bursting: a shockwave through the ground that throws everyone around it into the air. */
    static void shockwave(SpellEffects.Release r, Vec3 at) {
        double radius = r.area() + 2;
        Vec3 ground = SpellEffects.ground(r.level(), at);
        Fx.play(r.level(), FxEffect.SHOCKWAVE, ground, ground, Rune.EARTH.color(), (float) (radius / 3), null);
        for (LivingEntity target : others(r, at, radius)) {
            double share = share(target, at, radius);
            target.hurtServer(r.level(), SpellEffects.damage(r), (float) (2 * r.power() * share));
            SpellEffects.push(target, outward(target, at).scale(0.5 * share).add(0, 0.6 + 0.25 * Math.min(r.power(), 2), 0));
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), r.caster());
        }
    }

    /**
     * Fire turned around and Water, bursting: flash-freeze. Everyone in it is frozen stiff, and the
     * open water around it skins over with ice that thaws again as any ice would.
     */
    static void flashFreeze(SpellEffects.Release r, Vec3 at) {
        double radius = r.area() + 1;
        Fx.play(r.level(), FxEffect.FROST_NOVA, at, at, 0xA8E8FF, (float) Math.max(0.6, radius / 2.5), null);
        for (LivingEntity target : others(r, at, radius)) {
            target.clearFire();
            target.setTicksFrozen(target.getTicksRequiredToFreeze() + (int) (200 * r.power()));
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) (60 * r.duration()), 3), r.caster());
            target.hurtServer(r.level(), SpellEffects.damage(r), (float) (2 * r.power() * share(target, at, radius)));
        }
        BlockPos center = BlockPos.containing(at);
        int reach = (int) Math.ceil(radius);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-reach, -3, -reach), center.offset(reach, 1, reach))) {
            if (pos.distToCenterSqr(at.x, pos.getY() + 0.5, at.z) <= radius * radius) Freezing.freeze(r.level(), pos.immutable());
        }
        r.level().playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 1.6f);
    }

    // --- Sent along an arrow -----------------------------------------------------------------------

    /**
     * Lightning and Water along an arrow: a storm conduit. Where it lands everyone is soaked, and the
     * lightning runs through the wet from body to body, further and more often than through air.
     */
    static void stormConduit(SpellEffects.Release r, Vec3 at) {
        double radius = r.area() + 2;
        Fx.play(r.level(), FxEffect.WATER_SPRAY, at.add(0, 2, 0), at, Rune.WATER.color(), 1.2f, null);
        Fx.play(r.level(), FxEffect.LIGHTNING, at, at, Rune.LIGHTNING.color(), 1, null);
        LivingEntity first = others(r, at, radius).stream()
                .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(at))).orElse(null);
        if (first == null) return;
        SpellEffects.chain(r, first, r.power(), SpellEffects.damage(r), 5 + (r.power() >= 1.5 ? 1 : 0), 8, true);
    }

    // --- Lying in wait -----------------------------------------------------------------------------

    /** An Earth trap: a snare. Whoever steps on it is held fast, unable to take a step, for three seconds. */
    static void snare(SpellEffects.Release r, Vec3 at) {
        Fx.play(r.level(), FxEffect.MIRE, at, at, Rune.EARTH.color(), 1.2f, null);
        Fx.play(r.level(), FxEffect.EARTH_SPIKES, at, at.add(0, 1.5, 0), Rune.EARTH.color(), 0.8f, null);
        for (LivingEntity target : others(r, at, 2)) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) (60 * r.duration()), 6), r.caster());
            target.hurtServer(r.level(), SpellEffects.damage(r), (float) r.power());
            SpellEffects.push(target, target.getDeltaMovement().scale(-1));
        }
    }

    /** A Lightning trap: whoever steps on it is stunned stiff, and the shock leaps to whoever stands by. */
    static void stunGlyph(SpellEffects.Release r, Vec3 at) {
        Fx.play(r.level(), FxEffect.LIGHTNING, at, at, Rune.LIGHTNING.color(), 0.9f, null);
        List<LivingEntity> victims = others(r, at, 2);
        for (LivingEntity target : victims) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) (40 * r.duration()), 6), r.caster());
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) (80 * r.duration()), 1), r.caster());
        }
        if (!victims.isEmpty()) SpellEffects.chain(r, victims.getFirst(), r.power() * 0.7, SpellEffects.damage(r), 2, 5, false);
    }

    /**
     * A Light trap: a flare. Whoever steps on it is dazzled and left glowing for all to see, and any veil
     * near it is torn — the answer to someone hiding.
     */
    static void flare(SpellEffects.Release r, Vec3 at) {
        Fx.play(r.level(), FxEffect.NOVA, at, at, Rune.LIGHT.color(), 1.2f, null);
        Fx.play(r.level(), FxEffect.LIGHT_ORB, at.add(0, 1.5, 0), at, Rune.LIGHT.color(), 0.5f, null);
        for (LivingEntity target : others(r, at, 5)) {
            Veil.lift(target);
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) (200 * r.duration())), r.caster());
            if (target.distanceToSqr(at) <= 4) target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60), r.caster());
        }
    }

    // --- Who is reached ----------------------------------------------------------------------------

    /** Every living thing within a radius of a point, the caster included. */
    static List<LivingEntity> within(SpellEffects.Release r, Vec3 at, double radius) {
        ServerLevel level = r.level();
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius + 1, 2.5, radius + 1),
                e -> e.isAlive() && !e.isSpectator()
                        && e.position().add(0, e.getBbHeight() / 2, 0).multiply(1, 0, 1).distanceTo(at.multiply(1, 0, 1))
                        <= radius + e.getBbWidth() / 2);
    }

    /** Everyone within reach but the caster. */
    static List<LivingEntity> others(SpellEffects.Release r, Vec3 at, double radius) {
        return within(r, at, radius).stream().filter(e -> e != r.caster()).toList();
    }

    /** Everyone within reach who is not on the caster's side. */
    static List<LivingEntity> foes(SpellEffects.Release r, Vec3 at, double radius) {
        return within(r, at, radius).stream().filter(e -> !Allies.ally(r.caster(), e)).toList();
    }

    /** Full strength at the heart, half at the edge. */
    private static double share(LivingEntity target, Vec3 at, double radius) {
        return 1 - 0.5 * Math.min(1, target.position().distanceTo(at) / Math.max(1, radius));
    }

    private static Vec3 outward(LivingEntity target, Vec3 at) {
        Vec3 away = target.position().subtract(at).multiply(1, 0, 1);
        return away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
    }
}
