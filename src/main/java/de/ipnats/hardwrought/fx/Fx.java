package de.ipnats.hardwrought.fx;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The server's side of the FX module: it only says which effect plays where. Every client in range
 * then plays it on its own; nothing about the effect is simulated on the server.
 */
public final class Fx {
    /** Players farther away than this do not receive the effect. */
    public static final double RANGE = 160;

    private Fx() { }

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(FxPayload.TYPE, FxPayload.CODEC);
        FxCommands.initialize();
    }

    /** Play an effect for everyone near it. A colour of 0 keeps the effect's own colour. */
    public static void play(ServerLevel level, FxEffect effect, Vec3 pos, Vec3 target, int color, float scale,
                            Entity follow) {
        FxPayload payload = new FxPayload(effect.id(), pos, target, color == 0 ? effect.defaultColor() : color,
                scale, follow == null ? -1 : follow.getId());
        for (ServerPlayer player : PlayerLookup.around(level, pos, RANGE)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void play(ServerLevel level, FxEffect effect, Vec3 pos) {
        play(level, effect, pos, pos, 0, 1, null);
    }

    /** Play an effect for one player only, such as a flash of their own view. */
    public static void playFor(ServerPlayer player, FxEffect effect, Vec3 pos, Vec3 target, int color, float scale) {
        ServerPlayNetworking.send(player, new FxPayload(effect.id(), pos, target,
                color == 0 ? effect.defaultColor() : color, scale, player.getId()));
    }
}
