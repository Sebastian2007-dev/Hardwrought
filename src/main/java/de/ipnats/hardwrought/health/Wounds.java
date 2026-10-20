package de.ipnats.hardwrought.health;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

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
 * <p>A heavy blow from a monster, or a bite, leaves an {@linkplain ModEffects#OPEN_WOUND open wound}, and
 * while it is open dirt can get into it: a wound infection ({@link Disease#WOUND}). Only what cuts or
 * bites opens one — fire, frost, drowning and magic hurt, but leave no wound.
 *
 * <p>How bad the wound is follows how hard the blow was, in three levels (the effect's amplifier):
 *
 * <pre>
 *   I    superficial   a graze: closes in a minute, and rarely takes dirt (about one in twenty)
 *   II   deep          two minutes, about one in four
 *   III  severe        three minutes, about two in three
 * </pre>
 *
 * A bite is a level worse than a blow as hard — teeth carry more than a sword does. A bandage closes the
 * wound against dirt; spirit or antiseptic cleans it, and takes some of an infection already there away
 * with it.
 */
public final class Wounds {
    /** How long a wound stays open if nothing is done, in ticks, by level: one, two and three minutes. */
    static final int[] OPEN_TICKS = {1200, 2400, 3600};
    /** How long a bandage holds, in ticks: five minutes. */
    public static final int BANDAGE_TICKS = 6000;
    /** Damage from a monster's blow that opens a wound, and that opens a deep one, and a severe one. */
    static final float HEAVY_HIT = 4.0f;
    static final float DEEP_HIT = 7.0f;
    static final float SEVERE_HIT = 12.0f;
    /** Damage from a bite that opens one: any real bite. */
    static final float BITE = 1.0f;
    /** How often an open wound is checked for dirt getting in, in ticks. */
    static final int CHECK_TICKS = 40;
    /** The chance at each check that dirt gets in, by level. */
    static final double[] INFECTION_CHANCE = {0.0017, 0.0048, 0.012};

    private Wounds() { }

    /** Whether this bites rather than strikes. */
    public static boolean biter(Entity attacker) {
        return attacker instanceof Zombie || attacker instanceof Wolf || attacker instanceof Spider
                || attacker instanceof Silverfish || attacker instanceof Endermite
                || attacker instanceof net.minecraft.world.entity.animal.polarbear.PolarBear;
    }

    /** Whether this kind of harm can open a wound at all: what cuts or bites, not fire, frost, water or magic. */
    public static boolean cuts(DamageSource source) {
        return !source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypeTags.IS_FREEZING)
                && !source.is(DamageTypeTags.IS_DROWNING) && !source.is(DamageTypes.MAGIC)
                && !source.is(DamageTypes.INDIRECT_MAGIC) && !source.is(DamageTypes.WITHER)
                && !source.is(de.ipnats.hardwrought.core.registry.ModDamageTypes.HEX)
                && !source.is(de.ipnats.hardwrought.core.registry.ModDamageTypes.ELECTROCUTION)
                && !source.is(de.ipnats.hardwrought.core.registry.ModDamageTypes.VOID);
    }

    /** How bad a wound a blow this hard leaves, 0 to 2; a bite one level worse. */
    public static int level(float taken, boolean bite) {
        int level = taken >= SEVERE_HIT ? 2 : taken >= DEEP_HIT ? 1 : 0;
        return Math.min(2, level + (bite ? 1 : 0));
    }

    /** A player hurt by something: a bite or a heavy blow opens a wound, and tears any bandage off. */
    public static void hurt(ServerPlayer player, DamageSource source, float taken) {
        Entity attacker = source.getEntity();
        if (attacker == null || !cuts(source)) return;
        boolean bite = biter(attacker) && taken >= BITE;
        boolean blow = attacker instanceof Enemy && taken >= HEAVY_HIT;
        if (!bite && !blow) return;
        player.removeEffect(ModEffects.BANDAGED);
        MobEffectInstance open = player.getEffect(ModEffects.OPEN_WOUND);
        // A new wound on an old one: the worse of the two, and open for as long as the worse needs.
        int amplifier = Math.max(level(taken, bite), open == null ? 0 : open.getAmplifier());
        player.addEffect(new MobEffectInstance(ModEffects.OPEN_WOUND, OPEN_TICKS[amplifier], amplifier));
    }

    /** The chance that dirt gets into a wound this bad over the whole time it stays open, if left. */
    public static double infectionOdds(int level) {
        int checks = OPEN_TICKS[level] / CHECK_TICKS;
        return 1.0 - Math.pow(1.0 - INFECTION_CHANCE[level], checks);
    }

    /** Bound up: the wound is closed against dirt for as long as the bandage holds. */
    public static void bandage(ServerPlayer player) {
        bandage(player, BANDAGE_TICKS);
    }

    /** Bound for this long: a sterile dressing holds twice as long as a plain bandage. */
    public static void bandage(ServerPlayer player, int ticks) {
        player.removeEffect(ModEffects.OPEN_WOUND);
        player.addEffect(new MobEffectInstance(ModEffects.BANDAGED, ticks, 0));
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
                    && player.getRandom().nextDouble() < INFECTION_CHANCE[Math.clamp(amplifier, 0, 2)]) {
                Diseases.wounded(player, 5);
            }
            return true;
        }
    }
}
