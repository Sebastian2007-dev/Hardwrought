package de.ipnats.hardwrought.client.nuclear;

import de.ipnats.hardwrought.nuclear.ReactorCore;
import de.ipnats.hardwrought.nuclear.ReactorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/**
 * The reactor's control desk, written for someone who has never run one.
 *
 * <p>On the left the nine fuel channels as they stand in the core, the rods as a bar under them. On the
 * right six plain readings and whether the core may be opened. Under them one line that says what is
 * wrong or what to do next. Then the buttons: the automatic control's target power, reset and scram, and
 * the rods by hand. Every button explains itself on hover; the physics behind the readings — reactivity,
 * xenon, decay heat, pressure — shows on hovering the readings.
 */
public class ReactorScreen extends AbstractContainerScreen<ReactorMenu> {
    private static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, DARK = 0xFF555555;
    private static final int SLOT_DARK = 0xFF373737, SLOT = 0xFF8B8B8B, TEXT = 0xFF404040;
    private static final int GOOD = 0xFF2E7D32, WARN = 0xFFB26A00, BAD = 0xFF9E2A2A;
    private static final int READINGS_X = 66, READINGS_Y = 18, LINE = 9, READINGS = 6;
    private static final int RODS_Y = 76, HINT_Y = 88, AUTO_Y = 108, HAND_Y = 126;

    public ReactorScreen(ReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 240);
        inventoryLabelY = ReactorMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        button("setpoint_down", ReactorMenu.SETPOINT_DOWN, 8, AUTO_Y, 38);
        button("setpoint_up", ReactorMenu.SETPOINT_UP, 48, AUTO_Y, 38);
        button("reset", ReactorMenu.RESET, 88, AUTO_Y, 38);
        button("scram", ReactorMenu.SCRAM, 128, AUTO_Y, 40);
        button("insert_more", ReactorMenu.INSERT_MORE, 8, HAND_Y, 38);
        button("insert", ReactorMenu.INSERT, 48, HAND_Y, 38);
        button("withdraw", ReactorMenu.WITHDRAW, 88, HAND_Y, 38);
        button("withdraw_more", ReactorMenu.WITHDRAW_MORE, 128, HAND_Y, 40);
    }

    /** A button with its label and, on hover, what it does. */
    private void button(String name, int id, int x, int y, int width) {
        addRenderableWidget(Button.builder(Component.translatable("gui.hardwrought.reactor." + name), b -> press(id))
                .bounds(leftPos + x, topPos + y, width, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.hardwrought.reactor." + name + ".tip")))
                .build());
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private void slot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(leftPos + x - 1, topPos + y - 1, leftPos + x + 17, topPos + y + 17, SLOT_DARK);
        graphics.fill(leftPos + x, topPos + y, leftPos + x + 17, topPos + y + 17, LIGHT);
        graphics.fill(leftPos + x, topPos + y, leftPos + x + 16, topPos + y + 16, SLOT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, DARK);
        graphics.fill(x, y, x + imageWidth - 1, y + imageHeight - 1, LIGHT);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                slot(graphics, ReactorMenu.CHANNELS_X + column * 18, ReactorMenu.CHANNELS_Y + row * 18);
            }
        }
        // The player's inventory and hotbar.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) slot(graphics, 8 + column * 18, ReactorMenu.INVENTORY_Y + row * 18);
        }
        for (int column = 0; column < 9; column++) slot(graphics, 8 + column * 18, ReactorMenu.INVENTORY_Y + 58);
        // The core is closed while it runs: a red frame round the channels.
        if (!menu.shutDown()) {
            int x0 = x + ReactorMenu.CHANNELS_X - 2, y0 = y + ReactorMenu.CHANNELS_Y - 2;
            graphics.fill(x0, y0, x0 + 56, y0 + 1, BAD);
            graphics.fill(x0, y0 + 55, x0 + 56, y0 + 56, BAD);
            graphics.fill(x0, y0, x0 + 1, y0 + 56, BAD);
            graphics.fill(x0 + 55, y0, x0 + 56, y0 + 56, BAD);
        }
        // The rods: dark as far as they are in; an orange mark where they are going.
        int barX = x + 8, barY = y + RODS_Y, width = 52;
        graphics.fill(barX - 1, barY - 1, barX + width + 1, barY + 7, SLOT_DARK);
        graphics.fill(barX, barY, barX + width, barY + 6, 0xFFB0B0B0);
        int in = width * Math.clamp(menu.get(ReactorMenu.RODS), 0, 1000) / 1000;
        if (in > 0) graphics.fill(barX, barY, barX + in, barY + 6, 0xFF404858);
        int set = width * Math.clamp(menu.get(ReactorMenu.RODS_SET), 0, 1000) / 1000;
        graphics.fill(barX + set - 1, barY - 2, barX + set + 1, barY + 8, 0xFFE0A030);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT, false);

        double power = menu.get(ReactorMenu.POWER) / 10.0;
        int temperature = menu.get(ReactorMenu.TEMPERATURE);
        int setpoint = menu.get(ReactorMenu.SETPOINT);
        int volts = menu.get(ReactorMenu.VOLTS);
        int water = menu.get(ReactorMenu.WATER);
        boolean venting = menu.get(ReactorMenu.VENTING) != 0;
        Component target = setpoint < 0 ? Component.translatable("gui.hardwrought.reactor.manual")
                : Component.literal(fmt("%.0f %%", setpoint / 10.0));
        int line = 0;
        // Below a tenth of a percent the power is read off its log, so a startup is seen to climb.
        double precise = Math.pow(10, menu.get(ReactorMenu.LOG_POWER) / 100.0) * 100;
        String shown = precise >= 0.1 ? fmt("%.1f", power) : precise < 1e-4 ? "0" : fmt("%.4f", precise);
        reading(graphics, line++, Component.translatable("gui.hardwrought.reactor.power", shown, target),
                power > 110 ? BAD : power > 0.5 ? GOOD : TEXT);
        reading(graphics, line++, Component.translatable("gui.hardwrought.reactor.temperature", temperature),
                temperature > ReactorCore.SCRAM_TEMPERATURE ? BAD : temperature > 320 ? WARN : TEXT);
        reading(graphics, line++, Component.translatable("gui.hardwrought.reactor.rods", fmt("%.0f", menu.get(ReactorMenu.RODS) / 10.0)),
                TEXT);
        reading(graphics, line++, Component.translatable("gui.hardwrought.reactor.water", water), water < 1000 ? BAD : TEXT);
        reading(graphics, line++, Component.translatable(venting ? "gui.hardwrought.reactor.venting" : "gui.hardwrought.reactor.steam",
                menu.get(ReactorMenu.STEAM)), venting ? WARN : TEXT);
        reading(graphics, line++, Component.translatable("gui.hardwrought.reactor.current", volts), volts < 30 ? BAD : TEXT);

        boolean open = menu.shutDown();
        graphics.text(font, Component.translatable(open ? "gui.hardwrought.reactor.open" : "gui.hardwrought.reactor.closed"),
                READINGS_X, RODS_Y - 1, open ? GOOD : BAD, false);

        Hint hint = hint(power, setpoint, volts, water, venting);
        int hintY = HINT_Y;
        for (FormattedCharSequence part : font.split(hint.text(), imageWidth - 16)) {
            if (hintY > AUTO_Y - 9) break;
            graphics.text(font, part, 8, hintY, hint.colour(), false);
            hintY += 9;
        }
    }

    private record Hint(Component text, int colour) { }

    /** What is wrong, or what to do next: the first thing that stands in the way, in plain words. */
    private Hint hint(double power, int setpoint, int volts, int water, boolean venting) {
        int trip = menu.get(ReactorMenu.TRIP);
        if (trip != 0) {
            ReactorCore.Trip cause = ReactorCore.Trip.values()[Math.clamp(trip, 0, ReactorCore.Trip.values().length - 1)];
            return new Hint(Component.translatable("gui.hardwrought.reactor.hint.tripped",
                    Component.translatable("gui.hardwrought.reactor.trip." + cause.name().toLowerCase(Locale.ROOT))), BAD);
        }
        if (volts < 30) return new Hint(Component.translatable("gui.hardwrought.reactor.hint.power"), BAD);
        if (menu.get(ReactorMenu.DRIVES) < 4) {
            return new Hint(Component.translatable("gui.hardwrought.reactor.hint.drives", menu.get(ReactorMenu.DRIVES)), BAD);
        }
        if (water < 1000) return new Hint(Component.translatable("gui.hardwrought.reactor.hint.water"), BAD);
        int fuel = menu.get(ReactorMenu.FUEL);
        if (fuel < 8 && power < 1) return new Hint(Component.translatable("gui.hardwrought.reactor.hint.fuel", fuel), WARN);
        if (setpoint < 0 && power < 1) return new Hint(Component.translatable("gui.hardwrought.reactor.hint.ready"), GOOD);
        if (venting) return new Hint(Component.translatable("gui.hardwrought.reactor.hint.venting"), WARN);
        if (power < 1) {
            int period = menu.get(ReactorMenu.PERIOD);
            return period > 0
                    ? new Hint(Component.translatable("gui.hardwrought.reactor.hint.climbing", Math.round(period * Math.log(10))), GOOD)
                    : new Hint(Component.translatable("gui.hardwrought.reactor.hint.starting"), GOOD);
        }
        return new Hint(Component.translatable("gui.hardwrought.reactor.hint.running"), GOOD);
    }

    private void reading(GuiGraphicsExtractor graphics, int line, Component text, int colour) {
        String shown = font.plainSubstrByWidth(text.getString(), imageWidth - READINGS_X - 6);
        graphics.text(font, shown, READINGS_X, READINGS_Y + line * LINE, colour, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        // The physics behind the readings, for whoever wants it.
        if (isHovering(READINGS_X, READINGS_Y, imageWidth - READINGS_X - 4, READINGS * LINE, mouseX, mouseY)) {
            double reactivity = menu.get(ReactorMenu.REACTIVITY) / 1e4;
            graphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("gui.hardwrought.reactor.details"),
                    Component.translatable("gui.hardwrought.reactor.reactivity", fmt("%+.4f", reactivity),
                            fmt("%+.2f", reactivity / ReactorCore.BETA)),
                    Component.translatable("gui.hardwrought.reactor.pressure", fmt("%.1f", menu.get(ReactorMenu.PRESSURE) / 10.0)),
                    Component.translatable("gui.hardwrought.reactor.xenon", fmt("%.0f", menu.get(ReactorMenu.XENON) / 10.0)),
                    Component.translatable("gui.hardwrought.reactor.decay", fmt("%.1f", menu.get(ReactorMenu.DECAY) / 100.0)),
                    Component.translatable("gui.hardwrought.reactor.flow", fmt("%.0f", menu.get(ReactorMenu.FLOW) / 10.0)),
                    Component.translatable("gui.hardwrought.reactor.drives", menu.get(ReactorMenu.DRIVES))), mouseX, mouseY);
        }
        // The rods' bar, explained.
        if (isHovering(ReactorMenu.CHANNELS_X - 1, RODS_Y - 2, 54, 10, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.hardwrought.reactor.rods_bar.tip",
                    fmt("%.0f", menu.get(ReactorMenu.RODS) / 10.0), fmt("%.0f", menu.get(ReactorMenu.RODS_SET) / 10.0))), mouseX, mouseY);
        }
    }

    private static String fmt(String format, double value) {
        return String.format(Locale.ROOT, format, value);
    }
}
