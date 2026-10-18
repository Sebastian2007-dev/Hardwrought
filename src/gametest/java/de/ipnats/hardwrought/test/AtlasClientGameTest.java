package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.atlas.Atlas;
import de.ipnats.hardwrought.client.atlas.AtlasClient;
import de.ipnats.hardwrought.client.atlas.AtlasScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.item.ItemStack;

/**
 * The atlas opened in a real world: the land round the bearer drawn in, the bearer on it, marks with their
 * symbols, the list down the side, and the slip a new mark is written on. Photographed, because a map drawn
 * upside down or a row of squares a pixel off passes every server test there is.
 */
public final class AtlasClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().setItem(2, new ItemStack(Atlas.ITEM));
                // A flat world has nothing but grass: a pond, a wood, a hill of rock and a beach, so the page
                // shows how each is drawn.
                var level = player.level();
                int x0 = player.getBlockX(), z0 = player.getBlockZ();
                int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x0, z0) - 1;
                for (int dx = -40; dx <= 40; dx++) {
                    for (int dz = -40; dz <= 40; dz++) {
                        var at = new net.minecraft.core.BlockPos(x0 + dx, ground, z0 + dz);
                        double pond = Math.hypot(dx - 18, dz - 10), wood = Math.hypot(dx + 18, dz + 12), hill = Math.hypot(dx - 16, dz + 22);
                        if (pond < 11) level.setBlock(at, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 2);
                        else if (pond < 14) level.setBlock(at, net.minecraft.world.level.block.Blocks.SAND.defaultBlockState(), 2);
                        else if (wood < 14) level.setBlock(at.above(), net.minecraft.world.level.block.Blocks.OAK_LEAVES.defaultBlockState(), 2);
                        else if (hill < 10) level.setBlock(at.above((int) (10 - hill) / 3), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                    }
                }
                Atlas.record(player);
                Atlas.mark(player, player.getBlockX() + 24, player.getBlockZ() - 16, "Lager", 0, 1);
                Atlas.mark(player, player.getBlockX() - 20, player.getBlockZ() - 30, "Erzader", 0, 2);
                Atlas.mark(player, player.getBlockX() + 8, player.getBlockZ() + 26, "", 0, 5);
                Atlas.mark(player, player.getBlockX() - 30, player.getBlockZ() + 20, "", 1);
            });
            context.waitTicks(30);
            context.runOnClient(client -> AtlasClient.open());
            context.waitFor(client -> client.gui.screen() instanceof AtlasScreen
                    && !AtlasClient.tiles(client.level.dimension().identifier()).isEmpty()
                    && AtlasClient.markers(client.level.dimension().identifier()).size() == 4, 200);
            context.waitTicks(5);
            context.takeScreenshot("hardwrought-atlas");
            // Closer: the drawings of the land, and the names of the marks.
            for (int step = 0; step < 3; step++) {
                context.runOnClient(client -> client.gui.screen().mouseScrolled(client.gui.screen().width / 3.0,
                        client.gui.screen().height / 2.0, 0, 1));
            }
            context.waitTicks(3);
            context.takeScreenshot("hardwrought-atlas-close");
            // A right click on the map: the slip for a new mark.
            context.runOnClient(client -> client.gui.screen().mouseClicked(
                    new net.minecraft.client.input.MouseButtonEvent(client.gui.screen().width / 3.0, client.gui.screen().height / 3.0,
                            new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT, 0)), false));
            context.waitFor(client -> client.gui.screen() instanceof AtlasScreen atlas && atlas.writing(), 40);
            context.takeScreenshot("hardwrought-atlas-new-mark");
            context.runOnClient(client -> client.gui.setScreen(null));
        }
    }
}
