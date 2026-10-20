package de.ipnats.hardwrought.client.storage;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.core.networking.StoragePayloads;
import de.ipnats.hardwrought.storage.StorageDeskMenu;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A storage desk opened. Above, everything in the chests on its tubes, the most plentiful first,
 * six rows at a time under a search field; below, the player's own inventory.
 *
 * <p>A click on the list fetches: left a stack, right half of one, with shift straight into the
 * inventory. A click with something on the cursor puts it away, and shift-clicking a stack in the
 * inventory does the same. The wheel moves one item at a time, over the inventory and, with shift,
 * over the list. None of it happens here — the click is sent, and the list that comes
 * back is drawn.
 *
 * <p>With a workshop bench against the desk, a panel stands out to the right of it with the bench's
 * grid and result, and a button that clears the grid back into the store room.
 */
public class StorageDeskScreen extends AbstractContainerScreen<StorageDeskMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int TEXT = 0xFF404040;
    private static final int CELL = 18;
    private static final int COLUMNS = StorageDeskMenu.COLUMNS, ROWS = StorageDeskMenu.ROWS;
    /** The desk itself, without the bench's panel. */
    private static final int DESK_WIDTH = 176;
    /** The bench's panel: as tall as reaches below its result and the button beside it. */
    private static final int PANEL_HEIGHT = StorageDeskMenu.RESULT_Y + 28;
    private static final int CLEAR_X = StorageDeskMenu.RESULT_X + 26, CLEAR_Y = StorageDeskMenu.RESULT_Y + 2, CLEAR_SIZE = 12;

    private EditBox search;
    private String searchText = "";
    /** The list as it is drawn: filtered and sorted, rebuilt when the list or the search changes. */
    private List<StoragePayloads.Entry> shown = List.of();
    private StoragePayloads.Contents shownFrom;
    private String shownFor;
    private int scroll;
    /** Whether the screen is laid out with the bench's panel; the menu says so only once it is open. */
    private boolean laidOutWithBench;

    public StorageDeskScreen(StorageDeskMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 222);
        inventoryLabelY = StorageDeskMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        laidOutWithBench = menu.benchHere();
        super.init();
        // The desk and the bench's panel are centred together.
        if (laidOutWithBench) leftPos = (width - DESK_WIDTH - StorageDeskMenu.PANEL_WIDTH) / 2;
        search = new EditBox(font, leftPos + 82, topPos + 5, 86, 11,
                Component.translatable("gui.hardwrought.compendium.search"));
        search.setMaxLength(32);
        search.setHint(Component.translatable("gui.hardwrought.compendium.search"));
        search.setValue(searchText);
        search.setResponder(text -> {
            searchText = text;
            scroll = 0;
        });
        addRenderableWidget(search);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // The bench is told after the screen is up, and it may be broken while it is open: lay out again.
        if (menu.benchHere() != laidOutWithBench) rebuildWidgets();
    }

    // ---------------------------------------------------------------- the list

    private List<StoragePayloads.Entry> entries() {
        StoragePayloads.Contents contents = StorageClient.contents(menu.containerId);
        if (contents == shownFrom && searchText.equals(shownFor)) return shown;
        shownFrom = contents;
        shownFor = searchText;
        List<StoragePayloads.Entry> list = new ArrayList<>();
        if (contents != null) {
            String needle = searchText.trim().toLowerCase(Locale.ROOT);
            for (StoragePayloads.Entry entry : contents.entries()) {
                if (needle.isEmpty()
                        || entry.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)) {
                    list.add(entry);
                }
            }
            list.sort(Comparator.comparingInt(StoragePayloads.Entry::count).reversed()
                    .thenComparing(entry -> entry.stack().getHoverName().getString()));
        }
        shown = list;
        scroll = Math.clamp(scroll, 0, maxScroll());
        return shown;
    }

    private int maxScroll() {
        return Math.max(0, (shown.size() + COLUMNS - 1) / COLUMNS - ROWS);
    }

    /** Which cell of the list the pointer is over, counted across and down, or -1 for none. */
    private int cellAt(double mouseX, double mouseY) {
        double x = mouseX - leftPos - StorageDeskMenu.GRID_X + 1;
        double y = mouseY - topPos - StorageDeskMenu.GRID_Y + 1;
        if (x < 0 || y < 0 || x >= COLUMNS * CELL || y >= ROWS * CELL) return -1;
        return (int) (y / CELL) * COLUMNS + (int) (x / CELL);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        int x = leftPos;
        int y = topPos;
        if (laidOutWithBench) bench(graphics, mouseX, mouseY);
        graphics.fill(x, y, x + DESK_WIDTH, y + imageHeight, DARK);
        graphics.fill(x, y, x + DESK_WIDTH - 1, y + imageHeight - 1, LIGHT);
        graphics.fill(x + 1, y + 1, x + DESK_WIDTH - 1, y + imageHeight - 1, PANEL);

        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                slot(graphics, StorageDeskMenu.GRID_X + column * CELL, StorageDeskMenu.GRID_Y + row * CELL);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(graphics, 8 + column * CELL, StorageDeskMenu.INVENTORY_Y + row * CELL);
            }
        }
        for (int column = 0; column < 9; column++) slot(graphics, 8 + column * CELL, StorageDeskMenu.INVENTORY_Y + 58);

        List<StoragePayloads.Entry> list = entries();
        int hovered = cellAt(mouseX, mouseY);
        for (int cell = 0; cell < COLUMNS * ROWS; cell++) {
            int index = cell + scroll * COLUMNS;
            int cellX = x + StorageDeskMenu.GRID_X + cell % COLUMNS * CELL;
            int cellY = y + StorageDeskMenu.GRID_Y + cell / COLUMNS * CELL;
            if (index < list.size()) {
                StoragePayloads.Entry entry = list.get(index);
                graphics.item(entry.stack(), cellX, cellY);
                graphics.itemDecorations(font, entry.stack(), cellX, cellY,
                        entry.count() == 1 ? null : compact(entry.count()));
            }
            if (cell == hovered) {
                graphics.fill(cellX, cellY, cellX + 16, cellY + 16, 0x80FFFFFF);
                if (index < list.size() && menu.getCarried().isEmpty()) {
                    graphics.setTooltipForNextFrame(font, list.get(index).stack(), mouseX, mouseY);
                }
            }
        }

        // How far down the list the six rows stand, where there is more of it than they show.
        int rows = maxScroll();
        if (rows > 0) {
            int track = ROWS * CELL - 2;
            int bar = Math.max(8, track * ROWS / (ROWS + rows));
            int top = y + StorageDeskMenu.GRID_Y + (track - bar) * scroll / rows;
            graphics.fill(x + DESK_WIDTH - 5, y + StorageDeskMenu.GRID_Y, x + DESK_WIDTH - 3,
                    y + StorageDeskMenu.GRID_Y + track, SLOT);
            graphics.fill(x + DESK_WIDTH - 5, top, x + DESK_WIDTH - 3, top + bar, DARK);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        StoragePayloads.Contents contents = StorageClient.contents(menu.containerId);
        // An empty list wants a reason: no current and no chest are neither of them empty chests.
        if (contents != null && (!contents.powered() || contents.stores() == 0)) {
            Component none = Component.translatable(contents.powered()
                    ? "gui.hardwrought.storage.no_stores" : "gui.hardwrought.storage.no_power");
            graphics.text(font, none, (DESK_WIDTH - font.width(none)) / 2,
                    StorageDeskMenu.GRID_Y + ROWS * CELL / 2 - 4, 0xFFFFFFFF, true);
        } else if (contents != null) {
            Component stores = Component.translatable("gui.hardwrought.storage.stores", contents.stores());
            graphics.text(font, stores, DESK_WIDTH - 8 - font.width(stores), inventoryLabelY, TEXT, false);
        }
        if (laidOutWithBench) {
            Component bench = Component.translatable("block.hardwrought.workshop_bench");
            int room = StorageDeskMenu.PANEL_WIDTH - 10;
            graphics.text(font, font.width(bench) > room ? Component.literal(font.plainSubstrByWidth(bench.getString(), room)) : bench,
                    StorageDeskMenu.PANEL_X + 4, 8, TEXT, false);
        }
    }

    /**
     * The bench's panel, set against the right of the desk: its grid, an arrow down to the result, and
     * the button that clears the grid into the store room.
     */
    private void bench(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int px = leftPos + StorageDeskMenu.PANEL_X - 4, py = topPos + 4;
        int pw = StorageDeskMenu.PANEL_WIDTH + 4, ph = PANEL_HEIGHT - 4;
        graphics.fill(px, py, px + pw, py + ph, DARK);
        graphics.fill(px, py, px + pw - 1, py + ph - 1, LIGHT);
        graphics.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, PANEL);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                slot(graphics, StorageDeskMenu.CRAFT_X + column * CELL, StorageDeskMenu.CRAFT_Y + row * CELL);
            }
        }
        // The result in a larger frame, as on a crafting table, and an arrow down to it.
        int rx = leftPos + StorageDeskMenu.RESULT_X, ry = topPos + StorageDeskMenu.RESULT_Y;
        graphics.fill(rx - 5, ry - 5, rx + 21, ry + 21, LIGHT);
        graphics.fill(rx - 5, ry - 5, rx + 20, ry + 20, SLOT_DARK);
        graphics.fill(rx - 4, ry - 4, rx + 20, ry + 20, SLOT);
        int ax = rx + 8, ay = topPos + StorageDeskMenu.CRAFT_Y + 3 * CELL + 2;
        graphics.fill(ax - 1, ay, ax + 1, ay + 8, DARK);
        for (int i = 0; i < 4; i++) graphics.fill(ax - 4 + i, ay + 8 + i, ax + 4 - i, ay + 9 + i, DARK);
        int cx = leftPos + CLEAR_X, cy = topPos + CLEAR_Y;
        boolean hovered = mouseX >= cx && mouseX < cx + CLEAR_SIZE && mouseY >= cy && mouseY < cy + CLEAR_SIZE;
        graphics.fill(cx, cy, cx + CLEAR_SIZE, cy + CLEAR_SIZE, DARK);
        graphics.fill(cx, cy, cx + CLEAR_SIZE - 1, cy + CLEAR_SIZE - 1, LIGHT);
        graphics.fill(cx + 1, cy + 1, cx + CLEAR_SIZE - 1, cy + CLEAR_SIZE - 1, hovered ? 0xFFA8B8D8 : SLOT);
        graphics.centeredText(font, Component.literal("↑"), cx + CLEAR_SIZE / 2, cy + 2, 0xFFFFFFFF);
        if (hovered) {
            graphics.setTooltipForNextFrame(font, Component.translatable("gui.hardwrought.storage.clear_grid"), mouseX, mouseY);
        }
    }

    /** The bench's panel is part of the screen: a click on it is not a click beside the desk, which would throw what is held. */
    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        if (laidOutWithBench && mx >= xo + StorageDeskMenu.PANEL_X - 4 && mx < xo + StorageDeskMenu.PANEL_X + StorageDeskMenu.PANEL_WIDTH
                && my >= yo && my < yo + PANEL_HEIGHT) {
            return false;
        }
        return super.hasClickedOutside(mx, my, xo, yo);
    }

    private void slot(GuiGraphicsExtractor graphics, int atX, int atY) {
        int sx = leftPos + atX - 1;
        int sy = topPos + atY - 1;
        graphics.fill(sx, sy, sx + 18, sy + 18, LIGHT);
        graphics.fill(sx, sy, sx + 17, sy + 17, SLOT_DARK);
        graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT);
    }

    /** A number that still fits in the corner of a slot once a room is full of one thing. */
    private static String compact(int count) {
        if (count < 1000) return Integer.toString(count);
        if (count < 10000) return String.format(Locale.ROOT, "%.1fk", count / 1000.0);
        return count < 1_000_000 ? count / 1000 + "k" : String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (laidOutWithBench && event.button() == InputConstants.MOUSE_BUTTON_LEFT
                && event.x() >= leftPos + CLEAR_X && event.x() < leftPos + CLEAR_X + CLEAR_SIZE
                && event.y() >= topPos + CLEAR_Y && event.y() < topPos + CLEAR_Y + CLEAR_SIZE) {
            send(ItemStack.EMPTY, StoragePayloads.CLEAR_GRID);
            return true;
        }
        int cell = cellAt(event.x(), event.y());
        // Named, never numbered: the buttons are not counted from nought here, and a left click
        // taken for a right one fetches half a stack and sets one item down.
        boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (cell < 0 || !left && !right) return super.mouseClicked(event, doubled);
        // Through the screen, not the box alone: the screen would still count the box as focused, and a
        // later click on it would not focus it again.
        setFocused(null);
        List<StoragePayloads.Entry> list = entries();
        int index = cell + scroll * COLUMNS;
        ItemStack clicked = index < list.size() ? list.get(index).stack() : ItemStack.EMPTY;
        int action = right ? StoragePayloads.HALF
                : event.hasShiftDown() ? StoragePayloads.QUICK : StoragePayloads.TAKE;
        send(clicked, action);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        // One notch moves one item, as it does in every other container: down sends one away from
        // where the pointer is, up pulls one more of its kind in. Over the list that takes shift,
        // because without it the wheel turns the list.
        int cell = cellAt(mouseX, mouseY);
        List<StoragePayloads.Entry> list = entries();
        if (cell >= 0 && minecraft.hasShiftDown()) {
            int index = cell + scroll * COLUMNS;
            if (index < list.size()) {
                send(list.get(index).stack(), scrollY < 0 ? StoragePayloads.FETCH_ONE : StoragePayloads.PUT_ONE);
            }
            return true;
        }
        if (cell < 0 && hoveredSlot != null && hoveredSlot.hasItem()) {
            send(hoveredSlot.getItem().copyWithCount(1),
                    scrollY < 0 ? StoragePayloads.PUT_ONE : StoragePayloads.FETCH_ONE);
            return true;
        }
        scroll = Math.clamp(scroll + (scrollY > 0 ? -1 : 1), 0, maxScroll());
        return true;
    }

    private void send(ItemStack clicked, int action) {
        if (ClientPlayNetworking.canSend(StoragePayloads.Click.TYPE)) {
            ClientPlayNetworking.send(new StoragePayloads.Click(menu.containerId, clicked, action));
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // While the search field has the keys, the inventory key is a letter and not a way out.
        if (search.isFocused() && event.key() != InputConstants.KEY_ESCAPE) {
            search.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }
}
