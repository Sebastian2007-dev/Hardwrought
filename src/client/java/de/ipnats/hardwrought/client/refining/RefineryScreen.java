package de.ipnats.hardwrought.client.refining;

import de.ipnats.hardwrought.refining.FlareStackBlock;
import de.ipnats.hardwrought.refining.RefineryBaseBlock;
import de.ipnats.hardwrought.refining.RefineryMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/**
 * The window of a refinery block or a gas tank: its state in a few lines at the top, how far the batch
 * is, and below a gauge for each of its tanks, filled in the colour of what it holds. Hovering a gauge
 * names what is in it and how much.
 */
public class RefineryScreen extends AbstractContainerScreen<RefineryMenu> {
    private static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, DARK = 0xFF555555;
    private static final int SLOT_DARK = 0xFF373737, TEXT = 0xFF404040, EMPTY = 0xFF8B8B8B, BAR = 0xFF2A2320;
    private static final int GOOD = 0xFF2E7D32, BAD = 0xFF9E2A2A;
    private static final int PROGRESS_Y = 62, GAUGE_TOP = 72, GAUGE_BOTTOM = 122, GAUGE_W = 24, GAUGE_STEP = 40;

    public RefineryScreen(RefineryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 146);
    }

    /** Whether the block works in batches, and so has a bar for how far the batch is. */
    private boolean hasProgress() {
        int kind = menu.kind();
        return kind == RefineryMenu.KIND_BASE || kind == RefineryMenu.KIND_STAGE || kind == RefineryMenu.KIND_PROCESSOR;
    }

    private int gaugeX(int gauge) {
        int count = Math.max(1, menu.gauges());
        int width = count * GAUGE_STEP - (GAUGE_STEP - GAUGE_W);
        return (imageWidth - width) / 2 + gauge * GAUGE_STEP;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, DARK);
        graphics.fill(x, y, x + imageWidth - 1, y + imageHeight - 1, LIGHT);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL);

        if (hasProgress()) {
            graphics.fill(x + 7, y + PROGRESS_Y - 1, x + imageWidth - 7, y + PROGRESS_Y + 4, SLOT_DARK);
            graphics.fill(x + 8, y + PROGRESS_Y, x + imageWidth - 8, y + PROGRESS_Y + 3, BAR);
            int filled = (imageWidth - 16) * Math.clamp(menu.progress(), 0, 1000) / 1000;
            if (menu.running() && filled > 0) graphics.fill(x + 8, y + PROGRESS_Y, x + 8 + filled, y + PROGRESS_Y + 3, 0xFFE0A030);
        }

        for (int gauge = 0; gauge < menu.gauges(); gauge++) {
            int gx = x + gaugeX(gauge);
            graphics.fill(gx - 1, y + GAUGE_TOP - 1, gx + GAUGE_W + 1, y + GAUGE_BOTTOM + 1, SLOT_DARK);
            graphics.fill(gx, y + GAUGE_TOP, gx + GAUGE_W, y + GAUGE_BOTTOM, EMPTY);
            int capacity = menu.capacity(gauge);
            if (capacity <= 0) continue;
            int height = (int) Math.round((GAUGE_BOTTOM - GAUGE_TOP) * Math.min(1.0, menu.amount(gauge) / (double) capacity));
            if (height > 0) {
                int colour = RefineryMenu.colour(menu.substance(gauge));
                graphics.fill(gx, y + GAUGE_BOTTOM - height, gx + GAUGE_W, y + GAUGE_BOTTOM, colour);
                // A lighter rim on the surface, so a dark liquid still shows where it stands.
                graphics.fill(gx, y + GAUGE_BOTTOM - height, gx + GAUGE_W, y + GAUGE_BOTTOM - height + 1, 0x60FFFFFF);
            }
            // Marks every quarter of the way up.
            for (int quarter = 1; quarter < 4; quarter++) {
                int my = y + GAUGE_BOTTOM - (GAUGE_BOTTOM - GAUGE_TOP) * quarter / 4;
                graphics.fill(gx, my, gx + 4, my + 1, 0x80FFFFFF);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, TEXT, false);
        int lineY = 18;
        if (menu.kind() == RefineryMenu.KIND_FLARE) {
            boolean lit = menu.heated();
            graphics.text(font, Component.translatable(lit ? "gui.hardwrought.flare.lit" : "gui.hardwrought.flare.out"),
                    8, lineY, lit ? GOOD : BAD, false);
            lineY += 10;
        } else if (menu.kind() == RefineryMenu.KIND_DIESEL || menu.kind() == RefineryMenu.KIND_ENGINE
                || menu.kind() == RefineryMenu.KIND_BURNER) {
            boolean on = menu.running();
            String key = menu.kind() == RefineryMenu.KIND_BURNER
                    ? (on ? "gui.hardwrought.power.burning" : "gui.hardwrought.power.burner_out")
                    : (on ? "gui.hardwrought.power.running" : "gui.hardwrought.power.stopped");
            graphics.text(font, Component.translatable(key), 8, lineY, on ? GOOD : BAD, false);
            lineY += 10;
        } else if (menu.kind() != RefineryMenu.KIND_GAS_TANK) {
            boolean heated = menu.heated();
            MutableComponent state = Component.translatable(heated ? "gui.hardwrought.still.heated" : "gui.hardwrought.still.cold")
                    .append(" | ").append(Component.translatable(menu.running()
                            ? "gui.hardwrought.refinery.running" : "gui.hardwrought.refinery.idle"));
            graphics.text(font, state, 8, lineY, heated && menu.running() ? GOOD : heated ? TEXT : BAD, false);
            lineY += 10;
        }
        gaugeLabels(graphics);
        for (Component line : lines()) {
            for (FormattedCharSequence part : font.split(line, imageWidth - 16)) {
                if (lineY > PROGRESS_Y - 10) break;
                graphics.text(font, part, 8, lineY, TEXT, false);
                lineY += 9;
            }
        }
    }

    /** Under each gauge, what it holds and how much, cut short to the gauge's width. */
    private void gaugeLabels(GuiGraphicsExtractor graphics) {
        for (int gauge = 0; gauge < menu.gauges(); gauge++) {
            int centre = gaugeX(gauge) + GAUGE_W / 2;
            int substance = menu.substance(gauge);
            Component name = substance == RefineryMenu.NOTHING
                    ? Component.translatable("gui.hardwrought.refinery.nothing") : RefineryMenu.name(substance);
            // The gas tank's gauge and a burning stack's second, its gas line intake, count units of gas.
            boolean gas = RefineryMenu.isGas(substance) || menu.kind() == RefineryMenu.KIND_GAS_TANK
                    || menu.kind() == RefineryMenu.KIND_BURNER || (menu.kind() == RefineryMenu.KIND_FLARE && gauge == 1);
            String unit = gas ? "" : " mB";
            for (int row = 0; row < 2; row++) {
                String text = row == 0 ? name.getString() : menu.amount(gauge) + unit;
                // A long amount drops its unit before it is cut: 5000 reads better than "5000 m".
                if (row == 1 && font.width(text) > GAUGE_STEP - 2) text = String.valueOf(menu.amount(gauge));
                text = font.plainSubstrByWidth(text, GAUGE_STEP - 2);
                graphics.text(font, text, centre - font.width(text) / 2, GAUGE_BOTTOM + 3 + row * 9, TEXT, false);
            }
        }
    }

    /** What is particular to this block, under its state. */
    private List<Component> lines() {
        int info = menu.info();
        return switch (menu.kind()) {
            case RefineryMenu.KIND_BASE -> List.of(
                    Component.translatable("gui.hardwrought.refinery.trays", info, RefineryBaseBlock.MOST_STAGES),
                    flared(info));
            case RefineryMenu.KIND_STAGE -> info == 0
                    ? List.of(Component.translatable("message.hardwrought.refinery.no_base"))
                    : List.of(Component.translatable("gui.hardwrought.refinery.tray", info, RefineryBaseBlock.MOST_STAGES,
                    Component.translatable(RefineryBaseBlock.CUTS.get(info - 1).name())));
            case RefineryMenu.KIND_GAS_TANK -> List.of(Component.translatable("gui.hardwrought.refinery.pressure",
                    String.format(Locale.ROOT, "%.1f", info / 10.0)));
            case RefineryMenu.KIND_FLARE -> List.of(
                    menu.gauges() == 0 ? Component.translatable("gui.hardwrought.flare.no_tank")
                            : menu.running() ? Component.translatable("gui.hardwrought.flare.burning", info,
                                    RefineryMenu.isGas(menu.substance(0)) ? Component.translatable("gui.hardwrought.flare.units")
                                            : Component.literal("mB"), FlareStackBlock.CO2_PER_SECOND * 60)
                            : Component.translatable("gui.hardwrought.flare.how"),
                    // The progress field carries the igniter's voltage.
                    menu.progress() >= de.ipnats.hardwrought.electricity.MachineBlockEntity.STALL_VOLTS
                            ? Component.translatable("gui.hardwrought.flare.igniter", menu.progress())
                            : Component.translatable("gui.hardwrought.flare.no_igniter"));
            case RefineryMenu.KIND_DIESEL -> List.of(menu.running()
                    ? Component.translatable("gui.hardwrought.power.watts", info,
                            String.format(Locale.ROOT, "%.0f", de.ipnats.hardwrought.power.DieselGeneratorBlock.Entity.VOLTS))
                    : Component.translatable("gui.hardwrought.power.diesel_how"));
            case RefineryMenu.KIND_ENGINE -> List.of(
                    info < 0 ? Component.translatable("gui.hardwrought.power.no_plug")
                            : Component.translatable("gui.hardwrought.power.plug", info),
                    menu.running() ? Component.translatable("gui.hardwrought.power.rpm",
                            (int) de.ipnats.hardwrought.power.GasolineEngineBlock.Entity.RPM,
                            (int) de.ipnats.hardwrought.power.GasolineEngineBlock.Entity.CAPACITY)
                            : Component.translatable("gui.hardwrought.power.engine_how"));
            case RefineryMenu.KIND_BURNER -> List.of(Component.translatable("gui.hardwrought.power.burner_how"));
            default -> List.of();
        };
    }

    /** The cuts a column of so many trays has no tray for, and so burns off at its top. */
    private static Component flared(int trays) {
        if (trays >= RefineryBaseBlock.MOST_STAGES) return Component.translatable("gui.hardwrought.refinery.flared_none");
        MutableComponent cuts = Component.empty();
        for (int cut = trays; cut < RefineryBaseBlock.MOST_STAGES; cut++) {
            if (cut > trays) cuts.append(", ");
            cuts.append(Component.translatable(RefineryBaseBlock.CUTS.get(cut).name()));
        }
        return Component.translatable("gui.hardwrought.refinery.flared", cuts);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        for (int gauge = 0; gauge < menu.gauges(); gauge++) {
            if (!isHovering(gaugeX(gauge) - 1, GAUGE_TOP - 1, GAUGE_W + 2, GAUGE_BOTTOM - GAUGE_TOP + 2, mouseX, mouseY)) continue;
            int substance = menu.substance(gauge);
            String key = RefineryMenu.isGas(substance) || menu.kind() == RefineryMenu.KIND_GAS_TANK
                    || menu.kind() == RefineryMenu.KIND_BURNER
                    ? "gui.hardwrought.refinery.gauge_gas" : "gui.hardwrought.refinery.gauge_liquid";
            Component name = substance == RefineryMenu.NOTHING
                    ? Component.translatable("gui.hardwrought.refinery.nothing") : RefineryMenu.name(substance);
            graphics.setComponentTooltipForNextFrame(font,
                    List.of(Component.translatable(key, name, menu.amount(gauge), menu.capacity(gauge))), mouseX, mouseY);
        }
    }
}
