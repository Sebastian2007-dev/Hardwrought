package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.client.knowledge.CompendiumClient;
import de.ipnats.hardwrought.equipment.CarriedInventoryMenu;
import de.ipnats.hardwrought.equipment.CarriedSlot;
import de.ipnats.hardwrought.progression.RecipeSelectionMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    @Shadow @Final protected int imageHeight;
    @Shadow
    protected Slot hoveredSlot;

    @Unique private Button hardwrought$recipeChoiceButton;
    /** Dark enough to read as closed, light enough that the well beneath is still visible. */
    @Unique private static final int SHUT_COLOR = 0xC0101010;

    @Unique private Button hardwrought$pageButton;

    protected AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    /**
     * The two buttons the pack needs, under the panel rather than inside it.
     *
     * <p>Inside would have to find a free corner of a screen this mod does not own, in every menu
     * including ones from other mods. Under it there is always room, it never covers a slot, and it
     * is where the player is already looking when they reach for the bottom row.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void hardwrought$addPackButtons(CallbackInfo info) {
        if (!(menu instanceof CarriedInventoryMenu)) return;
        int y = topPos + imageHeight + 2;
        hardwrought$pageButton = Button.builder(Component.translatable("gui.hardwrought.backpack.page"),
                        button -> hardwrought$clickMenu(CarriedInventoryMenu.TOGGLE_PAGE_BUTTON))
                .bounds(leftPos + 4, y, 62, 14).build();
        addRenderableWidget(hardwrought$pageButton);
        hardwrought$refreshPackButtons();
    }

    @Unique
    private void hardwrought$clickMenu(int button) {
        if (minecraft == null || minecraft.gameMode == null) return;
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    /**
     * The page button says where it would take you, not where you are, and goes quiet where there is
     * no pack page to take you to.
     */
    @Unique
    private void hardwrought$refreshPackButtons() {
        if (hardwrought$pageButton == null || !(menu instanceof CarriedInventoryMenu carried)) return;
        boolean hasPage = carried.hardwrought$hasPackPage();
        hardwrought$pageButton.visible = hasPage;
        hardwrought$pageButton.active = hasPage;
        hardwrought$pageButton.setMessage(Component.translatable(carried.hardwrought$page() == 0
                ? "gui.hardwrought.backpack.page" : "gui.hardwrought.backpack.own"));
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

    @Inject(method = "tick", at = @At("TAIL"))
    private void hardwrought$updatePackButtons(CallbackInfo info) {
        hardwrought$refreshPackButtons();
    }

    /**
     * Shades over the squares that are shut.
     *
     * <p>The panel behind them is vanilla's texture, and it goes on drawing all twenty-seven wells
     * whatever the menu thinks. Without this a player with no pack sees a full inventory that
     * silently refuses everything, and a leather pack looks like it has twenty-seven places to put
     * things when it has nine. The shade is the only thing that tells them apart.
     *
     * <p>Drawn per position rather than per slot: the two grids share their coordinates, so a
     * position counts as shut only when nothing there is open.
     */
    @Inject(method = "extractSlots", at = @At("HEAD"))
    private void hardwrought$shadeShutSquares(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                              CallbackInfo info) {
        java.util.Set<Long> open = new java.util.HashSet<>();
        java.util.Set<Long> shut = new java.util.HashSet<>();
        for (Slot slot : menu.slots) {
            if (!(slot instanceof CarriedSlot)) continue;
            long position = (long) slot.x << 32 | (slot.y & 0xFFFFFFFFL);
            (slot.isActive() ? open : shut).add(position);
        }
        shut.removeAll(open);
        for (Slot slot : menu.slots) {
            if (!(slot instanceof CarriedSlot)) continue;
            long position = (long) slot.x << 32 | (slot.y & 0xFFFFFFFFL);
            if (!shut.remove(position)) continue;
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, SHUT_COLOR);
        }
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
