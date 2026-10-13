package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.client.knowledge.CompendiumClient;
import de.ipnats.hardwrought.client.knowledge.CompendiumScreen;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;

/**
 * The ribbons of the compendium on screen: where they hang, what a marked page looks like, and that
 * the key lays one in. A ribbon that covers the page arrows passes every server test there is.
 */
public final class BookmarkClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var knowledge = CoreLifecycle.require(server).knowledge();
                knowledge.forget(player.getUUID());
                knowledge.study(player, Items.CHEST);
                knowledge.discover(player, Items.FURNACE);
                // One known, one held, one never seen: a ribbon of each kind.
                knowledge.toggleBookmark(player, BuiltInRegistries.ITEM.getKey(Items.CHEST));
                knowledge.toggleBookmark(player, BuiltInRegistries.ITEM.getKey(Items.FURNACE));
                knowledge.toggleBookmark(player, BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT));
            });
            context.runOnClient(client -> CompendiumClient.openHome());
            context.waitForScreen(CompendiumScreen.class);
            context.waitTicks(10);
            context.takeScreenshot("hardwrought-compendium-ribbons-home");
            context.runOnClient(client -> CompendiumClient.openRecipes(Items.CHEST));
            context.waitTicks(10);
            context.takeScreenshot("hardwrought-compendium-ribbons-marked-page");
            context.runOnClient(client -> CompendiumClient.openRecipes(Items.HOPPER));
            context.waitTicks(10);
            context.takeScreenshot("hardwrought-compendium-ribbons-spare");

            // The key on an unmarked page lays a ribbon at it.
            context.runOnClient(client -> CompendiumClient.toggleBookmark(BuiltInRegistries.ITEM.getKey(Items.HOPPER)));
            context.waitTicks(10);
            int ribbons = context.computeOnClient(client -> CompendiumClient.bookmarks().size());
            if (ribbons != 4) throw new AssertionError("A fourth ribbon was laid in: " + ribbons);
            context.takeScreenshot("hardwrought-compendium-ribbons-four");
            context.runOnClient(client -> client.gui.setScreen(null));
        }
    }
}
