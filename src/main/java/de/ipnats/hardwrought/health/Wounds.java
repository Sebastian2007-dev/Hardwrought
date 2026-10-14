package de.ipnats.hardwrought.health;

import de.ipnats.hardwrought.core.registry.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Wounds, and keeping them clean.
 *
 * <p>A heavy blow from a monster, or a bite, leaves an {@linkplain ModEffects#OPEN_WOUND open wound} for
 * a couple of minutes, and while it is open dirt can get into it: a wound infection
 * ({@link Disease#WOUND}). A bite is dirtier than a blow — teeth carry more than a sword does. A bandage
 * closes the wound against it; spirit or antiseptic cleans it, and takes some of an infection already
 * there away with it.
 */
public final class Wounds {
    /** How long a wound stays open if nothing is done, in ticks: two minutes. */
    public static final int OPEN_TICKS = 2400;
    /** How long a bandage holds, in ticks: five minutes. */
    public static final int BANDAGE_TICKS = 6000;
    /** Damage from a monster's blow that opens a wound. */
    static final float HEAVY_HIT = 4.0f;
    /** Damage from a bite that opens one: any real bite. */
    static final float BITE = 1.0f;
    /** How often an open wound is checked for dirt getting in, in ticks. */
    static final int CHECK_TICKS = 40;
    /** The chance at each check, per level of the wound: over two minutes about four in ten for a blow. */
    static final double INFECTION_CHANCE = 0.008;

    private Wounds() { }

    /** Whether this bites rather than strikes. */
    public static boolean biter(Entity attacker) {
        return attacker instanceof Zombie || attacker instanceof Wolf || attacker instanceof Spider
                || attacker instanceof Silverfish || attacker instanceof Endermite
                || attacker instanceof net.minecraft.world.entity.animal.polarbear.PolarBear;
    }

    /** A player hurt by something: a bite or a heavy blow opens a wound, and tears any bandage off. */
    public static void hurt(ServerPlayer player, Entity attacker, float taken) {
        if (attacker == null) return;
        boolean bite = biter(attacker) && taken >= BITE;
        boolean blow = attacker instanceof Enemy && taken >= HEAVY_HIT;
        if (!bite && !blow) return;
        player.removeEffect(ModEffects.BANDAGED);
        MobEffectInstance open = player.getEffect(ModEffects.OPEN_WOUND);
        int amplifier = Math.max(bite ? 1 : 0, open == null ? 0 : open.getAmplifier());
        player.addEffect(new MobEffectInstance(ModEffects.OPEN_WOUND, OPEN_TICKS, amplifier));
    }

    /** Bound up: the wound is closed against dirt for as long as the bandage holds. */
    public static void bandage(ServerPlayer player) {
        player.removeEffect(ModEffects.OPEN_WOUND);
        player.addEffect(new MobEffectInstance(ModEffects.BANDAGED, BANDAGE_TICKS, 0));
    }

    /** Cleaned: the wound is closed, and this much of an infection already in it is gone. */
    public static void clean(ServerPlayer player, double load) {
        player.removeEffect(ModEffects.OPEN_WOUND);
        Diseases.treat(player, Disease.WOUND, load);
    }

    /** An open wound: while it lasts, and is not bandaged, dirt may get into it. */
    public static class OpenWound extends MobEffect {
        public OpenWound(MobEffectCategory category, int color) {
            super(category, color);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % CHECK_TICKS == 0;
        }

        @Override
        public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
            // Only a player the survival rules apply to is ever given an open wound (see MobHits).
            if (entity instanceof ServerPlayer player && !player.hasEffect(ModEffects.BANDAGED)
                    && player.getRandom().nextDouble() < INFECTION_CHANCE * (amplifier + 1)) {
                Diseases.wounded(player, 5);
            }
            return true;
        }
    }
}
