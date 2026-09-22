package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.equipment.EquipmentContainer;
import de.ipnats.hardwrought.equipment.WornStrap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The worn strap beside the character panel: a narrow board of squares that folds out from the left
 * edge of the inventory, and one small button at the top that opens and shuts it.
 *
 * <p>The board, recessed squares and fold tab use the same worn-leather sprite set. The squares
 * themselves belong to the menu; all that happens here is the board behind them, the button, and
 * the folding.
 *
 * <p>The strap gives way to the recipe book. Vanilla opens that book into exactly this space and
 * pushes the inventory right to make room, so the two cannot both be there — and a strap drawn over
 * the top of the book would look like a bug rather than a choice. With the book open the strap folds
 * itself away and its button says why.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractRecipeBookScreen<InventoryMenu> {
    private static final Identifier STRAP = equipmentSprite("strap");
    private static final Identifier WORN_SLOT = equipmentSprite("worn_slot");
    private static final WidgetSprites FOLD_BUTTON = new WidgetSprites(
            equipmentSprite("fold_button"), equipmentSprite("fold_button_blocked"),
            equipmentSprite("fold_button_hovered"));

    @Unique private Button hardwrought$foldButton;

    private InventoryScreenMixin() {
        super(null, null, null, null);
    }

    @Unique
    private static Identifier equipmentSprite(String name) {
        return Identifier.fromNamespaceAndPath("hardwrought", "equipment/" + name);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void hardwrought$addFoldButton(CallbackInfo info) {
        hardwrought$foldButton = new ImageButton(
                leftPos + WornStrap.BUTTON_X, topPos + WornStrap.BUTTON_Y,
                WornStrap.BUTTON_SIZE, WornStrap.BUTTON_SIZE, FOLD_BUTTON, button -> {
                    WornStrap.clientExpanded = !WornStrap.clientExpanded;
                    hardwrought$refreshFoldButton();
                });
        addRenderableWidget(hardwrought$foldButton);
        hardwrought$refreshFoldButton();
    }

    @Unique
    private void hardwrought$refreshFoldButton() {
        WornStrap.clientBlocked = hardwrought$recipeBookOpen();
        if (hardwrought$foldButton == null) return;
        boolean blocked = WornStrap.clientBlocked;
        hardwrought$foldButton.active = !blocked;
        // The arrow points where the click would take the strap, not where it is.
        hardwrought$foldButton.setMessage(Component.literal(
                WornStrap.clientExpanded && !blocked ? "<" : ">"));
        hardwrought$foldButton.setTooltip(Tooltip.create(Component.translatable(blocked
                ? "gui.hardwrought.equipment.blocked" : "gui.hardwrought.equipment.open")));
    }

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void hardwrought$followRecipeBook(CallbackInfo info) {
        // Vanilla moves the whole panel when the book opens, so the button has to move with it.
        if (hardwrought$foldButton != null) {
            hardwrought$foldButton.setX(leftPos + WornStrap.BUTTON_X);
            hardwrought$foldButton.setY(topPos + WornStrap.BUTTON_Y);
        }
        hardwrought$refreshFoldButton();
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void hardwrought$drawStrap(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                       float delta, CallbackInfo info) {
        if (!hardwrought$strapShown()) return;
        int x = leftPos + WornStrap.OFFSET_X;
        int y = topPos + WornStrap.PANEL_Y;
        int height = WornStrap.panelHeight(EquipmentContainer.SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, STRAP,
                x, y, WornStrap.WIDTH, height);
        for (int index = 0; index < EquipmentContainer.SIZE; index++) {
            int slotX = leftPos + WornStrap.SLOT_X;
            int slotY = topPos + WornStrap.slotY(index);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, WORN_SLOT,
                    slotX - 1, slotY - 1, 18, 18);
        }
    }

    /** True where the strap is both folded out and not being sat on by the recipe book. */
    @Unique
    private boolean hardwrought$strapShown() {
        return WornStrap.shown();
    }

    @Unique
    private boolean hardwrought$recipeBookOpen() {
        return ((RecipeBookScreenAccessor) this).hardwrought$recipeBook().isVisible();
    }
}
