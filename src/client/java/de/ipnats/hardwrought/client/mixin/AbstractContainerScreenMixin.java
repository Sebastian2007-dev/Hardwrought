package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.client.knowledge.CompendiumClient;
import de.ipnats.hardwrought.progression.RecipeSelectionMenu;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * R and U over any slot, in any container: the habit every recipe browser has taught.
 *
 * <p>Key mappings do not tick while a screen is open, so the inventory, a chest and a workbench all
 * need the keys read here. The compendium screen reads them itself.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen {
    @Shadow @Final protected AbstractContainerMenu menu;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow
    protected Slot hoveredSlot;

    @Unique private Button hardwrought$recipeChoiceButton;

    protected AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void hardwrought$addRecipeChoiceButton(CallbackInfo info) {
        if (!(menu instanceof AbstractCraftingMenu crafting)
                || !(menu instanceof RecipeSelectionMenu selection)) return;
        Slot result = crafting.getResultSlot();
        hardwrought$recipeChoiceButton = Button.builder(Component.literal("↻"), button -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(
                                menu.containerId, RecipeSelectionMenu.NEXT_RECIPE_BUTTON);
                    }
                })
                .bounds(leftPos + result.x + 1, topPos + result.y + 20, 16, 12)
                .tooltip(Tooltip.create(Component.translatable("gui.hardwrought.next_matching_recipe")))
                .build();
        hardwrought$recipeChoiceButton.visible = selection.hardwrought$recipeChoiceCount() > 1;
        hardwrought$recipeChoiceButton.active = hardwrought$recipeChoiceButton.visible;
        addRenderableWidget(hardwrought$recipeChoiceButton);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void hardwrought$updateRecipeChoiceButton(CallbackInfo info) {
        if (hardwrought$recipeChoiceButton == null
                || !(menu instanceof RecipeSelectionMenu selection)) return;
        boolean multiple = selection.hardwrought$recipeChoiceCount() > 1;
        hardwrought$recipeChoiceButton.visible = multiple;
        hardwrought$recipeChoiceButton.active = multiple;
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void hardwrought$lookUpHoveredSlot(KeyEvent event, CallbackInfoReturnable<Boolean> callback) {
        if (hoveredSlot == null) return;
        ItemStack stack = hoveredSlot.getItem();
        if (stack.isEmpty()) return;
        if (CompendiumClient.RECIPES.matches(event)) {
            CompendiumClient.openRecipes(stack.getItem());
            callback.setReturnValue(true);
        } else if (CompendiumClient.USAGES.matches(event)) {
            CompendiumClient.openUsages(stack.getItem());
            callback.setReturnValue(true);
        }
    }
}
