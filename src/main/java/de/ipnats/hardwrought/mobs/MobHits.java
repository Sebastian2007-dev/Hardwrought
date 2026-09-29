package de.ipnats.hardwrought.mobs;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModEffects;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a hit does besides the wound, per mob (mob specification §§ 6, 7, 9, 12, 35, 40, 41). Every
 * effect uses a system the player already knows — hydration, body temperature, stamina — rather than
 * more damage.
 */
public final class MobHits {
    /** § 6: water a husk's hit draws out of the player. */
    static final double HUSK_WATER = 4;
    /** § 9: degrees of body heat a stray's arrow takes. */
    static final double STRAY_CHILL = 1.2;
    /** §§ 6 and 9: ticks a player stays winded, recovering stamina at {@value #WINDED_RECOVERY} of the pace. */
    static final int WINDED_TICKS = 100;
    public static final double WINDED_RECOVERY = 0.5;
    /** § 7: how hard a drowned pulls a swimmer down. */
    static final double DROWNED_PULL = 0.45;
    /** § 12 and § 35: ticks a cave spider or a vex backs off after it has struck. */
    static final int CAVE_SPIDER_RETREAT = 60, VEX_RETREAT = 40;

    /** Until when, in game ticks, each mob keeps its distance after a hit; see {@link FleeAfterHitGoal}. */
    private static final Map<UUID, Long> retreating = new ConcurrentHashMap<>();

    private MobHits() { }

    public static void initialize() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register(MobHits::afterDamage);
    }

    static boolean retreating(LivingEntity mob) {
        Long until = retreating.get(mob.getUUID());
        if (until == null) return false;
        if (until <= mob.level().getGameTime()) {
            retreating.remove(mob.getUUID());
            return false;
        }
        return true;
    }

    private static void afterDamage(LivingEntity victim, DamageSource source, float baseDamage, float taken, boolean blocked) {
        if (!(victim.level() instanceof ServerLevel level)) return;
        if (victim instanceof WitherBoss wither) Bosses.witherHurt(level, wither);
        if (victim instanceof EnderDragon dragon) Bosses.dragonHurt(level, dragon);
        if (blocked || taken <= 0) return;
        // A fleeing mob that is hit turns on its pursuer.
        if (victim instanceof net.minecraft.world.entity.Mob fleeing) Panic.hurt(fleeing);
        Entity attacker = source.getEntity();
        if (attacker instanceof CaveSpider spider) retreat(spider, CAVE_SPIDER_RETREAT);
        if (attacker instanceof Vex vex) retreat(vex, VEX_RETREAT);
        if (!(victim instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) return;
        var runtime = CoreLifecycle.find(level.getServer());
        if (attacker instanceof Husk && runtime != null) {
            runtime.survival().afflict(player, -HUSK_WATER, 0, 0);
            player.addEffect(new MobEffectInstance(ModEffects.WINDED, WINDED_TICKS));
        }
        if (attacker instanceof Stray && runtime != null) {
            runtime.survival().afflict(player, 0, -STRAY_CHILL, 0);
            player.addEffect(new MobEffectInstance(ModEffects.WINDED, WINDED_TICKS));
        }
        if (attacker instanceof Drowned && player.isInWater()) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, Math.min(motion.y, -DROWNED_PULL), motion.z);
            player.needsSync = true;
        }
    }

    static void stopRetreat(LivingEntity mob) {
        retreating.remove(mob.getUUID());
    }

    private static void retreat(LivingEntity mob, int ticks) {
        retreating.put(mob.getUUID(), mob.level().getGameTime() + ticks);
    }
}
