package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.client.knowledge.CompendiumClient;
import de.ipnats.hardwrought.client.knowledge.CompendiumScreen;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;

/**
 * The written half of the compendium in chapters: the contents with some chapters under way and the
 * later ones still scrawled, and a note headed by the chapter it stands in.
 */
public final class JournalChaptersClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var knowledge = CoreLifecycle.require(server).knowledge();
                knowledge.forget(player.getUUID());
                for (String name : new String[] {"flint_hatchet", "flint_shard", "lighting_sticks", "leaf_string",
                        "stone_pickaxe", "brick_furnace", "unlit_torch", "smithing_gloves", "wooden_anvil", "wooden_hammer"}) {
                    knowledge.study(player, BuiltInRegistries.ITEM.getValue(Hardwrought.id(name)));
                }
                knowledge.study(player, Items.CAMPFIRE);
                knowledge.study(player, Items.RAW_IRON);
            });
            // Following thoughts is told in the corner: the last one done, and the one that comes next.
            context.waitTicks(30);
            context.takeScreenshot("hardwrought-journal-toast");
            context.runOnClient(client -> CompendiumClient.openJournal());
            context.waitForScreen(CompendiumScreen.class);
            context.waitTicks(10);
            context.takeScreenshot("hardwrought-journal-chapter-note");
            context.runOnClient(client -> setPage((CompendiumScreen) client.gui.screen(), 0));
            context.waitTicks(5);
            context.takeScreenshot("hardwrought-journal-contents");
            context.runOnClient(client -> client.gui.setScreen(null));
        }
    }

    private static void setPage(CompendiumScreen screen, int page) {
        try {
            var field = CompendiumScreen.class.getDeclaredField("page");
            field.setAccessible(true);
            field.setInt(screen, page);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
