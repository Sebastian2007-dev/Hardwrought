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
        PayloadTypeRegistry.clientboundPlay().register(SeasonPayload.TYPE, SeasonPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CompendiumPagePayload.TYPE, CompendiumPagePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(KnowledgeNotePayload.TYPE, KnowledgeNotePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(JournalNotePayload.TYPE, JournalNotePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ItemWeightPayload.TYPE, ItemWeightPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MeltingPointPayload.TYPE, MeltingPointPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FoodNutrientsPayload.TYPE, FoodNutrientsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ForgingPayloads.Open.TYPE, ForgingPayloads.Open.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ForgingPayloads.Begin.TYPE, ForgingPayloads.Begin.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ForgingPayloads.Strike.TYPE, ForgingPayloads.Strike.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ForgingPayloads.Cancel.TYPE, ForgingPayloads.Cancel.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AtlasPayloads.Tiles.TYPE, AtlasPayloads.Tiles.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AtlasPayloads.Markers.TYPE, AtlasPayloads.Markers.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AtlasPayloads.Bearers.TYPE, AtlasPayloads.Bearers.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AtlasPayloads.Request.TYPE, AtlasPayloads.Request.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AtlasPayloads.Mark.TYPE, AtlasPayloads.Mark.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AtlasPayloads.Unmark.TYPE, AtlasPayloads.Unmark.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UpgradePayloads.Open.TYPE, UpgradePayloads.Open.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpgradePayloads.Finish.TYPE, UpgradePayloads.Finish.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RunePayloads.Open.TYPE, RunePayloads.Open.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RunePayloads.Write.TYPE, RunePayloads.Write.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GrindingPayloads.Open.TYPE, GrindingPayloads.Open.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(GrindingPayloads.Pass.TYPE, GrindingPayloads.Pass.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SleepRequestPayload.TYPE, SleepRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DrinkByHandPayload.TYPE, DrinkByHandPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(DrinkByHandPayload.TYPE,
                (payload, context) -> de.ipnats.hardwrought.water.WaterEvents.drinkByHand(context.player()));
        PayloadTypeRegistry.serverboundPlay().register(CompendiumRequestPayload.TYPE, CompendiumRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LookPayloads.Request.TYPE, LookPayloads.Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LookPayloads.Answer.TYPE, LookPayloads.Answer.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(LookPayloads.Request.TYPE, (payload, context) ->
                ServerPlayNetworking.send(context.player(),
                        CoreLifecycle.require(context.server()).knowledge().look(context.player(), payload)));
        PayloadTypeRegistry.serverboundPlay().register(LookPayloads.Inside.TYPE, LookPayloads.Inside.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LookPayloads.Contents.TYPE, LookPayloads.Contents.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(LookPayloads.Inside.TYPE, (payload, context) ->
                ServerPlayNetworking.send(context.player(), de.ipnats.hardwrought.knowledge.LookInside.of(
                        context.player(),
                        CoreLifecycle.require(context.server()).knowledge().knowledge(context.player()),
                        payload.pos())));
        PayloadTypeRegistry.clientboundPlay().register(StoragePayloads.Contents.TYPE, StoragePayloads.Contents.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StoragePayloads.Click.TYPE, StoragePayloads.Click.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StoragePayloads.Click.TYPE, (payload, context) -> {
            if (context.player().containerMenu instanceof de.ipnats.hardwrought.storage.StorageDeskMenu desk
                    && desk.containerId == payload.containerId()) {
                desk.click(payload.clicked(), payload.action());
            }
        });
        PayloadTypeRegistry.serverboundPlay().register(BookmarkPayloads.Toggle.TYPE, BookmarkPayloads.Toggle.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BookmarkPayloads.Ask.TYPE, BookmarkPayloads.Ask.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BookmarkPayloads.Marks.TYPE, BookmarkPayloads.Marks.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BookmarkPayloads.Toggle.TYPE, (payload, context) -> {
            var knowledge = CoreLifecycle.require(context.server()).knowledge();
            knowledge.toggleBookmark(context.player(), payload.subject());
            ServerPlayNetworking.send(context.player(), knowledge.bookmarks(context.player()));
        });
        ServerPlayNetworking.registerGlobalReceiver(BookmarkPayloads.Ask.TYPE, (payload, context) ->
                ServerPlayNetworking.send(context.player(),
                        CoreLifecycle.require(context.server()).knowledge().bookmarks(context.player())));
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
            // The ribbons go with every page: what was a shadow on one may have been held since.
            ServerPlayNetworking.send(context.player(), knowledge.bookmarks(context.player()));
        });
    }
}
