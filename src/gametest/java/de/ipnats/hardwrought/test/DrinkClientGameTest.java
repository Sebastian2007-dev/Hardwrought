package de.ipnats.hardwrought.test;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Drinking with the hands, played with real keys: sneaking with an empty hand at deep water drinks from
 * it though no block is in reach behind the water, and the block in the other hand is not set down.
 */
public final class DrinkClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            BlockPos[] stand = new BlockPos[1];
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var level = player.level();
                int x0 = player.getBlockX(), z0 = player.getBlockZ();
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x0, z0);
                // A tower of water eight deep behind glass, east of a stone pillar as tall.
                for (int y = 0; y < 8; y++) {
                    for (int dx = 0; dx <= 6; dx++) {
                        for (int dz = -3; dz <= 3; dz++) {
                            BlockPos at = new BlockPos(x0 + 1 + dx, ground + y, z0 + dz);
                            boolean wall = dx == 0 || dx == 6 || Math.abs(dz) == 3;
                            level.setBlockAndUpdate(at, wall ? Blocks.GLASS.defaultBlockState() : Blocks.WATER.defaultBlockState());
                        }
                    }
                    level.setBlockAndUpdate(new BlockPos(x0, ground + y, z0), Blocks.STONE.defaultBlockState());
                }
                // The glass rim on the near side stops a block short of the top, so nothing is in reach but water.
                for (int dz = -2; dz <= 2; dz++) level.setBlockAndUpdate(new BlockPos(x0 + 1, ground + 7, z0 + dz), Blocks.WATER.defaultBlockState());
                stand[0] = new BlockPos(x0, ground + 8, z0);
                player.teleportTo(x0 + 0.5, ground + 8, z0 + 0.5);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COBBLESTONE, 5));
            });
            context.waitTicks(20);
            context.runOnClient(client -> {
                client.player.setYRot(-90);
                client.player.setXRot(50);
            });
            context.getInput().holdKey(options -> options.keyShift);
            context.waitTicks(5);
            context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            context.waitTicks(10);
            context.getInput().releaseKey(options -> options.keyShift);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var runtime = CoreLifecycle.require(server);
                if (runtime.survival().mayDrinkByHand(player)) {
                    throw new AssertionError("Sneaking at deep water with an empty hand drinks from it, with no block in reach behind it");
                }
                ItemStack offhand = player.getOffhandItem();
                if (!offhand.is(Items.COBBLESTONE) || offhand.getCount() != 5) {
                    throw new AssertionError("and the block in the other hand is not set down: " + offhand);
                }
                for (int dy = -8; dy <= 1; dy++) {
                    for (int dx = 0; dx <= 6; dx++) {
                        for (int dz = -3; dz <= 3; dz++) {
                            if (player.level().getBlockState(stand[0].offset(dx, dy, dz)).is(Blocks.COBBLESTONE)) {
                                throw new AssertionError("Nothing is placed in the water");
                            }
                        }
                    }
                }
            });
        }
    }
}
