package de.ipnats.hardwrought.client.materials;

import de.ipnats.hardwrought.materials.ChemMenu;
import de.ipnats.hardwrought.refining.RefineryMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/**
 * A chemistry machine opened: on the left the liquid and the gas it takes, as gauges; two input slots,
 * an arrow filling as the work goes, two output slots; on the right the gas it gives out. Its state and
 * voltage are written over the inventory.
 */
public class ChemScreen extends AbstractContainerScreen<ChemMenu> {
    private static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, DARK = 0xFF555555;
    private static final int SLOT_DARK = 0xFF373737, SLOT = 0xFF8B8B8B, TEXT = 0xFF404040, EMPTY = 0xFF8B8B8B;
    private static final int GOOD = 0xFF2E7D32, BAD = 0xFF9E2A2A;
    private static final int GAUGE_TOP = 18, GAUGE_BOTTOM = 70, GAUGE_W = 12;
    private static final int FLUID_X = 10, GAS_IN_X = 28, GAS_OUT_X = 152;
    /** A gauge's gas is drawn against what a store holds. */
    private static final int GAS_CAPACITY = 64;

    public ChemScreen(ChemMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        inventoryLabelY = imageHeight - 94;
    }

    private void slot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(leftPos + x - 1, topPos + y - 1, leftPos + x + 17, topPos + y + 17, SLOT_DARK);
        graphics.fill(leftPos + x, topPos + y, leftPos + x + 17, topPos + y + 17, LIGHT);
        graphics.fill(leftPos + x, topPos + y, leftPos + x + 16, topPos + y + 16, SLOT);
    }

    private void gauge(GuiGraphicsExtractor graphics, int x, int substance, int amount, int capacity) {
        int gx = leftPos + x;
        graphics.fill(gx - 1, topPos + GAUGE_TOP - 1, gx + GAUGE_W + 1, topPos + GAUGE_BOTTOM + 1, SLOT_DARK);
        graphics.fill(gx, topPos + GAUGE_TOP, gx + GAUGE_W, topPos + GAUGE_BOTTOM, EMPTY);
        if (capacity <= 0 || amount <= 0) return;
        int height = (int) Math.round((GAUGE_BOTTOM - GAUGE_TOP) * Math.min(1.0, amount / (double) capacity));
        graphics.fill(gx, topPos + GAUGE_BOTTOM - height, gx + GAUGE_W, topPos + GAUGE_BOTTOM, RefineryMenu.colour(substance));
        graphics.fill(gx, topPos + GAUGE_BOTTOM - height, gx + GAUGE_W, topPos + GAUGE_BOTTOM - height + 1, 0x60FFFFFF);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, DARK);
        graphics.fill(x, y, x + imageWidth - 1, y + imageHeight - 1, LIGHT);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL);
        slot(graphics, ChemMenu.INPUT_X, ChemMenu.TOP_Y);
        slot(graphics, ChemMenu.INPUT_X, ChemMenu.SECOND_Y);
        slot(graphics, ChemMenu.OUTPUT_X, ChemMenu.TOP_Y);
        slot(graphics, ChemMenu.OUTPUT_X, ChemMenu.SECOND_Y);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) slot(graphics, 8 + column * 18, 84 + row * 18);
        }
        for (int column = 0; column < 9; column++) slot(graphics, 8 + column * 18, 142);

        if (menu.get(ChemMenu.FLUID_CAPACITY) > 0) {
            gauge(graphics, FLUID_X, menu.get(ChemMenu.FLUID), menu.get(ChemMenu.FLUID_AMOUNT), menu.get(ChemMenu.FLUID_CAPACITY));
        }
        gauge(graphics, GAS_IN_X, menu.get(ChemMenu.GAS_IN), menu.get(ChemMenu.GAS_IN_AMOUNT), GAS_CAPACITY);
        gauge(graphics, GAS_OUT_X, menu.get(ChemMenu.GAS_OUT), menu.get(ChemMenu.GAS_OUT_AMOUNT), GAS_CAPACITY);

        // The arrow from the inputs to the outputs, filling as the work goes.
        int ax = x + 80, ay = y + 36, length = 26;
        graphics.fill(ax, ay, ax + length, ay + 6, SLOT_DARK);
        int filled = length * Math.clamp(menu.get(ChemMenu.PROGRESS), 0, 1000) / 1000;
        if (filled > 0) graphics.fill(ax, ay + 1, ax + filled, ay + 5, 0xFFE0A030);
        graphics.fill(ax + length, ay - 2, ax + length + 2, ay + 8, SLOT_DARK);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        int state = menu.get(ChemMenu.STATE);
        String key = switch (state) {
            case ChemMenu.RUNNING -> "gui.hardwrought.chem.running";
            case ChemMenu.STALLED -> "gui.hardwrought.chem.stalled";
            default -> "gui.hardwrought.chem.idle";
        };
        Component line = Component.translatable(key, String.format(Locale.ROOT, "%.0f", menu.get(ChemMenu.VOLTS) / 10.0));
        String text = font.plainSubstrByWidth(line.getString(), imageWidth - 16 - font.width(playerInventoryTitle) - 6);
        graphics.text(font, text, imageWidth - 8 - font.width(text), inventoryLabelY,
                state == ChemMenu.RUNNING ? GOOD : state == ChemMenu.STALLED ? BAD : TEXT, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        tooltip(graphics, mouseX, mouseY, FLUID_X, menu.get(ChemMenu.FLUID), menu.get(ChemMenu.FLUID_AMOUNT),
                menu.get(ChemMenu.FLUID_CAPACITY), false, "gui.hardwrought.chem.fluid_in");
        tooltip(graphics, mouseX, mouseY, GAS_IN_X, menu.get(ChemMenu.GAS_IN), menu.get(ChemMenu.GAS_IN_AMOUNT),
                GAS_CAPACITY, true, "gui.hardwrought.chem.gas_in");
        tooltip(graphics, mouseX, mouseY, GAS_OUT_X, menu.get(ChemMenu.GAS_OUT), menu.get(ChemMenu.GAS_OUT_AMOUNT),
                GAS_CAPACITY, true, "gui.hardwrought.chem.gas_out");
    }

    private void tooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int substance, int amount, int capacity,
                         boolean gas, String what) {
        if (capacity <= 0 || !isHovering(x - 1, GAUGE_TOP - 1, GAUGE_W + 2, GAUGE_BOTTOM - GAUGE_TOP + 2, mouseX, mouseY)) return;
        Component name = substance == RefineryMenu.NOTHING
                ? Component.translatable("gui.hardwrought.refinery.nothing") : RefineryMenu.name(substance);
        graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable(what),
                Component.translatable(gas ? "gui.hardwrought.refinery.gauge_gas" : "gui.hardwrought.refinery.gauge_liquid",
                        name, amount, capacity)), mouseX, mouseY);
    }
}
