package de.ipnats.hardwrought.client.knowledge;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.core.networking.CompendiumPagePayload;
import de.ipnats.hardwrought.knowledge.Compendium;
import de.ipnats.hardwrought.knowledge.CraftingTree;
import de.ipnats.hardwrought.knowledge.FluidIcons;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything that goes into one thing, laid out as a tree: the thing at the top, what it is made of
 * under it, and so on down to what is found rather than made, each with how much of it is needed.
 *
 * <p>The left page adds it up — what is still to be found, and what will be left over — and holds the
 * two controls: how many of the thing are wanted, and whether what the player carries counts. The
 * right page is the tree, dragged to move and zoomed with the wheel. A click on anything in it that can
 * be made more than one way turns to its next recipe, or to treating it as found; the choice is kept
 * for every tree that reaches the same thing.
 */
public class CraftingTreeScreen extends Screen {
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
    private static final int BACK_WIDTH = 54;
    private static final int LINE_HEIGHT = 9;
    private static final int ROW = 18;

    /** A node is a slot with its amount under it; the rows of the tree are this far apart. */
    private static final int SLOT = 18;
    private static final int LEVEL = 38;
    private static final int NODE_MIN_WIDTH = 24;
    private static final int GAP = 4;
    private static final float MIN_ZOOM = 0.3f;
    private static final float MAX_ZOOM = 2.0f;
    /** A press that moves less than this is a click, not a drag. */
    private static final double CLICK_SLOP = 3.0;
    /** How often the tree looks into the inventory again while it is open. */
    private static final long INVENTORY_MILLIS = 1000L;

    private static final int TEXT_COLOR = 0xFF1B120B;
    private static final int FADED_COLOR = 0xFF4A3522;
    private static final int RULE_COLOR = 0x906D5131;
    private static final int LINE_COLOR = 0xFF6D5131;
    private static final int DONE_COLOR = 0xFF2F5A1E;
    private static final int CHOICE_COLOR = 0xFF7A2B22;
    private static final Identifier BOOK_SPRITE = sprite("book");
    private static final Identifier SLOT_SPRITE = sprite("slot");
    private static final Identifier BUTTON_SPRITE = sprite("button");
    private static final Identifier BUTTON_HOVERED_SPRITE = sprite("button_hovered");

    private final Screen parent;
    private final Identifier subject;
    private int left;
    private int top;
    /** How many of the subject are wanted; 0 until the tree has said what one working makes. */
    private long amount;
    private CompendiumPagePayload built;
    private CraftingTree tree;
    private long builtAt;
    private float zoom = 1.0f;
    private float panX;
    private float panY;
    private boolean placed;
    private boolean dragging;
    private double pressX;
    private double pressY;
    private double dragged;
    /** How far the totals on the left page are scrolled, and how tall they were when last drawn. */
    private int scroll;
    private int listHeight;
    /** Where the row with the minus and plus was last drawn. */
    private int amountY;

    public CraftingTreeScreen(Screen parent, Identifier subject) {
        super(Component.translatable("gui.hardwrought.tree"));
        this.parent = parent;
        this.subject = subject;
        CompendiumClient.requestTree(subject);
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath("hardwrought", "compendium/" + name);
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
    }

    private int leftContentX() { return left + LEFT_PAPER_X + CONTENT_MARGIN; }
    private int rightContentX() { return left + RIGHT_PAPER_X + CONTENT_MARGIN; }
    private int viewX0() { return left + RIGHT_PAPER_X + 3; }
    private int viewX1() { return left + RIGHT_PAPER_X + PAPER_WIDTH - 3; }
    private int viewY0() { return top + PAPER_TOP + 3; }
    private int viewY1() { return top + CONTROLS_Y - 3; }

    // ---------------------------------------------------------------- working it out

    /** Works the tree out again where the answer, a choice or the inventory has changed. */
    private void rebuild(boolean force) {
        CompendiumPagePayload page = CompendiumClient.tree();
        if (page == null || !subject.equals(page.subject())) return;
        long now = System.currentTimeMillis();
        boolean stale = CompendiumClient.treeUsesInventory() && now - builtAt > INVENTORY_MILLIS;
        if (!force && page == built && !stale) return;
        built = page;
        builtAt = now;
        tree = new CraftingTree(page, amount, CompendiumClient::treeChoice,
                CompendiumClient.treeUsesInventory() ? inventory() : null);
        if (amount <= 0) amount = tree.root.needed;
        measure(tree.root);
        place(tree.root, 0, 0);
        if (!placed) {
            placed = true;
            fit();
        }
    }

    /** What the player carries, by item. */
    private Map<Identifier, Long> inventory() {
        Map<Identifier, Long> carried = new HashMap<>();
        if (minecraft == null || minecraft.player == null) return carried;
        var inventory = minecraft.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            carried.merge(Compendium.treeKey(stack), (long) stack.getCount(), Long::sum);
        }
        return carried;
    }

    /** Sets the tree so the whole of it fits across the page, at no more than its natural size. */
    private void fit() {
        int room = viewX1() - viewX0() - 8;
        zoom = Math.clamp(room / (float) Math.max(1, tree.root.width), MIN_ZOOM, 1.0f);
        panX = (viewX0() + viewX1()) / 2.0f - (tree.root.width / 2.0f) * zoom;
        panY = viewY0() + 4;
    }

    /** How wide each node's part of the tree is: its own label, or its children side by side. */
    private int measure(CraftingTree.Node node) {
        int own = Math.max(NODE_MIN_WIDTH, font.width(amountLabel(node)) + 4);
        int children = 0;
        for (CraftingTree.Node child : node.children) children += measure(child) + GAP;
        if (!node.children.isEmpty()) children -= GAP;
        node.width = Math.max(own, children);
        return node.width;
    }

    /** Lays a node out over its children, centred; x is where its part of the tree starts. */
    private void place(CraftingTree.Node node, int x, int depth) {
        node.y = depth * LEVEL;
        node.x = x + node.width / 2;
        int children = -GAP;
        for (CraftingTree.Node child : node.children) children += child.width + GAP;
        int at = x + Math.max(0, (node.width - children) / 2);
        for (CraftingTree.Node child : node.children) {
            place(child, at, depth + 1);
            at += child.width + GAP;
        }
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
        rebuild(false);
        button(graphics, leftContentX(), top + CONTROLS_Y, BACK_WIDTH,
                Component.translatable("gui.hardwrought.compendium.back"), mouseX, mouseY);
        button(graphics, leftContentX() + BACK_WIDTH + 4, top + CONTROLS_Y, CONTENT_WIDTH - BACK_WIDTH - 4,
                Component.translatable(CompendiumClient.treeUsesInventory()
                        ? "gui.hardwrought.tree.inventory_on" : "gui.hardwrought.tree.inventory_off"), mouseX, mouseY);
        button(graphics, rightContentX(), top + CONTROLS_Y, CONTENT_WIDTH,
                Component.translatable("gui.hardwrought.tree.recenter"), mouseX, mouseY);
        if (tree == null) {
            graphics.text(font, Component.translatable("gui.hardwrought.compendium.loading"),
                    leftContentX(), top + PAPER_TOP + 5, FADED_COLOR, false);
            return;
        }
        drawTotals(graphics, mouseX, mouseY);
        drawTree(graphics, mouseX, mouseY);
    }

    /** The left page: the name, how many, and what it all adds up to. */
    private void drawTotals(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = leftContentX();
        int y = top + PAPER_TOP + 5;
        y = graphics.textWithWordWrap(font, Component.translatable("gui.hardwrought.tree.title", name(tree.root)),
                x, y, CONTENT_WIDTH, TEXT_COLOR, false) + 3;
        // How many: a minus, the number, a plus; shift steps by ten.
        amountY = y;
        smallButton(graphics, x, y, Component.literal("-"), mouseX, mouseY);
        Component count = Component.translatable("gui.hardwrought.tree.amount", amountText(tree.root, amount));
        graphics.text(font, count, x + 16 + (CONTENT_WIDTH - 32) / 2 - font.width(count) / 2, y + 3, TEXT_COLOR, false);
        smallButton(graphics, x + CONTENT_WIDTH - 14, y, Component.literal("+"), mouseX, mouseY);
        y += 17;
        graphics.fill(x, y, x + CONTENT_WIDTH, y + 1, RULE_COLOR);
        y += 3;

        int listTop = y, listBottom = top + CONTROLS_Y - 3;
        scroll = Math.clamp(scroll, 0, Math.max(0, listHeight - (listBottom - listTop)));
        graphics.enableScissor(x - 1, listTop, x + CONTENT_WIDTH + 1, listBottom);
        int row = listTop - scroll;
        List<CraftingTree.Amount> raw = tree.raw();
        graphics.text(font, Component.translatable(CompendiumClient.treeUsesInventory()
                ? "gui.hardwrought.tree.missing" : "gui.hardwrought.tree.total"), x, row, FADED_COLOR, false);
        row += LINE_HEIGHT + 2;
        if (raw.isEmpty()) {
            row = graphics.textWithWordWrap(font, Component.translatable("gui.hardwrought.tree.all_there"),
                    x, row, CONTENT_WIDTH, DONE_COLOR, false) + 2;
        }
        List<Component> tooltip = null;
        for (CraftingTree.Amount line : raw) {
            if (drawAmount(graphics, line, x, row, listTop, listBottom, mouseX, mouseY)) tooltip = lines(line);
            row += ROW;
        }
        List<CraftingTree.Amount> spare = tree.leftovers();
        if (!spare.isEmpty()) {
            row += 3;
            graphics.text(font, Component.translatable("gui.hardwrought.tree.leftovers"), x, row, FADED_COLOR, false);
            row += LINE_HEIGHT + 2;
            for (CraftingTree.Amount line : spare) {
                if (drawAmount(graphics, line, x, row, listTop, listBottom, mouseX, mouseY)) tooltip = lines(line);
                row += ROW;
            }
        }
        row = graphics.textWithWordWrap(font, Component.translatable("gui.hardwrought.tree.hint"),
                x, row + 4, CONTENT_WIDTH, FADED_COLOR, false);
        graphics.disableScissor();
        listHeight = row + scroll - listTop;
        if (tooltip != null) graphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
    }

    /** One line of a total; true where the pointer is on it. */
    private boolean drawAmount(GuiGraphicsExtractor graphics, CraftingTree.Amount line, int x, int y,
                               int listTop, int listBottom, int mouseX, int mouseY) {
        icon(graphics, line.shown(), x, y);
        Component text = Component.literal(amountText(line.fluid(), line.shown(), line.amount()) + "  ")
                .append(name(line.shown()));
        graphics.text(font, font.substrByWidth(text, CONTENT_WIDTH - 20).getString(), x + 19, y + 4, TEXT_COLOR, false);
        return mouseY >= listTop && mouseY < listBottom && inside(mouseX, mouseY, x, y, CONTENT_WIDTH, ROW - 1);
    }

    private List<Component> lines(CraftingTree.Amount line) {
        FluidIcons.Ingredient fluid = FluidIcons.of(line.shown().stack());
        if (fluid != null) return List.of(fluid.name());
        return ShadowItem.tooltip(line.shown().stack().copyWithCount(1), line.shown().level() > 0);
    }

    /** The right page: the tree itself, moved and scaled. */
    private void drawTree(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.enableScissor(viewX0(), viewY0(), viewX1(), viewY1());
        graphics.pose().pushMatrix();
        graphics.pose().translate(panX, panY);
        graphics.pose().scale(zoom, zoom);
        drawLines(graphics, tree.root);
        boolean overView = inside(mouseX, mouseY, viewX0(), viewY0(), viewX1() - viewX0(), viewY1() - viewY0());
        CraftingTree.Node hovered = overView ? nodeAt(tree.root, treeX(mouseX), treeY(mouseY)) : null;
        drawNodes(graphics, tree.root, hovered);
        graphics.pose().popMatrix();
        graphics.disableScissor();
        if (hovered != null) graphics.setComponentTooltipForNextFrame(font, tooltip(hovered), mouseX, mouseY);
    }

    private void drawLines(GuiGraphicsExtractor graphics, CraftingTree.Node node) {
        if (node.children.isEmpty()) return;
        int from = node.y + SLOT + LINE_HEIGHT;
        int to = node.children.getFirst().y - 2;
        int middle = (from + to) / 2;
        graphics.fill(node.x, from, node.x + 1, middle, LINE_COLOR);
        int first = node.children.getFirst().x, last = node.children.getLast().x;
        graphics.fill(Math.min(first, node.x), middle, Math.max(last, node.x) + 1, middle + 1, LINE_COLOR);
        for (CraftingTree.Node child : node.children) {
            graphics.fill(child.x, middle, child.x + 1, to, LINE_COLOR);
            drawLines(graphics, child);
        }
    }

    private void drawNodes(GuiGraphicsExtractor graphics, CraftingTree.Node node, CraftingTree.Node hovered) {
        int x = node.x - SLOT / 2;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, x, node.y, SLOT, SLOT);
        icon(graphics, node.shown, x + 1, node.y + 1);
        if (node == hovered) graphics.fill(x + 1, node.y + 1, x + SLOT - 1, node.y + SLOT - 1, 0x60FFFFFF);
        // More than one way to make it: a corner turned down, as a reminder that it can be changed.
        if (node.choices > 1 || (node.choices == 1 && node.recipe == null)) {
            graphics.fill(x + SLOT - 4, node.y, x + SLOT, node.y + 1, CHOICE_COLOR);
            graphics.fill(x + SLOT - 3, node.y + 1, x + SLOT, node.y + 2, CHOICE_COLOR);
            graphics.fill(x + SLOT - 2, node.y + 2, x + SLOT, node.y + 3, CHOICE_COLOR);
            graphics.fill(x + SLOT - 1, node.y + 3, x + SLOT, node.y + 4, CHOICE_COLOR);
        }
        String label = amountLabel(node);
        boolean covered = node != tree.root && node.missing() <= 0;
        graphics.text(font, label, node.x - font.width(label) / 2 + 1, node.y + SLOT + 1,
                covered ? DONE_COLOR : TEXT_COLOR, false);
        for (CraftingTree.Node child : node.children) drawNodes(graphics, child, hovered);
    }

    private void icon(GuiGraphicsExtractor graphics, CompendiumPagePayload.Known shown, int x, int y) {
        FluidIcons.Ingredient fluid = FluidIcons.of(shown.stack());
        if (fluid != null) FluidIconRenderer.render(graphics, fluid, x, y);
        else ShadowItem.render(graphics, shown.stack().copyWithCount(1), x, y, shown.level() > 0);
    }

    /** What a node's label says: how much, ticked where the inventory and leftovers cover it. */
    private String amountLabel(CraftingTree.Node node) {
        String needed = amountText(node.fluid, node.shown, node.needed);
        return node != tree.root && node.needed > 0 && node.missing() <= 0 ? "✔" + needed : needed;
    }

    private List<Component> tooltip(CraftingTree.Node node) {
        List<Component> lines = new ArrayList<>();
        lines.add(name(node.shown));
        lines.add(Component.translatable("gui.hardwrought.tree.needed", amountText(node.fluid, node.shown, node.needed))
                .withStyle(ChatFormatting.GRAY));
        if (node.fromInventory > 0) {
            lines.add(Component.translatable("gui.hardwrought.tree.from_inventory",
                    amountText(node.fluid, node.shown, node.fromInventory)).withStyle(ChatFormatting.DARK_GREEN));
        }
        if (node.fromLeftover > 0) {
            lines.add(Component.translatable("gui.hardwrought.tree.from_leftover",
                    amountText(node.fluid, node.shown, node.fromLeftover)).withStyle(ChatFormatting.DARK_GREEN));
        }
        if (node.recipe != null) {
            lines.add(Component.translatable("gui.hardwrought.tree.recipe", node.choice + 1, node.choices)
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.hardwrought.tree.station", station(node.recipe))
                    .withStyle(ChatFormatting.GRAY));
            if (node.batches > 0) {
                lines.add(Component.translatable("gui.hardwrought.tree.batches", node.batches,
                        amountText(node.fluid, node.shown, node.perBatch)).withStyle(ChatFormatting.GRAY));
            }
            if (node.leftover > 0) {
                lines.add(Component.translatable("gui.hardwrought.tree.leftover",
                        amountText(node.fluid, node.shown, node.leftover)).withStyle(ChatFormatting.GRAY));
            }
        } else if (node.loop) {
            lines.add(Component.translatable("gui.hardwrought.tree.loop").withStyle(ChatFormatting.GRAY));
        } else if (node.choices > 0) {
            lines.add(Component.translatable("gui.hardwrought.tree.as_raw").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("gui.hardwrought.tree.no_recipe").withStyle(ChatFormatting.GRAY));
        }
        if (node.choices > 1 || (node.choices == 1 && node.recipe == null)) {
            lines.add(Component.translatable("gui.hardwrought.tree.hint_choose").withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.translatable("gui.hardwrought.tree.hint_open").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /** Where a recipe is worked: its station, or the player's own hands. */
    private static Component station(CompendiumPagePayload.Recipe recipe) {
        if (recipe.station().isEmpty()) return Component.translatable("gui.hardwrought.tree.by_hand");
        CompendiumPagePayload.Known station = Compendium.shownOption(recipe.station());
        return station.level() > 0 ? station.stack().getHoverName() : ShadowItem.UNKNOWN_NAME;
    }

    private static Component name(CraftingTree.Node node) {
        return name(node.shown);
    }

    private static Component name(CompendiumPagePayload.Known shown) {
        FluidIcons.Ingredient fluid = FluidIcons.of(shown.stack());
        if (fluid != null) return fluid.name();
        return shown.level() > 0 ? shown.stack().getHoverName() : ShadowItem.UNKNOWN_NAME;
    }

    private static String amountText(CraftingTree.Node node, long amount) {
        return amountText(node.fluid, node.shown, amount);
    }

    /** An amount as short as it can be said: 12, 1.5k, 250 mB, 8 units. */
    private static String amountText(boolean fluid, CompendiumPagePayload.Known shown, long amount) {
        String number = amount >= 100_000 ? (amount / 1000) + "k"
                : amount >= 10_000 ? String.format(java.util.Locale.ROOT, "%.1fk", amount / 1000.0) : Long.toString(amount);
        if (!fluid) return number;
        FluidIcons.Ingredient ingredient = FluidIcons.of(shown.stack());
        boolean gas = ingredient != null && ingredient.gas();
        return Component.translatable(gas ? "gui.hardwrought.tree.gas" : "gui.hardwrought.tree.fluid", number).getString();
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int width, Component label, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, BUTTON_HEIGHT);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? BUTTON_HOVERED_SPRITE : BUTTON_SPRITE,
                x, y, width, BUTTON_HEIGHT);
        graphics.text(font, label, x + width / 2 - font.width(label) / 2, y + 5, TEXT_COLOR, false);
    }

    private void smallButton(GuiGraphicsExtractor graphics, int x, int y, Component label, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, 14, 14);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? BUTTON_HOVERED_SPRITE : BUTTON_SPRITE, x, y, 14, 14);
        graphics.text(font, label, x + 7 - font.width(label) / 2 + 1, y + 3, TEXT_COLOR, false);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // ---------------------------------------------------------------- handling

    private double treeX(double mouseX) {
        return (mouseX - panX) / zoom;
    }

    private double treeY(double mouseY) {
        return (mouseY - panY) / zoom;
    }

    private static CraftingTree.Node nodeAt(CraftingTree.Node node, double x, double y) {
        if (x >= node.x - SLOT / 2.0 && x < node.x + SLOT / 2.0 && y >= node.y && y < node.y + SLOT) return node;
        for (CraftingTree.Node child : node.children) {
            CraftingTree.Node found = nodeAt(child, x, y);
            if (found != null) return found;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) return true;
        if (inside(event.x(), event.y(), leftContentX(), top + CONTROLS_Y, BACK_WIDTH, BUTTON_HEIGHT)) {
            onClose();
            return true;
        }
        if (inside(event.x(), event.y(), leftContentX() + BACK_WIDTH + 4, top + CONTROLS_Y,
                CONTENT_WIDTH - BACK_WIDTH - 4, BUTTON_HEIGHT)) {
            CompendiumClient.setTreeUsesInventory(!CompendiumClient.treeUsesInventory());
            rebuild(true);
            return true;
        }
        if (inside(event.x(), event.y(), rightContentX(), top + CONTROLS_Y, CONTENT_WIDTH, BUTTON_HEIGHT)) {
            if (tree != null) fit();
            return true;
        }
        if (tree != null) {
            int step = event.hasShiftDown() ? 10 : 1;
            int y = amountY;
            if (inside(event.x(), event.y(), leftContentX(), y, 14, 14)) {
                setAmount(amount - step);
                return true;
            }
            if (inside(event.x(), event.y(), leftContentX() + CONTENT_WIDTH - 14, y, 14, 14)) {
                setAmount(amount + step);
                return true;
            }
        }
        dragging = inside(event.x(), event.y(), viewX0(), viewY0(), viewX1() - viewX0(), viewY1() - viewY0());
        pressX = event.x();
        pressY = event.y();
        dragged = 0;
        return dragging;
    }

    private void setAmount(long wanted) {
        amount = Math.clamp(wanted, 1, 100_000);
        rebuild(true);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!dragging) return super.mouseDragged(event, dragX, dragY);
        panX += (float) dragX;
        panY += (float) dragY;
        dragged += Math.abs(dragX) + Math.abs(dragY);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean wasDragging = dragging;
        dragging = false;
        if (wasDragging && dragged < CLICK_SLOP && tree != null) {
            CraftingTree.Node node = nodeAt(tree.root, treeX(pressX), treeY(pressY));
            if (node != null) {
                clicked(node, event);
                return true;
            }
        }
        return super.mouseReleased(event);
    }

    /**
     * A click on a thing in the tree: shift opens its page in the book; otherwise it turns to its next
     * recipe, or with the right button its last, with treating it as found as one more step of the round.
     */
    private void clicked(CraftingTree.Node node, MouseButtonEvent event) {
        if (event.hasShiftDown()) {
            minecraft.gui.setScreen(parent);
            CompendiumClient.openRecipes(node.key);
            return;
        }
        if (node.choices == 0) return;
        int direction = event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? -1 : 1;
        // -1 is found, 0 .. choices-1 the recipes: a ring of choices + 1 steps.
        int next = Math.floorMod(node.choice + 1 + direction, node.choices + 1) - 1;
        CompendiumClient.chooseInTree(node.key, next);
        rebuild(true);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (mouseX < left + RIGHT_PAPER_X) {
            scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * ROW);
            return true;
        }
        // Zoomed about the pointer, so what is under it stays under it.
        double beforeX = treeX(mouseX), beforeY = treeY(mouseY);
        zoom = Math.clamp(zoom * (scrollY > 0 ? 1.15f : 1 / 1.15f), MIN_ZOOM, MAX_ZOOM);
        panX = (float) (mouseX - beforeX * zoom);
        panY = (float) (mouseY - beforeY * zoom);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_BACKSPACE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
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
