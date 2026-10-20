package de.ipnats.hardwrought.survival;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * A player who comes to see a sleeper — back from death, through a portal, or just arriving — is told
 * again, a moment later, that the sleeper is asleep and where they lie. The first sight of them is sent
 * by vanilla with everything else, but a player who had died and come back saw the sleepers of the
 * night standing; sent once more after that first sight has settled, the sleeping place and pose hold.
 * (The client also lays every sleeper down whatever it was told: {@code AvatarRendererMixin}.)
 */
public final class SleeperSync {
    /** How long after the first sight it is said again, in ticks. */
    static final int DELAY = 5;

    private record Pending(ServerPlayer sleeper, ServerPlayer viewer, long at) { }

    private static final List<Pending> PENDING = new ArrayList<>();

    private SleeperSync() { }

    public static void initialize() {
        EntityTrackingEvents.START_TRACKING.register((tracked, viewer) -> {
            if (tracked instanceof ServerPlayer sleeper && sleeper.isSleeping()) {
                PENDING.add(new Pending(sleeper, viewer, viewer.level().getServer().getTickCount() + DELAY));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(SleeperSync::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        long now = server.getTickCount();
        List<Pending> due = new ArrayList<>();
        PENDING.removeIf(pending -> {
            if (pending.at() > now) return false;
            due.add(pending);
            return true;
        });
        for (Pending pending : due) {
            ServerPlayer sleeper = pending.sleeper(), viewer = pending.viewer();
            if (sleeper.isRemoved() || viewer.isRemoved() || !sleeper.isSleeping() || viewer.level() != sleeper.level()) continue;
            var values = sleeper.getEntityData().getNonDefaultValues();
            if (values != null) viewer.connection.send(new ClientboundSetEntityDataPacket(sleeper.getId(), values));
            viewer.connection.send(ClientboundEntityPositionSyncPacket.of(sleeper));
        }
    }
}
