package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.client.knowledge.CompendiumClient;
import de.ipnats.hardwrought.client.knowledge.CompendiumScreen;
import de.ipnats.hardwrought.client.knowledge.MultiblockProjection;
import de.ipnats.hardwrought.client.knowledge.MultiblockSection;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.knowledge.Multiblocks;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * The compendium's multiblock section and a structure laid out in the world as ghost blocks: the
 * shelf of structures, and the smeltery set out, one block of it built, one wrong.
 */
public final class MultiblockClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("time set noon");
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var knowledge = CoreLifecycle.require(server).knowledge();
                for (String id : new String[] {"smeltery_bricks", "smeltery_controller", "smeltery_drain", "smeltery_glass"}) {
                    var block = BuiltInRegistries.BLOCK.getValue(Hardwrought.id(id));
                    knowledge.discover(player, block);
                    knowledge.study(player, block);
                }
                // Looking down at the ground in front, so the structure is laid out there.
                player.teleportTo(player.level(), player.getX(), player.getY(), player.getZ(), java.util.Set.of(), 0.0f, 45.0f, true);
            });
            context.waitTicks(5);
            context.runOnClient(client -> CompendiumClient.openShelf(de.ipnats.hardwrought.knowledge.KnowledgeCategory.MATERIALS, ""));
            context.waitForScreen(CompendiumScreen.class);
            context.runOnClient(client -> MultiblockSection.select());
            context.waitFor(client -> MultiblockSection.readable(Multiblocks.SMELTERY), 100);
            context.waitTicks(5);
            context.takeScreenshot("hardwrought-compendium-multiblocks");

            context.runOnClient(client -> {
                client.gui.setScreen(null);
                MultiblockProjection.start(Multiblocks.SMELTERY, MultiblockSection.studied());
            });
            context.waitTicks(5);
            if (!context.computeOnClient(client -> MultiblockProjection.placing())) {
                throw new AssertionError("The smeltery follows the look, waiting to be set down");
            }
            context.takeScreenshot("hardwrought-multiblock-projection-placing");
            context.runOnClient(client -> MultiblockProjection.confirm());
            context.waitTicks(5);
            if (!context.computeOnClient(client -> MultiblockProjection.active() && !MultiblockProjection.placing())) {
                throw new AssertionError("Set down, it stays where it was");
            }
            context.waitTicks(10);
            world.getConnection().waitForChunksRender();
            context.takeScreenshot("hardwrought-multiblock-projection");
            context.runOnClient(client -> MultiblockProjection.clear());
        }
    }
}
