package de.ipnats.hardwrought.client.magic;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.magic.CastSpellPayload;
import de.ipnats.hardwrought.magic.Rune;
import de.ipnats.hardwrought.magic.RuneKnowledge;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The drawing surface of controlled casting (magic specification sections 8 to 10): a sheet of
 * parchment on which the runes of a spell are drawn one after another, each in its own colour, and
 * then cast together.
 *
 * <p>Each press, drag and release of the mouse is one rune. The screen reads every stroke as it is
 * finished, to name it — if the caster knows the rune — but decides nothing: the strokes themselves
 * are sent, and the server reads them again.
 */
public class RuneCanvasScreen extends Screen {
    private static final int PARCHMENT = 0xFFE9DCB8;
    private static final int PARCHMENT_EDGE = 0xFF6B5432;
    private static final int INK = 0xFF2A2018;
    private static final int TEXT = 0xFFE9DCB8;
    private static final int FAINT = 0xFFB0A080;
    private static final int TABLE_WOOD = 0xFF29170F;
    private static final int TABLE_WOOD_GRAIN = 0xFF352016;
    private static final int TABLE_METAL = 0xFF8E9699;
    private static final int TABLE_GOLD = 0xFFC79737;
    private static final int TABLE_SURFACE = 0xFF120F20;
    private static final int TABLE_GRID = 0xFF211C36;
    private static final int ARCANE = 0xFF64E6D7;
    private static final int LORE_WIDTH = 132;

    private final boolean tableMounted;
    private final List<InkStroke> strokes = new ArrayList<>();
    /** The drawing as the server will read it: what each stroke is, and which belong together. */
    private List<de.ipnats.hardwrought.magic.SketchReader.Part> parts = List.of();
    private List<InkStroke> readStrokes = null;
    private InkStroke current;
    private boolean lore;
    private int size;
    private int canvasX;
    private int canvasY;
    private int controlsLeft;
    private int controlsY;
    private int controlsWidth;

    public RuneCanvasScreen() {
        this(false);
    }

    protected RuneCanvasScreen(boolean tableMounted) {
        super(Component.translatable(tableMounted ? "gui.hardwrought.magic_table" : "gui.hardwrought.rune_canvas"));
        this.tableMounted = tableMounted;
    }

    @Override
    protected void init() {
        int spare = lore ? LORE_WIDTH + 8 : 0;
        size = Math.max(104, Math.min(300, Math.min(height - (tableMounted ? 112 : 84), width - 40 - spare)));
        canvasX = (width - size - spare) / 2;
        canvasY = tableMounted ? 34 : 22;
        controlsY = canvasY + size + 34;
        int buttonWidth = Math.max(54, Math.min(76, (width - 60 - 12) / 4));
        controlsWidth = buttonWidth * 4 + 12;
        controlsLeft = (width - controlsWidth) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.hardwrought.rune_canvas.cast"), b -> cast())
                .bounds(controlsLeft, controlsY, buttonWidth, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.hardwrought.rune_canvas.undo"), b -> undo())
                .bounds(controlsLeft + buttonWidth + 4, controlsY, buttonWidth, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.hardwrought.rune_canvas.clear"), b -> strokes.clear())
                .bounds(controlsLeft + (buttonWidth + 4) * 2, controlsY, buttonWidth, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.hardwrought.rune_canvas.lore"), b -> {
            lore = !lore;
            rebuildWidgets();
        }).bounds(controlsLeft + (buttonWidth + 4) * 3, controlsY, buttonWidth, 18).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        if (tableMounted) {
            drawTable(graphics);
        } else {
            graphics.fill(canvasX - 3, canvasY - 3, canvasX + size + 3, canvasY + size + 3, PARCHMENT_EDGE);
            graphics.fill(canvasX, canvasY, canvasX + size, canvasY + size, PARCHMENT);
        }
        if (lore) drawLore(graphics);
    }

    private void drawTable(GuiGraphicsExtractor graphics) {
        int left = Math.min(canvasX - 14, controlsLeft - 10);
        int right = Math.max(canvasX + size + 14, controlsLeft + controlsWidth + 10);
        int top = canvasY - 22;
        int bottom = controlsY + 28;
        graphics.fill(left - 3, top - 3, right + 3, bottom + 3, 0xE00A0710);
        graphics.fill(left, top, right, bottom, TABLE_METAL);
        graphics.fill(left + 3, top + 3, right - 3, bottom - 3, TABLE_WOOD);
        for (int x = left + 8; x < right - 5; x += 18) {
            graphics.fill(x, top + 4, x + 2, bottom - 4, TABLE_WOOD_GRAIN);
        }
        graphics.fill(canvasX - 6, canvasY - 6, canvasX + size + 6, canvasY + size + 6, TABLE_GOLD);
        graphics.fill(canvasX - 3, canvasY - 3, canvasX + size + 3, canvasY + size + 3, TABLE_METAL);
        graphics.fill(canvasX, canvasY, canvasX + size, canvasY + size, TABLE_SURFACE);
        for (int p = 16; p < size; p += 16) {
            graphics.fill(canvasX + p, canvasY + 1, canvasX + p + 1, canvasY + size - 1, TABLE_GRID);
            graphics.fill(canvasX + 1, canvasY + p, canvasX + size - 1, canvasY + p + 1, TABLE_GRID);
        }
        int[][] studs = {{left + 5, top + 5}, {right - 9, top + 5}, {left + 5, bottom - 9}, {right - 9, bottom - 9}};
        for (int[] stud : studs) graphics.fill(stud[0], stud[1], stud[0] + 4, stud[1] + 4, TABLE_GOLD);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, canvasX + size / 2, tableMounted ? 17 : 8,
                tableMounted ? 0xFFFFD978 : TEXT);
        int[] colors = strokeColors();
        for (int i = 0; i < strokes.size(); i++) {
            InkStroke stroke = strokes.get(i);
            RuneInk.stroke(graphics, stroke.xs, stroke.ys, stroke.count, canvasX, canvasY, 1, 3, colors[i]);
        }
        if (current != null) RuneInk.stroke(graphics, current.xs, current.ys, current.count, canvasX, canvasY, 1, 3,
                tableMounted ? ARCANE : INK);
        drawReadings(graphics);
        graphics.text(font, Component.literal(parts().size() + "/" + de.ipnats.hardwrought.magic.Spell.MAX_SYMBOLS),
                canvasX + size - 30, canvasY - 15, tableMounted ? ARCANE : TEXT, true);
        if (strokes.isEmpty() && current == null) {
            graphics.centeredText(font, Component.translatable(tableMounted
                            ? "gui.hardwrought.magic_table.hint" : "gui.hardwrought.rune_canvas.hint"),
                    canvasX + size / 2, canvasY + size / 2 - 4, tableMounted ? 0xFF789B9D : 0xFF8A7A5A);
        }
    }

    /**
     * The drawing as the server will read it, read again whenever a stroke is added or taken away: a
     * rune or auxiliary rune per stroke, an arrow of shaft and head as one.
     */
    private List<de.ipnats.hardwrought.magic.SketchReader.Part> parts() {
        if (!strokes.equals(readStrokes)) {
            List<float[]> xs = new ArrayList<>(), ys = new ArrayList<>();
            for (InkStroke stroke : strokes) {
                xs.add(java.util.Arrays.copyOf(stroke.xs, stroke.count));
                ys.add(java.util.Arrays.copyOf(stroke.ys, stroke.count));
            }
            parts = de.ipnats.hardwrought.magic.SketchReader.read(xs, ys, size, false);
            readStrokes = new ArrayList<>(strokes);
        }
        return parts;
    }

    /** Each stroke in the colour of what it belongs to; both strokes of an arrow alike, a stroke read as nothing grey. */
    private int[] strokeColors() {
        int[] colors = new int[strokes.size()];
        java.util.Arrays.fill(colors, 0xFF9A9080);
        List<de.ipnats.hardwrought.magic.SketchReader.Part> read = parts();
        for (int i = 0; i < read.size(); i++) {
            var part = read.get(i);
            if (part.stroke() >= 0 && part.stroke() < colors.length) colors[part.stroke()] = RuneInk.separation(i);
            for (int partner : part.partners()) if (partner >= 0 && partner < colors.length) colors[partner] = RuneInk.separation(i);
        }
        return colors;
    }

    /** One line under the parchment: every rune and auxiliary rune drawn so far, numbered and coloured. */
    private void drawReadings(GuiGraphicsExtractor graphics) {
        int x = canvasX;
        int y = canvasY + size + 8;
        // First what the drawing will do, then what each of its marks is.
        Component preview = preview(parts());
        if (preview != null) {
            graphics.text(font, preview, x, y, 0xFFFFE9B0, true);
            y += 11;
        }
        List<de.ipnats.hardwrought.magic.SketchReader.Part> read = parts();
        for (int i = 0; i < read.size(); i++) {
            Component label = label(i, read.get(i));
            int w = font.width(label) + 8;
            if (x + w > canvasX + size && x > canvasX) {
                x = canvasX;
                y += 11;
            }
            if (y + 9 >= controlsY) break;
            graphics.text(font, label, x, y, RuneInk.separation(i), true);
            x += w;
        }
    }

    /**
     * What the drawing will do, in a few words: "Fire stream", "Light shield + Dark trap". Runes the
     * caster does not know stay a question here too.
     */
    private static Component preview(List<de.ipnats.hardwrought.magic.SketchReader.Part> parts) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || parts.isEmpty()) return null;
        net.minecraft.network.chat.MutableComponent text = Component.literal("→ ");
        boolean any = false;
        for (var group : de.ipnats.hardwrought.magic.Spell.groups(parts)) {
            de.ipnats.hardwrought.magic.Spell spell = de.ipnats.hardwrought.magic.Spell.of(group);
            if (spell == null) continue;
            if (any) text.append(Component.literal(" + "));
            any = true;
            net.minecraft.network.chat.MutableComponent elements = Component.empty();
            List<Rune> runes = spell.elements().stream().map(de.ipnats.hardwrought.magic.Spell.Element::rune).distinct().toList();
            for (int i = 0; i < runes.size(); i++) {
                if (i > 0) elements.append(Component.literal("/"));
                Rune rune = runes.get(i);
                elements.append(RuneKnowledge.knows(client.player, rune)
                        ? Component.translatable(rune.translationKey()).withColor(rune.color()) : Component.literal("?"));
            }
            if (spell.inverted()) elements = Component.translatable("magic.hardwrought.preview.inverted", elements);
            if (spell.gate() || spell.placement() == de.ipnats.hardwrought.magic.Spell.Placement.PORTAL) {
                // A gate: near or far, and what holds it steady.
                boolean far = spell.placement() == de.ipnats.hardwrought.magic.Spell.Placement.PORTAL;
                net.minecraft.network.chat.MutableComponent gate = Component.translatable(
                        far ? "magic.hardwrought.preview.gate_long" : "magic.hardwrought.preview.gate_short");
                if (!spell.elements().isEmpty()) gate.append(Component.literal(" + ")).append(elements);
                List<Rune> anchors = spell.anchors().stream().distinct().toList();
                if (!anchors.isEmpty()) {
                    net.minecraft.network.chat.MutableComponent held = Component.empty();
                    for (int i = 0; i < anchors.size(); i++) {
                        if (i > 0) held.append(Component.literal("/"));
                        Rune rune = anchors.get(i);
                        held.append(RuneKnowledge.knows(client.player, rune)
                                ? Component.translatable(rune.translationKey()).withColor(rune.color()) : Component.literal("?"));
                    }
                    gate.append(Component.translatable("magic.hardwrought.preview.anchored", held));
                } else if (far) {
                    gate.append(Component.translatable("magic.hardwrought.preview.unanchored"));
                }
                text.append(gate);
                continue;
            }
            String key;
            if (spell.cancelled()) key = "cancelled";
            else if (spell.shield() && spell.linger()) key = spell.core() == null ? "plain_fixed_shield" : "fixed_shield";
            else if (spell.shield()) key = spell.core() == null ? "plain_shield" : "shield";
            else if (spell.trap()) key = "trap";
            else if (spell.linger()) key = "linger";
            else if (spell.burst()) key = "burst";
            else key = spell.placement().name().toLowerCase(java.util.Locale.ROOT);
            text.append(Component.translatable("magic.hardwrought.preview." + key, elements));
        }
        return any ? text : null;
    }

    /** "2 Wind 84 %", "3 Arrow" for what the caster knows; a question mark for what they do not. */
    private static Component label(int index, de.ipnats.hardwrought.magic.SketchReader.Part part) {
        Minecraft client = Minecraft.getInstance();
        String number = (index + 1) + " ";
        if (client.player == null || !part.recognized() || !RuneKnowledge.knows(client.player, part.glyph())) {
            return Component.literal(number + "?");
        }
        Component name = Component.translatable(part.glyph().translationKey());
        // What it is in a gate, if it is part of one.
        Component role = part.gate() == de.ipnats.hardwrought.magic.SketchReader.Gate.NONE ? Component.empty()
                : Component.literal(" (").append(Component.translatable("magic.hardwrought.gate_part."
                        + part.gate().name().toLowerCase(java.util.Locale.ROOT))).append(Component.literal(")"));
        if (part.sign() != null) return Component.literal(number).append(name).append(role);
        return Component.literal(number).append(name).append(Component.literal(" " + Math.round(part.accuracy() * 100) + " %"))
                .append(role);
    }

    /** The runes the caster knows, drawn as they should be; the rest are still to be found. */
    private void drawLore(GuiGraphicsExtractor graphics) {
        int x = canvasX + size + 8;
        int y = canvasY;
        int rowHeight = Math.max(30, Math.min(48, size / Rune.values().length));
        graphics.fill(x - 2, y - 3, x + LORE_WIDTH, y + rowHeight * Rune.values().length + 3,
                tableMounted ? 0xE0120F20 : 0xC0201810);
        Minecraft client = Minecraft.getInstance();
        for (Rune rune : Rune.values()) {
            boolean known = client.player != null && RuneKnowledge.knows(client.player, rune);
            int glyph = rowHeight - 6;
            if (known) {
                graphics.fill(x + 2, y + 2, x + 2 + glyph, y + 2 + glyph,
                        tableMounted ? TABLE_GRID : PARCHMENT);
                RuneInk.shape(graphics, rune, x + 2, y + 2, glyph, 2, tableMounted ? ARCANE : INK);
                graphics.text(font, Component.translatable(rune.translationKey()), x + glyph + 8,
                        y + glyph / 2 - 2, 0xFF000000 | rune.color(), true);
            } else {
                graphics.fill(x + 2, y + 2, x + 2 + glyph, y + 2 + glyph, 0xFF3A3024);
                graphics.text(font, Component.literal("???"), x + glyph + 8, y + glyph / 2 - 2, FAINT, true);
            }
            y += rowHeight;
        }
    }

    private boolean onCanvas(double x, double y) {
        return x >= canvasX && x < canvasX + size && y >= canvasY && y < canvasY + size;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && onCanvas(event.x(), event.y())
                && strokes.size() < de.ipnats.hardwrought.magic.Spell.MAX_STROKES
                && parts().size() < de.ipnats.hardwrought.magic.Spell.MAX_SYMBOLS) {
            current = new InkStroke();
            current.add((float) (event.x() - canvasX), (float) (event.y() - canvasY));
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && onCanvas(event.x(), event.y())) {
            cast();
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (current != null) {
            float x = (float) Math.max(0, Math.min(size, event.x() - canvasX));
            float y = (float) Math.max(0, Math.min(size, event.y() - canvasY));
            current.add(x, y);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (current != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            current.finish();
            // A tap or a twitch is not a rune; it is simply not kept.
            if (current.count >= 3) strokes.add(current);
            current = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_SPACE) {
            cast();
            return true;
        }
        if (event.key() == InputConstants.KEY_BACKSPACE) {
            undo();
            return true;
        }
        return super.keyPressed(event);
    }

    /** Adds a finished stroke as if drawn, in parchment coordinates; for tests, which have no hand. */
    public void drawForTesting(float[] xs, float[] ys) {
        InkStroke stroke = new InkStroke();
        for (int i = 0; i < xs.length; i++) stroke.add(xs[i] * size / 300f, ys[i] * size / 300f);
        stroke.finish();
        strokes.add(stroke);
    }

    private void undo() {
        if (!strokes.isEmpty()) strokes.removeLast();
    }

    /** Sends the drawing to be cast and closes the parchment. An empty sheet is simply put away. */
    private void cast() {
        if (!strokes.isEmpty()) {
            List<CastSpellPayload.Stroke> payload = new ArrayList<>(strokes.size());
            for (InkStroke stroke : strokes) payload.add(stroke.toPayload());
            ClientPlayNetworking.send(new CastSpellPayload(payload, size, false));
        }
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
