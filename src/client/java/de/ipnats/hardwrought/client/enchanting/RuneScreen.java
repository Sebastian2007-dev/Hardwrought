package de.ipnats.hardwrought.client.enchanting;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.client.smithing.ItemPixels;
import de.ipnats.hardwrought.core.networking.RunePayloads;
import de.ipnats.hardwrought.enchanting.RuneEnchanting;
import de.ipnats.hardwrought.enchanting.RuneWork;
import de.ipnats.hardwrought.smithing.Mask;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A piece on the enchanting table. On the left the piece, large; on the right the runes that lie
 * ready. A rune is picked up from the right, turned (right click, the wheel, or R) and laid on the
 * piece; laid, it shows the level it will give there. A laid rune is picked up again with a click, or
 * wiped off with a right click. "Work in" writes them all for good.
 */
public class RuneScreen extends Screen {

    private static final int PANEL = 0xFFC6C6C6, PANEL_LIGHT = 0xFFFFFFFF, PANEL_MID = 0xFF8B8B8B, PANEL_DARK = 0xFF373737;
    private static final int TEXT = 0xFF404040, FADED = 0xFF606060, WARN = 0xFFA03020;
    private static final int CLOTH = 0xFF2B2338, CLOTH_GRID = 0xFF342B44;
    private static final int SURFACE_X = 10, SURFACE_Y = 22, LIST_Y = SURFACE_Y, LIST_WIDTH = 150, ROW = 24;
    /** Pixels to a square of the surface: four where the window has the room, three where it has not. */
    private int square = 4;
    private int surface, panelWidth, panelHeight, listX, rows;
    /** Lapis blue for a rune that is written, a paler one where it hangs over the edge, red where it may not lie. */
    private static final int OVER_EDGE = 0x70A0A8C8, FORBIDDEN = 0xD0D84030;
    /** Each ink as it lies written, and brighter under the pointer: lapis blue, mithril sea-green, adamant crimson. */
    private static final int[] INKS = {0xFF3A62D8, 0xFF2FB8A4, 0xFFB02A4E}, INKS_HOVER = {0xFF6F92FF, 0xFF73E6D1, 0xFFE0607F};
    /** The row of inks above the runes. */
    private static final int INK_BAR = 16;
    /** The inks as they look on a piece that has carried them a while. */
    private static final int[] OLD_INKS = {0xFF2A4390, 0xFF237A70, 0xFF7A2038};

    /** A rune as this screen knows it. */
    private record Rune(Identifier id, Holder<Enchantment> enchantment, int tier, RuneWork.Shape shape) { }

    private final RunePayloads.Open sitting;
    private final Map<Identifier, Rune> runes = new LinkedHashMap<>();
    private final List<RuneWork.Placement> laid = new ArrayList<>();
    /** Runes the piece carries that are not among those lying ready, looked up as they are needed. */
    private final Map<Identifier, Rune> others = new java.util.HashMap<>();
    /** Which of the runes the piece carries, by their place in the sitting's list, are to be wiped off. */
    private final java.util.Set<Integer> wiped = new java.util.TreeSet<>();
    private int left, top;
    private Identifier held;
    /** The ink the next rune is written in. */
    private RuneWork.Ink ink = RuneWork.Ink.LAPIS;
    private int turns;
    private int scroll;
    private Button workIn;
    private Component notice;
    private long noticeUntil;
    private int[] pixels;
    private Mask piece = Mask.empty();

    public RuneScreen(RunePayloads.Open sitting) {
        super(Component.translatable("gui.hardwrought.runes"));
        this.sitting = sitting;
    }

    @Override
    protected void init() {
        square = height >= 262 && width >= 390 ? 4 : 3;
        surface = RuneWork.GRID * square;
        panelWidth = SURFACE_X + surface + 10 + LIST_WIDTH + 12;
        panelHeight = SURFACE_Y + surface + 36;
        listX = SURFACE_X + surface + 10;
        rows = (surface - INK_BAR) / ROW;
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        runes.clear();
        if (minecraft != null && minecraft.level != null) {
            var registry = minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            for (Identifier id : sitting.runes()) {
                registry.get(ResourceKey.create(Registries.ENCHANTMENT, id)).ifPresent(rune -> {
                    int tier = RuneWork.tier(rune);
                    runes.put(id, new Rune(id, rune, tier, RuneWork.shape(id, tier, sitting.coarser())));
                });
            }
        }
        pixels = ItemPixels.of(piece());
        piece = ItemPixels.mask(pixels);
        workIn = addRenderableWidget(Button.builder(Component.translatable("gui.hardwrought.runes.work_in"), button -> workIn())
                .bounds(left + panelWidth - 10 - 90, top + panelHeight - 28, 90, 20).build());
    }

    private ItemStack piece() {
        return minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
    }

    @Override
    public void tick() {
        ItemStack piece = piece();
        // The piece was put away: the sitting is over.
        if (piece.isEmpty()) onClose();
        // Taking the one adamant rune off again can leave more lying than the piece now takes.
        if (workIn != null) {
            workIn.active = (!laid.isEmpty() || !wiped.isEmpty()) && affordable() && onPiece() <= capacity(laid);
        }
    }

    // ---------------------------------------------------------------- what is laid, and what it comes to

    /** A rune by its name: one of those lying ready, or one the piece carries from an earlier sitting. */
    private Rune rune(Identifier id) {
        Rune ready = runes.get(id);
        if (ready != null || minecraft == null || minecraft.level == null) return ready;
        return others.computeIfAbsent(id, name -> minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .get(ResourceKey.create(Registries.ENCHANTMENT, name)).map(found -> {
                    int tier = RuneWork.tier(found);
                    return new Rune(name, found, tier, RuneWork.shape(name, tier, sitting.coarser()));
                }).orElse(null));
    }

    private List<int[]> squares(RuneWork.Placement placement) {
        Rune rune = rune(placement.rune());
        return rune == null ? List.of() : RuneWork.squares(rune.shape(), placement);
    }

    private int level(RuneWork.Placement placement) {
        List<int[]> squares = squares(placement);
        return RuneWork.level(RuneWork.written(piece, squares), squares.size(),
                RuneWork.highest(rune(placement.rune()).enchantment().value().getMaxLevel(), placement.ink()), sitting.part());
    }

    /** The runes the piece carries that stay on it. */
    private List<RuneWork.Placement> kept() {
        List<RuneWork.Placement> kept = new ArrayList<>();
        for (int i = 0; i < sitting.carried().size(); i++) if (!wiped.contains(i)) kept.add(sitting.carried().get(i));
        return kept;
    }

    /** How many runes and other enchantments the piece will carry: what stays, and what is laid now. */
    private int onPiece() {
        return kept().size() + sitting.loose() + laid.size();
    }

    /** How many the piece takes with these laid: the finest ink on it counts, old or new. */
    private int capacity(List<RuneWork.Placement> laidNow) {
        List<RuneWork.Placement> all = kept();
        all.addAll(laidNow);
        return RuneWork.capacity(sitting.capacity(), all);
    }

    /** Whether the piece already has this enchantment and keeps it: as a rune that stays, or without one. */
    private boolean has(Rune rune) {
        if (piece().getEnchantments().getLevel(rune.enchantment()) <= 0) return false;
        for (int index : wiped) if (sitting.carried().get(index).rune().equals(rune.id())) return false;
        return true;
    }

    /** Why this rune may not be laid here, as a translation key's last part; null where it may. */
    private String objection(RuneWork.Placement placement) {
        List<RuneWork.Placement> with = new ArrayList<>(laid);
        with.add(placement);
        if (onPiece() + 1 > capacity(with)) return "too_many";
        Rune rune = rune(placement.rune());
        if (has(rune)) return "clash";
        List<int[]> squares = squares(placement);
        for (RuneWork.Placement old : kept()) if (RuneWork.overlap(squares, squares(old))) return "overlapping";
        for (RuneWork.Placement other : laid) {
            if (other.rune().equals(rune.id())) return "clash";
            if (RuneWork.overlap(squares, squares(other))) return "overlapping";
        }
        return level(placement) < 1 ? "too_little" : null;
    }

    private int powderNeeded(RuneWork.Ink of) {
        int powder = 0;
        for (RuneWork.Placement placement : laid) {
            if (placement.ink() == of) powder += RuneWork.powder(RuneWork.written(piece, squares(placement)));
        }
        return powder;
    }

    private int experienceNeeded() {
        int experience = 0;
        for (RuneWork.Placement placement : laid) experience += RuneWork.experienceFor(runes.get(placement.rune()).tier());
        return experience;
    }

    private int powderHeld(RuneWork.Ink of) {
        if (minecraft == null || minecraft.player == null) return 0;
        var inventory = minecraft.player.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(RuneEnchanting.powder(of))) count += inventory.getItem(slot).getCount();
        }
        return count;
    }

    private boolean affordable() {
        if (minecraft == null || minecraft.player == null) return false;
        if (minecraft.player.isCreative()) return true;
        for (RuneWork.Ink of : RuneWork.Ink.values()) if (powderHeld(of) < powderNeeded(of)) return false;
        return minecraft.player.experienceLevel >= experienceNeeded();
    }

    private void workIn() {
        if (laid.isEmpty() && wiped.isEmpty() || !affordable()) return;
        ClientPlayNetworking.send(new RunePayloads.Write(sitting.table(), piece.toArray(), laid, new ArrayList<>(wiped)));
        onClose();
    }

    private void say(String key) {
        notice = Component.translatable("message.hardwrought.runes.refused." + key);
        noticeUntil = System.currentTimeMillis() + 2500;
    }

    // ---------------------------------------------------------------- the hand

    private boolean overSurface(double x, double y) {
        return x >= left + SURFACE_X && x < left + SURFACE_X + surface && y >= top + SURFACE_Y && y < top + SURFACE_Y + surface;
    }

    private boolean overList(double x, double y) {
        return x >= left + listX && x < left + listX + LIST_WIDTH && y >= top + LIST_Y + INK_BAR
                && y < top + LIST_Y + INK_BAR + rows * ROW;
    }

    /** The ink under the pointer in the row of inks, or null. */
    private RuneWork.Ink inkAt(double x, double y) {
        if (y < top + LIST_Y || y >= top + LIST_Y + INK_BAR - 2 || x < left + listX || x >= left + listX + LIST_WIDTH) return null;
        return RuneWork.Ink.values()[Math.min(2, (int) ((x - left - listX) * 3 / LIST_WIDTH))];
    }

    /** Where the held rune would lie with the pointer here: its middle under the pointer. */
    private RuneWork.Placement hovering(double x, double y) {
        if (held == null || !overSurface(x, y)) return null;
        int size = runes.get(held).shape().size();
        int sx = (int) Math.floor((x - left - SURFACE_X) / square) - size / 2;
        int sy = (int) Math.floor((y - top - SURFACE_Y) / square) - size / 2;
        return new RuneWork.Placement(held, sx, sy, turns, ink);
    }

    /** The laid rune under the pointer, or null. */
    private RuneWork.Placement laidAt(double x, double y) {
        if (!overSurface(x, y)) return null;
        int sx = (int) Math.floor((x - left - SURFACE_X) / square), sy = (int) Math.floor((y - top - SURFACE_Y) / square);
        for (RuneWork.Placement placement : laid) {
            for (int[] cell : squares(placement)) if (cell[0] == sx && cell[1] == sy) return placement;
        }
        return null;
    }

    /** Which of the runes the piece carries lies under the pointer, wiped or not; -1 for none. */
    private int carriedAt(double x, double y) {
        if (!overSurface(x, y)) return -1;
        int sx = (int) Math.floor((x - left - SURFACE_X) / square), sy = (int) Math.floor((y - top - SURFACE_Y) / square);
        for (int i = 0; i < sitting.carried().size(); i++) {
            for (int[] cell : squares(sitting.carried().get(i))) if (cell[0] == sx && cell[1] == sy) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) return true;
        double x = event.x(), y = event.y();
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT && !right) return false;
        if (inkAt(x, y) != null) {
            ink = inkAt(x, y);
            return true;
        }
        if (overList(x, y)) {
            int row = scroll + (int) ((y - top - LIST_Y - INK_BAR) / ROW);
            List<Rune> all = new ArrayList<>(runes.values());
            if (row >= 0 && row < all.size()) {
                held = right || all.get(row).id().equals(held) ? null : all.get(row).id();
                turns = 0;
            }
            return true;
        }
        if (!overSurface(x, y)) {
            if (right) held = null;
            return false;
        }
        if (held != null) {
            if (right) {
                turns = (turns + 1) & 3;
                return true;
            }
            RuneWork.Placement placement = hovering(x, y);
            String objection = objection(placement);
            if (objection != null) {
                say(objection);
                return true;
            }
            laid.add(placement);
            held = null;
            return true;
        }
        RuneWork.Placement under = laidAt(x, y);
        if (under != null) {
            laid.remove(under);
            // A left click takes it back into the hand, turned as it lay; a right click wipes it off.
            if (!right) {
                held = under.rune();
                turns = under.turns();
                ink = under.ink();
            }
            return true;
        }
        // A rune from an earlier sitting stays where it is. A right click wipes it off, to make room
        // or to write it again in a finer ink; another right click leaves it after all.
        int old = carriedAt(x, y);
        if (old >= 0) {
            if (!right) say("old");
            else if (!wiped.remove(old)) wiped.add(old);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (overList(x, y)) {
            scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, runes.size() - rows));
            return true;
        }
        if (held != null) {
            turns = (turns + (scrollY < 0 ? 1 : 3)) & 3;
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_R && held != null) {
            turns = (turns + 1) & 3;
            return true;
        }
        return super.keyPressed(event);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL_DARK);
        graphics.fill(left + 1, top + 1, left + panelWidth - 1, top + panelHeight - 1, PANEL_LIGHT);
        graphics.fill(left + 3, top + 3, left + panelWidth - 1, top + panelHeight - 1, PANEL_MID);
        graphics.fill(left + 3, top + 3, left + panelWidth - 3, top + panelHeight - 3, PANEL);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(font, title, left + 10, top + 8, TEXT, false);
        Component where = Component.translatable("gui.hardwrought.runes.table", sitting.shelves(), sitting.level());
        if (sitting.locked() > 0) {
            where = where.copy().append(Component.translatable("gui.hardwrought.runes.locked", sitting.locked()));
        }
        graphics.text(font, where, left + panelWidth - 10 - font.width(where), top + 8, FADED, false);

        drawSurface(graphics, mouseX, mouseY);
        drawList(graphics, mouseX, mouseY);

        // What it all comes to, under the piece.
        boolean free = minecraft != null && minecraft.player != null && minecraft.player.isCreative();
        int experience = experienceNeeded();
        int levels = minecraft == null || minecraft.player == null ? 0 : minecraft.player.experienceLevel;
        int y = top + SURFACE_Y + surface + 6;
        graphics.text(font, Component.translatable("gui.hardwrought.runes.count", onPiece(), capacity(laid)),
                left + SURFACE_X, y, TEXT, false);
        // Every ink that is being written with, and what there is of it; lapis where nothing is laid yet.
        int x = left + SURFACE_X;
        for (RuneWork.Ink of : RuneWork.Ink.values()) {
            int need = powderNeeded(of);
            if (need == 0 && !(of == RuneWork.Ink.LAPIS && laid.isEmpty())) continue;
            Component cost = Component.translatable("gui.hardwrought.runes.powder",
                    Component.translatable("gui.hardwrought.runes.ink." + of.id()), need, powderHeld(of));
            graphics.text(font, cost, x, y + 11, free || powderHeld(of) >= need ? TEXT : WARN, false);
            x += font.width(cost) + 8;
        }
        graphics.text(font, Component.translatable("gui.hardwrought.runes.experience", experience, levels),
                x, y + 11, free || levels >= experience ? TEXT : WARN, false);
        Component line = notice != null && System.currentTimeMillis() < noticeUntil ? notice
                : Component.translatable(held != null ? "gui.hardwrought.runes.hint_held"
                        : sitting.part() ? "gui.hardwrought.runes.hint_part" : "gui.hardwrought.runes.hint_tool");
        graphics.text(font, shortened(line.getString(), panelWidth - 110 - SURFACE_X - 62), left + SURFACE_X + 62, y,
                notice != null && System.currentTimeMillis() < noticeUntil ? WARN : FADED, false);
    }

    private void drawSurface(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int sx = left + SURFACE_X, sy = top + SURFACE_Y;
        graphics.fill(sx - 1, sy - 1, sx + surface + 1, sy + surface + 1, PANEL_DARK);
        graphics.fill(sx, sy, sx + surface, sy + surface, CLOTH);
        int pixel = square * RuneWork.SCALE;
        for (int i = 1; i < Mask.SIZE; i++) {
            graphics.fill(sx + i * pixel, sy, sx + i * pixel + 1, sy + surface, CLOTH_GRID);
            graphics.fill(sx, sy + i * pixel, sx + surface, sy + i * pixel + 1, CLOTH_GRID);
        }
        // The piece itself, in its own colours.
        for (int y = 0; y < Mask.SIZE; y++) {
            for (int x = 0; x < Mask.SIZE; x++) {
                if (!piece.get(x, y)) continue;
                int colour = pixels != null && pixels.length == Mask.SIZE * Mask.SIZE ? pixels[y * Mask.SIZE + x] | 0xFF000000 : 0xFF9A9A9A;
                graphics.fill(sx + x * pixel, sy + y * pixel, sx + (x + 1) * pixel, sy + (y + 1) * pixel, colour);
            }
        }
        // What the piece carries from before: quieter than what is being written, and red once it is to be wiped off.
        int oldUnder = held == null ? carriedAt(mouseX, mouseY) : -1;
        for (int i = 0; i < sitting.carried().size(); i++) {
            RuneWork.Placement old = sitting.carried().get(i);
            Rune rune = rune(old.rune());
            if (rune == null) continue;
            boolean gone = wiped.contains(i);
            drawRune(graphics, old, gone ? 0x90D84030 : (i == oldUnder ? INKS : OLD_INKS)[old.ink().ordinal()]);
            if (gone || squares(old).isEmpty() || piece().getEnchantments().getLevel(rune.enchantment()) < 1) continue;
            int[] first = squares(old).getFirst();
            graphics.text(font, Component.translatable("enchantment.level." + piece().getEnchantments().getLevel(rune.enchantment())),
                    sx + first[0] * square + square + 1, sy + first[1] * square - 3, 0xFFD0D0D0, true);
        }
        if (oldUnder >= 0 && rune(sitting.carried().get(oldUnder).rune()) != null) {
            Rune rune = rune(sitting.carried().get(oldUnder).rune());
            graphics.setTooltipForNextFrame(List.of(
                    Enchantment.getFullname(rune.enchantment(), piece().getEnchantments().getLevel(rune.enchantment())).getVisualOrderText(),
                    Component.translatable(wiped.contains(oldUnder) ? "gui.hardwrought.runes.old_wiped" : "gui.hardwrought.runes.old")
                            .getVisualOrderText()), mouseX, mouseY);
        }
        RuneWork.Placement under = held == null ? laidAt(mouseX, mouseY) : null;
        for (RuneWork.Placement placement : laid) {
            drawRune(graphics, placement, (placement == under ? INKS_HOVER : INKS)[placement.ink().ordinal()]);
            // Its level, written beside it.
            int[] first = squares(placement).getFirst();
            Component level = Component.translatable("enchantment.level." + level(placement));
            graphics.text(font, level, sx + first[0] * square + square + 1, sy + first[1] * square - 3, 0xFFFFFFFF, true);
        }
        RuneWork.Placement ghost = hovering(mouseX, mouseY);
        if (ghost != null) {
            String objection = objection(ghost);
            drawRune(graphics, ghost, objection == null || objection.equals("too_little") ? INKS_HOVER[ghost.ink().ordinal()] : FORBIDDEN);
            Rune rune = rune(ghost.rune());
            int level = level(ghost);
            graphics.setTooltipForNextFrame(level < 1 ? Component.translatable("message.hardwrought.runes.refused.too_little")
                    : Enchantment.getFullname(rune.enchantment(), level), mouseX, mouseY);
        } else if (under != null) {
            graphics.setTooltipForNextFrame(Enchantment.getFullname(runes.get(under.rune()).enchantment(), level(under)),
                    mouseX, mouseY);
        }
    }

    /** A laid or hovering rune: its squares on the piece in ink, the rest only hinted at. */
    private void drawRune(GuiGraphicsExtractor graphics, RuneWork.Placement placement, int ink) {
        int sx = left + SURFACE_X, sy = top + SURFACE_Y;
        for (int[] cell : squares(placement)) {
            if (cell[0] < 0 || cell[1] < 0 || cell[0] >= RuneWork.GRID || cell[1] >= RuneWork.GRID) continue;
            boolean written = RuneWork.onPiece(piece, cell[0], cell[1]) || (ink & 0xFFFFFF) == 0xD84030;
            graphics.fill(sx + cell[0] * square, sy + cell[1] * square, sx + (cell[0] + 1) * square,
                    sy + (cell[1] + 1) * square, written || ink == FORBIDDEN ? ink : OVER_EDGE);
        }
    }

    private void drawList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int lx = left + listX, ly = top + LIST_Y + INK_BAR;
        // The inks: which one the next rune is written in, and how much of each there is.
        for (RuneWork.Ink of : RuneWork.Ink.values()) {
            int ix = lx + of.ordinal() * LIST_WIDTH / 3, iw = LIST_WIDTH / 3 - 2, iy = top + LIST_Y;
            boolean over = inkAt(mouseX, mouseY) == of;
            graphics.fill(ix, iy, ix + iw, iy + INK_BAR - 2, of == ink ? 0xFFE8E0A8 : over ? 0xFFD8D8D8 : 0xFFB4B4B4);
            graphics.fill(ix + 2, iy + 2, ix + 12, iy + INK_BAR - 4, PANEL_DARK);
            graphics.fill(ix + 3, iy + 3, ix + 11, iy + INK_BAR - 5, INKS[of.ordinal()]);
            graphics.text(font, String.valueOf(powderHeld(of)), ix + 15, iy + 3, TEXT, false);
            if (over) {
                graphics.setTooltipForNextFrame(Component.translatable("gui.hardwrought.runes.ink." + of.id() + ".about"),
                        mouseX, mouseY);
            }
        }
        List<Rune> all = new ArrayList<>(runes.values());
        for (int row = 0; row < rows && scroll + row < all.size(); row++) {
            Rune rune = all.get(scroll + row);
            int y = ly + row * ROW;
            boolean used = has(rune) || laid.stream().anyMatch(placement -> placement.rune().equals(rune.id()));
            boolean hovered = mouseX >= lx && mouseX < lx + LIST_WIDTH && mouseY >= y && mouseY < y + ROW;
            graphics.fill(lx, y, lx + LIST_WIDTH, y + ROW - 1, rune.id().equals(held) ? 0xFFE8E0A8 : hovered ? 0xFFD8D8D8 : 0xFFB4B4B4);
            graphics.fill(lx, y, lx + ROW - 1, y + ROW - 1, CLOTH);
            int dot = 2, inset = (ROW - 1 - rune.shape().size() * dot) / 2;
            for (int ry = 0; ry < rune.shape().size(); ry++) {
                for (int rx = 0; rx < rune.shape().size(); rx++) {
                    if (rune.shape().at(rx, ry)) {
                        graphics.fill(lx + inset + rx * dot, y + inset + ry * dot, lx + inset + (rx + 1) * dot,
                                y + inset + (ry + 1) * dot, used ? 0xFF5A5A6A : INKS_HOVER[ink.ordinal()]);
                    }
                }
            }
            graphics.text(font, shortened(rune.enchantment().value().description().getString(), LIST_WIDTH - 29),
                    lx + 27, y + 3, used ? FADED : TEXT, false);
            graphics.text(font, Component.translatable("gui.hardwrought.runes.row",
                            Component.translatable("enchantment.level." + rune.enchantment().value().getMaxLevel()),
                            RuneWork.experienceFor(rune.tier())), lx + 27, y + 13, FADED, false);
        }
        if (all.size() > rows) {
            int track = rows * ROW, bar = Math.max(8, track * rows / all.size());
            int at = (track - bar) * scroll / Math.max(1, all.size() - rows);
            graphics.fill(lx + LIST_WIDTH + 1, ly + at, lx + LIST_WIDTH + 3, ly + at + bar, PANEL_DARK);
        }
    }

    private String shortened(String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
