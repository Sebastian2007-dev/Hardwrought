package de.ipnats.hardwrought.client.atlas;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.atlas.Atlas;
import de.ipnats.hardwrought.core.networking.AtlasPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.MapColor;

import java.util.List;
import java.util.Map;

/**
 * The atlas opened: the land the bearer has walked, drawn on a page; where they stand; and the places
 * they have marked. The page is moved by dragging it and its scale changed with the wheel. A right
 * click marks a place — a name is asked for — and a right click on a mark takes it out again.
 */
public class AtlasScreen extends Screen {
    private static final int MARGIN = 14, BORDER = 8, FOOT = 26;
    private static final int COVER = 0xFF3A2A1C, COVER_EDGE = 0xFF5A4028, PAPER = 0xFFD9C9A0, PAPER_EDGE = 0xFF8A7650;
    private static final int INK = 0xFF2A2018, FADED = 0xFF5A4A38;
    /** Pixels to a square of the atlas, at each scale. A square is four blocks. */
    private static final int[] SCALES = {2, 3, 4, 6, 8};
    /** How near a mark the pointer has to be to mean it. */
    private static final int NEAR = 5;

    /** The middle of the page, in blocks. */
    private double centreX, centreZ;
    private int scale = 1;
    private boolean placed;
    private EditBox name;
    /** The place being named, or null when no mark is being made. */
    private int[] naming;

    public AtlasScreen() {
        super(Component.translatable("gui.hardwrought.atlas"));
    }

    @Override
    protected void init() {
        if (!placed) toBearer();
        name = addRenderableWidget(new EditBox(font, MARGIN + BORDER + 2, height - MARGIN - BORDER - 18, 160, 16,
                Component.translatable("gui.hardwrought.atlas.name")));
        name.setMaxLength(Atlas.MAX_NAME);
        name.setHint(Component.translatable("gui.hardwrought.atlas.name"));
        name.setVisible(naming != null);
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

    private int mapLeft() { return MARGIN + BORDER; }
    private int mapTop() { return MARGIN + BORDER; }
    private int mapRight() { return width - MARGIN - BORDER; }
    private int mapBottom() { return height - MARGIN - BORDER - FOOT; }

    /** Blocks to a pixel at the present scale. */
    private double blocksPerPixel() {
        return Atlas.BLOCKS / (double) SCALES[scale];
    }

    private double blockX(double screenX) {
        return centreX + (screenX - (mapLeft() + mapRight()) / 2.0) * blocksPerPixel();
    }

    private double blockZ(double screenY) {
        return centreZ + (screenY - (mapTop() + mapBottom()) / 2.0) * blocksPerPixel();
    }

    private int screenX(double blockX) {
        return (int) Math.round((mapLeft() + mapRight()) / 2.0 + (blockX - centreX) / blocksPerPixel());
    }

    private int screenY(double blockZ) {
        return (int) Math.round((mapTop() + mapBottom()) / 2.0 + (blockZ - centreZ) / blocksPerPixel());
    }

    private boolean onMap(double x, double y) {
        return x >= mapLeft() && x < mapRight() && y >= mapTop() && y < mapBottom();
    }

    /** The mark under the pointer, by its place in the list; -1 for none. */
    private int markAt(double x, double y) {
        List<Atlas.Marker> markers = AtlasClient.markers(dimension());
        for (int i = markers.size() - 1; i >= 0; i--) {
            if (Math.abs(screenX(markers.get(i).x()) - x) <= NEAR && Math.abs(screenY(markers.get(i).z()) - y) <= NEAR) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- the hand

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) return true;
        if (!onMap(event.x(), event.y()) || event.button() != InputConstants.MOUSE_BUTTON_RIGHT) return false;
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
            centreX -= dragX * blocksPerPixel();
            centreZ -= dragY * blocksPerPixel();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
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
                ClientPlayNetworking.send(new AtlasPayloads.Mark(naming[0], naming[1], name.getValue().strip()));
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

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(MARGIN, MARGIN, width - MARGIN, height - MARGIN, COVER);
        graphics.fill(MARGIN + 2, MARGIN + 2, width - MARGIN - 2, height - MARGIN - 2, COVER_EDGE);
        graphics.fill(MARGIN + BORDER - 2, MARGIN + BORDER - 2, width - MARGIN - BORDER + 2, height - MARGIN - BORDER + 2, PAPER_EDGE);
        graphics.fill(MARGIN + BORDER, MARGIN + BORDER, width - MARGIN - BORDER, height - MARGIN - BORDER, PAPER);
        drawLand(graphics);
    }

    /**
     * The land, row of squares by row of squares. Neighbouring squares of one colour are drawn as one
     * stretch, so a sea or a plain costs a handful of rectangles and not thousands.
     */
    private void drawLand(GuiGraphicsExtractor graphics) {
        Map<Long, byte[]> tiles = AtlasClient.tiles(dimension());
        int left = mapLeft(), top = mapTop(), right = mapRight(), bottom = mapBottom();
        int pixel = SCALES[scale];
        int firstX = (int) Math.floor(blockX(left) / Atlas.BLOCKS), firstZ = (int) Math.floor(blockZ(top) / Atlas.BLOCKS);
        int startX = screenX(firstX * (double) Atlas.BLOCKS), startY = screenY(firstZ * (double) Atlas.BLOCKS);
        int across = (right - startX) / pixel + 1, down = (bottom - startY) / pixel + 1;
        long lastKey = Long.MIN_VALUE;
        byte[] lastTile = null;
        for (int row = 0; row < down; row++) {
            int squareZ = firstZ + row, y0 = Math.max(top, startY + row * pixel), y1 = Math.min(bottom, startY + (row + 1) * pixel);
            if (y1 <= y0) continue;
            int runFrom = 0, runColour = 0;
            for (int column = 0; column <= across; column++) {
                int colour = 0;
                if (column < across) {
                    int squareX = firstX + column;
                    long key = Atlas.key(squareX >> 2, squareZ >> 2);
                    if (key != lastKey) {
                        lastKey = key;
                        lastTile = tiles.get(key);
                    }
                    if (lastTile != null) {
                        colour = 0xFF000000 | MapColor.getColorFromPackedId(lastTile[(squareZ & 3) * Atlas.SQUARES + (squareX & 3)] & 0xFF);
                    }
                }
                if (colour == runColour) continue;
                if (runColour != 0) {
                    int x0 = Math.max(left, startX + runFrom * pixel), x1 = Math.min(right, startX + column * pixel);
                    if (x1 > x0) graphics.fill(x0, y0, x1, y1, runColour);
                }
                runFrom = column;
                runColour = colour;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        Identifier dimension = dimension();
        List<Atlas.Marker> markers = AtlasClient.markers(dimension);
        int hovered = naming == null && onMap(mouseX, mouseY) ? markAt(mouseX, mouseY) : -1;
        for (int i = 0; i < markers.size(); i++) {
            Atlas.Marker marker = markers.get(i);
            int x = screenX(marker.x()), y = screenY(marker.z());
            if (!onMap(x, y)) continue;
            int colour = marker.kind() == 1 ? 0xFFC02020 : 0xFF2050C0;
            graphics.fill(x - 3, y - 3, x + 4, y + 4, INK);
            graphics.fill(x - 2, y - 2, x + 3, y + 3, i == hovered ? 0xFFFFFFFF : colour);
            if (marker.kind() != 1 && !marker.name().isEmpty() && SCALES[scale] >= 3) {
                graphics.text(font, marker.name(), x + 6, y - 4, INK, false);
            }
        }
        if (naming != null) {
            int x = screenX(naming[0]), y = screenY(naming[1]);
            graphics.fill(x - 3, y - 3, x + 4, y + 4, INK);
            graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFFFFE060);
        }
        // The bearer: a point, and a stroke the way they face.
        if (minecraft != null && minecraft.player != null) {
            int x = screenX(minecraft.player.getX()), y = screenY(minecraft.player.getZ());
            if (onMap(x, y)) {
                double facing = Math.toRadians(minecraft.player.getYRot());
                int fx = x - (int) Math.round(Math.sin(facing) * 5), fy = y + (int) Math.round(Math.cos(facing) * 5);
                graphics.fill(fx - 1, fy - 1, fx + 2, fy + 2, INK);
                graphics.fill(x - 3, y - 3, x + 4, y + 4, INK);
                graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFFFFFFFF);
            }
        }

        int foot = mapBottom() + 6;
        Map<Long, byte[]> tiles = AtlasClient.tiles(dimension);
        if (naming == null) {
            Component status = tiles.isEmpty() ? Component.translatable("gui.hardwrought.atlas.empty")
                    : Component.translatable("gui.hardwrought.atlas.status",
                            (int) Math.floor(onMap(mouseX, mouseY) ? blockX(mouseX) : centreX),
                            (int) Math.floor(onMap(mouseX, mouseY) ? blockZ(mouseY) : centreZ), tiles.size());
            graphics.text(font, status, mapLeft() + 2, foot, INK, false);
            Component help = Component.translatable("gui.hardwrought.atlas.help");
            if (font.width(help) <= mapRight() - mapLeft() - 4) graphics.text(font, help, mapLeft() + 2, foot + 11, FADED, false);
        }
        if (hovered >= 0) {
            Atlas.Marker marker = markers.get(hovered);
            Component what = marker.kind() == 1 ? Component.translatable("gui.hardwrought.atlas.death")
                    : marker.name().isEmpty() ? Component.translatable("gui.hardwrought.atlas.unnamed") : Component.literal(marker.name());
            graphics.setTooltipForNextFrame(List.of(what.getVisualOrderText(),
                    Component.literal(marker.x() + ", " + marker.z()).getVisualOrderText(),
                    Component.translatable("gui.hardwrought.atlas.remove").getVisualOrderText()), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
