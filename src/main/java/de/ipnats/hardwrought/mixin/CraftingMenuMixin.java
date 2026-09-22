package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.progression.RecipeSelectionMenu;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies a remembered recipe choice after vanilla updates either crafting grid. */
@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
    private static void hardwrought$applySelectedRecipe(AbstractContainerMenu menu,
                                                         ServerLevel level, Player player,
                                                         CraftingContainer input,
                                                         ResultContainer result,
                                                         RecipeHolder<CraftingRecipe> recipe,
                                                         CallbackInfo info) {
        if (menu instanceof RecipeSelectionMenu selection && player instanceof ServerPlayer serverPlayer) {
            selection.hardwrought$refreshRecipeChoices(level, serverPlayer, input, result);
        }
    }
}
