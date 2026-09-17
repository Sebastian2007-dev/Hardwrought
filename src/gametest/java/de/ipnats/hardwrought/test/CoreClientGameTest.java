package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.client.debug.DebugHud;
import de.ipnats.hardwrought.client.survival.SurvivalHud;
import de.ipnats.hardwrought.core.networking.SleepRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;

public final class CoreClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        TestWorldSave save;
        long beforeClose;
        try (var world = context.worldBuilder().adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
            save = world.getWorldSave();
            world.getConnection().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                if (!de.ipnats.hardwrought.core.CoreRuntime.mayDebug(world.getConnection().getServerPlayer())) {
                    throw new AssertionError("Test player must have game-master permission");
                }
            });
            context.waitFor(client -> SurvivalHud.snapshot() != null);
            context.runOnClient(client -> SurvivalHud.setDetailsVisible(true));
            context.waitFor(client -> SurvivalHud.detailsVisible());
            context.takeScreenshot("hardwrought-milestone-1-survival-hud");
            verifyAcceleratedSleep(context, world);
            context.runOnClient(client -> client.player.connection.sendCommand("hardwrought debug on"));
            context.waitFor(client -> !DebugHud.snapshot().isEmpty());
            context.runOnClient(client -> {
                if (DebugHud.snapshot().stream().noneMatch(line -> line.contains("server tick="))) {
                    throw new AssertionError("HUD must receive authoritative server snapshot");
                }
                if (SurvivalHud.snapshot().capacityKg() != 45.0) {
                    throw new AssertionError("Survival HUD must receive server carry capacity");
                }
            });
            context.takeScreenshot("hardwrought-milestone-1-hud");
            verifyReload(world);
            world.getServer().runCommand("tick freeze");
            long frozenTick = world.getServer().computeOnServer(server -> CoreLifecycle.require(server).scheduler().ticks());
            context.waitTicks(10);
            long stillFrozen = world.getServer().computeOnServer(server -> CoreLifecycle.require(server).scheduler().ticks());
            if (frozenTick != stillFrozen) throw new AssertionError("Simulation advances while game ticks are frozen");
            world.getServer().runCommand("tick unfreeze");
            context.runOnClient(client -> client.player.connection.sendCommand("hardwrought debug off"));
            context.waitFor(client -> DebugHud.snapshot().isEmpty());
            // Leave enabled to verify disconnect clears all client diagnostics.
            context.runOnClient(client -> client.player.connection.sendCommand("hardwrought debug on"));
            context.waitFor(client -> !DebugHud.snapshot().isEmpty());
            beforeClose = world.getServer().computeOnServer(server -> CoreLifecycle.require(server).scheduler().ticks());
        }
        context.runOnClient(client -> {
            if (!DebugHud.snapshot().isEmpty()) throw new AssertionError("HUD leaked between server sessions");
            if (SurvivalHud.snapshot() != null) throw new AssertionError("Survival HUD leaked between server sessions");
        });
        try (var reopened = save.open()) {
            long afterOpen = reopened.getServer().computeOnServer(server -> CoreLifecycle.require(server).scheduler().ticks());
            if (afterOpen < beforeClose) throw new AssertionError("Simulation clock lost during save/reopen");
            context.runOnClient(client -> {
                if (!DebugHud.snapshot().isEmpty()) throw new AssertionError("Debug subscription leaked into reopened world");
            });
        }
    }

    private static void verifyAcceleratedSleep(ClientGameTestContext context,
                                               net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        world.getServer().runCommand("difficulty peaceful");
        world.getServer().waitFor(server -> !server.getPlayerList().getPlayers().isEmpty()
                && server.getPlayerList().getPlayers().getFirst().onGround(), 400);
        context.runOnClient(client -> ClientPlayNetworking.send(new SleepRequestPayload()));
        world.getServer().waitFor(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            return player.isSleeping() && server.tickRateManager().tickrate() > 20.0f;
        }, 400);
        float accelerated = world.getServer().computeOnServer(server -> server.tickRateManager().tickrate());
        if (accelerated != 100.0f) throw new AssertionError("One sleeping single-player must accelerate real ticks to 100 TPS");
        context.runOnClient(client -> ClientPlayNetworking.send(new SleepRequestPayload()));
        world.getServer().waitFor(server -> !server.getPlayerList().getPlayers().getFirst().isSleeping()
                && server.tickRateManager().tickrate() == 20.0f, 400);
    }

    private static void verifyReload(net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        try {
            var pack = world.getWorldSave().getSaveDirectory().resolve("datapacks/hardwrought-test-override");
            var material = pack.resolve("data/hardwrought/hardwrought/materials/copper.json");
            java.nio.file.Files.createDirectories(material.getParent());
            java.nio.file.Files.writeString(pack.resolve("pack.mcmeta"),
                    "{\"pack\":{\"description\":\"Hardwrought reload test\",\"min_format\":[121,0],\"max_format\":[121,0]}}");
            java.nio.file.Files.writeString(material, "{\"tier\":3,\"density_kg_m3\":8960,\"melting_point_c\":1084.62}");
            var serverContext = world.getServer();
            var validReload = serverContext.computeOnServer(server -> {
                server.getPackRepository().reload();
                var selected = new java.util.ArrayList<>(server.getPackRepository().getSelectedIds());
                selected.add("file/hardwrought-test-override");
                return server.reloadResources(selected);
            });
            serverContext.waitFor(server -> validReload.isDone(), 1_200);
            validReload.join();
            serverContext.runOnServer(server -> {
                if (CoreLifecycle.require(server).materials().get(de.ipnats.hardwrought.Hardwrought.id("copper")).tier() != 3) {
                    throw new AssertionError("Datapack override was not applied");
                }
            });
            java.nio.file.Files.writeString(material, "{\"tier\":-8}");
            var invalidReload = serverContext.computeOnServer(server -> server.reloadResources(server.getPackRepository().getSelectedIds()));
            serverContext.waitFor(server -> invalidReload.isDone(), 1_200);
            if (!invalidReload.isCompletedExceptionally()) throw new AssertionError("Invalid datapack was accepted");
            serverContext.runOnServer(server -> {
                if (CoreLifecycle.require(server).materials().get(de.ipnats.hardwrought.Hardwrought.id("copper")).tier() != 3) {
                    throw new AssertionError("Failed reload changed the active material table");
                }
            });
            // Restore a valid pack before testing a world restart.
            java.nio.file.Files.writeString(material, "{\"tier\":3,\"density_kg_m3\":8960,\"melting_point_c\":1084.62}");
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
