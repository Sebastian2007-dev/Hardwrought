package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.client.smithing.ForgingScreen;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.smithing.Heat;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * The parts of smithing that only exist on screen: hot metal glowing in the hand, and the anvil
 * with a piece on it. Photographed, because a glow colour or a grid drawn in the wrong place passes
 * every server test there is.
 */
public final class SmithingClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            BlockPos[] anvil = new BlockPos[1];
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                BlockPos pos = player.blockPosition().relative(player.getDirection(), 2);
                player.level().setBlockAndUpdate(pos, ModBlocks.WOODEN_ANVIL.defaultBlockState());
                anvil[0] = pos;
                long now = player.level().getGameTime();
                ItemStack raw = new ItemStack(Items.RAW_IRON);
                raw.set(ModDataComponents.HEAT, new Heat(1150f, now));
                ItemStack bar = new ItemStack(Items.IRON_INGOT);
                bar.set(ModDataComponents.HEAT, new Heat(800f, now));
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HAMMER));
                player.setItemInHand(InteractionHand.OFF_HAND, raw);
                player.getInventory().setItem(1, bar);
                player.getInventory().setItem(2, new ItemStack(Items.IRON_INGOT));
            });
            context.waitTicks(10);
            context.takeScreenshot("hardwrought-smithing-hot-metal-in-hand");

            context.runOnClient(client -> client.gui.setScreen(new ForgingScreen(anvil[0],
                    List.of(BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT)))));
            context.waitTicks(5);
            context.takeScreenshot("hardwrought-smithing-anvil");
            context.runOnClient(client -> client.gui.setScreen(null));

            // Three hot bars: first the choice of what to make, then a pick head under way.
            var head = de.ipnats.hardwrought.smithing.ToolParts.part(
                    de.ipnats.hardwrought.smithing.ToolParts.SmithMetal.IRON,
                    de.ipnats.hardwrought.smithing.ToolParts.Part.PICKAXE_HEAD);
            var axe = de.ipnats.hardwrought.smithing.ToolParts.part(
                    de.ipnats.hardwrought.smithing.ToolParts.SmithMetal.IRON,
                    de.ipnats.hardwrought.smithing.ToolParts.Part.AXE_HEAD);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                ItemStack bars = new ItemStack(Items.IRON_INGOT, 3);
                bars.set(ModDataComponents.HEAT, new Heat(1250f, player.level().getGameTime()));
                player.setItemInHand(InteractionHand.OFF_HAND, bars);
            });
            context.waitTicks(3);
            List<net.minecraft.resources.Identifier> options = List.of(
                    BuiltInRegistries.ITEM.getKey(head), BuiltInRegistries.ITEM.getKey(axe));
            context.runOnClient(client -> client.gui.setScreen(new ForgingScreen(anvil[0], options)));
            context.waitTicks(5);
            context.takeScreenshot("hardwrought-smithing-choice");
            context.runOnClient(client -> {
                var source = de.ipnats.hardwrought.client.smithing.ItemPixels.mask(
                        de.ipnats.hardwrought.client.smithing.ItemPixels.of(new ItemStack(Items.IRON_INGOT)));
                var target = de.ipnats.hardwrought.client.smithing.ItemPixels.mask(
                        de.ipnats.hardwrought.client.smithing.ItemPixels.of(new ItemStack(head)));
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new de.ipnats.hardwrought.core.networking.ForgingPayloads.Begin(anvil[0],
                                BuiltInRegistries.ITEM.getKey(head), source.toArray(), target.toArray()));
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new de.ipnats.hardwrought.core.networking.ForgingPayloads.Strike(anvil[0], 7, 8));
            });
            context.waitTicks(10);
            context.takeScreenshot("hardwrought-smithing-work");
            context.runOnClient(client -> client.gui.setScreen(null));

            // The joined forges: a burning 2x2 on the left, a cold 3x3 under a hood on the right.
            BlockPos[] forges = new BlockPos[2];
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                net.minecraft.core.Direction ahead = player.getDirection();
                net.minecraft.core.Direction right = ahead.getClockWise();
                BlockPos base = player.blockPosition().relative(ahead, 7);
                BlockPos small = base.relative(right, 0);
                BlockPos large = base.relative(right, 3);
                forges[0] = small;
                forges[1] = large;
                for (int x = 0; x < 3; x++) {
                    for (int z = 0; z < 3; z++) {
                        if (x < 2 && z < 2) {
                            level.setBlockAndUpdate(small.offset(x, 0, z), ModBlocks.FORGE.defaultBlockState());
                        }
                        level.setBlockAndUpdate(large.offset(x, 0, z), ModBlocks.FORGE.defaultBlockState());
                    }
                }
                // A canopy of nine hoods over it, and a pipe off its middle that bends away at the top.
                java.util.List<BlockPos> flue = new java.util.ArrayList<>();
                for (int x = 0; x < 3; x++) {
                    for (int z = 0; z < 3; z++) flue.add(large.offset(x, 2, z));
                }
                flue.add(large.offset(1, 3, 1));
                flue.add(large.offset(1, 4, 1));
                flue.add(large.offset(2, 4, 1));
                for (BlockPos pos : flue) {
                    level.setBlockAndUpdate(pos, (pos.getY() == large.getY() + 2 ? ModBlocks.FORGE_HOOD
                            : ModBlocks.GAS_PIPE).defaultBlockState());
                }
                for (BlockPos pos : flue) {
                    level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(
                            level.getBlockState(pos), level, pos));
                }
                var smallForge = de.ipnats.hardwrought.smithing.ForgeMultiblock.getOrForm(level, small);
                de.ipnats.hardwrought.smithing.ForgeMultiblock.controller(level, smallForge).addFuel(16000);
                var smallController = de.ipnats.hardwrought.smithing.ForgeMultiblock.controller(level, smallForge);
                for (int i = 0; i < 3; i++) {
                    ItemStack raw = new ItemStack(Items.RAW_IRON);
                    raw.set(ModDataComponents.HEAT, new Heat(900f + i, level.getGameTime()));
                    smallController.place(raw, smallController.layout());
                }
                de.ipnats.hardwrought.smithing.ForgeMultiblock.getOrForm(level, large);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                level.setBlockAndUpdate(anvil[0], Blocks.AIR.defaultBlockState());
            });
            context.runOnClient(client -> client.player.setXRot(20.0f));
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("hardwrought-smithing-forge-multiblocks");
            // The canopy and its pipe, looked at from beside and a little below.
            context.runOnClient(client -> {
                client.player.setXRot(-12.0f);
                client.player.setYRot(client.player.getYRot() + 22.0f);
            });
            context.waitTicks(5);
            context.takeScreenshot("hardwrought-smithing-forge-hood-canopy");
            context.runOnClient(client -> {
                client.player.setXRot(20.0f);
                client.player.setYRot(client.player.getYRot() - 22.0f);
            });

            for (int which = 0; which < 2; which++) {
                BlockPos at = forges[which];
                world.getServer().runOnServer(server -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    var level = player.level();
                    var controller = (de.ipnats.hardwrought.smithing.ForgeBlockEntity) level.getBlockEntity(at);
                    controller.place(new ItemStack(Items.COAL, 20), controller.layout());
                    player.openMenu(controller);
                });
                context.waitTicks(10);
                context.takeScreenshot(which == 0 ? "hardwrought-forge-gui-small" : "hardwrought-forge-gui-large");
                context.runOnClient(client -> client.player.closeContainer());
                context.waitTicks(2);
            }

            // Waterlogged fences holding a full, a half and a quarter block, beside half a block of free water.
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                net.minecraft.core.Direction ahead = player.getDirection();
                net.minecraft.core.Direction right = ahead.getClockWise();
                BlockPos row = player.blockPosition().relative(ahead, 4);
                // Glass basins, so each cell keeps its water and it can be seen from the side.
                for (int along = -2; along <= 6; along++) {
                    for (int deep = -1; deep <= 1; deep++) {
                        level.setBlockAndUpdate(row.relative(right, along).relative(ahead, deep),
                                Blocks.GLASS.defaultBlockState());
                    }
                }
                int[] amounts = {1000, 500, 250};
                for (int i = 0; i < amounts.length; i++) {
                    BlockPos cell = row.relative(right, -1 + 2 * i);
                    level.setBlockAndUpdate(cell, Blocks.OAK_FENCE.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true));
                    de.ipnats.hardwrought.water.WaterStorage.setAmount(level, cell, amounts[i]);
                }
                BlockPos free = row.relative(right, 5);
                level.setBlockAndUpdate(free, Blocks.AIR.defaultBlockState());
                de.ipnats.hardwrought.water.WaterStorage.setAmount(level, free, 500);
            });
            context.runOnClient(client -> client.player.setXRot(25.0f));
            context.waitTicks(20);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("hardwrought-waterlogged-levels");
        }
    }
}
