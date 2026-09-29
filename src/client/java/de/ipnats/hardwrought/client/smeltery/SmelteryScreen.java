package de.ipnats.hardwrought.client.smeltery;

import de.ipnats.hardwrought.smeltery.MoltenMetals;
import de.ipnats.hardwrought.smeltery.SmelteryControllerBlockEntity;
import de.ipnats.hardwrought.smeltery.SmelteryMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The smeltery opened: fuel under a flame on the left, the pieces melting in the middle, and on the
 * right the tank — its metals in layers, lowest first — and a thermometer with the fire's goal marked.
 */
public class SmelteryScreen extends AbstractContainerScreen<SmelteryMenu> {
    private static final Identifier FLAME = Identifier.withDefaultNamespace("container/furnace/lit_progress");
    private static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, DARK = 0xFF555555;
    private static final int SLOT_DARK = 0xFF373737, SLOT = 0xFF8B8B8B, TEXT = 0xFF404040;
    private static final int TANK_X = 164, TANK_W = 22, TOP = 18, BOTTOM = 90;
    private static final int GAUGE_X = 190;
    private static final double SCALE_C = 3600.0;

    public SmelteryScreen(SmelteryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 204, 186);
        inventoryLabelY = imageHeight - 94;
    }

    private SmelteryControllerBlockEntity smeltery() {
        return minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof SmelteryControllerBlockEntity entity ? entity : null;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, DARK);
        graphics.fill(x, y, x + imageWidth - 1, y + imageHeight - 1, LIGHT);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL);
        slot(graphics, SmelteryMenu.FUEL_X, SmelteryMenu.FUEL_Y);
        for (int i = 0; i < SmelteryControllerBlockEntity.MELT_SLOTS; i++) {
            int[] at = SmelteryMenu.meltPosition(i);
            slot(graphics, at[0], at[1]);
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) slot(graphics, 8 + column * 18, 104 + row * 18);
        }
        for (int column = 0; column < 9; column++) slot(graphics, 8 + column * 18, 162);

        int burning = (int) Math.ceil(menu.burnLeft() / 1000.0 * 14);
        if (burning > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FLAME, 14, 14, 0, 14 - burning,
                    x + SmelteryMenu.FUEL_X + 1, y + SmelteryMenu.FUEL_Y - 17 + 14 - burning, 14, burning);
        }

        // The tank, its metals in layers from the bottom up.
        int tx = x + TANK_X;
        graphics.fill(tx - 1, y + TOP - 1, tx + TANK_W + 1, y + BOTTOM + 1, SLOT_DARK);
        graphics.fill(tx, y + TOP, tx + TANK_W, y + BOTTOM, 0xFF2A2320);
        SmelteryControllerBlockEntity smeltery = smeltery();
        int capacity = menu.capacity();
        if (smeltery != null && capacity > 0) {
            int bottom = y + BOTTOM;
            for (Map.Entry<String, Integer> fluid : smeltery.fluids().entrySet()) {
                int height = (int) Math.max(1, Math.round(fluid.getValue() / (double) capacity * (BOTTOM - TOP)));
                graphics.fill(tx, bottom - height, tx + TANK_W, bottom, MoltenColors.of(fluid.getKey()));
                bottom -= height;
            }
        }

        int gx = x + GAUGE_X;
        graphics.fill(gx - 1, y + TOP - 1, gx + 7, y + BOTTOM + 1, SLOT_DARK);
        graphics.fill(gx, y + TOP, gx + 6, y + BOTTOM, 0xFF2A2320);
        int height = BOTTOM - TOP;
        int filled = (int) Math.round(Math.min(1.0, menu.temperature() / SCALE_C) * height);
        if (filled > 0) graphics.fill(gx, y + BOTTOM - filled, gx + 6, y + BOTTOM, glow(menu.temperature()));
        int mark = (int) Math.round(Math.min(1.0, menu.target() / SCALE_C) * height);
        graphics.fill(gx - 2, y + BOTTOM - mark, gx + 8, y + BOTTOM - mark + 1, 0xFFFFFFFF);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        Component heat = Component.translatable("gui.hardwrought.forge.heat", menu.temperature());
        graphics.text(font, heat, imageWidth - 8 - font.width(heat), titleLabelY, TEXT, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (isHovering(TANK_X - 1, TOP - 1, TANK_W + 2, BOTTOM - TOP + 2, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            SmelteryControllerBlockEntity smeltery = smeltery();
            if (smeltery == null || !smeltery.formed()) {
                lines.add(Component.translatable("gui.hardwrought.smeltery.unformed"));
            } else {
                lines.add(Component.translatable("gui.hardwrought.smeltery.capacity", smeltery.fluidTotal(), menu.capacity()));
                List<Map.Entry<String, Integer>> fluids = new ArrayList<>(smeltery.fluids().entrySet());
                for (int i = fluids.size() - 1; i >= 0; i--) {
                    var fluid = fluids.get(i);
                    lines.add(Component.translatable("gui.hardwrought.smeltery.fluid",
                            Component.translatable("molten.hardwrought." + fluid.getKey()), fluid.getValue(),
                            String.format(java.util.Locale.ROOT, "%.1f", fluid.getValue() / (double) MoltenMetals.INGOT)));
                }
            }
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
        if (isHovering(GAUGE_X - 1, TOP - 1, 8, BOTTOM - TOP + 2, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("gui.hardwrought.forge.heat", menu.temperature()),
                    Component.translatable("gui.hardwrought.forge.heading", menu.target()),
                    Component.translatable("gui.hardwrought.smeltery.heat_hint")), mouseX, mouseY);
        }
    }

    private void slot(GuiGraphicsExtractor graphics, int ax, int ay) {
        int sx = leftPos + ax - 1, sy = topPos + ay - 1;
        graphics.fill(sx, sy, sx + 18, sy + 18, LIGHT);
        graphics.fill(sx, sy, sx + 17, sy + 17, SLOT_DARK);
        graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT);
    }

    private static int glow(int celsius) {
        if (celsius < 500) return 0xFF6A2A1A;
        if (celsius < 1000) return 0xFFB23A1A;
        if (celsius < 1500) return 0xFFE0701E;
        if (celsius < 2500) return 0xFFF5B03A;
        return 0xFFFFF0B0;
    }
}
