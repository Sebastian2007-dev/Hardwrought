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
