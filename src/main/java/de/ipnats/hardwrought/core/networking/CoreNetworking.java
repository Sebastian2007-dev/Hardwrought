package de.ipnats.hardwrought.core.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import de.ipnats.hardwrought.core.events.CoreLifecycle;

public final class CoreNetworking {
    private CoreNetworking() { }

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(DebugSnapshotPayload.TYPE, DebugSnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SurvivalSnapshotPayload.TYPE, SurvivalSnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CombatSnapshotPayload.TYPE, CombatSnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EnvironmentSnapshotPayload.TYPE, EnvironmentSnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CompendiumPagePayload.TYPE, CompendiumPagePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SleepRequestPayload.TYPE, SleepRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CompendiumRequestPayload.TYPE, CompendiumRequestPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SleepRequestPayload.TYPE,
                (payload, context) -> CoreLifecycle.require(context.server()).survival().toggleSleep(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(CompendiumRequestPayload.TYPE, (payload, context) -> {
            // The server is the only side that may know what this player has found out, so it builds
            // the page rather than the client reading the recipe table for itself.
            var knowledge = CoreLifecycle.require(context.server()).knowledge();
            ServerPlayNetworking.send(context.player(),
                    knowledge.compendium().answer(context.player(), knowledge, payload));
        });
    }
}
