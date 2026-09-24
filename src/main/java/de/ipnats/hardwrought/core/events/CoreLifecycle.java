package de.ipnats.hardwrought.core.events;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.CoreRuntime;
import de.ipnats.hardwrought.core.utilities.ServerThread;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CoreLifecycle {
    private static final Map<MinecraftServer, CoreRuntime> RUNTIMES = new ConcurrentHashMap<>();

    private CoreLifecycle() { }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CoreRuntime runtime = new CoreRuntime(server);
            RUNTIMES.put(server, runtime);
            CoreEvents.SERVER_READY.invoker().onReady(runtime);
            Hardwrought.LOGGER.info("Core ready: {} materials, saved simulation tick {}",
                    runtime.materials().size(), runtime.scheduler().ticks());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> require(server).tick());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CoreRuntime runtime = RUNTIMES.remove(server);
            if (runtime != null) {
                runtime.survival().shutdown();
                runtime.waterFlow().shutdown();
            }
        });
        // A pack cannot be lost, and that has to hold across the one event that takes everything
        // else: a player who has died comes back wearing one.
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) -> {
                    CoreRuntime runtime = RUNTIMES.get(newPlayer.level().getServer());
                    if (runtime != null) runtime.equipment().respawned(newPlayer);
                });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // The weight table lives in a datapack, so the client is told it once rather than
            // guessing at what every item weighs.
            CoreRuntime runtime = RUNTIMES.get(server);
            if (runtime != null && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
                    .canSend(handler.player, de.ipnats.hardwrought.core.networking.ItemWeightPayload.TYPE)) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(handler.player,
                        new de.ipnats.hardwrought.core.networking.ItemWeightPayload(runtime.itemWeights()));
            }
            // Melting points likewise, so an ore can say which furnace it needs.
            if (runtime != null && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
                    .canSend(handler.player, de.ipnats.hardwrought.core.networking.MeltingPointPayload.TYPE)) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(handler.player,
                        new de.ipnats.hardwrought.core.networking.MeltingPointPayload(
                                de.ipnats.hardwrought.metallurgy.Smelting.itemMeltingPoints(runtime.materials())));
            }
            if (runtime == null) return;
            // The first time a player is seen they are given what the mod assumes they start with.
            // Every time after that, only the guarantee that they are wearing a pack at all — which
            // also quietly fits out every player of a world saved before packs existed.
            if (handler.player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM
                    .get(net.minecraft.stats.Stats.PLAY_TIME)) == 0) {
                runtime.equipment().welcome(handler.player);
            } else {
                runtime.equipment().ensureBackpack(handler.player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            CoreRuntime runtime = RUNTIMES.get(server);
            if (runtime != null) {
                runtime.removeViewer(handler.player.getUUID());
                runtime.knowledge().removePlayer(handler.player.getUUID());
                runtime.survival().disconnect(handler.player.getUUID());
                runtime.combat().disconnect(handler.player.getUUID());
                runtime.environment().disconnect(handler.player.getUUID());
            }
            // Half-finished work on a log or a bench is not carried over a reconnect.
            de.ipnats.hardwrought.progression.LogWorking.forget(handler.player.getUUID());
            de.ipnats.hardwrought.progression.NailDriving.forget(handler.player.getUUID());
        });
    }

    public static CoreRuntime require(MinecraftServer server) {
        ServerThread.require(server);
        CoreRuntime runtime = RUNTIMES.get(server);
        if (runtime == null) throw new IllegalStateException("Hardwrought server services are not ready");
        return runtime;
    }

    public static CoreRuntime find(MinecraftServer server) {
        return server == null ? null : RUNTIMES.get(server);
    }
}
