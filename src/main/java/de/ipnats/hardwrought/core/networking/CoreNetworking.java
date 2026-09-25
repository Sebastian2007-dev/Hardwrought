package de.ipnats.hardwrought.core.networking;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.equipment.InventorySorting;

public final class CoreNetworking {
    private CoreNetworking() { }

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(DebugSnapshotPayload.TYPE, DebugSnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SurvivalSnapshotPayload.TYPE, SurvivalSnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CombatSnapshotPayload.TYPE, CombatSnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EnvironmentSnapshotPayload.TYPE, EnvironmentSnapshotPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CompendiumPagePayload.TYPE, CompendiumPagePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(KnowledgeNotePayload.TYPE, KnowledgeNotePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ItemWeightPayload.TYPE, ItemWeightPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MeltingPointPayload.TYPE, MeltingPointPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FoodNutrientsPayload.TYPE, FoodNutrientsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ForgingPayloads.Open.TYPE, ForgingPayloads.Open.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ForgingPayloads.Begin.TYPE, ForgingPayloads.Begin.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ForgingPayloads.Strike.TYPE, ForgingPayloads.Strike.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ForgingPayloads.Cancel.TYPE, ForgingPayloads.Cancel.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SleepRequestPayload.TYPE, SleepRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CompendiumRequestPayload.TYPE, CompendiumRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(GasPushPayload.TYPE, GasPushPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(GasPushPayload.TYPE, (payload, context) ->
                de.ipnats.hardwrought.environment.GasPush.push(context.player(), payload.pos(), payload.direction()));
        PayloadTypeRegistry.serverboundPlay().register(ScrollMovePayload.TYPE, ScrollMovePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ScrollMovePayload.TYPE, (payload, context) ->
                de.ipnats.hardwrought.equipment.ScrollTransfer.scroll(context.player(), payload.containerId(),
                        payload.slot(), payload.push()));
        PayloadTypeRegistry.serverboundPlay().register(SortInventoryPayload.TYPE, SortInventoryPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SortInventoryPayload.TYPE, (payload, context) ->
                InventorySorting.sort(context.player(), payload.containerId(), payload.slot()));
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
