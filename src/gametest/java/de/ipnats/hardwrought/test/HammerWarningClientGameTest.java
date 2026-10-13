package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.client.smithing.ForgingScreen;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.metallurgy.Alloys;
import de.ipnats.hardwrought.smithing.ToolParts;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Too light a hammer, on the forging board: the warning has to stand where the smith is looking and
 * name the hammer that will do, for one piece and for a list of them.
 */
public final class HammerWarningClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            BlockPos[] anvil = new BlockPos[1];
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().clearContent();
                // A steel hammer, and tungsten steel to work: two rungs too light for its hammer head.
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.STEEL_HAMMER));
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Alloys.TUNGSTEN_STEEL_INGOT, 8));
                anvil[0] = player.blockPosition().north();
            });
            context.waitTicks(10);
            Identifier head = BuiltInRegistries.ITEM.getKey(
                    ToolParts.part(ToolParts.SmithMetal.TUNGSTEN_STEEL, ToolParts.Part.HAMMER_HEAD));
            Identifier blade = BuiltInRegistries.ITEM.getKey(
                    ToolParts.part(ToolParts.SmithMetal.TUNGSTEN_STEEL, ToolParts.Part.SWORD_BLADE));
            context.runOnClient(client -> client.gui.setScreen(new ForgingScreen(anvil[0], List.of(head))));
            context.waitTicks(10);
            context.takeScreenshot("hardwrought-forging-hammer-too-light");
            context.runOnClient(client -> client.gui.setScreen(new ForgingScreen(anvil[0], List.of(blade, head))));
            context.waitTicks(10);
            // Pointing at the tile of the head is what brings the warning up.
            double[] at = context.computeOnClient(client -> {
                var screen = client.gui.screen();
                double scale = (double) client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
                return new double[] {((screen.width - 300) / 2.0 + 10 + 140 + 60) * scale, ((screen.height - 170) / 2.0 + 50) * scale};
            });
            context.getInput().setCursorPos(at[0], at[1]);
            context.waitTicks(5);
            context.takeScreenshot("hardwrought-forging-hammer-too-light-choice");
            context.runOnClient(client -> client.gui.setScreen(null));
        }
    }
}
