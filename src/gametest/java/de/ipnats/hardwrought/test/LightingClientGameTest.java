package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.client.environment.PlacedLights;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;

/**
 * Placed lights in the shaders: a wall at night, lit by a torch, a soul torch and a lantern, each in
 * its own colour, and none of them through the pillar in front of the wall.
 */
public final class LightingClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("weather clear");
            // Midnight at new moon: no sky light to hide the torches' own.
            world.getServer().runCommand("time set 114000");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                player.teleportTo(level, player.getX(), player.getY(), player.getZ(), java.util.Set.of(), 180.0f, 8.0f, true);
                BlockPos feet = player.blockPosition();
                BlockPos wall = feet.north(9);
                for (int x = -6; x <= 6; x++) {
                    for (int y = 0; y < 5; y++) {
                        level.setBlockAndUpdate(wall.east(x).above(y), Blocks.STONE_BRICKS.defaultBlockState());
                    }
                }
                // A pillar two blocks before the wall, and a torch on the ground before it and off to the side.
                for (int y = 0; y < 3; y++) level.setBlockAndUpdate(wall.south(2).above(y), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(wall.south(4).east(2), Blocks.TORCH.defaultBlockState());
                // A blue soul torch on the wall to the left, a lantern on a post to the right.
                level.setBlockAndUpdate(wall.west(4).south().above(2), Blocks.SOUL_WALL_TORCH.defaultBlockState()
                        .setValue(WallTorchBlock.FACING, Direction.SOUTH));
                level.setBlockAndUpdate(wall.east(4).south(3), Blocks.OAK_FENCE.defaultBlockState());
                level.setBlockAndUpdate(wall.east(4).south(3).above(), Blocks.LANTERN.defaultBlockState());
            });
            context.waitFor(client -> PlacedLights.placed().size() >= 3, 200);
            context.runOnClient(client -> {
                if (PlacedLights.placed().stream().noneMatch(light -> light.color() != PlacedLights.colorOf("torch"))) {
                    throw new AssertionError("A soul torch and a lantern must not shine like an ordinary torch");
                }
            });
            // Long enough for the eyes to adapt to the dark.
            context.waitTicks(200);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("hardwrought-lighting-placed");
        }
    }
}
