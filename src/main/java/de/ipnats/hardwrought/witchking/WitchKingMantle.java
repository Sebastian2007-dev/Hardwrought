package de.ipnats.hardwrought.witchking;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Witch King's mantle, what his fight gives: the magic way to fly (endgame § 4). Worn on the back it
 * glides as elytra do; and held jump, while gliding, drives the wearer on along their gaze with a rush of
 * soul fire — at the cost of the mantle itself, mended with pale shards.
 */
public class WitchKingMantle extends Item {
    /** How hard it drives on, per tick, and the fastest it drives to. */
    static final double THRUST = 0.09, TOP_SPEED = 1.9;
    /** Ticks of thrust per point of wear. */
    static final int WEAR_EVERY = 12;

    public WitchKingMantle(Properties properties) {
        super(properties);
    }

    static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(WitchKingMantle::tick);
        MantleFlight.initialize();
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isFallFlying()) continue;
            ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
            if (!worn.is(WitchKingContent.MANTLE)) continue;
            ServerLevel level = player.level();
            // A thin trail of souls behind anyone gliding on it.
            if (player.tickCount % 3 == 0) level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 0.6, player.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
            if (!player.getLastClientInput().jump() || worn.getDamageValue() >= worn.getMaxDamage() - 1) continue;
            drive(player, level, worn);
        }
    }

    /** One tick of driving on along the gaze. */
    public static void drive(ServerPlayer player, ServerLevel level, ItemStack worn) {
        Vec3 look = player.getLookAngle();
        Vec3 velocity = player.getDeltaMovement().add(look.scale(THRUST));
        if (velocity.length() > TOP_SPEED) velocity = velocity.normalize().scale(TOP_SPEED);
        player.setDeltaMovement(velocity);
        player.needsSync = true;
        Vec3 behind = player.position().add(0, 0.6, 0).subtract(look.scale(0.8));
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, behind.x, behind.y, behind.z, 3, 0.15, 0.15, 0.15, 0.02);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, behind.x, behind.y, behind.z, 2, 0.2, 0.2, 0.2, 0.05);
        if (player.tickCount % 16 == 0) level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(),
                SoundSource.PLAYERS, 0.8f, 1.4f);
        if (player.tickCount % WEAR_EVERY == 0) worn.hurtAndBreak(1, player, EquipmentSlot.CHEST);
    }
}
