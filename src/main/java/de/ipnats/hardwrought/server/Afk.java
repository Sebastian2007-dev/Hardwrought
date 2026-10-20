package de.ipnats.hardwrought.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who is away from the keyboard. A server full of hardship has two places where someone who is not
 * there gets in the way of those who are: the night, which only passes quickly when everyone sleeps,
 * and a boss fight, which grows with the number of people in it. Someone away counts for neither.
 *
 * <p>Vanilla already keeps the moment of each player's last act — moving, turning, chatting, using a
 * thing — for its idle kick; that is what is read here, so nothing new is tracked per tick. A player
 * can also say so with {@code /afk}, which holds until their next act.
 *
 * <p>Changes are announced to everyone, and the player list shows them greyed out with a mark.
 */
public final class Afk {
    /** How often, in ticks, the server looks who has gone or come back. */
    static final int CHECK_TICKS = 100;
    static final long GRACE_MILLIS = 2000;

    /**
     * Players who said they are away, with the moment they said it. The command that says it is an act
     * itself, and vanilla may note it a moment before or after running it, so acts within
     * {@link #GRACE_MILLIS} of saying it do not count as coming back.
     */
    private static final Map<UUID, Long> DECLARED = new HashMap<>();
    /** Who was away at the last look, so only changes are announced. */
    private static final Set<UUID> AWAY = new HashSet<>();

    private Afk() { }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % CHECK_TICKS == 0) check(server);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            DECLARED.remove(handler.player.getUUID());
            AWAY.remove(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            DECLARED.clear();
            AWAY.clear();
        });
    }

    /**
     * Whether this player is away: said so and has not acted since, or has done nothing for the
     * configured time. A sleeper is never away — lying still is what sleeping is.
     */
    public static boolean away(ServerPlayer player) {
        if (player == null || player.isSleeping()) return false;
        Long declared = DECLARED.get(player.getUUID());
        if (declared != null) {
            if (player.getLastActionTime() <= declared + GRACE_MILLIS) return true;
            DECLARED.remove(player.getUUID());
        }
        int minutes = ServerConfig.get().afkMinutes();
        return minutes > 0 && Util.getMillis() - player.getLastActionTime() > minutes * 60_000L;
    }

    /** {@code /afk}: away until the next act. Returns false when they already were. */
    public static boolean declare(ServerPlayer player) {
        boolean was = away(player);
        DECLARED.put(player.getUUID(), Util.getMillis());
        if (!was) changed(player, true);
        return !was;
    }

    /** For tests: takes back a declaration without announcing anything. */
    public static void forget(UUID player) {
        DECLARED.remove(player);
        AWAY.remove(player);
    }

    /** Whether this player is announced as away right now; what the player list shows. */
    public static boolean shown(UUID player) {
        return AWAY.contains(player);
    }

    /** The players taking part in the world: online, not spectating, and not away. */
    public static List<ServerPlayer> present(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream().filter(player -> !player.isSpectator() && !away(player)).toList();
    }

    private static void check(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean now = away(player);
            if (now != AWAY.contains(player.getUUID())) changed(player, now);
        }
    }

    private static void changed(ServerPlayer player, boolean away) {
        if (away) AWAY.add(player.getUUID());
        else AWAY.remove(player.getUUID());
        MinecraftServer server = player.level().getServer();
        server.getPlayerList().broadcastSystemMessage(Component.translatable(
                away ? "message.hardwrought.afk.away" : "message.hardwrought.afk.back", player.getDisplayName())
                .withStyle(ChatFormatting.GRAY), false);
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of(player)));
    }

    /** What the player list shows for someone away: their name, greyed, with a mark before it. */
    public static Component tabName(ServerPlayer player, Component vanilla) {
        if (!AWAY.contains(player.getUUID())) return vanilla;
        Component name = vanilla != null ? vanilla : player.getDisplayName();
        return Component.literal("[AFK] ").withStyle(ChatFormatting.DARK_GRAY)
                .append(name.copy().withStyle(ChatFormatting.GRAY));
    }
}
