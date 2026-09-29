package de.ipnats.hardwrought.mobs;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Mob specification § 16: a witch reads the fight. Its potion is chosen for the target in front of
 * it, and when it fights among other monsters it supports them.
 *
 * <ul>
 *   <li>a target nearly dead gets harming;</li>
 *   <li>one in heavy armor gets weakness — the armor does not help against it;</li>
 *   <li>one that is fast gets slowness;</li>
 *   <li>otherwise poison, and harming once poisoned.</li>
 * </ul>
 * Healing its raider allies stays vanilla's.
 */
public final class WitchTactics {
    static final float LOW_HEALTH = 6;
    static final int HEAVY_ARMOR = 12;
    static final double FAST = 0.2;
    /** A support potion needs at least this many fighting monsters around the witch. */
    static final int GROUP = 3;
    static final double GROUP_RANGE = 8;
    static final int SUPPORT_TICKS = 200;

    private WitchTactics() { }

    /** The potion to throw at this target; vanilla's own choice stands where it is healing an ally. */
    public static Holder<Potion> choose(Holder<Potion> vanilla, LivingEntity target) {
        if (vanilla.is(Potions.HEALING) || vanilla.is(Potions.REGENERATION)) return vanilla;
        if (target.getHealth() <= LOW_HEALTH) return Potions.HARMING;
        if (target.getArmorValue() >= HEAVY_ARMOR && !target.hasEffect(MobEffects.WEAKNESS)) return Potions.WEAKNESS;
        double speed = target.getDeltaMovement().horizontalDistance();
        if ((target.isSprinting() || speed > FAST) && !target.hasEffect(MobEffects.SLOWNESS)) return Potions.SLOWNESS;
        if (!target.hasEffect(MobEffects.POISON)) return Potions.POISON;
        return Potions.HARMING;
    }

    /** Throws strength into the midst of the monsters it fights beside. */
    public static class SupportGoal extends Goal {
        private final Witch witch;
        private long readyAt;

        public SupportGoal(Witch witch) {
            this.witch = witch;
        }

        private List<Monster> allies() {
            return witch.level().getEntitiesOfClass(Monster.class, witch.getBoundingBox().inflate(GROUP_RANGE),
                    mob -> mob != witch && !(mob instanceof Witch) && mob.getTarget() != null && !mob.hasEffect(MobEffects.STRENGTH));
        }

        @Override
        public boolean canUse() {
            if (!(witch.level() instanceof ServerLevel level) || level.getGameTime() < readyAt || witch.getTarget() == null) return false;
            return allies().size() >= GROUP;
        }

        @Override
        public void start() {
            ServerLevel level = (ServerLevel) witch.level();
            readyAt = level.getGameTime() + SUPPORT_TICKS;
            List<Monster> allies = allies();
            Vec3 centre = Vec3.ZERO;
            for (Monster ally : allies) centre = centre.add(ally.position());
            centre = centre.scale(1.0 / allies.size());
            ThrownSplashPotion potion = new ThrownSplashPotion(level, witch,
                    PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRENGTH));
            Vec3 aim = centre.subtract(witch.getEyePosition());
            potion.shoot(aim.x, aim.y + aim.horizontalDistance() * 0.2, aim.z, 0.75f, 4.0f);
            level.addFreshEntity(potion);
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }
}
