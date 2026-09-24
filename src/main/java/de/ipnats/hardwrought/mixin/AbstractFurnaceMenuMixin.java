package de.ipnats.hardwrought.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import de.ipnats.hardwrought.smithing.Heating;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Shift-clicking a piece of forge metal puts it in the furnace again.
 *
 * <p>The menu decides where a shift-clicked stack goes by asking whether any smelting recipe takes
 * it. Raw ore and ingots have none any more — the furnace only heats them for the anvil — so the menu
 * sent them to the other end of the inventory instead. What the furnace heats counts as what it takes.
 */
@Mixin(AbstractFurnaceMenu.class)
public abstract class AbstractFurnaceMenuMixin {
    @ModifyReturnValue(method = "canSmelt", at = @At("RETURN"))
    private boolean hardwrought$heatsForgeMetal(boolean smelts, ItemStack stack) {
        return smelts || Heating.recipeFor(stack) != null;
    }
}
