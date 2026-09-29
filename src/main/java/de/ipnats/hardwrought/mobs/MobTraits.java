package de.ipnats.hardwrought.mobs;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.combat.CombatDamageType;
import de.ipnats.hardwrought.combat.DamageSplit;
import de.ipnats.hardwrought.mixin.MobAccessor;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Bogged;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What each kind of mob is given when it enters the world (mob specification §§ 2–39): its goals and
 * the few attributes that make its role. Goals are added every time a mob is loaded, since goals are
 * not saved; attributes are transient for the same reason; equipment is handed out only once, and the
 * mob is marked for it.
 */
public final class MobTraits {
    static final String OUTFITTED = "hardwrought.outfitted";
    /** § 24: share of melee piglins that carry a shield. */
    static final double PIGLIN_SHIELD_CHANCE = 0.25;

    private static final Identifier HUSK_PURSUIT = Hardwrought.id("husk_pursuit");
    private static final Identifier DROWNED_SWIM = Hardwrought.id("drowned_swim");
    private static final Identifier STEADY = Hardwrought.id("stagger_resistance");
    private static final Identifier GHAST_RANGE = Hardwrought.id("ghast_range");

    private MobTraits() { }

    public static void onLoad(Entity entity, ServerLevel level) {
        if (!(entity instanceof Mob mob)) return;
        var goals = ((MobAccessor) mob).hardwrought$goalSelector();
        boolean firstTime = entity.addTag(OUTFITTED);

        // § 2: a hostile mob with nothing to chase goes to look where it heard something.
        if (mob instanceof Monster monster) goals.addGoal(4, new InvestigateNoiseGoal(monster));

        if (mob instanceof Zombie zombie) {
            // § 5.1: a zombie breaks through with its tool, and without one only through what a bare
            // hand could. Vanilla's door breaking would let any zombie on hard through every wooden
            // door, tool or not.
            zombie.setCanBreakDoors(false);
            goals.addGoal(1, new BreachGoal(zombie));
        }
        // § 6: a husk does not give up a chase.
        if (mob instanceof Husk) modify(mob, Attributes.FOLLOW_RANGE, HUSK_PURSUIT, 0.5);
        // § 7: the drowned are at home in water, and boats are no refuge from them.
        if (mob instanceof Drowned drowned) {
            modify(mob, Attributes.MOVEMENT_SPEED, DROWNED_SWIM, 0.25);
            goals.addGoal(2, new BoatBreakerGoal(drowned));
        }
        // §§ 8–10, 28: skeletons of every kind fight at range, not toe to toe; the bogged from cover.
        if (mob instanceof AbstractSkeleton skeleton) {
            goals.addGoal(3, new RangedTacticsGoal(skeleton, MobTraits::holdsRanged, skeleton instanceof Bogged));
        }
        // §§ 16, 33, 34: the witch, the pillager and the evoker keep their distance too.
        if (mob instanceof Pillager || mob instanceof Evoker || mob instanceof Witch) {
            goals.addGoal(3, new RangedTacticsGoal((PathfinderMob) mob, ignored -> true, false));
        }
        if (mob instanceof Witch witch) goals.addGoal(2, new WitchTactics.SupportGoal(witch));
        // § 11: spiders spin webs; § 12: a cave spider poisons and pulls back.
        if (mob instanceof Spider spider && !(mob instanceof CaveSpider)) goals.addGoal(3, new SpiderWebGoal(spider));
        if (mob instanceof CaveSpider || mob instanceof Vex) goals.addGoal(0, new FleeAfterHitGoal((PathfinderMob) mob));
        // § 15: an angry enderman flanks.
        if (mob instanceof Enderman enderman) goals.addGoal(1, new EndermanFlankGoal(enderman));
        // § 22: hot landings.
        if (mob instanceof MagmaCube cube) goals.addGoal(0, new WatchGoals.HotLanding(cube));
        // § 23: a ghast picks its targets from far off.
        if (mob instanceof Ghast) modify(mob, Attributes.FOLLOW_RANGE, GHAST_RANGE, 0.3);
        // §§ 26 and 27: charges.
        if (mob instanceof Hoglin || mob instanceof Zoglin) goals.addGoal(1, new ChargeGoal(mob));
        // §§ 25, 27, 36: the heavy hitters stand their ground.
        if (mob instanceof PiglinBrute) modify(mob, Attributes.KNOCKBACK_RESISTANCE, STEADY, 0.8);
        if (mob instanceof Zoglin) modify(mob, Attributes.KNOCKBACK_RESISTANCE, STEADY, 0.9);
        // §§ 25, 32, 36, 37: the siege mobs, each breaking only what its role allows (§ 43).
        if (mob instanceof PiglinBrute || mob instanceof Vindicator) {
            goals.addGoal(1, new BreachGoal((PathfinderMob) mob, Mob::getMainHandItem));
        }
        if (mob instanceof Ravager || mob instanceof Warden) {
            // Weak wooden and light construction only: what an axe could do, never masonry or steel.
            ItemStack weight = new ItemStack(Items.IRON_AXE);
            goals.addGoal(1, new BreachGoal((PathfinderMob) mob, ignored -> weight));
        }
        // § 39.2: a creaking is worse in the dark.
        if (mob instanceof Creaking) goals.addGoal(0, new WatchGoals.DarkSpeed(mob));

        if (firstTime && mob instanceof Piglin piglin) {
            ItemStack weapon = piglin.getMainHandItem();
            // § 24: a shield piglin, to stand in front of the crossbows.
            if (!weapon.isEmpty() && !(weapon.getItem() instanceof CrossbowItem) && piglin.getOffhandItem().isEmpty()
                    && level.getRandom().nextDouble() < PIGLIN_SHIELD_CHANCE) {
                piglin.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            }
        }
    }

    /** Throws this entity away from a point, as a heavy blow does. */
    public static void shove(LivingEntity target, double strength, double fromX, double fromZ) {
        net.minecraft.world.phys.Vec3 away = new net.minecraft.world.phys.Vec3(target.getX() - fromX, 0, target.getZ() - fromZ);
        if (away.lengthSqr() < 1.0e-4) return;
        away = away.normalize().scale(strength);
        target.setDeltaMovement(target.getDeltaMovement().add(away.x, Math.min(0.4, strength * 0.3), away.z));
        target.needsSync = true;
    }

    static boolean holdsRanged(PathfinderMob mob) {
        ItemStack held = mob.getMainHandItem();
        return held.getItem() instanceof BowItem || held.getItem() instanceof CrossbowItem;
    }

    private static void modify(Mob mob, Holder<Attribute> attribute, Identifier id, double share) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null || instance.hasModifier(id)) return;
        AttributeModifier.Operation operation = attribute.equals(Attributes.KNOCKBACK_RESISTANCE)
                ? AttributeModifier.Operation.ADD_VALUE : AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
        instance.addTransientModifier(new AttributeModifier(id, share, operation));
    }

    /**
     * § 17: a slime is hard to hurt by hitting it and easy by cutting it. Factor on the damage a slime
     * takes: blunt hits at 60 %, cutting and piercing ones at 130 %, by the weapon's share of each.
     * Every other creature takes damage as before.
     */
    public static double incomingFactor(LivingEntity defender, DamageSplit weapon, CombatDamageType unprofiled) {
        if (!(defender instanceof Slime) || defender instanceof MagmaCube) return 1;
        if (weapon == null) {
            return unprofiled == CombatDamageType.BLUNT ? 0.6
                    : unprofiled == CombatDamageType.SLASH || unprofiled == CombatDamageType.PIERCE ? 1.3 : 1;
        }
        return weapon.share(CombatDamageType.BLUNT) * 0.6
                + (weapon.share(CombatDamageType.SLASH) + weapon.share(CombatDamageType.PIERCE)) * 1.3
                + (1 - weapon.share(CombatDamageType.BLUNT) - weapon.share(CombatDamageType.SLASH)
                        - weapon.share(CombatDamageType.PIERCE));
    }

    /**
     * §§ 25, 27, 36, 37: how long a stagger holds these mobs. The brute and the ravager shake it off
     * quickly, the zoglin almost at once, the warden not at all.
     */
    public static int staggerTicks(LivingEntity entity, int ticks) {
        if (entity instanceof Warden) return 0;
        if (entity instanceof Zoglin) return ticks / 5;
        if (entity instanceof PiglinBrute) return (int) (ticks * 0.3);
        if (entity instanceof Ravager) return ticks / 2;
        return ticks;
    }
}
