package de.ipnats.hardwrought.client.smeltery;

import de.ipnats.hardwrought.smeltery.MoltenMetals;
import de.ipnats.hardwrought.smeltery.SmelteryControllerBlockEntity;
import de.ipnats.hardwrought.smeltery.SmelteryMenu;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
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
    private static final int TANK_X = 164, TANK_W = 108, TOP = 18, BOTTOM = 90;
    private static final int GAUGE_X = 282;
    private static final double SCALE_C = SmelteryControllerBlockEntity.COKE_BLOWN_C;
    private static final int TOOLTIP_WIDTH = 200;
    private static final int SELECTED = 0xFFFFF2A0, POURS_Y = 93;
    /** A layer is never drawn thinner than this while there is room: thin enough to show, thick enough to click. */
    private static final int LAYER_MIN = 11;

    public SmelteryScreen(SmelteryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 296, 186);
        inventoryLabelY = imageHeight - 94;
    }

    /**
     * What the tank holds, asked of the smeltery itself. The menu cannot say: it sends its numbers as
     * shorts, and a tank of more than 28 blocks holds more millibuckets than a short does.
     */
    private int capacity() {
        SmelteryControllerBlockEntity smeltery = smeltery();
        return smeltery == null ? 0 : smeltery.capacity();
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
        int capacity = capacity();
        if (smeltery != null && capacity > 0) {
            for (TankLayer layer : tankLayers(smeltery, capacity)) {
                int top = y + layer.top();
                int bottom = y + layer.bottom();
                int colour = MoltenColors.of(layer.material());
                graphics.fill(tx, top, tx + TANK_W, bottom, colour);
                if (layer.material().equals(smeltery.bottomCastable())) {
                    // What a faucet pours next wears a bright frame.
                    graphics.fill(tx, top, tx + TANK_W, top + 1, SELECTED);
                    graphics.fill(tx, bottom - 1, tx + TANK_W, bottom, SELECTED);
                    graphics.fill(tx, top, tx + 1, bottom, SELECTED);
                    graphics.fill(tx + TANK_W - 1, top, tx + TANK_W, bottom, SELECTED);
                }
                if (layer.bottom() - layer.top() >= font.lineHeight + 2) {
                    Component translated = Component.translatable("molten.hardwrought." + layer.material());
                    String name = shortened(translated.getString(), TANK_W - 6);
                    int textX = tx + (TANK_W - font.width(name)) / 2;
                    int textY = top + (bottom - top - font.lineHeight) / 2;
                    int ink = textColour(colour);
                    graphics.text(font, name, textX, textY, ink, ink == 0xFFFFFFFF);
                }
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
        // Under the tank, on the line of the inventory's own label: what a faucet would pour.
        SmelteryControllerBlockEntity smeltery = smeltery();
        String next = smeltery == null ? null : smeltery.bottomCastable();
        if (next == null) return;
        graphics.fill(TANK_X, POURS_Y, TANK_X + 7, POURS_Y + 7, SLOT_DARK);
        graphics.fill(TANK_X + 1, POURS_Y + 1, TANK_X + 6, POURS_Y + 6, MoltenColors.of(next));
        String pours = Component.translatable("gui.hardwrought.smeltery.pours_next",
                Component.translatable("molten.hardwrought." + next)).getString();
        graphics.text(font, shortened(pours, imageWidth - 8 - TANK_X - 10), TANK_X + 10, POURS_Y, TEXT, false);
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
                TankLayer layer = tankLayerAt(smeltery, mouseY - topPos);
                if (layer == null) {
                    lines.add(Component.translatable("gui.hardwrought.smeltery.capacity",
                            smeltery.fluidTotal(), capacity()));
                } else {
                    lines.add(Component.translatable("gui.hardwrought.smeltery.fluid",
                            Component.translatable("molten.hardwrought." + layer.material()), layer.amount(),
                            String.format(java.util.Locale.ROOT, "%.1f", layer.amount() / (double) MoltenMetals.INGOT)));
                    if (layer.material().equals(smeltery.bottomCastable())) {
                        lines.add(Component.translatable("gui.hardwrought.smeltery.selected")
                                .withStyle(net.minecraft.ChatFormatting.GREEN));
                    } else if (MoltenMetals.ingot(layer.material()) != null) {
                        lines.add(Component.translatable("gui.hardwrought.smeltery.select")
                                .withStyle(net.minecraft.ChatFormatting.YELLOW));
                    }
                }
            }
            tooltip(graphics, lines, mouseX, mouseY);
        }
        if (isHovering(GAUGE_X - 1, TOP - 1, 8, BOTTOM - TOP + 2, mouseX, mouseY)) {
            tooltip(graphics, List.of(
                    Component.translatable("gui.hardwrought.forge.heat", menu.temperature()),
                    Component.translatable("gui.hardwrought.forge.heading", menu.target()),
                    Component.translatable("gui.hardwrought.smeltery.heat_hint"),
                    Component.translatable("gui.hardwrought.smeltery.heat_coal"),
                    Component.translatable("gui.hardwrought.smeltery.heat_coke"),
                    Component.translatable("gui.hardwrought.smeltery.heat_lava")), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                && event.x() >= leftPos + TANK_X && event.x() < leftPos + TANK_X + TANK_W
                && event.y() >= topPos + TOP && event.y() < topPos + BOTTOM) {
            SmelteryControllerBlockEntity smeltery = smeltery();
            TankLayer layer = smeltery == null ? null : tankLayerAt(smeltery, event.y() - topPos);
            int material = layer == null ? -1 : MoltenMetals.ORDER.indexOf(layer.material());
            if (material >= 0 && MoltenMetals.ingot(layer.material()) != null
                    && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        SmelteryMenu.SELECT_FLUID_BUTTON + material);
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    /** The map's first entry is physically lowest and is what the faucet draws. */
    private List<TankLayer> tankLayers(SmelteryControllerBlockEntity smeltery, int capacity) {
        List<TankLayer> layers = new ArrayList<>();
        int count = smeltery.fluids().size();
        if (capacity <= 0 || count == 0) return layers;
        // To scale, a single bar in a big tank is a line one pixel high that nobody can click. Every
        // layer gets a height that can be read and hit; what that takes comes off the thickest ones.
        int space = BOTTOM - TOP, least = Math.max(1, Math.min(LAYER_MIN, space / count));
        int[] heights = new int[count];
        int used = 0, index = 0;
        for (int amount : smeltery.fluids().values()) {
            heights[index] = (int) Math.max(least, Math.round(amount / (double) capacity * space));
            used += heights[index++];
        }
        while (used > space) {
            int thickest = 0;
            for (int i = 1; i < count; i++) if (heights[i] > heights[thickest]) thickest = i;
            if (heights[thickest] <= 1) break;
            heights[thickest]--;
            used--;
        }
        int bottom = BOTTOM;
        index = 0;
        for (Map.Entry<String, Integer> fluid : smeltery.fluids().entrySet()) {
            int top = Math.max(TOP, bottom - heights[index++]);
            if (bottom > TOP) layers.add(new TankLayer(fluid.getKey(), fluid.getValue(), top, bottom));
            bottom = top;
        }
        return layers;
    }

    private TankLayer tankLayerAt(SmelteryControllerBlockEntity smeltery, double y) {
        for (TankLayer layer : tankLayers(smeltery, capacity())) {
            if (y >= layer.top() && y < layer.bottom()) return layer;
        }
        return null;
    }

    private String shortened(String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    private static int textColour(int background) {
        int r = background >> 16 & 0xFF, g = background >> 8 & 0xFF, b = background & 0xFF;
        return r * 299 + g * 587 + b * 114 >= 145_000 ? 0xFF202020 : 0xFFFFFFFF;
    }

    private record TankLayer(String material, int amount, int top, int bottom) { }

    /** Over every melting place with something in it: how far it has melted, or why it does not. */
    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, net.minecraft.world.inventory.Slot slot, int mouseX, int mouseY) {
        super.extractSlot(graphics, slot, mouseX, mouseY);
        int place = place(slot);
        if (place < 0 || !slot.hasItem()) return;
        int state = menu.meltState(place);
        int bx = slot.x, by = slot.y + 14;
        graphics.fill(bx + 1, by, bx + 15, by + 2, 0xFF1A1410);
        if (state == SmelteryControllerBlockEntity.TOO_COLD) {
            graphics.fill(bx + 1, by, bx + 15, by + 2, 0xFF3A5A9A);
        } else if (state == SmelteryControllerBlockEntity.TANK_FULL) {
            graphics.fill(bx + 1, by, bx + 15, by + 2, 0xFFB03030);
        } else if (state > 0) {
            graphics.fill(bx + 1, by, bx + 1 + Math.max(1, state * 14 / 1000), by + 2, 0xFFF0A030);
        }
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(net.minecraft.world.item.ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        int place = hoveredSlot == null ? -1 : place(hoveredSlot);
        if (place < 0) return lines;
        int state = menu.meltState(place);
        Component status = state == SmelteryControllerBlockEntity.TOO_COLD
                ? Component.translatable("gui.hardwrought.smeltery.too_cold").withStyle(net.minecraft.ChatFormatting.AQUA)
                : state == SmelteryControllerBlockEntity.TANK_FULL
                ? Component.translatable("gui.hardwrought.smeltery.tank_full").withStyle(net.minecraft.ChatFormatting.RED)
                : Component.translatable("gui.hardwrought.smeltery.melting", state / 10).withStyle(net.minecraft.ChatFormatting.GOLD);
        lines.add(status);
        return lines;
    }

    /** Which melting place a slot is, or -1 for the fuel and the player's own slots. */
    private int place(net.minecraft.world.inventory.Slot slot) {
        int index = menu.slots.indexOf(slot);
        return index >= 1 && index <= SmelteryControllerBlockEntity.MELT_SLOTS ? index - 1 : -1;
    }

    /** A tooltip whose long lines are broken to a readable width instead of running off the screen. */
    private void tooltip(GuiGraphicsExtractor graphics, List<Component> lines, int mouseX, int mouseY) {
        List<net.minecraft.util.FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) wrapped.addAll(font.split(line, TOOLTIP_WIDTH));
        graphics.setTooltipForNextFrame(font, wrapped, mouseX, mouseY);
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
