package de.ipnats.hardwrought.client.chemistry;

import de.ipnats.hardwrought.chemistry.StillBlockEntity;
import de.ipnats.hardwrought.chemistry.StillMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The still opened. The charge on the left with the bottles under it and a flame between them while
 * there is fire under the pot; an arrow filling towards the products on the right as the batch boils.
 */
public class StillScreen extends AbstractContainerScreen<StillMenu> {
    private static final Identifier FLAME = Identifier.withDefaultNamespace("container/furnace/lit_progress");
    private static final Identifier ARROW = Identifier.withDefaultNamespace("container/furnace/burn_progress");
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int TEXT = 0xFF404040;

    public StillScreen(StillMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, DARK);
        graphics.fill(x, y, x + imageWidth - 1, y + imageHeight - 1, LIGHT);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL);

        slot(graphics, StillMenu.CHARGE_AT);
        slot(graphics, StillMenu.BOTTLES_AT);
        for (int i = 0; i < StillBlockEntity.OUTPUTS; i++) slot(graphics, StillMenu.outputAt(i));
        slot(graphics, StillMenu.RETURNED_AT);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) slot(graphics, new int[] {8 + column * 18, 84 + row * 18});
        }
        for (int column = 0; column < 9; column++) slot(graphics, new int[] {8 + column * 18, 142});

        if (menu.heated()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FLAME, 14, 14, 0, 0, x + 27, y + 36, 14, 14);
        }
        int filled = (int) Math.ceil(menu.progress() * 24);
        if (filled > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, 24, 16, 0, 0, x + 60, y + 25, filled, 16);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        Component heat = Component.translatable(menu.heated() ? "gui.hardwrought.still.heated" : "gui.hardwrought.still.cold");
        graphics.text(font, heat, imageWidth - 8 - font.width(heat), inventoryLabelY, TEXT, false);
    }

    private void slot(GuiGraphicsExtractor graphics, int[] at) {
        int sx = leftPos + at[0] - 1;
        int sy = topPos + at[1] - 1;
        graphics.fill(sx, sy, sx + 18, sy + 18, LIGHT);
        graphics.fill(sx, sy, sx + 17, sy + 17, SLOT_DARK);
        graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT);
    }
}
