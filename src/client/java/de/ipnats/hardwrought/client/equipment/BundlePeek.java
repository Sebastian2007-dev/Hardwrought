package de.ipnats.hardwrought.client.equipment;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

import java.util.List;

/**
 * A bundle held open. Vanilla shows what is in a bundle only while the pointer rests on it, and takes
 * things out one by one in the order the wheel has chosen. Held with shift, the look into the bundle
 * stays where it opened while the pointer moves over it, and a click on a thing in it takes that thing
 * out onto the cursor.
 *
 * <p>Taking out is vanilla's own: the chosen thing is selected in the bundle, as the wheel would, and
 * the bundle is right-clicked, as a player would. Nothing about a bundle's rules is written again here.
 */
public final class BundlePeek {
    private static final int COLUMNS = 4, CELL = 24, PAD = 6, HEAD = 14, FOOT = 12;

    /** The bundle held open, the screen it is open in, and where the look into it stands. */
    private static Slot slot;
    private static AbstractContainerScreen<?> screen;
    private static int left, top;

    private BundlePeek() { }

    /** Whether a bundle is held open, so the screen's own tooltip stays out of the way. */
    public static boolean open(AbstractContainerScreen<?> on) {
        return slot != null && screen == on;
    }

    private static List<ItemStack> contents(Slot at) {
        BundleContents contents = at.getItem().get(DataComponents.BUNDLE_CONTENTS);
        return contents == null ? List.of() : contents.itemCopies().toList();
    }

    /** Opens or closes the look with shift, and draws it while it is open. */
    public static void render(AbstractContainerScreen<?> on, GuiGraphicsExtractor graphics, Slot hovered, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.hasShiftDown()) {
            slot = null;
            return;
        }
        if ((slot == null || screen != on) && hovered != null && hovered.getItem().getItem() instanceof BundleItem
                && !contents(hovered).isEmpty()) {
            slot = hovered;
            screen = on;
            left = mouseX + 12;
            top = mouseY - 12;
        }
        if (!open(on)) return;
        List<ItemStack> items = contents(slot);
        // An emptied or a moved bundle closes the look.
        if (items.isEmpty() || !(slot.getItem().getItem() instanceof BundleItem)) {
            slot = null;
            return;
        }
        Font font = minecraft.font;
        int rows = (items.size() + COLUMNS - 1) / COLUMNS;
        Component title = slot.getItem().getHoverName();
        Component hint = Component.translatable("gui.hardwrought.bundle.take");
        int width = Math.max(COLUMNS * CELL, Math.max(font.width(title), font.width(hint))) + PAD * 2;
        int height = HEAD + rows * CELL + FOOT + PAD;
        // Kept on the screen.
        left = Math.min(left, on.width - width - 4);
        top = Math.clamp(top, 4, Math.max(4, on.height - height - 4));
        TooltipRenderUtil.extractTooltipBackground(graphics, left, top, width, height, null);
        graphics.text(font, title, left + PAD, top + 3, 0xFFFFFFFF, true);
        int under = cellAt(items.size(), mouseX, mouseY);
        for (int i = 0; i < items.size(); i++) {
            int x = left + PAD + (i % COLUMNS) * CELL, y = top + HEAD + (i / COLUMNS) * CELL;
            graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, i == under ? 0xC0808080 : 0x80303030);
            graphics.item(items.get(i), x + 4, y + 4);
            graphics.itemDecorations(font, items.get(i), x + 4, y + 4);
        }
        Component footer = under >= 0 ? items.get(under).getHoverName() : hint;
        graphics.text(font, footer, left + PAD, top + HEAD + rows * CELL + 3, under >= 0 ? 0xFFFFFFA0 : 0xFFA0A0A0, true);
    }

    /** Where a thing in the open look is drawn, its middle in screen coordinates: for tests. */
    public static int[] cellCentre(int index) {
        return new int[] {left + PAD + (index % COLUMNS) * CELL + CELL / 2, top + HEAD + (index / COLUMNS) * CELL + CELL / 2};
    }

    private static int cellAt(int count, double mouseX, double mouseY) {
        int x = (int) Math.floor((mouseX - left - PAD) / CELL), y = (int) Math.floor((mouseY - top - HEAD) / CELL);
        if (mouseX < left + PAD || mouseY < top + HEAD || x < 0 || x >= COLUMNS || y < 0) return -1;
        int index = y * COLUMNS + x;
        return index < count ? index : -1;
    }

    /**
     * A click into the open look takes the thing under it out onto the cursor. Returns whether the click
     * was the look's, so the screen does not act on it as well.
     */
    public static boolean click(AbstractContainerScreen<?> on, MouseButtonEvent event, int leftPos, int topPos) {
        if (!open(on)) return false;
        List<ItemStack> items = contents(slot);
        int index = cellAt(items.size(), event.x(), event.y());
        if (index < 0) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null || !on.getMenu().getCarried().isEmpty()) return true;
        BundleItem.toggleSelectedItem(slot.getItem(), index);
        minecraft.getConnection().send(new ServerboundSelectBundleItemPacket(slot.index, index));
        // Vanilla takes the selected thing out of a bundle right-clicked with an empty cursor.
        on.mouseClicked(new MouseButtonEvent(leftPos + slot.x + 8, topPos + slot.y + 8,
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_RIGHT, 0)), false);
        return true;
    }
}
