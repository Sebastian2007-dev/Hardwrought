package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.client.combat.CombatHud;
import de.ipnats.hardwrought.client.environment.DynamicLight;
import de.ipnats.hardwrought.client.environment.EnvironmentHud;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.environment.GasMixture;
import de.ipnats.hardwrought.client.debug.DebugHud;
import de.ipnats.hardwrought.combat.CombatDamageType;
import de.ipnats.hardwrought.combat.CombatEvent;
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
        net.minecraft.core.BlockPos waterMark;
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
            verifyDaytimeBedSleep(context, world);
            verifyCombatFeedback(context, world);
            verifyFiniteWater(context, world);
            verifySealedRoomAndCarriedLight(context, world);
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
            // An exact partial amount, parked in the world to be looked for again after a reload.
            // It lives in a chunk attachment, and an attachment registered too late is unknown while
            // chunks are read, so everything stored in them is silently discarded on load.
            waterMark = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                var pos = player.blockPosition().above(8);
                level.setBlockAndUpdate(pos.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    level.setBlockAndUpdate(pos.relative(side),
                            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                }
                de.ipnats.hardwrought.water.WaterStorage.setAmount(level, pos, 340);
                return pos;
            });
            beforeClose = world.getServer().computeOnServer(server -> CoreLifecycle.require(server).scheduler().ticks());
        }
        context.runOnClient(client -> {
            if (!DebugHud.snapshot().isEmpty()) throw new AssertionError("HUD leaked between server sessions");
            if (SurvivalHud.snapshot() != null) throw new AssertionError("Survival HUD leaked between server sessions");
        });
        try (var reopened = save.open()) {
            long afterOpen = reopened.getServer().computeOnServer(server -> CoreLifecycle.require(server).scheduler().ticks());
            if (afterOpen < beforeClose) throw new AssertionError("Simulation clock lost during save/reopen");
            reopened.getServer().runOnServer(server -> {
                var level = server.getPlayerList().getPlayers().getFirst().level();
                int amount = de.ipnats.hardwrought.water.WaterStorage.amount(level, waterMark);
                if (amount != 340) {
                    throw new AssertionError("An exact water amount must survive save and reload, "
                            + "found " + amount + " mB instead of 340");
                }
            });
            context.runOnClient(client -> {
                if (!DebugHud.snapshot().isEmpty()) throw new AssertionError("Debug subscription leaked into reopened world");
            });
        }
    }

    private static void verifyAcceleratedSleep(ClientGameTestContext context,
                                               net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        world.getServer().runCommand("difficulty peaceful");
        // Section 11: sleeping is a choice, not something the clock permits. Broad daylight.
        world.getServer().runCommand("time set noon");
        world.getServer().waitFor(server -> !server.getPlayerList().getPlayers().isEmpty()
                && server.getPlayerList().getPlayers().getFirst().onGround(), 400);
        context.runOnClient(client -> ClientPlayNetworking.send(new SleepRequestPayload()));
        world.getServer().waitFor(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            return player.isSleeping() && server.tickRateManager().tickrate() > 20.0f;
        }, 400);
        float accelerated = world.getServer().computeOnServer(server -> server.tickRateManager().tickrate());
        if (accelerated != 100.0f) throw new AssertionError("One sleeping single-player must accelerate real ticks to 100 TPS");
        // Nothing wakes the player once they are rested; sleeping on builds restlessness instead.
        world.getServer().runOnServer(server -> {
            var runtime = CoreLifecycle.require(server);
            var player = server.getPlayerList().getPlayers().getFirst();
            runtime.survival().setVitalsForTesting(player,
                    runtime.survival().vitals(player).withStress(0).withFatigue(0));
        });
        world.getServer().waitFor(server -> {
            var runtime = CoreLifecycle.require(server);
            var player = server.getPlayerList().getPlayers().getFirst();
            return player.isSleeping() && runtime.survival().vitals(player).stress() > 0;
        }, 600);
        context.waitFor(client -> SurvivalHud.snapshot() != null && SurvivalHud.snapshot().stress() > 0);

        context.runOnClient(client -> ClientPlayNetworking.send(new SleepRequestPayload()));
        world.getServer().waitFor(server -> !server.getPlayerList().getPlayers().getFirst().isSleeping()
                && server.tickRateManager().tickrate() == 20.0f, 400);
    }

    /**
     * A real bed at noon. Lying down was only half the problem: {@code Player#tick} re-reads the bed
     * rule every tick and threw the sleeper straight back out, so the check that matters is that the
     * player is still asleep a second later.
     */
    private static void verifyDaytimeBedSleep(ClientGameTestContext context,
                                              net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        world.getServer().runCommand("time set noon");
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var level = player.level();
            var bedPos = player.blockPosition();
            var block = (net.minecraft.world.level.block.AbstractBedBlock)
                    net.minecraft.world.level.block.Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED);
            var facing = net.minecraft.core.Direction.EAST;
            var foot = block.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, facing);
            level.setBlockAndUpdate(bedPos, foot);
            level.setBlockAndUpdate(bedPos.relative(facing), foot
                    .setValue(net.minecraft.world.level.block.BedBlock.PART,
                            net.minecraft.world.level.block.state.properties.BedPart.HEAD));
            if (level.isDarkOutside()) throw new AssertionError("The test has to run in daylight");
            player.startSleepInBed(block, level.getBlockState(bedPos), block.getBedRule(level, bedPos), bedPos);
        });
        world.getServer().waitFor(server -> server.getPlayerList().getPlayers().getFirst().isSleeping(), 200);
        // Long enough for the per-tick bed check that used to end it to have run many times over.
        context.waitTicks(25);
        world.getServer().runOnServer(server -> {
            if (!server.getPlayerList().getPlayers().getFirst().isSleeping()) {
                throw new AssertionError("A daytime sleeper must not be thrown out of bed again");
            }
        });
        context.takeScreenshot("hardwrought-milestone-1-daytime-bed");
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.stopSleepInBed(true, true);
            var level = player.level();
            var bedPos = player.blockPosition();
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                if (level.getBlockState(bedPos.relative(side)).getBlock()
                        instanceof net.minecraft.world.level.block.AbstractBedBlock) {
                    level.setBlockAndUpdate(bedPos.relative(side),
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
            }
            level.setBlockAndUpdate(bedPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        });
        world.getServer().waitFor(server -> !server.getPlayerList().getPlayers().getFirst().isSleeping(), 200);
    }

    private static void verifyCombatFeedback(ClientGameTestContext context,
                                            net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        world.getServer().runCommand("gamemode survival");
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
            player.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
        });
        world.getServer().waitFor(server -> server.getPlayerList().getPlayers().getFirst().isBlocking(), 400);
        context.waitFor(client -> CombatHud.snapshot() != null
                && CombatHud.snapshot().event() == CombatEvent.GUARD_UP);
        context.runOnClient(client -> {
            if (CombatHud.snapshot().parryWindowTicks() != 6) {
                throw new AssertionError("The client must learn the parry window from the server shield profile");
            }
        });
        context.takeScreenshot("hardwrought-milestone-2-guard");
        world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().stopUsingItem());
        context.waitFor(client -> CombatHud.snapshot().event() == CombatEvent.GUARD_DOWN);
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.hurtServer(player.level(), player.level().damageSources().generic(), 1.0f);
        });
        context.waitFor(client -> CombatHud.activeLabel() == CombatEvent.HIT);
        context.takeScreenshot("hardwrought-milestone-2-hit");
    }

    /**
     * Builds a real sealed room around the player and checks the whole Milestone-3 path: the server
     * recognises the enclosed space, its air is used up, the client is told without being given
     * numbers it has no instrument for, a safety lamp turns those numbers on, and a carried torch
     * lights the room.
     */
    /**
     * Section 23.1 in a live world: two sources with a gap between them used to fill that gap with a
     * third. They must not any more, or every other part of the water system has nothing to stand on.
     */
    private static void verifyFiniteWater(ClientGameTestContext context,
                                          net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var level = player.level();
            var base = player.blockPosition().above(6);
            // A closed stone trough three blocks long, with a source at each end and a gap between.
            for (int x = -2; x <= 2; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        boolean hull = Math.abs(x) == 2 || Math.abs(z) == 1 || y != 0;
                        level.setBlockAndUpdate(base.offset(x, y, z), hull
                                ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    }
                }
            }
            level.setBlockAndUpdate(base.offset(-1, 0, 0),
                    net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
            level.setBlockAndUpdate(base.offset(1, 0, 0),
                    net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
        });
        context.waitTicks(120);
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var level = player.level();
            var middle = player.blockPosition().above(6);
            // Two full blocks went in. Whatever the water did with itself, that is what is left.
            int total = 0;
            for (int x = -1; x <= 1; x++) {
                total += de.ipnats.hardwrought.water.WaterStorage.amount(level, middle.offset(x, 0, 0));
            }
            if (total != 2 * de.ipnats.hardwrought.water.WaterAmounts.BLOCK) {
                throw new AssertionError("Water is not conserved: expected 2000 mB, found " + total);
            }
            if (de.ipnats.hardwrought.water.WaterStorage.amount(level, middle) <= 0) {
                throw new AssertionError("Water did not spread into the gap at all");
            }
            if (level.getFluidState(middle).isSource()) {
                throw new AssertionError("The gap became a full block out of nothing");
            }
            var runtime = CoreLifecycle.require(server);
            int table = runtime.water().groundwaterLevel(level, middle);
            if (table >= level.getSeaLevel()) {
                throw new AssertionError("The water table must lie below sea level");
            }
            // Clean the trough up again.
            for (int x = -2; x <= 2; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        level.setBlockAndUpdate(middle.offset(x, y, z),
                                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    }
                }
            }
        });
    }

    private static void verifySealedRoomAndCarriedLight(ClientGameTestContext context,
                                                        net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> shell(server, net.minecraft.world.level.block.Blocks.STONE));
        context.waitFor(client -> EnvironmentHud.snapshot() != null && EnvironmentHud.snapshot().sealed(), 600);
        context.waitFor(client -> EnvironmentHud.snapshot().gases().oxygen()
                < GasMixture.OUTDOOR_OXYGEN - 0.0005, 600);
        context.runOnClient(client -> {
            if (EnvironmentHud.snapshot().instrumented()) {
                throw new AssertionError("Bad air must not be readable without an instrument");
            }
            if (EnvironmentHud.snapshot().volume() != 27) {
                throw new AssertionError("The server must report the real enclosed volume");
            }
        });

        world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setItemSlot(
                net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TORCH)));
        context.waitFor(client -> DynamicLight.placedAt() != null, 600);
        context.runOnClient(client -> {
            net.minecraft.core.BlockPos at = DynamicLight.placedAt();
            if (!client.level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
                throw new AssertionError("A carried torch must light the space it is carried through");
            }
        });

        world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
                .addItem(new net.minecraft.world.item.ItemStack(ModItems.SAFETY_LAMP)));
        context.waitFor(client -> EnvironmentHud.snapshot().instrumented(), 600);
        context.runOnClient(client -> EnvironmentHud.snapshot());
        context.takeScreenshot("hardwrought-milestone-3-sealed-room");
        verifySuffocation(context, world);

        // Open the roof again so the rest of the run does not happen in spent air.
        world.getServer().runOnServer(server -> shell(server, net.minecraft.world.level.block.Blocks.AIR));
        context.waitFor(client -> !EnvironmentHud.snapshot().sealed(), 600);
    }

    /**
     * Air below the lethal threshold has to actually cost health. The periodic hazard was once gated
     * on the saved clock, which is one tick behind inside a simulation pass, so it never fired.
     */
    private static void verifySuffocation(ClientGameTestContext context,
                                          net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        float before = world.getServer().computeOnServer(
                server -> server.getPlayerList().getPlayers().getFirst().getHealth());
        context.runOnClient(client -> client.player.connection.sendCommand("hardwrought air set oxygen 0.01"));
        world.getServer().waitFor(server -> server.getPlayerList().getPlayers().getFirst().getHealth() < before, 400);
        // The combat feed must name the real cause: suffocation, not a blunt impact.
        context.waitFor(client -> CombatHud.snapshot() != null
                && CombatHud.snapshot().event() == CombatEvent.HIT
                && CombatHud.snapshot().damageType() == CombatDamageType.SUFFOCATION, 400);
        context.takeScreenshot("hardwrought-milestone-3-suffocation");
        context.runOnClient(client -> client.player.connection.sendCommand("hardwrought air set oxygen 0.209"));
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.setHealth(player.getMaxHealth());
        });
    }

    /** A 5x5x5 hull with a 3x3x3 interior around the player; AIR as the wall removes it again. */
    private static void shell(net.minecraft.server.MinecraftServer server,
                              net.minecraft.world.level.block.Block wall) {
        var player = server.getPlayerList().getPlayers().getFirst();
        var level = player.level();
        var base = player.blockPosition();
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 3; y++) {
                for (int z = -2; z <= 2; z++) {
                    boolean hull = Math.abs(x) == 2 || Math.abs(z) == 2 || y == -1 || y == 3;
                    if (y == -1 && wall == net.minecraft.world.level.block.Blocks.AIR) continue;
                    level.setBlockAndUpdate(base.offset(x, y, z), hull
                            ? wall.defaultBlockState()
                            : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
            }
        }
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
