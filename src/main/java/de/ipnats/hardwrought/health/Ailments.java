package de.ipnats.hardwrought.health;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * What harm leaves behind when it is not a cut or a bite (those open a {@linkplain Wounds wound}). Each
 * kind of harm leaves its own:
 *
 * <pre>
 *   fire, lava       burn              may become inflamed, like a wound; water cools it, a bandage keeps it clean
 *   frost            frostbite         slower on the feet and with a pick; warmth heals it faster
 *   drowning         water in the lungs  a cough that costs breath; past the first level it may turn to a cold
 *   magic, wither    drain             breath comes back slower, hunger faster
 * </pre>
 *
 * <p>Each comes in three levels, and the level is how much of the harm was taken: every hit adds to how
 * long the ailment lasts, and the longer it is, the worse — a brush with a flame is a first-degree
 * burn, a while on fire a second, lava a third. It eases off as it runs down.
 */
public final class Ailments {
    /** The longest any of them lasts: three minutes. */
    static final int MOST_TICKS = 3600;
    /** From this many ticks left an ailment is at its second level, and from this many at its third. */
    static final int SECOND = 900, THIRD = 2000;
    /** How often an ailment does what it does, in ticks. */
    static final int CHECK_TICKS = 20;
    /** Ticks each point of harm adds, by kind. */
    static final int BURN_PER_DAMAGE = 150, FROST_PER_DAMAGE = 200, LUNGS_PER_DAMAGE = 300, DRAIN_PER_DAMAGE = 200;
    /** The chance at each check that a burn becomes inflamed, by level: about as often as a wound that bad. */
    static final double[] INFLAMMATION_CHANCE = {0.0008, 0.0024, 0.006};
    /** The chance at each check that water in the lungs becomes a cold, by level. */
    static final double[] COLD_CHANCE = {0.0, 0.002, 0.006};

    private Ailments() { }

    /**
     * Once a second, outside the effects' own ticks (which may not change the effects they run in): water
     * cools a burn, warmth thaws frostbite, and each ailment drops a level as it runs down.
     */
    public static void initialize() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % CHECK_TICKS != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) settle(player.level(), player);
        });
    }

    private static final java.util.List<Holder<MobEffect>> ALL = java.util.List.of(
            ModEffects.BURN, ModEffects.FROSTBITE, ModEffects.WATER_IN_LUNGS, ModEffects.DRAIN);

    static void settle(ServerLevel level, ServerPlayer player) {
        if (ALL.stream().noneMatch(player::hasEffect)) return;
        if (player.hasEffect(ModEffects.BURN) && player.isInWater()) ease(player, ModEffects.BURN, CHECK_TICKS * 9);
        // The search for a fire nearby is a box of blocks: only for frostbite, and never into an unloaded chunk.
        if (player.hasEffect(ModEffects.FROSTBITE) && player.getTicksFrozen() == 0
                && (level.dimension() == Level.NETHER || level.hasChunkAt(player.blockPosition())
                        && de.ipnats.hardwrought.environment.RadiantHeat.nearby(level, player.blockPosition()) > 2.0)) {
            ease(player, ModEffects.FROSTBITE, CHECK_TICKS * 2);
        }
        for (Holder<MobEffect> ailment : ALL) {
            MobEffectInstance now = player.getEffect(ailment);
            if (now != null && level(now.getDuration()) < now.getAmplifier()) setLeft(player, ailment, now.getDuration());
        }
    }

    /** A player harmed by something: what kind of harm it was decides what, if anything, it leaves. */
    public static void harmed(ServerPlayer player, DamageSource source, float taken) {
        // Creative and spectating players are left out by the caller, as for wounds (see MobHits).
        if (taken <= 0) return;
        if (source.is(DamageTypeTags.IS_FIRE)) {
            worsen(player, ModEffects.BURN, taken * BURN_PER_DAMAGE);
        } else if (source.is(DamageTypeTags.IS_FREEZING)) {
            worsen(player, ModEffects.FROSTBITE, taken * FROST_PER_DAMAGE);
        } else if (source.is(DamageTypeTags.IS_DROWNING)) {
            worsen(player, ModEffects.WATER_IN_LUNGS, taken * LUNGS_PER_DAMAGE);
        } else if (drains(source)) {
            worsen(player, ModEffects.DRAIN, taken * DRAIN_PER_DAMAGE);
        }
    }

    /** Magic sent by someone, and the wither; not the sting of spirit on a wound or a potion drunk. */
    static boolean drains(DamageSource source) {
        return source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL) || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypes.MAGIC) && source.getEntity() != null;
    }

    /** The level an ailment is at with this many ticks left, 0 to 2. */
    public static int level(int ticks) {
        return ticks >= THIRD ? 2 : ticks >= SECOND ? 1 : 0;
    }

    /** Adds to an ailment, or begins it, and sets its level by how long it now has to run. */
    public static void worsen(LivingEntity entity, Holder<MobEffect> ailment, double ticks) {
        MobEffectInstance now = entity.getEffect(ailment);
        int left = Math.min(MOST_TICKS, (now == null ? 0 : now.getDuration()) + (int) Math.ceil(ticks));
        setLeft(entity, ailment, left);
    }

    private static void setLeft(LivingEntity entity, Holder<MobEffect> ailment, int left) {
        entity.removeEffect(ailment);
        if (left > 0) entity.addEffect(new MobEffectInstance(ailment, left, level(left)));
    }

    /** Takes this many ticks more off an ailment, easing it; for water on a burn and warmth on frostbite. */
    static void ease(LivingEntity entity, Holder<MobEffect> ailment, int ticks) {
        MobEffectInstance now = entity.getEffect(ailment);
        if (now == null) return;
        setLeft(entity, ailment, now.getDuration() - ticks);
    }

    /** How much slower breath comes back, for the drain. */
    public static double recoveryFactor(Player player) {
        MobEffectInstance drain = player.getEffect(ModEffects.DRAIN);
        return drain == null ? 1.0 : 1.0 - 0.2 * (drain.getAmplifier() + 1);
    }

    /** How much hungrier, for the drain. */
    public static double hungerFactor(Player player) {
        MobEffectInstance drain = player.getEffect(ModEffects.DRAIN);
        return drain == null ? 1.0 : 1.0 + 0.25 * (drain.getAmplifier() + 1);
    }

    /** Common to the four: they act once a second, and only on players the survival rules apply to. */
    abstract static class Ailment extends MobEffect {
        Ailment(MobEffectCategory category, int color) {
            super(category, color);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % CHECK_TICKS == 0;
        }

        @Override
        public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
            if (entity instanceof ServerPlayer player && !player.isCreative() && !player.isSpectator()) {
                act(level, player, Math.clamp(amplifier, 0, 2));
            }
            return true;
        }

        abstract void act(ServerLevel level, ServerPlayer player, int amplifier);
    }

    /** A burn: smoulders, may become inflamed unless bandaged, and cools ten times as fast in water. */
    public static class Burn extends Ailment {
        public Burn(MobEffectCategory category, int color) {
            super(category, color);
        }

        @Override
        void act(ServerLevel level, ServerPlayer player, int amplifier) {
            if (player.isInWater()) {
                // Cooling, which settle() does; here only the steam of it.
                if (level.getRandom().nextInt(3) == 0) {
                    level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
                }
                return;
            }
            if (level.getRandom().nextInt(4 - amplifier) == 0) {
                level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 1 + amplifier, 0.2, 0.3, 0.2, 0.005);
            }
            if (!player.hasEffect(ModEffects.BANDAGED) && level.getRandom().nextDouble() < INFLAMMATION_CHANCE[amplifier]) {
                Diseases.wounded(player, 5);
            }
        }
    }

    /** Frostbite: slows the feet and the pick, and thaws three times as fast where it is warm. */
    public static class Frostbite extends Ailment {
        public Frostbite(MobEffectCategory category, int color) {
            super(category, color);
            addAttributeModifier(Attributes.MOVEMENT_SPEED, Hardwrought.id("frostbite_speed"), -0.06,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            addAttributeModifier(Attributes.BLOCK_BREAK_SPEED, Hardwrought.id("frostbite_mining"), -0.12,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }

        @Override
        void act(ServerLevel level, ServerPlayer player, int amplifier) {
            if (level.getRandom().nextInt(4 - amplifier) == 0) {
                level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.0, player.getZ(), 1 + amplifier, 0.25, 0.4, 0.25, 0.0);
            }
        }
    }

    /** Water in the lungs: a cough that costs breath, and past the first level, perhaps a cold. */
    public static class WaterInLungs extends Ailment {
        public WaterInLungs(MobEffectCategory category, int color) {
            super(category, color);
        }

        @Override
        void act(ServerLevel level, ServerPlayer player, int amplifier) {
            if (level.getRandom().nextInt(6 - amplifier * 2) == 0) {
                var runtime = CoreLifecycle.find(level.getServer());
                if (runtime != null) runtime.survival().spendStamina(player, 1.0 + amplifier);
                level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT_DROWN, SoundSource.PLAYERS, 0.4f, 1.4f);
                level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getEyeY(), player.getZ(), 4, 0.15, 0.05, 0.15, 0.1);
            }
            if (level.getRandom().nextDouble() < COLD_CHANCE[amplifier]) Diseases.infect(player, Disease.COLD, 5);
        }
    }

    /** The drain: breath back slower and hunger faster (read by the survival metabolism); it shows. */
    public static class Drain extends Ailment {
        public Drain(MobEffectCategory category, int color) {
            super(category, color);
        }

        @Override
        void act(ServerLevel level, ServerPlayer player, int amplifier) {
            if (level.getRandom().nextInt(4 - amplifier) == 0) {
                level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(), 1 + amplifier, 0.25, 0.4, 0.25, 0.0);
            }
        }
    }
}
