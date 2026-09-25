package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.machinery.CogwheelBlock;
import de.ipnats.hardwrought.machinery.CrankBoxBlock;
import de.ipnats.hardwrought.machinery.ShaftBlock;
import de.ipnats.hardwrought.machinery.ShaftBlockEntity;
import de.ipnats.hardwrought.machinery.WaterWheelBlock;
import de.ipnats.hardwrought.machinery.WindmillBlock;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Milestone 10 on screen: gears meshing, a belt between two shafts, a water wheel and a windmill.
 * Photographed, because a gear drawn on the wrong axis passes every server test there is.
 */
public final class MachineryClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                // Face north and build in front of the player, so the picture is the same every time.
                player.teleportTo(level, player.getX(), player.getY(), player.getZ(), java.util.Set.of(), 180.0f, -12.0f, true);
                BlockPos base = player.blockPosition().north(10).east(4).above();
                // A crank box driving a shaft east into a small gear, which meshes with another and a large one.
                level.setBlockAndUpdate(base.west(3), ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
                level.setBlockAndUpdate(base.west(2), ModBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
                level.setBlockAndUpdate(base.west(1), ModBlocks.GEARBOX.defaultBlockState());
                level.setBlockAndUpdate(base.west(1).south(), ModBlocks.COGWHEEL.defaultBlockState()
                        .setValue(CogwheelBlock.AXIS, Direction.Axis.Z));
                level.setBlockAndUpdate(base.west(1).south().above(), ModBlocks.COGWHEEL.defaultBlockState()
                        .setValue(CogwheelBlock.AXIS, Direction.Axis.Z));
                level.setBlockAndUpdate(base.west(2).south().above(2), ModBlocks.LARGE_COGWHEEL.defaultBlockState()
                        .setValue(CogwheelBlock.AXIS, Direction.Axis.Z));
                // A belt from the shaft up to another above it.
                BlockPos upper = base.west(2).above(3);
                level.setBlockAndUpdate(upper, ModBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
                ((ShaftBlockEntity) level.getBlockEntity(base.west(2))).setBelt(upper);
                ((ShaftBlockEntity) level.getBlockEntity(upper)).setBelt(base.west(2));
                de.ipnats.hardwrought.machinery.Kinetics.update(level, base.west(2));
                // A water wheel and a windmill, standing still here but drawn in full.
                level.setBlockAndUpdate(base.east(3).above(), ModBlocks.WATER_WHEEL.defaultBlockState()
                        .setValue(WaterWheelBlock.AXIS, Direction.Axis.X));
                level.setBlockAndUpdate(base.east(2).above(5), ModBlocks.WINDMILL.defaultBlockState()
                        .setValue(WindmillBlock.FACING, Direction.SOUTH));
            });
            context.waitTicks(40);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("hardwrought-machinery");

            // Milestone 11: a rig on a crank box, and a still over a campfire with a charge in it.
            BlockPos[] still = new BlockPos[1];
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                BlockPos base = player.blockPosition().north(5).above();
                level.setBlockAndUpdate(base.west(2), ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
                level.setBlockAndUpdate(base.west(2).above(), ModBlocks.DRILLING_RIG.defaultBlockState());
                level.setBlockAndUpdate(base.east(2), net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
                level.setBlockAndUpdate(base.east(2).above(), ModBlocks.STILL.defaultBlockState());
                // An ore drill: a titanium frame round its head, a bronze block in it, a crank box under it.
                BlockPos head = base.north(3).above();
                level.setBlockAndUpdate(head.below(), ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
                level.setBlockAndUpdate(head, ModBlocks.ORE_DRILL.defaultBlockState());
                var frame = de.ipnats.hardwrought.machinery.OreDrillBlockEntity.framePositions(head);
                for (int i = 0; i < frame.size(); i++) {
                    level.setBlockAndUpdate(frame.get(i), (i == 4 ? ModBlocks.DRILL_FRAME_BRONZE
                            : ModBlocks.DRILL_FRAME_TITANIUM).defaultBlockState());
                }
                still[0] = base.east(2).above();
                var entity = (de.ipnats.hardwrought.chemistry.StillBlockEntity) level.getBlockEntity(still[0]);
                entity.setItem(de.ipnats.hardwrought.chemistry.StillBlockEntity.CHARGE,
                        new net.minecraft.world.item.ItemStack(de.ipnats.hardwrought.core.registry.ModItems.CRUDE_OIL_BUCKET));
                entity.setItem(de.ipnats.hardwrought.chemistry.StillBlockEntity.BOTTLES,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE, 3));
                entity.setItem(de.ipnats.hardwrought.chemistry.StillBlockEntity.FIRST_OUTPUT,
                        de.ipnats.hardwrought.chemistry.Purity.with(new net.minecraft.world.item.ItemStack(
                                de.ipnats.hardwrought.core.registry.ModItems.RAW_SULFUR), 0.68));
            });
            context.waitTicks(60);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("hardwrought-oil-rig-and-still");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.openMenu((de.ipnats.hardwrought.chemistry.StillBlockEntity) player.level().getBlockEntity(still[0]));
            });
            context.waitTicks(10);
            context.takeScreenshot("hardwrought-still-screen");
            context.runOnClient(client -> client.player.closeContainer());
            // The compendium's view of the drill, whole and then its bottom layer on its own.
            var rig = de.ipnats.hardwrought.knowledge.Multiblocks.ORE_DRILL_RIG;
            var viewer = context.computeOnClient(client -> {
                var screen = new de.ipnats.hardwrought.client.knowledge.MultiblockScreen(null, rig, rig.blocks());
                client.gui.setScreen(screen);
                return screen;
            });
            context.waitTicks(10);
            context.takeScreenshot("hardwrought-multiblock-view");
            context.runOnClient(client -> viewer.stepLayer(1));
            context.waitTicks(5);
            context.takeScreenshot("hardwrought-multiblock-layer");
            context.runOnClient(client -> client.gui.setScreen(null));
        }
        // The Ultra switch sits under the game mode when a world is made.
        context.runOnClient(client -> net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(client, null));
        context.waitTicks(20);
        context.runOnClient(client -> {
            for (var child : client.gui.screen().children()) {
                if (child instanceof net.minecraft.client.gui.components.CycleButton<?> button
                        && button.getMessage().getString().startsWith("Ultra")) {
                    button.onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));
                }
            }
        });
        context.waitTicks(5);
        context.takeScreenshot("hardwrought-create-world-ultra");
        context.runOnClient(client -> client.gui.setScreen(null));
    }
}
