package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.client.enchanting.RuneScreen;
import de.ipnats.hardwrought.core.networking.RunePayloads;
import de.ipnats.hardwrought.enchanting.RuneEnchanting;
import de.ipnats.hardwrought.smithing.ToolParts;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;

/**
 * A sitting at the enchanting table as it looks: the piece in its own outline and the runes that lie
 * ready beside it. Photographed, because a rune drawn off the piece or a list that runs out of its
 * panel passes every server test there is.
 */
public final class EnchantingClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            BlockPos table = world.getServer().computeOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(
                        ToolParts.part(ToolParts.SmithMetal.IRON, ToolParts.Part.CUIRASS)));
                player.getInventory().setItem(1, new ItemStack(RuneEnchanting.powder(), 24));
                player.giveExperienceLevels(20);
                return player.blockPosition();
            });
            context.waitTicks(10);
            context.runOnClient(client -> client.gui.setScreen(new RuneScreen(new RunePayloads.Open(table, 9, 20, 3, true,
                    List.of(Enchantments.PROTECTION.identifier(), Enchantments.PROJECTILE_PROTECTION.identifier(),
                            Enchantments.FIRE_PROTECTION.identifier(), Enchantments.UNBREAKING.identifier(),
                            Enchantments.BLAST_PROTECTION.identifier(), Enchantments.THORNS.identifier()), 2,
                    List.of(new de.ipnats.hardwrought.enchanting.RuneWork.Placement(Enchantments.PROTECTION.identifier(), 18, 14, 0),
                            new de.ipnats.hardwrought.enchanting.RuneWork.Placement(Enchantments.MENDING.identifier(), 20, 26, 1,
                                    de.ipnats.hardwrought.enchanting.RuneWork.Ink.ADAMANT)), 0, 2))));
            context.waitTicks(10);
            context.takeScreenshot("hardwrought-runes");
            context.runOnClient(client -> client.gui.setScreen(null));

            // The trace at the smithing table: mithril's figure of eight and adamant's spiral over the piece.
            for (int pattern = 1; pattern <= 2; pattern++) {
                int which = pattern;
                context.runOnClient(client -> client.gui.setScreen(new de.ipnats.hardwrought.client.smithing.UpgradeScreen(
                        new de.ipnats.hardwrought.core.networking.UpgradePayloads.Open(table, which,
                                net.minecraft.resources.Identifier.fromNamespaceAndPath("hardwrought",
                                        which == 1 ? "mithril_chestplate" : "adamant_chestplate")))));
                context.waitTicks(10);
                context.takeScreenshot("hardwrought-upgrade-" + pattern);
                context.runOnClient(client -> client.gui.setScreen(null));
                context.waitTicks(2);
            }
        }
    }
}
