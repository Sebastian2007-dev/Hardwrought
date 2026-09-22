package de.ipnats.hardwrought.progression;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;

/** A crafting menu that can choose between several recipes matching the same grid. */
public interface RecipeSelectionMenu {
    /** Kept well outside vanilla's small per-menu button ids. */
    int NEXT_RECIPE_BUTTON = 0x485701;

    /** Re-evaluates the alternatives after the crafting grid changes. */
    void hardwrought$refreshRecipeChoices(ServerLevel level, ServerPlayer player,
                                           CraftingContainer input, ResultContainer result);

    /** Selects the next valid result without consuming anything. */
    boolean hardwrought$nextRecipe(ServerPlayer player);

    /** Synced to the client; the button is unnecessary for zero or one possible result. */
    int hardwrought$recipeChoiceCount();
}
