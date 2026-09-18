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
            if (runtime != null) runtime.survival().shutdown();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            CoreRuntime runtime = RUNTIMES.get(server);
            if (runtime != null) {
                runtime.removeViewer(handler.player.getUUID());
                runtime.survival().disconnect(handler.player.getUUID());
                runtime.combat().disconnect(handler.player.getUUID());
                runtime.environment().disconnect(handler.player.getUUID());
            }
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
