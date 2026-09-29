package de.ipnats.hardwrought.mobs;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Mob specification §§ 21 and 22: blazes and magma cubes are hot, and heat is something the survival
 * system already understands. Standing near them raises the ambient temperature the body works
 * against — which heavy metal armor already makes worse, as the metabolism counts armor mass against
 * heat. A magma cube leaves the ground it lands on hot for a while, burning anyone standing on it.
 */
public final class MobHeat {
    /** Ambient heat right beside a blaze, in °C, falling off to nothing at {@value #BLAZE_RANGE} blocks. */
    static final double BLAZE_HEAT = 14, BLAZE_RANGE = 8;
    /** Heat per size step of a magma cube, falling off to nothing at {@value #MAGMA_RANGE} blocks. */
    static final double MAGMA_HEAT_PER_SIZE = 4, MAGMA_RANGE = 4;
    /** Standing on ground a magma cube has just landed on. */
    static final double HOT_SURFACE_HEAT = 8;
    static final double MAX_HEAT = 35;
    /** How long ground stays hot after a magma cube landed on it, in ticks. */
    static final int HOT_SURFACE_TICKS = 200;

    private static final Map<ServerLevel, Long2LongOpenHashMap> hotSurfaces = new WeakHashMap<>();

    private MobHeat() { }

    public static void initialize() {
        ServerTickEvents.END_LEVEL_TICK.register(MobHeat::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> hotSurfaces.clear());
    }

    /** Degrees the mobs about this player add to the air it breathes. */
    public static double nearby(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return 0;
        double heat = 0;
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(BLAZE_RANGE),
                entity -> entity instanceof Blaze || entity instanceof MagmaCube)) {
            double distance = entity.distanceTo(player);
            if (entity instanceof Blaze) {
                heat += BLAZE_HEAT * Math.max(0, 1 - distance / BLAZE_RANGE);
            } else if (entity instanceof MagmaCube cube) {
                heat += MAGMA_HEAT_PER_SIZE * cube.getSize() * Math.max(0, 1 - distance / MAGMA_RANGE);
            }
        }
        if (isHot(level, player.blockPosition().below())) heat += HOT_SURFACE_HEAT;
        return Math.min(MAX_HEAT, heat);
    }

    /** A magma cube came down here: the ground under it is hot for a while. */
    public static void landed(ServerLevel level, BlockPos ground) {
        if (level.getBlockState(ground).isAir()) return;
        hotSurfaces.computeIfAbsent(level, ignored -> new Long2LongOpenHashMap())
                .put(ground.asLong(), level.getGameTime() + HOT_SURFACE_TICKS);
    }

    public static boolean isHot(ServerLevel level, BlockPos ground) {
        Long2LongOpenHashMap hot = hotSurfaces.get(level);
        return hot != null && hot.get(ground.asLong()) > level.getGameTime();
    }

    private static void tick(ServerLevel level) {
        Long2LongOpenHashMap hot = hotSurfaces.get(level);
        if (hot == null || hot.isEmpty() || level.getGameTime() % 10 != 0) return;
        long now = level.getGameTime();
        hot.long2LongEntrySet().removeIf(entry -> entry.getLongValue() <= now);
        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator() || player.isSteppingCarefully()
                    || player.hasEffect(MobEffects.FIRE_RESISTANCE)) continue;
            if (player.onGround() && hot.get(player.blockPosition().below().asLong()) > now) {
                player.hurtServer(level, level.damageSources().hotFloor(), 1.0f);
            }
        }
    }
}
