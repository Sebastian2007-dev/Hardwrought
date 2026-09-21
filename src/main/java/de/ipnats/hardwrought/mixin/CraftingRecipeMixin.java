package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.progression.ToolCrafting;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tool-assisted crafting: a tool in a recipe is used, not used up.
 *
 * <p>Specification section 71 puts tools between the player and the material at every step, and a
 * workbench is no exception — but a recipe that swallowed a whole hatchet would be a strange way to
 * say so. A tool listed as an ingredient comes back out of the grid one point of wear worse, and
 * only disappears when it finally wears through.
 *
 * <p>Vanilla decides what is returned from a crafting grid in one place, from the item alone. The
 * stack in the grid is what knows its own wear, so the returned list is corrected here rather than
 * the item being given a remainder it cannot describe.
 */
@Mixin(CraftingRecipe.class)
public interface CraftingRecipeMixin {
    @Inject(method = "defaultCraftingReminder", at = @At("RETURN"), cancellable = true)
    private static void hardwrought$returnToolsWorn(CraftingInput input,
                                                    CallbackInfoReturnable<NonNullList<ItemStack>> info) {
        NonNullList<ItemStack> remaining = info.getReturnValue();
        if (remaining == null) return;
        for (int slot = 0; slot < remaining.size() && slot < input.size(); slot++) {
            if (!remaining.get(slot).isEmpty()) continue;
            ItemStack used = input.getItem(slot);
            if (!ToolCrafting.isCraftingTool(used)) continue;
            remaining.set(slot, ToolCrafting.worn(used));
        }
        info.setReturnValue(remaining);
    }
}
