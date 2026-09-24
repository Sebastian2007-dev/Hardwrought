package de.ipnats.hardwrought.metallurgy;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.knowledge.WorldRecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * Section 53, first step of the processing chain: a chunk of raw ore broken down to powder.
 *
 * <p>One chunk, one powder. The later steps — screening, washing, concentration — are where a
 * better process gets more out of the same ore; crushing only changes its form.
 *
 * <p>Raw ore goes in, and so does a finished ingot: filing a bar back down is how metal is got into
 * a mixture such as bronze, which is made from powders. Coal, gems and the like drop as finished
 * items already, and a crusher that ground diamonds into diamond powder would be a way to destroy
 * them rather than to work them.
 */
public final class Crushing {
    private static final Map<Item, Item> RESULTS = new HashMap<>();

    private Crushing() { }

    /** What one piece of this becomes, or null where it is not crushed at all. */
    public static Item result(ItemStack stack) {
        if (stack.isEmpty()) return null;
        return table().get(stack.getItem());
    }

    public static boolean isCrushable(ItemStack stack) {
        return result(stack) != null;
    }

    /**
     * The table as the compendium shows it: one row per thing the crusher takes, with the crusher
     * as the station. Read straight off the table, so a row added there is a row in the book.
     */
    public static java.util.List<WorldRecipes.WorldRecipe> worldRecipes() {
        ItemStack crusher = new ItemStack(ModBlocks.STARTER_CRUSHER);
        java.util.List<WorldRecipes.WorldRecipe> recipes = new java.util.ArrayList<>();
        for (Map.Entry<Item, Item> row : table().entrySet()) {
            var input = BuiltInRegistries.ITEM.getKey(row.getKey());
            recipes.add(new WorldRecipes.WorldRecipe(
                    Hardwrought.id("crushing/" + input.getNamespace() + "_" + input.getPath()),
                    WorldRecipes.one(new ItemStack(row.getKey())), new ItemStack(row.getValue()), crusher));
        }
        // The table is a hash map; the book should not reshuffle between two openings.
        recipes.sort(java.util.Comparator.comparing(recipe -> recipe.id().toString()));
        return recipes;
    }

    /**
     * Built on first use rather than in a static initialiser, because the powders and the raw metals
     * are registered by the mod initialiser and do not exist when this class is first loaded.
     */
    private static Map<Item, Item> table() {
        if (RESULTS.isEmpty()) {
            RESULTS.put(Items.RAW_IRON, OrePowders.powder(OrePowders.VanillaOre.IRON));
            RESULTS.put(Items.RAW_COPPER, OrePowders.powder(OrePowders.VanillaOre.COPPER));
            RESULTS.put(Items.RAW_GOLD, OrePowders.powder(OrePowders.VanillaOre.GOLD));
            RESULTS.put(Items.IRON_INGOT, OrePowders.powder(OrePowders.VanillaOre.IRON));
            RESULTS.put(Items.COPPER_INGOT, OrePowders.powder(OrePowders.VanillaOre.COPPER));
            RESULTS.put(Items.GOLD_INGOT, OrePowders.powder(OrePowders.VanillaOre.GOLD));
            for (Metal metal : Metal.values()) {
                RESULTS.put(ModMetals.raw(metal), OrePowders.powder(metal));
                RESULTS.put(ModMetals.ingot(metal), OrePowders.powder(metal));
            }
        }
        return RESULTS;
    }
}
