package de.ipnats.hardwrought.client.knowledge;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.client.mixin.GuiGraphicsExtractorAccessor;
import de.ipnats.hardwrought.knowledge.Multiblocks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A structure of several blocks, laid open in the compendium: the left page names its parts and how
 * many of each, the right page shows it built. Dragging turns it, the wheel brings it nearer, and the
 * arrows under it take it apart layer by layer from the ground up.
 *
 * <p>Built whole it is drawn the way it looks once finished; a single layer is drawn as the loose
 * blocks that go into it, since that is what is put down. Where any of several blocks will do, the
 * view cycles through the ones the player has studied, and only those.
 */
public class MultiblockScreen extends Screen {
    private static final int PANEL_WIDTH = 344;
    private static final int PANEL_HEIGHT = 204;
    private static final int LEFT_PAPER_X = 13;
    private static final int RIGHT_PAPER_X = 186;
    private static final int PAPER_WIDTH = 145;
    private static final int PAPER_TOP = 13;
    private static final int PAPER_BOTTOM = 190;
    private static final int CONTENT_MARGIN = 7;
    private static final int CONTENT_WIDTH = PAPER_WIDTH - CONTENT_MARGIN * 2;
    private static final int BUTTON_HEIGHT = 18;
    private static final int CONTROLS_Y = PAPER_BOTTOM - 4 - BUTTON_HEIGHT;
    private static final int ARROW_WIDTH = 22;
    private static final int LEGEND_ROW = 20;
    private static final long CYCLE_MILLIS = 1000L;
    private static final float MIN_ZOOM = 0.5f;
    private static final float MAX_ZOOM = 3.0f;

    private static final int TEXT_COLOR = 0xFF1B120B;
    private static final int FADED_COLOR = 0xFF4A3522;
    private static final Identifier BOOK_SPRITE = sprite("book");
    private static final Identifier BUTTON_SPRITE = sprite("button");
    private static final Identifier BUTTON_HOVERED_SPRITE = sprite("button_hovered");

    private final Screen parent;
    private final Multiblocks.Multiblock multiblock;
    /** The blocks the player has studied; the others of a choice are not shown. */
    private final Set<Identifier> known;
    private int left;
    private int top;
    /** -1 for the whole structure, otherwise the layer shown on its own, counted from the ground. */
    private int layer = -1;
    private float yaw = 225.0f;
    private float pitch = 30.0f;
    private float zoom = 1.0f;
    private boolean turning;

    public MultiblockScreen(Screen parent, Multiblocks.Multiblock multiblock, Set<Identifier> known) {
        super(name(multiblock));
        this.parent = parent;
        this.multiblock = multiblock;
        this.known = known;
    }

    public static Component name(Multiblocks.Multiblock multiblock) {
        return Component.translatable("multiblock." + multiblock.id().getNamespace() + "." + multiblock.id().getPath());
    }

    /** Whether a player who knows these blocks knows enough of the structure to be shown it. */
    public static boolean readable(Multiblocks.Multiblock multiblock, Set<Identifier> known) {
        for (List<Identifier> choice : multiblock.legend().values()) {
            if (choice.stream().noneMatch(known::contains)) return false;
        }
        return true;
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath("hardwrought", "compendium/" + name);
    }

    // ---------------------------------------------------------------- setting out the page

    private int leftContentX() { return left + LEFT_PAPER_X + CONTENT_MARGIN; }
    private int rightContentX() { return left + RIGHT_PAPER_X + CONTENT_MARGIN; }
    private int viewX0() { return left + RIGHT_PAPER_X + 3; }
    private int viewX1() { return left + RIGHT_PAPER_X + PAPER_WIDTH - 3; }
    private int viewY0() { return top + PAPER_TOP + 3; }
    private int viewY1() { return top + CONTROLS_Y - 3; }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
    }

    /** The block a character stands for just now: one the player knows, cycling where several are. */
    private Block shown(char symbol) {
        List<Identifier> choices = new ArrayList<>();
        for (Identifier id : multiblock.legend().getOrDefault(symbol, List.of())) {
            if (known.contains(id)) choices.add(id);
        }
        if (choices.isEmpty()) return null;
        Identifier id = choices.get((int) ((System.currentTimeMillis() / CYCLE_MILLIS) % choices.size()));
        return BuiltInRegistries.BLOCK.getValue(id);
    }

    private List<MultiblockRenderState.Placed> blocks() {
        List<MultiblockRenderState.Placed> placed = new ArrayList<>();
        for (int y = 0; y < multiblock.height(); y++) {
            if (layer >= 0 && y != layer) continue;
            for (int z = 0; z < multiblock.depth(); z++) {
                for (int x = 0; x < multiblock.width(); x++) {
                    char symbol = multiblock.at(x, y, z);
                    if (symbol == ' ') continue;
                    Block block = shown(symbol);
                    if (block == null) continue;
                    BlockState state = block.defaultBlockState();
                    if (layer < 0 && multiblock.formed().isPresent()) {
                        state = finished(state, multiblock.formed().get().part(x, y, z));
                    }
                    placed.add(new MultiblockRenderState.Placed(new BlockPos(x, y, z), state));
                }
            }
        }
        return placed;
    }

    /** A block as it looks in the finished structure: told its part, and that the whole stands. */
    private static BlockState finished(BlockState state, int part) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty number && property.getName().equals("part")
                    && number.getPossibleValues().contains(part)) {
                state = state.setValue(number, part);
            } else if (property instanceof BooleanProperty flag && property.getName().equals("formed")) {
                state = state.setValue(flag, true);
            }
        }
        return state;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BOOK_SPRITE, left, top, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int x = leftContentX();
        int y = top + PAPER_TOP + 5;
        y = graphics.textWithWordWrap(font, getTitle(), x, y, CONTENT_WIDTH, TEXT_COLOR, false) + 2;
        Component layerLine = layer < 0
                ? Component.translatable("gui.hardwrought.multiblock.all_layers", multiblock.height())
                : Component.translatable("gui.hardwrought.multiblock.layer", layer + 1, multiblock.height());
        graphics.text(font, layerLine, x, y, FADED_COLOR, false);
        y += 14;
        Map<Character, Integer> counts = multiblock.count(layer);
        for (Map.Entry<Character, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == 0) continue;
            Block block = shown(entry.getKey());
            if (block == null) continue;
            graphics.item(new ItemStack(block), x, y);
            Component line = Component.translatable("gui.hardwrought.multiblock.count", entry.getValue(), block.getName());
            graphics.textWithWordWrap(font, line, x + 20, y + 4, CONTENT_WIDTH - 20, TEXT_COLOR, false);
            y += LEGEND_ROW;
        }
        if (multiblock.legend().values().stream().anyMatch(choice -> choice.size() > 1)) {
            y = graphics.textWithWordWrap(font, Component.translatable("gui.hardwrought.multiblock.any_of"),
                    x, y + 2, CONTENT_WIDTH, FADED_COLOR, false) + 4;
        }
        graphics.textWithWordWrap(font, Component.translatable("gui.hardwrought.multiblock.hint"),
                x, Math.max(y + 4, top + CONTROLS_Y - 30), CONTENT_WIDTH, FADED_COLOR, false);

        button(graphics, leftContentX(), top + CONTROLS_Y, 54, Component.translatable("gui.hardwrought.compendium.back"),
                mouseX, mouseY);
        button(graphics, rightContentX(), top + CONTROLS_Y, ARROW_WIDTH, Component.literal("▼"), mouseX, mouseY);
        button(graphics, rightContentX() + CONTENT_WIDTH - ARROW_WIDTH, top + CONTROLS_Y, ARROW_WIDTH,
                Component.literal("▲"), mouseX, mouseY);
        Component layerLabel = layer < 0 ? Component.translatable("gui.hardwrought.multiblock.whole")
                : Component.literal((layer + 1) + " / " + multiblock.height());
        graphics.text(font, layerLabel, rightContentX() + CONTENT_WIDTH / 2 - font.width(layerLabel) / 2,
                top + CONTROLS_Y + 5, FADED_COLOR, false);

        drawStructure(graphics);
    }

    private void drawStructure(GuiGraphicsExtractor graphics) {
        int x0 = viewX0();
        int y0 = viewY0();
        int x1 = viewX1();
        int y1 = viewY1();
        int w = multiblock.width();
        int h = layer < 0 ? multiblock.height() : 1;
        int d = multiblock.depth();
        // Big enough that the structure, turned any way, still fits the page at the nearest zoom of one.
        float across = (float) Math.sqrt(w * w + h * h + d * d);
        float scale = Math.min(x1 - x0, y1 - y0) / across * zoom;
        float centerY = layer < 0 ? multiblock.height() / 2.0f : layer + 0.5f;
        ((GuiGraphicsExtractorAccessor) graphics).hardwrought$renderState().addPicturesInPictureState(
                new MultiblockRenderState(blocks(), yaw, pitch, w / 2.0f, centerY, d / 2.0f, x0, y0, x1, y1, scale));
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int width, Component label, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, BUTTON_HEIGHT);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? BUTTON_HOVERED_SPRITE : BUTTON_SPRITE,
                x, y, width, BUTTON_HEIGHT);
        graphics.text(font, label, x + width / 2 - font.width(label) / 2, y + 5, TEXT_COLOR, false);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // ---------------------------------------------------------------- handling

    /** Up a layer, or down; past the top or the bottom it shows the whole again. */
    public void stepLayer(int direction) {
        int count = multiblock.height();
        // -1 is the whole, 0 .. count-1 the layers: a ring of count + 1 steps.
        layer = Math.floorMod(layer + 1 + direction, count + 1) - 1;
    }

    public int layer() {
        return layer;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) return true;
        if (inside(event.x(), event.y(), leftContentX(), top + CONTROLS_Y, 54, BUTTON_HEIGHT)) {
            onClose();
            return true;
        }
        if (inside(event.x(), event.y(), rightContentX(), top + CONTROLS_Y, ARROW_WIDTH, BUTTON_HEIGHT)) {
            stepLayer(-1);
            return true;
        }
        if (inside(event.x(), event.y(), rightContentX() + CONTENT_WIDTH - ARROW_WIDTH, top + CONTROLS_Y,
                ARROW_WIDTH, BUTTON_HEIGHT)) {
            stepLayer(1);
            return true;
        }
        turning = inside(event.x(), event.y(), viewX0(), viewY0(), viewX1() - viewX0(), viewY1() - viewY0());
        return turning;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!turning) return super.mouseDragged(event, dragX, dragY);
        yaw = (yaw + (float) dragX * 1.5f) % 360.0f;
        pitch = Math.clamp(pitch + (float) dragY * 1.5f, -90.0f, 90.0f);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        turning = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        zoom = Math.clamp(zoom * (scrollY > 0 ? 1.15f : 1 / 1.15f), MIN_ZOOM, MAX_ZOOM);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        switch (event.key()) {
            case InputConstants.KEY_UP, InputConstants.KEY_PAGEUP -> stepLayer(1);
            case InputConstants.KEY_DOWN, InputConstants.KEY_PAGEDOWN -> stepLayer(-1);
            case InputConstants.KEY_LEFT -> yaw -= 15.0f;
            case InputConstants.KEY_RIGHT -> yaw += 15.0f;
            case InputConstants.KEY_BACKSPACE -> onClose();
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    /** Back into the book, on the page it was opened from. */
    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
