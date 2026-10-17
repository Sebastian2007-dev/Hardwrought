package de.ipnats.hardwrought.mobs;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;

/**
 * Mob specification §§ 40 and 41: the bosses fight in phases, and bring help.
 *
 * <p><b>Wither.</b> Vanilla already lets it break blocks and fly; Hardwrought's statics make that
 * matter, since a column it breaks brings down what stood on it. Here it also summons wither
 * skeletons at half and at a quarter of its health — so trapping it under stone is no longer a quiet
 * kill.
 *
 * <p><b>Ender dragon.</b> It has an AI of its own now, with its own phases: see {@code VoidDragon}.
 */
public final class Bosses {
    static final String WITHER_HALF = "hardwrought.wither_adds_half", WITHER_QUARTER = "hardwrought.wither_adds_quarter";
    static final int WITHER_ADDS = 3;

    private Bosses() { }

    static void witherHurt(ServerLevel level, WitherBoss wither) {
        float share = wither.getHealth() / wither.getMaxHealth();
        if (share < 0.5f && wither.addTag(WITHER_HALF)) summon(level, wither, EntityTypes.WITHER_SKELETON, WITHER_ADDS, 3);
        if (share < 0.25f && wither.addTag(WITHER_QUARTER)) summon(level, wither, EntityTypes.WITHER_SKELETON, WITHER_ADDS, 3);
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
