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
            verifyRecipeChoiceButton(context, world);
            context.runOnClient(client -> SurvivalHud.setDetailsVisible(true));
            context.waitFor(client -> SurvivalHud.detailsVisible());
            context.takeScreenshot("hardwrought-milestone-1-survival-hud");
            verifyAcceleratedSleep(context, world);
            verifyDaytimeBedSleep(context, world);
            verifyCombatFeedback(context, world);
            verifyFiniteWater(context, world);
            verifySealedRoomAndCarriedLight(context, world);
            verifyCompendium(context, world);
            verifyWornStrap(context, world);
            verifyBadWaterThirst(context, world);
            verifyFirecraft(context, world);
            // The weight table has to reach the client, or every tooltip would guess.
            context.waitFor(client -> !de.ipnats.hardwrought.client.survival.WeightTooltip.weights().isEmpty());
            context.runOnClient(client -> {
                var weights = de.ipnats.hardwrought.client.survival.WeightTooltip.weights();
                if (!weights.containsKey(de.ipnats.hardwrought.Hardwrought.id("flint_shard"))) {
                    throw new AssertionError("The datapack weight table must reach the client whole");
                }
                double listed = de.ipnats.hardwrought.survival.CarryWeight.perItem(
                        new net.minecraft.world.item.ItemStack(
                                de.ipnats.hardwrought.core.registry.ModItems.FLINT_SHARD), weights);
                if (listed != weights.get(de.ipnats.hardwrought.Hardwrought.id("flint_shard"))) {
                    throw new AssertionError("A tooltip must show the weight the server actually uses");
                }
            });
            context.runOnClient(client -> SurvivalHud.setDetailsVisible(true));
            context.waitTicks(2);
            context.takeScreenshot("hardwrought-milestone-1-carried-weight");

            context.runOnClient(client -> client.player.connection.sendCommand("hardwrought debug on"));
            context.waitFor(client -> !DebugHud.snapshot().isEmpty());
            context.runOnClient(client -> {
                if (DebugHud.snapshot().stream().noneMatch(line -> line.contains("server tick="))) {
                    throw new AssertionError("HUD must receive authoritative server snapshot");
                }
                if (SurvivalHud.snapshot().capacityKg()
                        != de.ipnats.hardwrought.survival.CarryWeight.BASE_CAPACITY_KG) {
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
            // Milestone 8: what a player has found out has to outlive the session they found it in.
            world.getServer().runOnServer(server -> {
                var runtime = CoreLifecycle.require(server);
                var player = server.getPlayerList().getPlayers().getFirst();
                runtime.knowledge().forget(player.getUUID());
                runtime.knowledge().study(player, net.minecraft.world.item.Items.DIAMOND);
                runtime.knowledge().discover(player, net.minecraft.world.item.Items.EMERALD);
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
                var runtime = CoreLifecycle.require(server);
                var player = server.getPlayerList().getPlayers().getFirst();
                var knowledge = runtime.knowledge();
                if (knowledge.level(player, net.minecraft.world.item.Items.DIAMOND)
                        != de.ipnats.hardwrought.knowledge.KnowledgeLevel.STUDIED) {
                    throw new AssertionError("A studied entry must survive closing and reopening the world");
                }
                if (knowledge.level(player, net.minecraft.world.item.Items.EMERALD)
                        != de.ipnats.hardwrought.knowledge.KnowledgeLevel.DISCOVERED) {
                    throw new AssertionError("A discovered entry must survive, and must not be promoted");
                }
                if (knowledge.level(player, net.minecraft.world.item.Items.NETHERITE_INGOT)
                        != de.ipnats.hardwrought.knowledge.KnowledgeLevel.UNKNOWN) {
                    throw new AssertionError("Reopening a world must not invent knowledge");
                }
            });
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

    /** The selector must exist in the real 2x2 inventory screen, not only in server menu logic. */
    private static void verifyRecipeChoiceButton(ClientGameTestContext context,
                                                  net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var menu = player.inventoryMenu;
            menu.getCraftSlots().setItem(0, new net.minecraft.world.item.ItemStack(
                    net.minecraft.world.item.Items.BARRIER));
            menu.slotsChanged(menu.getCraftSlots());
            menu.broadcastChanges();
        });
        context.waitFor(client -> ((de.ipnats.hardwrought.progression.RecipeSelectionMenu)
                client.player.inventoryMenu).hardwrought$recipeChoiceCount() == 2);
        context.runOnClient(client -> client.gui.setScreen(
                new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player)));
        context.waitFor(client -> client.gui.screen() != null
                && client.gui.screen().children().stream()
                .filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast)
                .anyMatch(button -> button.visible && button.getMessage().getString().equals("↻")));

        net.minecraft.world.item.Item[] first = new net.minecraft.world.item.Item[1];
        context.runOnClient(client -> {
            first[0] = client.player.inventoryMenu.getResultSlot().getItem().getItem();
            client.gameMode.handleInventoryButtonClick(client.player.inventoryMenu.containerId,
                    de.ipnats.hardwrought.progression.RecipeSelectionMenu.NEXT_RECIPE_BUTTON);
        });
        context.waitFor(client -> !client.player.inventoryMenu.getResultSlot().getItem().is(first[0]));
        context.takeScreenshot("hardwrought-recipe-choice-button");
        context.runOnClient(client -> client.gui.setScreen(null));
        world.getServer().runOnServer(server -> {
            var menu = server.getPlayerList().getPlayers().getFirst().inventoryMenu;
            menu.getCraftSlots().clearContent();
            menu.broadcastChanges();
        });
    }

    /**
     * Milestone 8: the browser has to draw, and the hard part is the shadow. An undiscovered entry
     * is the item model particle texture tinted black, which is the one thing in this milestone that
     * cannot be checked without a real client and a real atlas.
     */
    /**
     * Milestone 9: the worn strap and the pack's page have to draw. Slot coordinates, a panel drawn
     * behind them and a folding button are exactly the kind of thing that compiles, passes every
     * server test, and is still visibly wrong on screen — so this opens the real inventory and
     * photographs it.
     */
    private static void verifyWornStrap(ClientGameTestContext context,
                                        net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var equipment = CoreLifecycle.require(server).equipment();
            equipment.setBackpack(player, new net.minecraft.world.item.ItemStack(
                    de.ipnats.hardwrought.core.registry.ModItems.BASIC_BACKPACK));
            equipment.setLamp(player, new net.minecraft.world.item.ItemStack(
                    de.ipnats.hardwrought.core.registry.ModItems.SAFETY_LAMP));
            player.inventoryMenu.broadcastChanges();
        });
        // The strap only means anything once the pack has reached the client.
        context.waitFor(client -> ((de.ipnats.hardwrought.equipment.CarriedInventoryMenu)
                client.player.inventoryMenu).hardwrought$packSlots()
                == de.ipnats.hardwrought.equipment.BackpackTier.BASIC.slots());

        context.runOnClient(client -> {
            de.ipnats.hardwrought.equipment.WornStrap.clientExpanded = true;
            client.gui.setScreen(
                    new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player));
        });
        context.waitTicks(5);
        context.takeScreenshot("hardwrought-milestone-9-worn-strap-open");

        // What is worn has to be reachable, not merely drawn.
        context.runOnClient(client -> {
            var worn = client.player.inventoryMenu.slots.stream()
                    .filter(slot -> slot instanceof de.ipnats.hardwrought.equipment.WornSlot)
                    .toList();
            if (worn.size() != de.ipnats.hardwrought.equipment.EquipmentContainer.SIZE) {
                throw new AssertionError("The strap must carry exactly its worn squares, found "
                        + worn.size());
            }
            if (worn.stream().noneMatch(slot -> slot.getItem().is(
                    de.ipnats.hardwrought.core.registry.ModItems.BASIC_BACKPACK))) {
                throw new AssertionError("The pack the player is wearing must show in the strap");
            }
            if (worn.stream().anyMatch(slot -> !slot.isActive())) {
                throw new AssertionError("A folded-out strap must have every square open");
            }
        });

        // Folded away, the squares go with it.
        context.runOnClient(client -> de.ipnats.hardwrought.equipment.WornStrap.clientExpanded = false);
        context.waitTicks(5);
        context.takeScreenshot("hardwrought-milestone-9-worn-strap-folded");
        context.runOnClient(client -> {
            if (client.player.inventoryMenu.slots.stream()
                    .filter(slot -> slot instanceof de.ipnats.hardwrought.equipment.WornSlot)
                    .anyMatch(net.minecraft.world.inventory.Slot::isActive)) {
                throw new AssertionError("A folded strap must leave no square open to a click");
            }
        });
        context.runOnClient(client -> de.ipnats.hardwrought.equipment.WornStrap.clientExpanded = true);

        // And the pack's own page, which shares its coordinates with the player's own grid.
        context.runOnClient(client -> client.gameMode.handleInventoryButtonClick(
                client.player.inventoryMenu.containerId,
                de.ipnats.hardwrought.equipment.CarriedInventoryMenu.TOGGLE_PAGE_BUTTON));
        context.waitFor(client -> ((de.ipnats.hardwrought.equipment.CarriedInventoryMenu)
                client.player.inventoryMenu).hardwrought$page() == 1);
        context.waitTicks(5);
        // The page is only useful if the pack's own row is reachable and the grid behind it is not.
        context.runOnClient(client -> {
            long packOpen = client.player.inventoryMenu.slots.stream()
                    .filter(slot -> slot instanceof de.ipnats.hardwrought.equipment.CarriedSlot)
                    .filter(slot -> !(slot.container instanceof net.minecraft.world.entity.player.Inventory))
                    .filter(net.minecraft.world.inventory.Slot::isActive).count();
            long ownOpen = client.player.inventoryMenu.slots.stream()
                    .filter(slot -> slot instanceof de.ipnats.hardwrought.equipment.CarriedSlot)
                    .filter(slot -> slot.container instanceof net.minecraft.world.entity.player.Inventory)
                    .filter(net.minecraft.world.inventory.Slot::isActive).count();
            if (packOpen != de.ipnats.hardwrought.equipment.BackpackTier.BASIC.slots()) {
                throw new AssertionError("The pack page must open exactly its own row: " + packOpen
                        + " open, page " + ((de.ipnats.hardwrought.equipment.CarriedInventoryMenu)
                        client.player.inventoryMenu).hardwrought$page() + ", slots "
                        + ((de.ipnats.hardwrought.equipment.CarriedInventoryMenu)
                        client.player.inventoryMenu).hardwrought$packSlots());
            }
            if (ownOpen != 0) {
                throw new AssertionError("and must close the grid behind it: " + ownOpen + " open");
            }
        });
        context.takeScreenshot("hardwrought-milestone-9-pack-page");
        context.runOnClient(client -> client.gameMode.handleInventoryButtonClick(
                client.player.inventoryMenu.containerId,
                de.ipnats.hardwrought.equipment.CarriedInventoryMenu.TOGGLE_PAGE_BUTTON));
        context.waitFor(client -> ((de.ipnats.hardwrought.equipment.CarriedInventoryMenu)
                client.player.inventoryMenu).hardwrought$page() == 0);
        context.runOnClient(client -> client.gui.setScreen(null));
        // Put the player back as they were found. The allowance the later HUD check reads is the
        // worn pack's, so a leather pack left on here would quietly move a number two tests away.
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            CoreLifecycle.require(server).equipment().setBackpack(player,
                    new net.minecraft.world.item.ItemStack(
                            de.ipnats.hardwrought.core.registry.ModItems.STARTER_BACKPACK));
            player.inventoryMenu.broadcastChanges();
        });
    }

    private static void verifyCompendium(ClientGameTestContext context,
                                         net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            var runtime = CoreLifecycle.require(server);
            var player = server.getPlayerList().getPlayers().getFirst();
            runtime.knowledge().forget(player.getUUID());
            // Studied entries near the front of the registry, so the first page shows real icons
            // next to the shadows and a broken item renderer could not pass unnoticed.
            runtime.knowledge().study(player, net.minecraft.world.item.Items.STONE);
            runtime.knowledge().study(player, net.minecraft.world.item.Items.GRANITE);
            runtime.knowledge().study(player, net.minecraft.world.item.Items.DIORITE);
        });
        // The toast cycles through what was learned, and the index it picks has to land inside the
        // list whatever the clock and the notification-time setting say. It once did not, and an
        // index of minus one crashed the render thread.
        for (int count = 1; count <= de.ipnats.hardwrought.core.networking.KnowledgeNotePayload.MAX_NOTES; count++) {
            for (double multiplier : new double[]{0.0, 0.5, 1.0, 5.0}) {
                for (long time : new long[]{Long.MIN_VALUE, -1L, 0L, 1L, 4999L, 5000L,
                        1234567L, Long.MAX_VALUE}) {
                    int index = de.ipnats.hardwrought.client.knowledge.KnowledgeToast
                            .entryIndex(time, 5000.0 * multiplier, count);
                    if (index < 0 || index >= count) {
                        throw new AssertionError("Knowledge toast picked line " + index + " of "
                                + count + " at time " + time + " with multiplier " + multiplier);
                    }
                }
            }
        }

        // Learning something has to leave a mark, the way an unlocked recipe does.
        context.waitFor(client -> client.gui.toastManager().getToast(
                de.ipnats.hardwrought.client.knowledge.KnowledgeToast.class,
                net.minecraft.client.gui.components.toasts.Toast.NO_TOKEN) != null);
        // The toast slides in; a screenshot taken the instant it exists catches only its edge.
        context.waitTicks(20);
        context.takeScreenshot("hardwrought-milestone-8-knowledge-toast");

        context.runOnClient(client -> de.ipnats.hardwrought.client.knowledge.CompendiumClient.openShelf(
                de.ipnats.hardwrought.knowledge.KnowledgeCategory.MATERIALS, ""));
        context.waitFor(client -> de.ipnats.hardwrought.client.knowledge.CompendiumClient.page() != null);
        context.runOnClient(client -> {
            var page = de.ipnats.hardwrought.client.knowledge.CompendiumClient.page();
            if (page.entries().isEmpty()) throw new AssertionError("The materials shelf must not be empty");
            if (page.entries().stream().noneMatch(entry -> entry.level() == 0)) {
                throw new AssertionError("A player who has found almost nothing must see shadows");
            }
            if (page.entries().stream().noneMatch(entry -> entry.level() == 2)) {
                throw new AssertionError("What was studied must come back as studied");
            }
        });
        // Rendering happens between ticks; a screenshot is the proof that it happened at all.
        context.waitTicks(5);
        context.takeScreenshot("hardwrought-milestone-8-compendium-shelf");

        context.runOnClient(client -> de.ipnats.hardwrought.client.knowledge.CompendiumClient.openRecipes(
                ModItems.COMPENDIUM));
        context.waitFor(client -> {
            var page = de.ipnats.hardwrought.client.knowledge.CompendiumClient.page();
            return page != null && !page.recipes().isEmpty();
        });
        context.waitTicks(5);
        context.takeScreenshot("hardwrought-milestone-8-compendium-recipe");
        // A feather has no recipe at all; the page is worth nothing unless it says where one comes
        // from instead.
        context.runOnClient(client -> de.ipnats.hardwrought.client.knowledge.CompendiumClient.openRecipes(
                net.minecraft.world.item.Items.FEATHER));
        context.waitFor(client -> {
            var page = de.ipnats.hardwrought.client.knowledge.CompendiumClient.page();
            return page != null && !page.sources().isEmpty();
        });
        context.waitTicks(5);
        context.takeScreenshot("hardwrought-milestone-8-compendium-sources");
        context.runOnClient(client -> client.gui.setScreen(null));
        world.getServer().runOnServer(server -> CoreLifecycle.require(server).knowledge()
                .forget(server.getPlayerList().getPlayers().getFirst().getUUID()));
    }

    /**
     * Section 23.2: bad water leaves a real player thirsty, and never poisoned. This needs the real
     * client player because a mock one is always in creative, and a creative player never drinks.
     *
     * <p>The illness is a roll per drink, so the check is a great many drinks of nothing: the drink
     * is worth zero hydration, so nothing moves except the roll. Four hundred rolls at one in twenty
     * leaves a chance of missing that is far smaller than the chance of the build machine catching
     * fire.
     */
    private static void verifyBadWaterThirst(ClientGameTestContext context,
                                             net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            var runtime = CoreLifecycle.require(server);
            var player = server.getPlayerList().getPlayers().getFirst();
            player.removeAllEffects();
            // Stop at the first hit: every drink that lands also writes a line of chat, and four
            // hundred of them would bury every screenshot this test takes afterwards.
            for (int roll = 0; roll < 400
                    && !player.hasEffect(de.ipnats.hardwrought.core.registry.ModEffects.THIRST); roll++) {
                runtime.survival().drink(player, 0.0, de.ipnats.hardwrought.water.WaterQuality.SALT);
            }
            if (!player.hasEffect(de.ipnats.hardwrought.core.registry.ModEffects.THIRST)) {
                throw new AssertionError("Sea water has to leave the drinker thirsty");
            }
            if (player.hasEffect(net.minecraft.world.effect.MobEffects.POISON)) {
                throw new AssertionError("And it must not poison them; that was the old answer");
            }
            // The drain is read by the metabolism pass, so it has to be visible there.
            if (de.ipnats.hardwrought.survival.SurvivalSystem.thirstDrain(player)
                    != de.ipnats.hardwrought.survival.SurvivalSystem.THIRST_PER_SECOND) {
                throw new AssertionError("The thirst a player carries has to cost them something");
            }
            player.removeAllEffects();
        });
    }

    /**
     * Fire by friction, checked with the one player in these tests who is not in creative: a mock
     * player never wears a tool out, so the cost of lighting a fire can only be seen here.
     */
    private static void verifyFirecraft(ClientGameTestContext context,
                                        net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            var level = server.getPlayerList().getPlayers().getFirst().level();
            var player = server.getPlayerList().getPlayers().getFirst();
            net.minecraft.core.BlockPos ground = player.blockPosition().below().east(2);
            net.minecraft.core.BlockPos fire = ground.above();
            level.setBlockAndUpdate(ground, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(fire, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

            var campfire = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CAMPFIRE);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, campfire);
            campfire.useOn(new net.minecraft.world.item.context.UseOnContext(level, player,
                    net.minecraft.world.InteractionHand.MAIN_HAND, campfire,
                    new net.minecraft.world.phys.BlockHitResult(
                            net.minecraft.world.phys.Vec3.atCenterOf(ground),
                            net.minecraft.core.Direction.UP, ground, false)));
            if (level.getBlockState(fire).getValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
                throw new AssertionError("A campfire a player sets down must not already be burning");
            }

            var sticks = new net.minecraft.world.item.ItemStack(
                    de.ipnats.hardwrought.core.registry.ModItems.LIGHTING_STICKS);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sticks);
            sticks.useOn(new net.minecraft.world.item.context.UseOnContext(level, player,
                    net.minecraft.world.InteractionHand.MAIN_HAND, sticks,
                    new net.minecraft.world.phys.BlockHitResult(
                            net.minecraft.world.phys.Vec3.atCenterOf(fire),
                            net.minecraft.core.Direction.UP, fire, false)));
            if (!level.getBlockState(fire).getValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
                throw new AssertionError("The fire-lighting sticks have to light it");
            }
            if (sticks.getDamageValue() <= 0) {
                throw new AssertionError("And lighting it has to wear them down");
            }

            // The furnace the bricks build, stood next to the fire that fired them.
            net.minecraft.core.BlockPos furnace = fire.east();
            level.setBlockAndUpdate(furnace.below(),
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(furnace,
                    de.ipnats.hardwrought.core.registry.ModBlocks.BRICK_FURNACE.defaultBlockState());
            if (!(level.getBlockEntity(furnace)
                    instanceof de.ipnats.hardwrought.metallurgy.BrickFurnaceBlockEntity)) {
                throw new AssertionError("The brick furnace must carry its own block entity");
            }
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    net.minecraft.world.item.ItemStack.EMPTY);
        });
        // Turn to face what was just built, or the picture is of the grass behind it.
        context.runOnClient(client -> {
            client.player.setYRot(-90.0f);
            client.player.setXRot(10.0f);
        });
        context.waitTicks(10);
        context.takeScreenshot("hardwrought-milestone-7-brick-furnace");
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
