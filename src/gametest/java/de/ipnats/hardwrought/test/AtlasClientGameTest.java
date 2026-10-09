package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.atlas.Atlas;
import de.ipnats.hardwrought.client.atlas.AtlasClient;
import de.ipnats.hardwrought.client.atlas.AtlasScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.item.ItemStack;

/**
 * The atlas opened in a real world: the land round the bearer drawn in, the bearer on it, and a mark.
 * Photographed, because a map drawn upside down or a row of squares a pixel off passes every server
 * test there is.
 */
public final class AtlasClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().setItem(2, new ItemStack(Atlas.ITEM));
                Atlas.record(player);
                Atlas.mark(player, player.getBlockX() + 24, player.getBlockZ() - 16, "Lager", 0);
                Atlas.mark(player, player.getBlockX() - 30, player.getBlockZ() + 20, "", 1);
            });
            context.waitTicks(30);
            context.runOnClient(client -> AtlasClient.open());
            context.waitFor(client -> client.gui.screen() instanceof AtlasScreen
                    && !AtlasClient.tiles(client.level.dimension().identifier()).isEmpty()
                    && AtlasClient.markers(client.level.dimension().identifier()).size() == 2, 200);
            context.waitTicks(5);
            context.takeScreenshot("hardwrought-atlas");
            context.runOnClient(client -> client.gui.setScreen(null));
        }
    }
}
