package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.progression.BenchTier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.List;

/** A bundle is leaf fibre over leather, in the grid a player carries. */
public final class BundleGameTests {
    @GameTest
    public void fibreOverLeatherIsABundleInTheInventoryGrid(GameTestHelper helper) {
        var recipe = (ShapedRecipe) helper.getLevel().getServer().getRecipeManager()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.withDefaultNamespace("bundle"))).orElseThrow().value();
        for (var cord : List.of(ModItems.GREEN_FIBRE, ModItems.LEAF_STRING, Items.STRING)) {
            var grid = CraftingInput.of(2, 2, List.of(ItemStack.EMPTY, new ItemStack(cord), ItemStack.EMPTY, new ItemStack(Items.LEATHER)));
            helper.assertTrue(recipe.matches(grid, helper.getLevel()), cord + " over leather makes a bundle");
        }
        helper.assertTrue(BenchTier.required(new ItemStack(Items.BUNDLE)) == BenchTier.INVENTORY,
                "and the grid in the inventory may make it");
        helper.succeed();
    }
}
