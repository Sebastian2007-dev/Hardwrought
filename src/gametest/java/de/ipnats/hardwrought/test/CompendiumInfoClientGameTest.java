package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.client.knowledge.CompendiumClient;
import de.ipnats.hardwrought.client.knowledge.CompendiumInfo;
import de.ipnats.hardwrought.client.knowledge.CompendiumScreen;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * The lines the compendium has about a thing, under its name on the left page: which line a thing
 * gets — its own, its family's, or none — and how the page looks with them, studied and not, short and
 * running past the page, and on a page that is only where a thing is found.
 */
public final class CompendiumInfoClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Map<Identifier, String> expected = Map.of(
                Hardwrought.id("forge"), "compendium.hardwrought.info.forge",
                Hardwrought.id("tin_powder"), "compendium.hardwrought.info.family.powder",
                Hardwrought.id("coal_powder"), "compendium.hardwrought.info.coal_powder",
                Hardwrought.id("diesel_bucket"), "compendium.hardwrought.info.diesel",
                Hardwrought.id("steel_pickaxe_head"), "compendium.hardwrought.info.family.tool_part",
                Hardwrought.id("stainless_steel_pickaxe"), "compendium.hardwrought.info.family.stainless_steel",
                Hardwrought.id("deepslate_tin_ore"), "compendium.hardwrought.info.family.ore",
                Hardwrought.id("basic_crusher_mk4"), "compendium.hardwrought.info.family.mark",
                Identifier.withDefaultNamespace("torch"), "compendium.minecraft.info.torch");
        context.runOnClient(client -> {
            expected.forEach((id, key) -> {
                String found = CompendiumInfo.key(id);
                if (!key.equals(found)) throw new AssertionError(id + " reads " + found + ", not " + key);
            });
            if (CompendiumInfo.key(Identifier.withDefaultNamespace("stone")) != null) {
                throw new AssertionError("Plain stone has nothing written about it");
            }
            if (CompendiumInfo.key(Hardwrought.id("steel_ingot")) == null
                    || CompendiumInfo.key(Hardwrought.id("steel_ingot")).contains("family.steel")) {
                throw new AssertionError("A steel bar is not a steel tool");
            }
        });

        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                var knowledge = CoreLifecycle.require(server).knowledge();
                knowledge.forget(player.getUUID());
                for (String name : new String[] {"forge", "raw_tin", "stainless_steel_pickaxe", "smeltery_controller"}) {
                    knowledge.study(player, BuiltInRegistries.ITEM.getValue(Hardwrought.id(name)));
                }
                knowledge.discover(player, Items.TORCH);
            });
            shoot(context, Hardwrought.id("forge"), "hardwrought-compendium-info-own");
            shoot(context, Hardwrought.id("stainless_steel_pickaxe"), "hardwrought-compendium-info-family");
            // Found, never made: the left page keeps the lines, and the places go on the right.
            shoot(context, Hardwrought.id("raw_tin"), "hardwrought-compendium-info-found");
            shoot(context, Identifier.withDefaultNamespace("torch"), "hardwrought-compendium-info-unstudied");
            shoot(context, Hardwrought.id("smeltery_controller"), "hardwrought-compendium-info-long");
            context.runOnClient(client -> client.gui.setScreen(null));
        }
    }

    private static void shoot(ClientGameTestContext context, Identifier id, String name) {
        context.runOnClient(client -> CompendiumClient.openRecipes(id));
        context.waitForScreen(CompendiumScreen.class);
        context.waitTicks(10);
        context.takeScreenshot(name);
    }
}
