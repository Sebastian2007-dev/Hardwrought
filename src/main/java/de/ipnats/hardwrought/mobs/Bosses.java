package de.ipnats.hardwrought.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Mob specification §§ 40 and 41: the bosses fight in phases, and bring help.
 *
 * <p><b>Wither.</b> Vanilla already lets it break blocks and fly; Hardwrought's statics make that
 * matter, since a column it breaks brings down what stood on it. Here it also summons wither
 * skeletons at half and at a quarter of its health — so trapping it under stone is no longer a quiet
 * kill.
 *
 * <p><b>Ender dragon.</b> Phase 1 is vanilla's aerial fight. Below three quarters of its health,
 * phase 2: endermen come to its defence. Below 45 %, phase 3: it comes down and presses the fight on
 * the ground. Below 20 %, phase 4: it charges the nearest player again and again.
 */
public final class Bosses {
    static final String WITHER_HALF = "hardwrought.wither_adds_half", WITHER_QUARTER = "hardwrought.wither_adds_quarter";
    static final String DRAGON_2 = "hardwrought.dragon_phase_2", DRAGON_3 = "hardwrought.dragon_phase_3",
            DRAGON_4 = "hardwrought.dragon_phase_4";
    static final int WITHER_ADDS = 3, DRAGON_ENDERMEN = 4;
    /** In the final phase, the share of hits that turn the dragon into a charge. */
    static final double FINAL_CHARGE_CHANCE = 0.3;

    private Bosses() { }

    static void witherHurt(ServerLevel level, WitherBoss wither) {
        float share = wither.getHealth() / wither.getMaxHealth();
        if (share < 0.5f && wither.addTag(WITHER_HALF)) summon(level, wither, EntityTypes.WITHER_SKELETON, WITHER_ADDS, 3);
        if (share < 0.25f && wither.addTag(WITHER_QUARTER)) summon(level, wither, EntityTypes.WITHER_SKELETON, WITHER_ADDS, 3);
    }

    static void dragonHurt(ServerLevel level, EnderDragon dragon) {
        float share = dragon.getHealth() / dragon.getMaxHealth();
        if (share < 0.75f && dragon.addTag(DRAGON_2)) {
            BlockPos origin = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dragon.getFightOrigin());
            for (int i = 0; i < DRAGON_ENDERMEN; i++) {
                BlockPos at = origin.offset(level.getRandom().nextInt(17) - 8, 0, level.getRandom().nextInt(17) - 8);
                at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
                Mob enderman = EntityTypes.ENDERMAN.spawn(level, at, EntitySpawnReason.REINFORCEMENT);
                ServerPlayer target = nearestPlayer(level, dragon);
                if (enderman != null && target != null) enderman.setTarget(target);
            }
        }
        if (share < 0.45f && dragon.addTag(DRAGON_3)) {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.LANDING_APPROACH);
        }
        if (share < 0.2f) {
            dragon.addTag(DRAGON_4);
            ServerPlayer target = nearestPlayer(level, dragon);
            if (target != null && level.getRandom().nextDouble() < FINAL_CHARGE_CHANCE) {
                dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(target.position());
                dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
            }
        }
    }

    private static void summon(ServerLevel level, LivingEntity boss, EntityType<? extends Mob> type, int count, int spread) {
        ServerPlayer target = nearestPlayer(level, boss);
        for (int i = 0; i < count; i++) {
            BlockPos at = boss.blockPosition().offset(level.getRandom().nextInt(spread * 2 + 1) - spread, 0,
                    level.getRandom().nextInt(spread * 2 + 1) - spread);
            // Down to the ground under the boss, which may be hovering.
            while (at.getY() > level.getMinY() && level.getBlockState(at.below()).isAir()) at = at.below();
            Mob add = type.spawn(level, at, EntitySpawnReason.REINFORCEMENT);
            if (add != null && target != null) add.setTarget(target);
        }
    }

    private static ServerPlayer nearestPlayer(ServerLevel level, LivingEntity boss) {
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator()) continue;
            double distance = player.distanceToSqr(boss);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }
}
