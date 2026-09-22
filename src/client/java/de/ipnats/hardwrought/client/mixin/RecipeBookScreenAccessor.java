package de.ipnats.hardwrought.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Whether the recipe book is currently open.
 *
 * <p>The screen keeps its book to itself, and the worn strap has to know: vanilla opens the book into
 * exactly the space the strap hangs in, and pushes the whole panel right to make room for it.
 */
@Mixin(AbstractRecipeBookScreen.class)
public interface RecipeBookScreenAccessor {
    @Accessor("recipeBookComponent")
    RecipeBookComponent<?> hardwrought$recipeBook();
}
