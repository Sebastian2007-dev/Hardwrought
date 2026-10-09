package de.ipnats.hardwrought.client.electricity;

import de.ipnats.hardwrought.electricity.MachineBlockEntity;
import de.ipnats.hardwrought.electricity.MachineMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

/**
 * A machine opened. Input, an arrow filling, output — and on the left a voltmeter: a column from
 * nought to 120 volts with the band the machine is made for marked on it, so that "why does it not
 * run" is answered by looking.
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final Identifier ARROW = Identifier.withDefaultNamespace("container/furnace/burn_progress");
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int TEXT = 0xFF404040;
    private static final int GOOD = 0xFF3FA34D, LOW = 0xFF8A8A8A, HIGH = 0xFFE08A1E, DEADLY = 0xFFD03A2B;

    /** The voltmeter: where it stands, and the voltage at its top. */
    private static final int GAUGE_X = 20, GAUGE_TOP = 18, GAUGE_HEIGHT = 52, GAUGE_WIDTH = 10;
    private static final float GAUGE_VOLTS = 120.0f;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
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

        slot(graphics, MachineMenu.INPUT_AT);
        slot(graphics, MachineMenu.OUTPUT_AT);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) slot(graphics, new int[] {8 + column * 18, 84 + row * 18});
        }
        for (int column = 0; column < 9; column++) slot(graphics, new int[] {8 + column * 18, 142});

        // The way the piece has to go, so that the arrow filling along it reads as one.
        graphics.fill(x + 88, y + 42, x + 112, y + 44, SLOT);
        int filled = (int) Math.ceil(menu.progress() * 24);
        if (filled > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, 24, 16, 0, 0, x + 88, y + 35, filled, 16);
        }

        // The voltmeter: a dark glass, the band the machine is made for, and the column standing in it.
        int left = x + GAUGE_X, top = y + GAUGE_TOP, bottom = top + GAUGE_HEIGHT;
        graphics.fill(left - 1, top - 1, left + GAUGE_WIDTH + 1, bottom + 1, SLOT_DARK);
        graphics.fill(left, top, left + GAUGE_WIDTH, bottom, 0xFF1C1C1C);
        graphics.fill(left, at(bottom, (float) MachineBlockEntity.STRAIN_VOLTS), left + GAUGE_WIDTH,
                at(bottom, (float) MachineBlockEntity.STALL_VOLTS), 0xFF24402A);
        float volts = menu.volts();
        if (volts > 0.5f) {
            graphics.fill(left + 2, at(bottom, Math.min(volts, GAUGE_VOLTS)), left + GAUGE_WIDTH - 2, bottom, colour(volts));
        }
        // A mark at the voltage it is made for.
        int rated = at(bottom, (float) MachineBlockEntity.RATED_VOLTS);
        graphics.fill(left - 3, rated, left, rated + 1, TEXT);
        graphics.fill(left + GAUGE_WIDTH, rated, left + GAUGE_WIDTH + 3, rated + 1, TEXT);
    }

    private static int at(int bottom, float volts) {
        return bottom - Math.round(volts / GAUGE_VOLTS * GAUGE_HEIGHT);
    }

    private static int colour(float volts) {
        if (volts < MachineBlockEntity.STALL_VOLTS) return LOW;
        if (volts < MachineBlockEntity.STRAIN_VOLTS) return GOOD;
        return volts < MachineBlockEntity.BURN_VOLTS ? HIGH : DEADLY;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        Component reading = Component.translatable("gui.hardwrought.machine.reading",
                String.format(Locale.ROOT, "%.0f", menu.volts()), String.format(Locale.ROOT, "%.1f", menu.amps()));
        graphics.text(font, reading, imageWidth - 8 - font.width(reading), inventoryLabelY, TEXT, false);
        Component status = Component.translatable(switch (menu.status()) {
            case MachineMenu.RUNNING -> "gui.hardwrought.machine.running";
            case MachineMenu.STALLED -> "gui.hardwrought.machine.stalled";
            case MachineMenu.BURNT -> "gui.hardwrought.machine.burnt";
            default -> "gui.hardwrought.machine.idle";
        });
        int middle = (MachineMenu.INPUT_AT[0] + MachineMenu.OUTPUT_AT[0] + 16) / 2;
        graphics.text(font, status, middle - font.width(status) / 2, 58,
                menu.status() == MachineMenu.BURNT || menu.status() == MachineMenu.STALLED ? 0xFFA02A20 : TEXT, false);
    }

    private void slot(GuiGraphicsExtractor graphics, int[] at) {
        int sx = leftPos + at[0] - 1;
        int sy = topPos + at[1] - 1;
        graphics.fill(sx, sy, sx + 18, sy + 18, LIGHT);
        graphics.fill(sx, sy, sx + 17, sy + 17, SLOT_DARK);
        graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT);
    }
}
