package de.ipnats.hardwrought.client.smithing;

import de.ipnats.hardwrought.smithing.ForgeBlockEntity;
import de.ipnats.hardwrought.smithing.ForgeMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * The forge opened. Fuel on the left under a flame that burns down as the coal does, the pieces in
 * the middle, and on the right a thermometer: the fire's heat, and a mark for what it is heading for.
 */
public class ForgeScreen extends AbstractContainerScreen<ForgeMenu> {
    private static final Identifier FLAME = Identifier.withDefaultNamespace("container/furnace/lit_progress");
    private static final double SCALE_C = 3500.0;
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int TEXT = 0xFF404040;
    private static final int GAUGE_X = 150;
    private static final int GAUGE_TOP = 17;
    private static final int GAUGE_BOTTOM = 69;

    public ForgeScreen(ForgeMenu menu, Inventory inventory, Component title) {
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

        int layout = menu.layout();
        for (int i = 0; i < menu.fuelCount(); i++) slot(graphics, ForgeMenu.fuelPosition(layout, i));
        for (int i = 0; i < ForgeBlockEntity.metalSlots(layout); i++) slot(graphics, ForgeMenu.metalPosition(layout, i));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) slot(graphics, new int[] {8 + column * 18, 84 + row * 18});
        }
        for (int column = 0; column < 9; column++) slot(graphics, new int[] {8 + column * 18, 142});

        // The flame above the fuel burns down with the coal.
        int flameX = x + 26 + 1;
        int flameY = y + (layout == 2 ? 18 : 36);
        int burning = (int) Math.ceil(menu.burnLeft() / 1000.0 * 14);
        if (burning > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FLAME, 14, 14, 0, 14 - burning,
                    flameX, flameY + 14 - burning, 14, burning);
        }

        // Thermometer: the fire now, and a white mark where it is going.
        int gx = x + GAUGE_X;
        graphics.fill(gx - 1, y + GAUGE_TOP - 1, gx + 9, y + GAUGE_BOTTOM + 1, SLOT_DARK);
        graphics.fill(gx, y + GAUGE_TOP, gx + 9, y + GAUGE_BOTTOM + 1, LIGHT);
        graphics.fill(gx, y + GAUGE_TOP, gx + 8, y + GAUGE_BOTTOM, 0xFF2A2320);
        int height = GAUGE_BOTTOM - GAUGE_TOP;
        int filled = (int) Math.round(Math.min(1.0, menu.temperature() / SCALE_C) * height);
        if (filled > 0) graphics.fill(gx, y + GAUGE_BOTTOM - filled, gx + 8, y + GAUGE_BOTTOM, glow(menu.temperature()));
        int mark = (int) Math.round(Math.min(1.0, menu.target() / SCALE_C) * height);
        graphics.fill(gx - 2, y + GAUGE_BOTTOM - mark, gx + 10, y + GAUGE_BOTTOM - mark + 1, 0xFFFFFFFF);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        Component heat = Component.translatable("gui.hardwrought.forge.heat", menu.temperature());
        graphics.text(font, heat, GAUGE_X + 9 - font.width(heat), titleLabelY, TEXT, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (isHovering(GAUGE_X - 1, GAUGE_TOP - 1, 11, GAUGE_BOTTOM - GAUGE_TOP + 2, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font, java.util.List.of(
                    Component.translatable("gui.hardwrought.forge.heat", menu.temperature()),
                    Component.translatable("gui.hardwrought.forge.heading", menu.target()),
                    Component.translatable("gui.hardwrought.forge.holds")), mouseX, mouseY);
        }
    }

    private void slot(GuiGraphicsExtractor graphics, int[] at) {
        int sx = leftPos + at[0] - 1;
        int sy = topPos + at[1] - 1;
        graphics.fill(sx, sy, sx + 18, sy + 18, LIGHT);
        graphics.fill(sx, sy, sx + 17, sy + 17, SLOT_DARK);
        graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT);
    }

    /** From dull red through orange to yellow-white, as iron looks at that heat. */
    private static int glow(int celsius) {
        if (celsius < 500) return 0xFF6A2A1A;
        if (celsius < 900) return 0xFFB23A1A;
        if (celsius < 1200) return 0xFFE0701E;
        if (celsius < 1700) return 0xFFF5B03A;
        return 0xFFFFF0B0;
    }
}
