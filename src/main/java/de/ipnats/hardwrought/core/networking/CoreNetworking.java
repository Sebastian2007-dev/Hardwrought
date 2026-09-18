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
        PayloadTypeRegistry.serverboundPlay().register(SleepRequestPayload.TYPE, SleepRequestPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SleepRequestPayload.TYPE,
                (payload, context) -> CoreLifecycle.require(context.server()).survival().toggleSleep(context.player()));
    }
}
