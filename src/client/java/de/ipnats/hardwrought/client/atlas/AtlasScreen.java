package de.ipnats.hardwrought.client.atlas;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.atlas.Atlas;
import de.ipnats.hardwrought.core.networking.AtlasPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The atlas opened, as a book: a leather cover, the map drawn on its page in ink and watercolour (see
 * {@link AtlasPainter}) with a compass rose and a scale bar, and down the side the places the bearer has
 * marked and the other bearers near them.
 *
 * <p>The page is moved by dragging it and its scale changed with the wheel; space brings it back to the
 * bearer. A right click marks a place: a name and a symbol are asked for. A right click on a mark, here or
 * in the list, takes it out again, and a click on one in the list turns the page to it.
 */
public class AtlasScreen extends Screen {
    private static final int OUTER = 8, LEATHER = 7, SIDE = 118, GUTTER = 10, HEAD = 16, FOOT = 26, ROW = 14;
    private static final int COVER = 0xFF4A3220, COVER_DARK = 0xFF2E1E12, STITCH = 0xFF8A6A48;
    private static final int PAPER = 0xFFE8D9B0, PAPER_DARK = 0xFFD8C69A, RULE = 0xFF9A8058;
    private static final int INK = 0xFF2A2018, FADED = 0xFF6A5640, SELF = 0xFFD8392B, OTHERS = 0xFF3A70C0, PARTY = 0xFF3C9A48;
    /** Pixels to a square of the atlas, at each scale. A square is four blocks. */
    private static final int[] SCALES = {1, 2, 3, 4, 6, 8};
    /** How near a mark the pointer has to be to mean it. */
    private static final int NEAR = 6;
    /** The bars a scale may show, in blocks. */
    private static final int[] BARS = {8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096};
    private static final Identifier MAP = Identifier.fromNamespaceAndPath(Hardwrought.MOD_ID, "dynamic/atlas_page");

    /** The middle of the page, in blocks. */
    private double centreX, centreZ;
    private int scale = 2;
    private boolean placed;
    private EditBox name;
    /** The place being named, or null when no mark is being made; and the symbol chosen for it. */
    private int[] naming;
    private int icon;
    private int listScroll;

    private final AtlasPainter painter = new AtlasPainter();
    private DynamicTexture texture;
    private int[] pixels = new int[0];
    /** What the page was last painted for; painted again only when any of it changes. */
    private long paintedFor = Long.MIN_VALUE;
    /** What the pointer is over, gathered while drawing and shown once the widgets are drawn. */
    private List<FormattedCharSequence> tooltip = List.of();

    public AtlasScreen() {
        super(Component.translatable("gui.hardwrought.atlas"));
    }

    @Override
    protected void init() {
        if (!placed) toBearer();
        name = addRenderableWidget(new EditBox(font, panelLeft() + 9, panelTop() + 16, 182, 14,
                Component.translatable("gui.hardwrought.atlas.name")));
        name.setMaxLength(Atlas.MAX_NAME);
        name.setHint(Component.translatable("gui.hardwrought.atlas.name"));
        // Ink on a strip of paper: the black box of the game's own fields does not belong in a book.
        name.setBordered(false);
        name.setTextColor(INK);
        name.setTextShadow(false);
        name.setVisible(naming != null);
        paintedFor = Long.MIN_VALUE;
    }

    @Override
    public void removed() {
        super.removed();
        if (texture != null && minecraft != null) {
            minecraft.getTextureManager().release(MAP);
            texture = null;
        }
    }

    private void toBearer() {
        if (minecraft == null || minecraft.player == null) return;
        centreX = minecraft.player.getX();
        centreZ = minecraft.player.getZ();
        placed = true;
    }

    private Identifier dimension() {
        return minecraft == null || minecraft.level == null ? Identifier.withDefaultNamespace("overworld")
                : minecraft.level.dimension().identifier();
    }

    // ---------------------------------------------------------------- the layout

    private boolean sidebar() { return width >= 380; }
    private int paperLeft() { return OUTER + LEATHER; }
    private int paperTop() { return OUTER + LEATHER; }
    private int paperRight() { return width - OUTER - LEATHER; }
    private int paperBottom() { return height - OUTER - LEATHER; }
    private int mapLeft() { return paperLeft() + 6; }
    private int mapTop() { return paperTop() + HEAD; }
    private int mapRight() { return paperRight() - 6 - (sidebar() ? SIDE + GUTTER : 0); }
    private int mapBottom() { return paperBottom() - FOOT; }
    private int sideLeft() { return mapRight() + GUTTER; }
    private int sideRight() { return paperRight() - 6; }
    private int panelLeft() { return (mapLeft() + mapRight()) / 2 - 100; }
    private int panelTop() { return mapBottom() - 78; }

    /** Blocks to a pixel at the present scale. */
    private double bpp() {
        return Atlas.BLOCKS / (double) SCALES[scale];
    }

    /** The world pixel the page's left edge falls on: a block at x is on world pixel x / bpp. */
    private long worldX0() {
        return (long) Math.floor(centreX / bpp()) - (mapRight() - mapLeft()) / 2;
    }

    private long worldZ0() {
        return (long) Math.floor(centreZ / bpp()) - (mapBottom() - mapTop()) / 2;
    }

    private double blockX(double screenX) {
        return (worldX0() + (screenX - mapLeft()) + 0.5) * bpp();
    }

    private double blockZ(double screenY) {
        return (worldZ0() + (screenY - mapTop()) + 0.5) * bpp();
    }

    private int screenX(double blockX) {
        return mapLeft() + (int) (Math.floor(blockX / bpp()) - worldX0());
    }

    private int screenY(double blockZ) {
        return mapTop() + (int) (Math.floor(blockZ / bpp()) - worldZ0());
    }

    private boolean onMap(double x, double y) {
        return x >= mapLeft() && x < mapRight() && y >= mapTop() && y < mapBottom();
    }

    private boolean onSide(double x, double y) {
        return sidebar() && x >= sideLeft() && x < sideRight() && y >= mapTop() + 14 && y < mapBottom();
    }

    /** The mark under the pointer on the map, by its place in the list; -1 for none. */
    private int markAt(double x, double y) {
        List<Atlas.Marker> markers = AtlasClient.markers(dimension());
        for (int i = markers.size() - 1; i >= 0; i--) {
            if (Math.abs(screenX(markers.get(i).x()) - x) <= NEAR && Math.abs(screenY(markers.get(i).z()) - y) <= NEAR) return i;
        }
        return -1;
    }

    /** The row of the list under the pointer: a mark's place in the list, or −1. */
    private int rowAt(double x, double y) {
        if (!onSide(x, y)) return -1;
        int row = (int) ((y - mapTop() - 14) / ROW) + listScroll;
        return row < AtlasClient.markers(dimension()).size() ? row : -1;
    }

    private int visibleRows() {
        return Math.max(1, (mapBottom() - mapTop() - 14) / ROW);
    }

    /** The symbol button of the naming panel under the pointer, or −1. */
    private int iconAt(double x, double y) {
        if (naming == null) return -1;
        int top = panelTop() + 36;
        if (y < top || y >= top + 16) return -1;
        int column = (int) ((x - panelLeft() - 6) / 24);
        return column >= 0 && column < Atlas.ICONS && x >= panelLeft() + 6 ? column : -1;
    }

    // ---------------------------------------------------------------- the hand

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        // The page's own clicks come first: the screen would otherwise take a click on the map for itself.
        int chosen = iconAt(event.x(), event.y());
        if (chosen >= 0) {
            icon = chosen;
            return true;
        }
        int row = rowAt(event.x(), event.y());
        if (row >= 0) {
            Atlas.Marker marker = AtlasClient.markers(dimension()).get(row);
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) ClientPlayNetworking.send(new AtlasPayloads.Unmark(row));
            else {
                centreX = marker.x() + 0.5;
                centreZ = marker.z() + 0.5;
            }
            return true;
        }
        if (!onMap(event.x(), event.y()) || event.button() != InputConstants.MOUSE_BUTTON_RIGHT || naming != null) {
            return super.mouseClicked(event, doubled);
        }
        int mark = markAt(event.x(), event.y());
        if (mark >= 0) {
            ClientPlayNetworking.send(new AtlasPayloads.Unmark(mark));
            return true;
        }
        naming = new int[] {(int) Math.floor(blockX(event.x())), (int) Math.floor(blockZ(event.y()))};
        name.setValue("");
        name.setVisible(true);
        setFocused(name);
        name.setFocused(true);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && onMap(event.x(), event.y()) && naming == null) {
            centreX -= dragX * bpp();
            centreZ -= dragY * bpp();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (onSide(x, y)) {
            int rows = AtlasClient.markers(dimension()).size();
            listScroll = Math.clamp(listScroll - (int) Math.signum(scrollY), 0, Math.max(0, rows - visibleRows()));
            return true;
        }
        if (!onMap(x, y)) return super.mouseScrolled(x, y, scrollX, scrollY);
        // The place under the pointer stays under the pointer.
        double atX = blockX(x), atZ = blockZ(y);
        scale = Math.clamp(scale + (int) Math.signum(scrollY), 0, SCALES.length - 1);
        centreX += atX - blockX(x);
        centreZ += atZ - blockZ(y);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (naming != null) {
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
                ClientPlayNetworking.send(new AtlasPayloads.Mark(naming[0], naming[1], name.getValue().strip(), icon));
                stopNaming();
                return true;
            }
            if (event.key() == InputConstants.KEY_ESCAPE) {
                stopNaming();
                return true;
            }
            return super.keyPressed(event);
        }
        if (event.key() == InputConstants.KEY_SPACE) {
            toBearer();
            return true;
        }
        // The key that opened the book closes it again.
        if (AtlasClient.OPEN.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void stopNaming() {
        naming = null;
        name.setVisible(false);
        name.setFocused(false);
        setFocused(null);
    }

    // ---------------------------------------------------------------- the book

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        // The cover: leather, a darker rim, a line of stitching round it.
        graphics.fill(OUTER, OUTER, width - OUTER, height - OUTER, COVER_DARK);
        graphics.fill(OUTER + 1, OUTER + 1, width - OUTER - 1, height - OUTER - 1, COVER);
        for (int x = OUTER + 4; x < width - OUTER - 4; x += 4) {
            graphics.fill(x, OUTER + 3, x + 2, OUTER + 4, STITCH);
            graphics.fill(x, height - OUTER - 4, x + 2, height - OUTER - 3, STITCH);
        }
        for (int y = OUTER + 4; y < height - OUTER - 4; y += 4) {
            graphics.fill(OUTER + 3, y, OUTER + 4, y + 2, STITCH);
            graphics.fill(width - OUTER - 4, y, width - OUTER - 3, y + 2, STITCH);
        }
        // The paper, a shade darker at its edge.
        graphics.fill(paperLeft() - 1, paperTop() - 1, paperRight() + 1, paperBottom() + 1, RULE);
        graphics.fill(paperLeft(), paperTop(), paperRight(), paperBottom(), PAPER_DARK);
        graphics.fill(paperLeft() + 2, paperTop() + 2, paperRight() - 2, paperBottom() - 2, PAPER);
        if (sidebar()) {
            // The gutter between the map and the list, as the fold of the book.
            int fold = mapRight() + GUTTER / 2;
            graphics.fill(fold - 2, paperTop(), fold + 2, paperBottom(), PAPER_DARK);
            graphics.fill(fold, paperTop(), fold + 1, paperBottom(), RULE);
        }
        drawPage(graphics);
        drawOverlay(graphics, mouseX, mouseY);
    }

    /** The map itself: painted into a texture when the view or the atlas changes, and laid on the page. */
    private void drawPage(GuiGraphicsExtractor graphics) {
        if (minecraft == null) return;
        int width = mapRight() - mapLeft(), height = mapBottom() - mapTop();
        if (width <= 0 || height <= 0) return;
        Map<Long, byte[]> tiles = AtlasClient.tiles(dimension());
        long wanted = AtlasPainter.mix(AtlasPainter.mix(worldX0(), worldZ0()), AtlasPainter.mix(scale * 31L + tiles.size(),
                (long) width << 16 | height));
        if (texture == null || texture.getPixels() == null || texture.getPixels().getWidth() != width
                || texture.getPixels().getHeight() != height) {
            if (texture != null) minecraft.getTextureManager().release(MAP);
            texture = new DynamicTexture(() -> "Hardwrought atlas page", width, height, false);
            minecraft.getTextureManager().register(MAP, texture);
            pixels = new int[width * height];
            paintedFor = Long.MIN_VALUE;
        }
        if (wanted != paintedFor) {
            painter.paint(pixels, width, height, worldX0(), worldZ0(), bpp(), tiles,
                    minecraft.level != null && minecraft.level.dimensionType().hasCeiling());
            var image = texture.getPixels();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) image.setPixel(x, y, pixels[y * width + x]);
            }
            texture.upload();
            paintedFor = wanted;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, MAP, mapLeft(), mapTop(), 0, 0, width, height, width, height);
        // A double rule round the map, as a printed plate has.
        frame(graphics, mapLeft() - 1, mapTop() - 1, mapRight() + 1, mapBottom() + 1, INK);
        frame(graphics, mapLeft() - 3, mapTop() - 3, mapRight() + 3, mapBottom() + 3, RULE);
    }

    private static void frame(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int colour) {
        graphics.fill(x0, y0, x1, y0 + 1, colour);
        graphics.fill(x0, y1 - 1, x1, y1, colour);
        graphics.fill(x0, y0, x0 + 1, y1, colour);
        graphics.fill(x1 - 1, y0, x1, y1, colour);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (!tooltip.isEmpty()) graphics.setTooltipForNextFrame(tooltip, mouseX, mouseY);
    }

    /** Everything drawn over the map and beside it: drawn with the page, so the text field of a new mark lies on top. */
    private void drawOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Identifier dimension = dimension();
        Component title = Component.translatable("gui.hardwrought.atlas.title", dimensionName(dimension));
        graphics.text(font, title, (mapLeft() + mapRight() - font.width(title)) / 2, paperTop() + 4, INK, false);

        graphics.enableScissor(mapLeft(), mapTop(), mapRight(), mapBottom());
        List<Atlas.Marker> markers = AtlasClient.markers(dimension);
        int hoveredRow = rowAt(mouseX, mouseY);
        int hovered = naming == null && onMap(mouseX, mouseY) ? markAt(mouseX, mouseY) : hoveredRow;
        for (int i = 0; i < markers.size(); i++) drawMark(graphics, markers.get(i), i == hovered);
        tooltip = new ArrayList<>();
        for (Atlas.Bearer bearer : AtlasClient.bearers(dimension)) {
            int x = screenX(bearer.x()), y = screenY(bearer.z());
            int colour = bearer.party() ? PARTY : OTHERS;
            if (bearer.party() && !inMap(x, y)) {
                // Off the page: a mark on its edge, pointing the way to them, with their name.
                edge(graphics, x, y, colour, Component.literal(bearer.name()));
                continue;
            }
            arrow(graphics, x, y, bearer.yaw(), colour);
            if (bearer.party() || SCALES[scale] >= 3) label(graphics, Component.literal(bearer.name()), x + 9, y - 4);
            if (Math.abs(mouseX - x) <= NEAR && Math.abs(mouseY - y) <= NEAR && naming == null) {
                tooltip.add(Component.literal(bearer.name()).getVisualOrderText());
            }
        }
        if (naming != null) AtlasIcons.draw(graphics, AtlasIcons.MARKS[icon], screenX(naming[0]), screenY(naming[1]), 1, true);
        if (minecraft != null && minecraft.player != null) {
            arrow(graphics, screenX(minecraft.player.getX()), screenY(minecraft.player.getZ()), minecraft.player.getYRot(), SELF);
        }
        rose(graphics, mapRight() - 22, mapTop() + 22);
        scaleBar(graphics, mapLeft() + 8, mapBottom() - 14);
        graphics.disableScissor();

        if (sidebar()) drawList(graphics, markers, hoveredRow);
        if (naming != null) drawPanel(graphics, mouseX, mouseY);

        int foot = mapBottom() + 6;
        Map<Long, byte[]> tiles = AtlasClient.tiles(dimension);
        if (naming == null) {
            Component status = tiles.isEmpty() ? Component.translatable("gui.hardwrought.atlas.empty")
                    : Component.translatable("gui.hardwrought.atlas.status",
                            (int) Math.floor(onMap(mouseX, mouseY) ? blockX(mouseX) : centreX),
                            (int) Math.floor(onMap(mouseX, mouseY) ? blockZ(mouseY) : centreZ), tiles.size());
            graphics.text(font, status, mapLeft() + 2, foot, INK, false);
            Component help = Component.translatable("gui.hardwrought.atlas.help");
            if (font.width(help) <= paperRight() - mapLeft() - 8) graphics.text(font, help, mapLeft() + 2, foot + 10, FADED, false);
        }
        if (hovered >= 0 && hovered < markers.size()) {
            Atlas.Marker marker = markers.get(hovered);
            tooltip.add(markName(marker).getVisualOrderText());
            tooltip.add(Component.literal(marker.x() + ", " + marker.z()).getVisualOrderText());
            if (hoveredRow >= 0) tooltip.add(Component.translatable("gui.hardwrought.atlas.go").getVisualOrderText());
            tooltip.add(Component.translatable("gui.hardwrought.atlas.remove").getVisualOrderText());
        }
        int chosen = iconAt(mouseX, mouseY);
        if (chosen >= 0) tooltip.add(Component.translatable("gui.hardwrought.atlas.icon." + AtlasIcons.MARKS[chosen].key()).getVisualOrderText());
    }

    private Component markName(Atlas.Marker marker) {
        return marker.kind() == 1 ? Component.translatable("gui.hardwrought.atlas.death")
                : marker.name().isEmpty() ? Component.translatable("gui.hardwrought.atlas.icon." + AtlasIcons.MARKS[marker.icon()].key())
                : Component.literal(marker.name());
    }

    private static Component dimensionName(Identifier dimension) {
        return Component.translatableWithFallback("gui.hardwrought.atlas.dimension." + dimension.getPath(), dimension.getPath());
    }

    private void drawMark(GuiGraphicsExtractor graphics, Atlas.Marker marker, boolean hovered) {
        int x = screenX(marker.x()), y = screenY(marker.z());
        AtlasIcons.Icon icon = marker.kind() == 1 ? AtlasIcons.SKULL : AtlasIcons.MARKS[marker.icon()];
        AtlasIcons.draw(graphics, icon, x, y, 1, hovered);
        if (marker.kind() != 1 && !marker.name().isEmpty() && SCALES[scale] >= 3) {
            label(graphics, Component.literal(marker.name()), x + 7, y - 4);
        }
    }

    /** A name on the map, on a scrap of clean paper so the land under it does not swallow it. */
    private void label(GuiGraphicsExtractor graphics, Component text, int x, int y) {
        int w = font.width(text);
        graphics.fill(x - 2, y - 1, x + w + 2, y + 9, 0xD0E8D9B0);
        graphics.text(font, text, x, y, INK, false);
    }

    /**
     * An arrow the way someone faces: a long point with a notch cut into its back, so the way it points
     * cannot be mistaken, in ink round a coloured middle.
     */
    private static void arrow(GuiGraphicsExtractor graphics, int x, int y, float yaw, int fill) {
        double facing = Math.toRadians(yaw);
        dart(graphics, x, y, -Math.sin(facing), Math.cos(facing), fill);
    }

    /** How far an arrow reaches from its place, in pixels, with room for its rim. */
    private static final int DART = 10;

    /**
     * The arrow's shape, pointing along (fx, fz): a tip, two barbs and the notch between them, filled with
     * its colour and rimmed all round with one pixel of ink.
     */
    private static void dart(GuiGraphicsExtractor graphics, int x, int y, double fx, double fz, int fill) {
        double tipX = fx * 8, tipZ = fz * 8;
        double backX = -fx * 5, backZ = -fz * 5, notchX = -fx * 1.5, notchZ = -fz * 1.5;
        double sideX = -fz * 5, sideZ = fx * 5;
        double leftX = backX + sideX, leftZ = backZ + sideZ, rightX = backX - sideX, rightZ = backZ - sideZ;
        int span = 2 * DART + 3;
        boolean[][] body = new boolean[span][span];
        for (int dy = -DART; dy <= DART; dy++) {
            for (int dx = -DART; dx <= DART; dx++) {
                double px = dx + 0.5, py = dy + 0.5;
                body[dy + DART + 1][dx + DART + 1] = inside(px, py, tipX, tipZ, leftX, leftZ, notchX, notchZ)
                        || inside(px, py, tipX, tipZ, notchX, notchZ, rightX, rightZ);
            }
        }
        for (int row = 0; row < span; row++) {
            for (int column = 0; column < span; column++) {
                int colour;
                if (body[row][column]) {
                    colour = fill;
                } else if (touches(body, row, column)) {
                    colour = INK;
                } else {
                    continue;
                }
                int px = x + column - DART - 1, py = y + row - DART - 1;
                graphics.fill(px, py, px + 1, py + 1, colour);
            }
        }
    }

    /** Whether a pixel outside the arrow lies against it, so it belongs to the rim. */
    private static boolean touches(boolean[][] body, int row, int column) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int r = row + dr, c = column + dc;
                if (r >= 0 && c >= 0 && r < body.length && c < body.length && body[r][c]) return true;
            }
        }
        return false;
    }

    /** Whether a point is on the map, clear of its edge by the room an arrow takes. */
    private boolean inMap(int x, int y) {
        return x >= mapLeft() + 8 && x <= mapRight() - 8 && y >= mapTop() + 8 && y <= mapBottom() - 8;
    }

    /** Someone off the page: an arrow at its edge, on the line from the middle to them, and their name. */
    private void edge(GuiGraphicsExtractor graphics, int x, int y, int colour, Component name) {
        double cx = (mapLeft() + mapRight()) / 2.0, cy = (mapTop() + mapBottom()) / 2.0;
        double dx = x - cx, dy = y - cy;
        double length = Math.max(1e-6, Math.hypot(dx, dy));
        double halfW = (mapRight() - mapLeft()) / 2.0 - 10, halfH = (mapBottom() - mapTop()) / 2.0 - 10;
        double t = Math.min(halfW / Math.max(1e-6, Math.abs(dx)), halfH / Math.max(1e-6, Math.abs(dy)));
        int ex = (int) Math.round(cx + dx * t), ey = (int) Math.round(cy + dy * t);
        dart(graphics, ex, ey, dx / length, dy / length, colour);
        int width = font.width(name);
        int lx = Math.clamp(ex - width / 2, mapLeft() + 2, mapRight() - width - 2);
        int ly = dy > 0 ? ey - 18 : ey + 10;
        label(graphics, name, lx, Math.clamp(ly, mapTop() + 2, mapBottom() - 11));
    }

    private static boolean inside(double px, double py, double ax, double ay, double bx, double by, double cx, double cy) {
        double d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
        double d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
        double d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
        boolean negative = d1 < 0 || d2 < 0 || d3 < 0, positive = d1 > 0 || d2 > 0 || d3 > 0;
        return !(negative && positive);
    }

    /** A compass rose: four long points and four short ones round a ring, north marked. */
    private void rose(GuiGraphicsExtractor graphics, int cx, int cy) {
        for (int y = -9; y <= 9; y++) {
            for (int x = -9; x <= 9; x++) {
                double r = Math.sqrt(x * x + y * y);
                if (r > 6.3 && r < 7.4) graphics.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, FADED);
            }
        }
        for (int t = 0; t < 14; t++) {
            int half = (14 - t) / 5;
            // Each long point: one side in ink, the other side light, as engraved roses are shaded.
            graphics.fill(cx - half, cy - 1 - t, cx, cy - t, INK);
            graphics.fill(cx, cy - 1 - t, cx + half + 1, cy - t, t == 0 ? INK : 0xFFF4EEDC);
            graphics.fill(cx - half, cy + t, cx + 1, cy + t + 1, 0xFFF4EEDC);
            graphics.fill(cx + 1, cy + t, cx + half + 1, cy + t + 1, INK);
            graphics.fill(cx + t, cy - half, cx + t + 1, cy, INK);
            graphics.fill(cx + t, cy, cx + t + 1, cy + half + 1, 0xFFF4EEDC);
            graphics.fill(cx - 1 - t, cy - half, cx - t, cy + 1, 0xFFF4EEDC);
            graphics.fill(cx - 1 - t, cy + 1, cx - t, cy + half + 1, INK);
        }
        for (int t = 1; t < 7; t++) {
            graphics.fill(cx + t, cy - t, cx + t + 1, cy - t + 1, FADED);
            graphics.fill(cx - t, cy - t, cx - t + 1, cy - t + 1, FADED);
            graphics.fill(cx + t, cy + t, cx + t + 1, cy + t + 1, FADED);
            graphics.fill(cx - t, cy + t, cx - t + 1, cy + t + 1, FADED);
        }
        graphics.text(font, "N", cx - font.width("N") / 2 + 1, cy - 23, INK, false);
    }

    /** A bar of a round number of blocks, in alternate ink and paper, as long as suits the scale. */
    private void scaleBar(GuiGraphicsExtractor graphics, int x, int y) {
        int blocks = BARS[0];
        for (int bar : BARS) {
            blocks = bar;
            if (bar / bpp() >= 40) break;
        }
        int length = (int) Math.round(blocks / bpp());
        Component label = Component.translatable("gui.hardwrought.atlas.blocks", blocks);
        graphics.fill(x - 3, y - 11, x + Math.max(length, font.width(label)) + 4, y + 7, 0xC8E8D9B0);
        graphics.text(font, label, x, y - 10, INK, false);
        frame(graphics, x - 1, y - 1, x + length + 1, y + 5, INK);
        for (int part = 0; part < 4; part++) {
            int from = x + length * part / 4, to = x + length * (part + 1) / 4;
            graphics.fill(from, y, to, y + 4, part % 2 == 0 ? INK : 0xFFF4EEDC);
        }
    }

    /** The marks down the side of the page, with how far each is, and the other bearers under them. */
    private void drawList(GuiGraphicsExtractor graphics, List<Atlas.Marker> markers, int hoveredRow) {
        int left = sideLeft(), right = sideRight(), top = mapTop();
        graphics.text(font, Component.translatable("gui.hardwrought.atlas.marks"), left + 2, top, INK, false);
        graphics.fill(left, top + 10, right, top + 11, RULE);
        if (markers.isEmpty()) {
            graphics.textWithWordWrap(font, Component.translatable("gui.hardwrought.atlas.no_marks"), left + 2, top + 16,
                    right - left - 4, FADED, false);
        }
        double px = minecraft != null && minecraft.player != null ? minecraft.player.getX() : centreX;
        double pz = minecraft != null && minecraft.player != null ? minecraft.player.getZ() : centreZ;
        int rows = visibleRows();
        listScroll = Math.clamp(listScroll, 0, Math.max(0, markers.size() - rows));
        for (int row = 0; row < rows && listScroll + row < markers.size(); row++) {
            int index = listScroll + row;
            Atlas.Marker marker = markers.get(index);
            int y = top + 14 + row * ROW;
            if (index == hoveredRow) graphics.fill(left, y - 1, right, y + ROW - 1, 0x40806040);
            AtlasIcons.draw(graphics, marker.kind() == 1 ? AtlasIcons.SKULL : AtlasIcons.MARKS[marker.icon()],
                    left + 6, y + 5, 1, false);
            String distance = Component.translatable("gui.hardwrought.atlas.distance",
                    (int) Math.round(Math.sqrt(Math.pow(marker.x() - px, 2) + Math.pow(marker.z() - pz, 2)))).getString();
            int room = right - left - 16 - font.width(distance) - 4;
            String shown = font.plainSubstrByWidth(markName(marker).getString(), room);
            graphics.text(font, shown, left + 13, y + 1, INK, false);
            graphics.text(font, distance, right - font.width(distance) - 1, y + 1, FADED, false);
        }
        if (markers.size() > rows) {
            int track = rows * ROW, thumb = Math.max(8, track * rows / markers.size());
            int at = top + 14 + (track - thumb) * listScroll / Math.max(1, markers.size() - rows);
            graphics.fill(right + 1, top + 14, right + 3, top + 14 + track, 0x30000000);
            graphics.fill(right + 1, at, right + 3, at + thumb, FADED);
        }
        List<Atlas.Bearer> bearers = AtlasClient.bearers(dimension());
        if (!bearers.isEmpty()) {
            int y = mapBottom() - 12 - Math.min(bearers.size(), 4) * 11;
            graphics.fill(left, y - 3, right, y - 2, RULE);
            graphics.text(font, Component.translatable("gui.hardwrought.atlas.companions"), left + 2, y, INK, false);
            for (int i = 0; i < Math.min(bearers.size(), 4); i++) {
                Atlas.Bearer bearer = bearers.get(i);
                int by = y + 11 + i * 11;
                arrow(graphics, left + 6, by + 4, bearer.yaw(), OTHERS);
                String distance = Component.translatable("gui.hardwrought.atlas.distance",
                        (int) Math.round(Math.sqrt(Math.pow(bearer.x() - px, 2) + Math.pow(bearer.z() - pz, 2)))).getString();
                graphics.text(font, bearer.name(), left + 14, by, INK, false);
                graphics.text(font, distance, right - font.width(distance) - 1, by, FADED, false);
            }
        }
    }

    /** The slip a new mark is written on: its name and its symbol. */
    private void drawPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = panelLeft(), top = panelTop();
        graphics.fill(left - 1, top - 1, left + 201, top + 73, INK);
        graphics.fill(left, top, left + 200, top + 72, PAPER_DARK);
        graphics.text(font, Component.translatable("gui.hardwrought.atlas.new_mark"), left + 6, top + 4, INK, false);
        graphics.fill(left + 4, top + 14, left + 196, top + 30, INK);
        graphics.fill(left + 5, top + 15, left + 195, top + 29, 0xFFF4EEDC);
        for (int i = 0; i < Atlas.ICONS; i++) {
            int x = left + 6 + i * 24, y = top + 36;
            boolean chosen = i == icon, under = iconAt(mouseX, mouseY) == i;
            if (chosen || under) graphics.fill(x, y, x + 20, y + 16, chosen ? 0xFFF4EEDC : 0x60FFFFFF);
            if (chosen) frame(graphics, x, y, x + 20, y + 16, INK);
            AtlasIcons.draw(graphics, AtlasIcons.MARKS[i], x + 10, y + 8, 1, false);
        }
        graphics.text(font, Component.translatable("gui.hardwrought.atlas.naming_help"), left + 6, top + 59, FADED, false);
    }

    /** Whether a new mark is being written: for tests. */
    public boolean writing() {
        return naming != null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
