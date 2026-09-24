package de.ipnats.hardwrought.client.knowledge;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.core.networking.CompendiumPagePayload;
import de.ipnats.hardwrought.knowledge.Journal;
import de.ipnats.hardwrought.knowledge.KnowledgeCategory;
import de.ipnats.hardwrought.knowledge.LootSources;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The compendium: the book a player keeps, in two halves.
 *
 * <p>Opening it lands on the front page, where the halves are chosen between. <b>Knowledge</b> is
 * the recipe browser — shelves on the left, a search field, R for how a thing is made and U for what
 * it is used in, click an ingredient to walk to it, backspace to walk back. Its one departure from
 * every other browser is that an undiscovered entry is drawn as its own black shadow and named with
 * question marks, so the shape of what is still ahead stays visible without giving it away.
 *
 * <p><b>Thoughts</b> is the written half: a short chain of notes that say what the next problem is
 * and roughly where its answer lies. It exists because the browser cannot help with a thing the
 * player has never heard of, and it holds to the same bargain — a note that has not occurred to the
 * player yet is a page of unreadable scrawl rather than an absent one.
 */
public class CompendiumScreen extends Screen {
    /**
     * The front page. Client-side only and never sent: it says the same thing to every player, and a
     * round trip before the book so much as appears would be felt on a poor connection.
     */
    public static final int MODE_HOME = -1;

    private static final int PANEL_WIDTH = 344;
    private static final int PANEL_HEIGHT = 204;

    // The paper as it is painted in book.png. Every position on a page is worked out from these and
    // nothing else, so a redrawn book needs these five numbers changed and no others. Laying the
    // pages out from the panel's own width instead put both of them six pixels into the spine.
    private static final int LEFT_PAPER_X = 13;
    private static final int RIGHT_PAPER_X = 186;
    private static final int PAPER_WIDTH = 145;
    private static final int PAPER_TOP = 13;
    private static final int PAPER_BOTTOM = 190;

    private static final int CONTENT_MARGIN = 7;
    private static final int CONTENT_WIDTH = PAPER_WIDTH - CONTENT_MARGIN * 2;
    private static final int BUTTON_HEIGHT = 18;
    /** The row the back button and the page arrows sit on, inside the paper's bottom edge. */
    private static final int CONTROLS_Y = PAPER_BOTTOM - 4 - BUTTON_HEIGHT;
    /** Nothing on a page is drawn below this, so it can never run into the controls. */
    private static final int CONTENT_BOTTOM = CONTROLS_Y - 4;

    private static final int TAB_WIDTH = CONTENT_WIDTH - 3;
    private static final int TAB_HEIGHT = 14;
    private static final int TABS_TOP = PAPER_TOP + 9;
    private static final int SLOT = 18;
    private static final int GRID_STEP = 21;
    private static final int GRID_COLUMNS = 6;
    private static final int SEARCH_TOP = PAPER_TOP + 2;
    private static final int SHELF_HEADING_TOP = SEARCH_TOP + 23;
    private static final int GRID_TOP = SHELF_HEADING_TOP + 13;
    /** As many rows as fit between the heading and the controls. */
    private static final int GRID_ROWS = (CONTENT_BOTTOM - GRID_TOP - SLOT) / GRID_STEP + 1;
    private static final int PER_PAGE = GRID_COLUMNS * GRID_ROWS;
    private static final int SOURCE_HEIGHT = 18;
    private static final int SOURCES_TOP = PAPER_TOP + 32;
    private static final int SOURCE_ROWS = (CONTENT_BOTTOM - SOURCES_TOP) / SOURCE_HEIGHT;
    private static final int METHOD_TAB_SIZE = 24;
    private static final int METHOD_COUNT = 6;
    /** Recipe cards start under the bookmarks and stack until the page is full. */
    private static final int CARDS_TOP = 36;
    private static final int CARD_PADDING = 3;
    private static final int CARD_GAP = 4;
    private static final int LINE_HEIGHT = 9;
    private static final int JOURNAL_TEXT_TOP = PAPER_TOP + 47;
    private static final int JOURNAL_LINE = 10;
    private static final int JOURNAL_LINES = (CONTENT_BOTTOM - JOURNAL_TEXT_TOP) / JOURNAL_LINE;
    /** How long one option of a tag ingredient is shown before the next one takes its turn. */
    private static final long CYCLE_MILLIS = 1000L;

    /** The two halves on the front page, one centred on each page. */
    private static final int HALF_WIDTH = 130;
    private static final int HALF_HEIGHT = 142;
    private static final int HALF_TOP = PAPER_TOP + (PAPER_BOTTOM - PAPER_TOP - HALF_HEIGHT) / 2;

    private static final Identifier BOOK_SPRITE = sprite("book");
    private static final Identifier SLOT_SPRITE = sprite("slot");
    private static final Identifier HALF_SPRITE = sprite("half");
    private static final Identifier HALF_HOVERED_SPRITE = sprite("half_hovered");
    private static final Identifier TAB_SPRITE = sprite("tab");
    private static final Identifier TAB_SELECTED_SPRITE = sprite("tab_selected");
    private static final Identifier CARD_SPRITE = sprite("card");
    private static final Identifier KNOWLEDGE_EMBLEM_SPRITE = sprite("emblem_knowledge");
    private static final Identifier THOUGHTS_EMBLEM_SPRITE = sprite("emblem_thoughts");
    private static final Identifier FOLLOWED_SPRITE = sprite("followed");
    private static final Identifier BUTTON_SPRITE = sprite("button");
    private static final Identifier BUTTON_HOVERED_SPRITE = sprite("button_hovered");
    private static final Identifier SEARCH_SPRITE = sprite("search");
    private static final Identifier METHOD_TAB_SPRITE = sprite("method_tab");
    private static final Identifier METHOD_TAB_SELECTED_SPRITE = sprite("method_tab_selected");
    private static final ItemStack[] METHOD_ICONS = {
            new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.CHEST),
            new ItemStack(Items.CAMPFIRE), new ItemStack(Items.SMITHING_TABLE),
            new ItemStack(Items.FURNACE), new ItemStack(Items.OAK_LOG)
    };

    private static final int TEXT_COLOR = 0xFF1B120B;
    private static final int FADED_COLOR = 0xFF4A3522;
    private static final int RULE_COLOR = 0x906D5131;
    /** Muted enough to mark a note as followed without turning the page into a checklist. */
    private static final int DONE_COLOR = 0xFF445128;
    /** Dark enough to read as ink on the page rather than as a second paragraph. */
    private static final int SCRAWL_COLOR = 0xFF483B2D;

    /** Drawn under an unread note: real glyph shapes, shuffled every frame, saying nothing. */
    private static final String SCRAWL = "mnwwmnwmmwnwmmnwwmnwmwnmmwwnmwnmwwn";

    private final Deque<View> history = new ArrayDeque<>();
    /** Rebuilt every frame so a click can reuse exactly what was drawn. */
    private final List<Hotspot> hotspots = new ArrayList<>();
    private int mode;
    private Identifier subject;
    private EditBox search;
    /** What is in the search field, kept apart from the widget so a mode change cannot lose it. */
    private String searchText = "";
    private int page;
    private int selectedMethod = CompendiumPagePayload.METHOD_CRAFTING;
    private boolean methodChosen;
    private int lastMouseX;
    private int lastMouseY;
    private int left;
    private int top;
    /** Set while the search field is being written to by the browser rather than by the player. */
    private boolean quietSearch;
    /** Cleared whenever a new journal is asked for, so the book opens on the note in hand once. */
    private boolean journalOpened;

    public CompendiumScreen(int mode, Identifier subject) {
        super(Component.translatable("gui.hardwrought.compendium"));
        this.mode = mode;
        this.subject = subject;
    }

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath("hardwrought", "compendium/" + name);
    }

    private int leftPageX() { return left + LEFT_PAPER_X; }
    private int rightPageX() { return left + RIGHT_PAPER_X; }
    private int leftContentX() { return leftPageX() + CONTENT_MARGIN; }
    private int rightContentX() { return rightPageX() + CONTENT_MARGIN; }

    /** One place the browser has been, so backspace can return to it. */
    private record View(int mode, Identifier subject, String search, int page) { }

    /**
     * One drawn stack and where it ended up, so a click knows what it landed on. A stand-in icon —
     * the egg for a mob, the chest for a loot table — carries its own tooltip and leads nowhere,
     * because the thing it stands for is not an item.
     */
    private record Hotspot(int x, int y, ItemStack stack, boolean known, List<Component> tooltip,
                           boolean navigable) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
        }

        List<Component> lines() {
            return tooltip != null ? tooltip : ShadowItem.tooltip(stack, known);
        }
    }

    /** Called when something else has already sent the request for a new page. */
    public void showing(int mode, Identifier subject) {
        remember();
        this.mode = mode;
        this.subject = subject;
        this.page = 0;
        if (mode == CompendiumPagePayload.MODE_RECIPES || mode == CompendiumPagePayload.MODE_USAGES) {
            selectedMethod = CompendiumPagePayload.METHOD_CRAFTING;
            methodChosen = false;
        }
        this.journalOpened = false;
        setSearchQuietly("");
        rebuildWidgets();
    }

    /** Walks back up to the front page from wherever the book is currently open. */
    public void showHome() {
        if (mode == MODE_HOME) return;
        remember();
        mode = MODE_HOME;
        page = 0;
        setSearchQuietly("");
        rebuildWidgets();
    }

    private void remember() {
        history.push(new View(mode, subject, searchText, page));
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        search = null;
        if (mode == MODE_HOME) return;

        if (mode == CompendiumPagePayload.MODE_SHELF) {
            search = new EditBox(font, rightContentX() + 4, top + SEARCH_TOP + 4, CONTENT_WIDTH - 8, 14,
                    Component.translatable("gui.hardwrought.compendium.search"));
            search.setMaxLength(48);
            search.setBordered(false);
            search.setTextColor(TEXT_COLOR);
            search.setTextColorUneditable(FADED_COLOR);
            search.setHint(Component.translatable("gui.hardwrought.compendium.search"));
            search.setValue(searchText);
            search.setResponder(text -> {
                searchText = text;
                if (!quietSearch && mode == CompendiumPagePayload.MODE_SHELF) {
                    page = 0;
                    CompendiumClient.refresh(mode, subject, text);
                }
            });
            addRenderableWidget(search);
        }
    }

    private void setSearchQuietly(String text) {
        searchText = text;
        if (search == null) return;
        quietSearch = true;
        search.setValue(text);
        quietSearch = false;
    }

    /**
     * One step back. The front page is the floor of the book rather than a dead end, so backspace on
     * a page nothing led to still goes somewhere.
     */
    private void goBack() {
        if (history.isEmpty()) {
            showHome();
            return;
        }
        View view = history.pop();
        mode = view.mode();
        subject = view.subject();
        page = view.page();
        // Walking back is the one case where the note to open on is already known: it is the one the
        // player left, not the one they are working towards.
        journalOpened = mode == CompendiumPagePayload.MODE_JOURNAL;
        setSearchQuietly(view.search());
        if (mode != MODE_HOME) CompendiumClient.refresh(mode, subject, view.search());
        rebuildWidgets();
    }

    private void turnPage(int direction) {
        int pages = pageCount();
        page = Math.floorMod(page + direction, Math.max(1, pages));
    }

    private int pageCount() {
        if (mode == MODE_HOME) return 1;
        CompendiumPagePayload current = CompendiumClient.page();
        if (current == null) return 1;
        if (mode == CompendiumPagePayload.MODE_JOURNAL) {
            return Math.max(1, current.journal().size());
        }
        if (mode == CompendiumPagePayload.MODE_SHELF) {
            return Math.max(1, (current.entries().size() + PER_PAGE - 1) / PER_PAGE);
        }
        int method = effectiveMethod(current);
        if (method == CompendiumPagePayload.METHOD_LOOT) {
            return Math.max(1, (current.sources().size() + SOURCE_ROWS * 2 - 1) / (SOURCE_ROWS * 2));
        }
        return Math.max(1, recipePages(recipesFor(current, method)).size());
    }

    private List<CompendiumPagePayload.Recipe> recipesFor(CompendiumPagePayload current, int method) {
        return current.recipes().stream().filter(recipe -> recipe.method() == method).toList();
    }

    private boolean methodAvailable(CompendiumPagePayload current, int method) {
        return method == CompendiumPagePayload.METHOD_LOOT
                ? !current.sources().isEmpty() : !recipesFor(current, method).isEmpty();
    }

    /** A bookmark is useful only when there is something behind it. */
    private List<Integer> availableMethods(CompendiumPagePayload current) {
        if (current == null) return List.of();
        List<Integer> methods = new ArrayList<>();
        for (int method = 0; method < METHOD_COUNT; method++) {
            if (methodAvailable(current, method)) methods.add(method);
        }
        return methods;
    }

    private int effectiveMethod(CompendiumPagePayload current) {
        if (methodChosen) return selectedMethod;
        if (methodAvailable(current, selectedMethod)) return selectedMethod;
        for (int method = 0; method < METHOD_COUNT; method++) {
            if (methodAvailable(current, method)) return method;
        }
        return selectedMethod;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        // Authored at its actual screen size: no repeated or squeezed paper pattern.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BOOK_SPRITE,
                left, top, PANEL_WIDTH, PANEL_HEIGHT);
        if (mode == CompendiumPagePayload.MODE_SHELF) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SEARCH_SPRITE,
                    rightContentX(), top + SEARCH_TOP, CONTENT_WIDTH, 20);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hotspots.clear();
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (mode == MODE_HOME) {
            drawHome(graphics, mouseX, mouseY);
            return;
        }
        CompendiumPagePayload current = CompendiumClient.page();
        drawBookControls(graphics, mouseX, mouseY);
        if (mode == CompendiumPagePayload.MODE_JOURNAL) {
            ink(graphics, Component.translatable("gui.hardwrought.compendium.journal"),
                    leftContentX(), top + PAPER_TOP + 5, FADED_COLOR);
            if (current == null) {
                ink(graphics, Component.translatable("gui.hardwrought.compendium.loading"),
                        leftContentX(), top + JOURNAL_TEXT_TOP, FADED_COLOR);
            } else {
                drawJournal(graphics, current);
            }
        } else {
            drawTabs(graphics, mouseX, mouseY);
            if (mode != CompendiumPagePayload.MODE_SHELF) drawMethodTabs(graphics, current, mouseX, mouseY);
            int headingX = mode == CompendiumPagePayload.MODE_SHELF ? rightContentX() : leftContentX();
            int headingY = top + (mode == CompendiumPagePayload.MODE_SHELF ? SHELF_HEADING_TOP : TABS_TOP);
            wrapInk(graphics, heading(), headingX, headingY, CONTENT_WIDTH, TEXT_COLOR);
            if (mode != CompendiumPagePayload.MODE_SHELF && current != null
                    && effectiveMethod(current) != CompendiumPagePayload.METHOD_LOOT) {
                int below = headingY + font.split(heading(), CONTENT_WIDTH).size() * LINE_HEIGHT + 6;
                drawProperties(graphics, current, headingX, below);
            }
            if (current == null) {
                ink(graphics, Component.translatable("gui.hardwrought.compendium.loading"),
                        headingX, top + CARDS_TOP + 4, FADED_COLOR);
            } else if (mode == CompendiumPagePayload.MODE_SHELF) {
                drawShelf(graphics, current);
            } else {
                drawRecipes(graphics, current);
            }
        }
        if (pageCount() > 1) {
            centeredInk(graphics, Component.translatable(
                            mode == CompendiumPagePayload.MODE_JOURNAL
                                    ? "gui.hardwrought.compendium.note" : "gui.hardwrought.compendium.page",
                            page + 1, pageCount()),
                    // Level with the middle of the page buttons it sits between, not below them on
                    // the edge of the cover.
                    rightPageX() + PAPER_WIDTH / 2, top + CONTROLS_Y + 5, FADED_COLOR);
        }
        drawTooltip(graphics, mouseX, mouseY);
    }

    private void drawBookControls(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawBookButton(graphics, leftContentX(), top + CONTROLS_Y, 54, 18,
                Component.translatable("gui.hardwrought.compendium.back"), mouseX, mouseY);
        if (pageCount() > 1) {
            drawBookButton(graphics, rightContentX(), top + CONTROLS_Y, 22, 18,
                    Component.literal("‹"), mouseX, mouseY);
            drawBookButton(graphics, rightContentX() + CONTENT_WIDTH - 22,
                    top + CONTROLS_Y, 22, 18, Component.literal("›"), mouseX, mouseY);
        }
    }

    private void drawBookButton(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                Component label, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                hovered ? BUTTON_HOVERED_SPRITE : BUTTON_SPRITE, x, y, width, height);
        centeredInk(graphics, label, x + width / 2, y + 5, TEXT_COLOR);
    }

    private void drawMethodTabs(GuiGraphicsExtractor graphics, CompendiumPagePayload current,
                                int mouseX, int mouseY) {
        List<Integer> methods = availableMethods(current);
        if (methods.isEmpty()) return;
        int start = rightPageX() + (PAPER_WIDTH - METHOD_TAB_SIZE * methods.size()) / 2;
        int active = current == null ? selectedMethod : effectiveMethod(current);
        for (int index = 0; index < methods.size(); index++) {
            int method = methods.get(index);
            int x = start + index * METHOD_TAB_SIZE;
            int y = top + (method == active ? 8 : 10);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    method == active ? METHOD_TAB_SELECTED_SPRITE : METHOD_TAB_SPRITE,
                    x, y, METHOD_TAB_SIZE, METHOD_TAB_SIZE);
            graphics.item(METHOD_ICONS[method], x + 4, y + 3);
            if (inside(mouseX, mouseY, x, y, METHOD_TAB_SIZE, METHOD_TAB_SIZE)) {
                graphics.setComponentTooltipForNextFrame(font,
                        List.of(Component.translatable(methodKey(method))), mouseX, mouseY);
            }
        }
    }

    private static String methodKey(int method) {
        String[] names = {"crafting", "loot", "cooking", "smithing", "smelting", "in_world"};
        return "gui.hardwrought.compendium.method." + names[Math.clamp(method, 0, names.length - 1)];
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // ---------------------------------------------------------------- the front page

    /**
     * The two halves of the book, and nothing else. Neither half is an item, so neither gets an item
     * icon: a charcoal grid and a page of handwriting say what each one is faster than a picture of
     * a book would. Both are authored sprites so their uneven strokes match the handmade pages.
     */
    private void drawHome(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawHalf(graphics, mouseX, mouseY, homeLeft(0), "knowledge", false);
        drawHalf(graphics, mouseX, mouseY, homeLeft(1), "journal", true);
    }

    /** Where one of the two halves starts: each is centred on its own page. */
    private int homeLeft(int half) {
        return (half == 0 ? leftPageX() : rightPageX()) + (PAPER_WIDTH - HALF_WIDTH) / 2;
    }

    private void drawHalf(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, String name,
                          boolean written) {
        int y = top + HALF_TOP;
        boolean hovered = overHalf(x, mouseX, mouseY);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                hovered ? HALF_HOVERED_SPRITE : HALF_SPRITE, x, y, HALF_WIDTH, HALF_HEIGHT);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                written ? THOUGHTS_EMBLEM_SPRITE : KNOWLEDGE_EMBLEM_SPRITE,
                x + HALF_WIDTH / 2 - 16, y + 18, 32, 32);
        centeredInk(graphics, Component.translatable("gui.hardwrought.compendium." + name),
                x + HALF_WIDTH / 2, y + 58, TEXT_COLOR);
        wrapInk(graphics,
                Component.translatable("gui.hardwrought.compendium." + name + ".about"),
                x + 10, y + 76, HALF_WIDTH - 20, FADED_COLOR);
    }

    private boolean overHalf(int x, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + HALF_WIDTH
                && mouseY >= top + HALF_TOP && mouseY < top + HALF_TOP + HALF_HEIGHT;
    }

    // ---------------------------------------------------------------- the written half

    /**
     * One note, filling the page the way a page of a book does. The chain is walked with the same
     * arrows that turn a shelf, because it is the same gesture and a player should not have to learn
     * a second one.
     */
    private void drawJournal(GuiGraphicsExtractor graphics, CompendiumPagePayload current) {
        List<CompendiumPagePayload.Note> notes = current.journal();
        if (notes.isEmpty()) {
            ink(graphics, Component.translatable("gui.hardwrought.compendium.no_notes"),
                    leftContentX(), top + JOURNAL_TEXT_TOP, FADED_COLOR);
            return;
        }
        landOnCurrentNote(notes);
        CompendiumPagePayload.Note note = notes.get(Math.min(page, notes.size() - 1));
        boolean unread = note.state() == Journal.HIDDEN;
        int textLeft = leftContentX();
        Item subjectItem = note.subject() == null ? null : BuiltInRegistries.ITEM.getValue(note.subject());
        if (!note.parts().isEmpty()) {
            // A thought that takes several things together shows every one of them, each as itself
            // once held and as a shadow until then, along the top of the facing page.
            int partY = top + PAPER_TOP + 17;
            for (int index = 0; index < note.parts().size(); index++) {
                Item part = BuiltInRegistries.ITEM.getValue(note.parts().get(index).id());
                if (part == null || part == Items.AIR) continue;
                int partX = rightContentX() + 1 + index * GRID_STEP;
                ItemStack stack = new ItemStack(part);
                boolean held = note.parts().get(index).held();
                drawSlotBackground(graphics, partX, partY);
                ShadowItem.render(graphics, stack, partX, partY, held);
                hotspots.add(new Hotspot(partX, partY, stack, held, null, true));
            }
        } else if (subjectItem != null) {
            // Drawn by the same rule as everything else in the book: the thing a note is pointing at
            // is a black shadow until the player has held it, and its own icon afterwards.
            ItemStack stack = new ItemStack(subjectItem);
            boolean known = note.state() == Journal.DONE;
            drawSlotBackground(graphics, leftContentX(), top + PAPER_TOP + 17);
            ShadowItem.render(graphics, stack, leftContentX(), top + PAPER_TOP + 17, known);
            hotspots.add(new Hotspot(leftContentX(), top + PAPER_TOP + 17, stack, known, null, true));
            textLeft = leftContentX() + 22;
        }
        // A title longer than the room beside the icon takes a second line rather than running off
        // the page; one that would need a third is cut short.
        int titleColor = note.state() == Journal.DONE ? DONE_COLOR : TEXT_COLOR;
        List<FormattedCharSequence> title = unread ? List.of(scrawl(18).getVisualOrderText())
                : font.split(Component.translatable(titleKey(note.id())), leftContentX() + CONTENT_WIDTH - textLeft);
        if (title.size() == 1) {
            ink(graphics, title.getFirst(), textLeft, top + PAPER_TOP + 21, titleColor);
        } else {
            ink(graphics, title.get(0), textLeft, top + PAPER_TOP + 16, titleColor);
            ink(graphics, title.size() > 2 ? ellipsis(title.get(1), leftContentX() + CONTENT_WIDTH - textLeft)
                    : title.get(1), textLeft, top + PAPER_TOP + 26, titleColor);
        }
        int rule = top + JOURNAL_TEXT_TOP - 8;
        graphics.fill(leftContentX(), rule, leftContentX() + CONTENT_WIDTH, rule + 1, RULE_COLOR);
        graphics.fill(rightContentX(), rule, rightContentX() + CONTENT_WIDTH, rule + 1, RULE_COLOR);
        int wrap = CONTENT_WIDTH;
        if (unread) {
            // Not a blank page. The player is meant to see that there is more written ahead, only
            // not to read it — the same bargain the black shadows on the knowledge side strike.
            int y = wrapInk(graphics,
                    Component.translatable("gui.hardwrought.compendium.unread"),
                    leftContentX(), top + JOURNAL_TEXT_TOP, wrap, FADED_COLOR);
            for (int line = 0; line < 4; line++) {
                ink(graphics, scrawl(26 + line % 3 * 4), leftContentX(), y + 10 + line * 12,
                        SCRAWL_COLOR);
            }
            return;
        }
        List<FormattedCharSequence> lines = font.split(Component.translatable(textKey(note.id())), wrap);
        int linesPerPage = JOURNAL_LINES;
        for (int index = 0; index < Math.min(lines.size(), linesPerPage * 2); index++) {
            int x = index < linesPerPage ? leftContentX() : rightContentX();
            ink(graphics, lines.get(index), x, top + JOURNAL_TEXT_TOP + index % linesPerPage * JOURNAL_LINE,
                    TEXT_COLOR);
        }
        if (note.state() == Journal.DONE) {
            // Wrapped beside its tick, and kept on one page: a mark split across the spine would
            // read as two things.
            List<FormattedCharSequence> followed = font.split(
                    Component.translatable("gui.hardwrought.compendium.followed"), CONTENT_WIDTH - 12);
            int followedIndex = Math.min(lines.size() + 1, linesPerPage * 2 - followed.size());
            if (followedIndex < linesPerPage && followedIndex + followed.size() > linesPerPage) {
                followedIndex = linesPerPage;
            }
            int followedX = followedIndex < linesPerPage ? leftContentX() : rightContentX();
            int y = top + JOURNAL_TEXT_TOP + followedIndex % linesPerPage * JOURNAL_LINE;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FOLLOWED_SPRITE, followedX, y, 9, 9);
            for (FormattedCharSequence line : followed) {
                ink(graphics, line, followedX + 12, y, DONE_COLOR);
                y += JOURNAL_LINE;
            }
        }
    }

    /** Writing that is there without being readable: the font shuffles it anew every frame. */
    private static Component scrawl(int length) {
        return Component.literal(SCRAWL.substring(0, Math.min(length, SCRAWL.length())))
                .withStyle(ChatFormatting.OBFUSCATED);
    }

    /**
     * Opens the written half on the note the player is actually on rather than at the beginning.
     * Once per page the server sends, so the arrows keep working afterwards.
     */
    private void landOnCurrentNote(List<CompendiumPagePayload.Note> notes) {
        if (journalOpened) return;
        journalOpened = true;
        for (int index = 0; index < notes.size(); index++) {
            if (notes.get(index).state() != Journal.DONE) {
                page = index;
                return;
            }
        }
        page = notes.size() - 1;
    }

    private static String titleKey(Identifier id) {
        return "journal.hardwrought." + id.getPath() + ".title";
    }

    private static String textKey(Identifier id) {
        return "journal.hardwrought." + id.getPath() + ".text";
    }

    // ---------------------------------------------------------------- the knowledge half

    private Component heading() {
        if (mode == CompendiumPagePayload.MODE_SHELF) {
            return Component.translatable(CompendiumClient.shelf().translationKey());
        }
        CompendiumPagePayload current = CompendiumClient.page();
        Component name = ShadowItem.UNKNOWN_NAME;
        if (current != null && !current.entries().isEmpty() && current.entries().getFirst().level() > 0) {
            Item item = BuiltInRegistries.ITEM.getValue(subject);
            if (item != null) name = new ItemStack(item).getHoverName();
        }
        String key = mode == CompendiumPagePayload.MODE_RECIPES
                ? "gui.hardwrought.compendium.recipes_for" : "gui.hardwrought.compendium.usages_of";
        return Component.translatable(key, name);
    }

    /**
     * What the subject is like, under its name on the left page: for now how hot it has to get
     * before it melts, and which furnace gets it there. The number is only given once the thing has
     * been studied, the same rule the rest of the book keeps for properties; before that the line is
     * there with question marks in it, so the player knows there is something to find out.
     */
    private void drawProperties(GuiGraphicsExtractor graphics, CompendiumPagePayload current, int x, int y) {
        Double melting = de.ipnats.hardwrought.client.survival.MeltingPointTooltip.meltingPoint(subject);
        if (melting == null) return;
        boolean studied = !current.entries().isEmpty()
                && current.entries().getFirst().level() >= de.ipnats.hardwrought.knowledge.KnowledgeLevel.STUDIED.ordinal();
        graphics.fill(x, y, x + CONTENT_WIDTH, y + 1, RULE_COLOR);
        y += 5;
        String degrees = studied ? String.format(java.util.Locale.ROOT, "%.0f", melting) : "???";
        y = wrapLines(graphics, Component.translatable("tooltip.hardwrought.melting_point", degrees), x, y, TEXT_COLOR);
        if (studied) {
            wrapLines(graphics, de.ipnats.hardwrought.client.survival.MeltingPointTooltip.furnaceNeeded(melting),
                    x, y, FADED_COLOR);
        }
    }

    /** A line cut to fit with an ellipsis, for the rare text that would need more room than there is. */
    private FormattedCharSequence ellipsis(FormattedCharSequence line, int width) {
        StringBuilder plain = new StringBuilder();
        line.accept((index, style, codePoint) -> {
            plain.appendCodePoint(codePoint);
            return true;
        });
        String cut = font.plainSubstrByWidth(plain.toString(), width - font.width("…"));
        return Component.literal(cut.stripTrailing() + "…").getVisualOrderText();
    }

    /** Draws wrapped text and says where the next line would start. */
    private int wrapLines(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        for (FormattedCharSequence line : font.split(text, CONTENT_WIDTH)) {
            ink(graphics, line, x, y, color);
            y += LINE_HEIGHT;
        }
        return y;
    }

    private void drawTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (mode != CompendiumPagePayload.MODE_SHELF) return;
        int y = top + TABS_TOP;
        for (KnowledgeCategory category : KnowledgeCategory.values()) {
            boolean selected = mode == CompendiumPagePayload.MODE_SHELF
                    && category == CompendiumClient.shelf();
            boolean hovered = mouseX >= leftContentX() && mouseX < leftContentX() + TAB_WIDTH
                    && mouseY >= y && mouseY < y + TAB_HEIGHT;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    selected ? TAB_SELECTED_SPRITE : TAB_SPRITE,
                    leftContentX(), y, TAB_WIDTH + (selected ? 3 : 0), TAB_HEIGHT);
            ink(graphics, Component.translatable(category.translationKey()), leftContentX() + 6, y + 3,
                    selected || hovered ? TEXT_COLOR : FADED_COLOR);
            y += TAB_HEIGHT;
        }
    }

    private void drawShelf(GuiGraphicsExtractor graphics, CompendiumPagePayload current) {
        List<CompendiumPagePayload.Entry> entries = current.entries();
        int first = page * PER_PAGE;
        // The grid is centred on the page rather than hung off its left margin.
        int originX = rightContentX() + (CONTENT_WIDTH - (GRID_COLUMNS - 1) * GRID_STEP - 16) / 2;
        int originY = top + GRID_TOP;
        for (int index = 0; index < PER_PAGE && first + index < entries.size(); index++) {
            CompendiumPagePayload.Entry entry = entries.get(first + index);
            Item item = BuiltInRegistries.ITEM.getValue(entry.id());
            if (item == null) continue;
            int x = originX + (index % GRID_COLUMNS) * GRID_STEP;
            int y = originY + (index / GRID_COLUMNS) * GRID_STEP;
            slot(graphics, x, y, new ItemStack(item), entry.level() > 0);
        }
        if (entries.isEmpty()) {
            ink(graphics, Component.translatable("gui.hardwrought.compendium.empty"),
                    originX, originY + 4, FADED_COLOR);
        }
    }

    private void drawRecipes(GuiGraphicsExtractor graphics, CompendiumPagePayload current) {
        int method = effectiveMethod(current);
        if (method == CompendiumPagePayload.METHOD_LOOT) {
            drawSources(graphics, current);
            return;
        }
        List<CompendiumPagePayload.Recipe> recipes = recipesFor(current, method);
        int originY = top + CARDS_TOP;
        if (recipes.isEmpty()) {
            // A feather has no recipe, and saying so is still worth a line — just not the only one.
            wrapInk(graphics, Component.translatable(current.sources().isEmpty()
                            ? "gui.hardwrought.compendium.no_recipes"
                            : "gui.hardwrought.compendium.not_crafted"),
                    rightContentX(), originY + 4, CONTENT_WIDTH, FADED_COLOR);
            return;
        }
        List<List<CompendiumPagePayload.Recipe>> pages = recipePages(recipes);
        int y = originY;
        for (CompendiumPagePayload.Recipe recipe : pages.get(Math.min(page, pages.size() - 1))) {
            Card card = card(recipe);
            drawCard(graphics, recipe, card, rightContentX(), y);
            y += card.height() + CARD_GAP;
        }
    }

    /**
     * How one recipe card is laid out: how many slots wide its grid is, how tall, and the lines of
     * instruction under it. Worked out once per card, and used both to draw it and to decide how many
     * cards fit on a page, so the two can never disagree.
     */
    private record Card(int columns, int gridHeight, List<FormattedCharSequence> instruction, int height) { }

    private Card card(CompendiumPagePayload.Recipe recipe) {
        int inputs = recipe.inputs().size();
        int columns = recipe.width() > 0 ? recipe.width() : Math.min(3, Math.max(1, inputs));
        int rows = Math.max(1, (inputs + columns - 1) / columns);
        int gridHeight = rows * SLOT;
        List<FormattedCharSequence> instruction = recipe.method() == CompendiumPagePayload.METHOD_IN_WORLD
                ? font.split(Component.translatable(instructionKey(recipe.id())), CONTENT_WIDTH - 2 * CARD_PADDING - 2)
                : List.of();
        int height = CARD_PADDING * 2 + gridHeight
                + (instruction.isEmpty() ? 0 : 3 + instruction.size() * LINE_HEIGHT);
        return new Card(columns, gridHeight, instruction, height);
    }

    /**
     * The recipes split into pages by the room they take rather than by a fixed count. A tall card
     * — a full grid, a long instruction — gets the page it needs instead of spilling into the next
     * one or under the controls. A card taller than a whole page still gets a page of its own.
     */
    private List<List<CompendiumPagePayload.Recipe>> recipePages(List<CompendiumPagePayload.Recipe> recipes) {
        int room = CONTENT_BOTTOM - CARDS_TOP;
        List<List<CompendiumPagePayload.Recipe>> pages = new ArrayList<>();
        List<CompendiumPagePayload.Recipe> current = new ArrayList<>();
        int used = 0;
        for (CompendiumPagePayload.Recipe recipe : recipes) {
            int height = card(recipe).height();
            if (!current.isEmpty() && used + height > room) {
                pages.add(current);
                current = new ArrayList<>();
                used = 0;
            }
            current.add(recipe);
            used += height + CARD_GAP;
        }
        if (!current.isEmpty()) pages.add(current);
        return pages;
    }

    /**
     * The instruction printed under a recipe that is performed rather than crafted. Recipes that
     * come in families share one line: the id's path up to its first slash names the family, so
     * every row the crusher produces reads the same instruction.
     */
    private static String instructionKey(Identifier id) {
        String path = id.getPath();
        int slash = path.indexOf('/');
        String group = slash < 0 ? path : path.substring(0, slash);
        return "recipe." + id.getNamespace() + "." + group + ".instruction";
    }

    /** Where the thing is found when nobody makes it: what drops it, what it is broken out of. */
    private void drawSources(GuiGraphicsExtractor graphics, CompendiumPagePayload current) {
        List<CompendiumPagePayload.Source> sources = current.sources();
        if (sources.isEmpty()) {
            wrapInk(graphics, Component.translatable("gui.hardwrought.compendium.no_recipes"),
                    rightContentX(), top + CARDS_TOP + 4, CONTENT_WIDTH, FADED_COLOR);
            return;
        }
        int perPage = SOURCE_ROWS * 2;
        int first = page * perPage;
        for (int index = 0; index < perPage && first + index < sources.size(); index++) {
            CompendiumPagePayload.Source source = sources.get(first + index);
            int originX = index < SOURCE_ROWS ? leftContentX() : rightContentX();
            int y = top + SOURCES_TOP + index % SOURCE_ROWS * SOURCE_HEIGHT;
            ItemStack icon = LootSourceLabels.icon(source);
            boolean known = source.kind() != LootSources.KIND_BLOCK || source.level() > 0;
            Component label = LootSourceLabels.label(source, known);
            if (!icon.isEmpty()) {
                drawSlotBackground(graphics, originX, y);
                ShadowItem.render(graphics, icon, originX, y, known);
                hotspots.add(new Hotspot(originX, y, icon, known, List.of(label),
                        source.kind() == LootSources.KIND_BLOCK));
            }
            List<FormattedCharSequence> labelLines = font.split(label, CONTENT_WIDTH - 22);
            if (!labelLines.isEmpty()) {
                ink(graphics, labelLines.size() > 1 ? ellipsis(labelLines.getFirst(), CONTENT_WIDTH - 22)
                        : labelLines.getFirst(), originX + 22, y + 4, TEXT_COLOR);
            }
        }
    }

    private void drawCard(GuiGraphicsExtractor graphics, CompendiumPagePayload.Recipe recipe, Card card,
                          int x, int y) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CARD_SPRITE, x, y, CONTENT_WIDTH, card.height());
        // Item positions are where the item is drawn; its slot frame sits one pixel outside that.
        int gridX = x + CARD_PADDING + 2;
        int gridY = y + CARD_PADDING + 1;
        for (int index = 0; index < recipe.inputs().size(); index++) {
            option(graphics, recipe.inputs().get(index), gridX + (index % card.columns()) * SLOT,
                    gridY + (index / card.columns()) * SLOT);
        }
        // Arrow and result are placed as if every grid were three wide, so that results line up
        // down a page however different the recipes above them are.
        int middle = gridY + (card.gridHeight() - SLOT) / 2;
        int arrowX = gridX + Math.max(3, card.columns()) * SLOT + 4;
        ink(graphics, Component.literal("→"), arrowX, middle + 4, TEXT_COLOR);
        option(graphics, recipe.result(), arrowX + 13, middle);
        if (!recipe.station().isEmpty()) {
            option(graphics, recipe.station(), x + CONTENT_WIDTH - CARD_PADDING - SLOT, gridY);
        }
        int textY = y + CARD_PADDING + card.gridHeight() + 3;
        for (FormattedCharSequence line : card.instruction()) {
            ink(graphics, line, x + CARD_PADDING + 1, textY, FADED_COLOR);
            textY += LINE_HEIGHT;
        }
    }

    /** Draws one recipe slot, cycling through the options where an ingredient is a whole tag. */
    private void option(GuiGraphicsExtractor graphics, CompendiumPagePayload.Slot slot, int x, int y) {
        if (slot.isEmpty()) {
            drawSlotBackground(graphics, x, y);
            return;
        }
        int index = (int) ((System.currentTimeMillis() / CYCLE_MILLIS) % slot.options().size());
        CompendiumPagePayload.Known known = slot.options().get(index);
        slot(graphics, x, y, known.stack(), known.level() > 0);
    }

    private void slot(GuiGraphicsExtractor graphics, int x, int y, ItemStack stack, boolean known) {
        drawSlotBackground(graphics, x, y);
        ShadowItem.render(graphics, stack, x, y, known);
        hotspots.add(new Hotspot(x, y, stack, known, null, true));
    }

    /** The fixed 18x18 inset is deliberately lighter than the page so black unknowns stay legible. */
    private void drawSlotBackground(GuiGraphicsExtractor graphics, int itemX, int itemY) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE,
                itemX - 1, itemY - 1, SLOT, SLOT);
    }

    private void drawTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (Hotspot hotspot : hotspots) {
            if (!hotspot.contains(mouseX, mouseY)) continue;
            graphics.setComponentTooltipForNextFrame(font, hotspot.lines(), mouseX, mouseY);
            return;
        }
    }

    /** Charcoal ink stays crisp on pale scraps; the default drop shadow muddies small glyphs. */
    private void ink(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        graphics.text(font, text, x, y, color, false);
    }

    private void ink(GuiGraphicsExtractor graphics, FormattedCharSequence text,
                     int x, int y, int color) {
        graphics.text(font, text, x, y, color, false);
    }

    private void centeredInk(GuiGraphicsExtractor graphics, Component text,
                             int centerX, int y, int color) {
        graphics.text(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private int wrapInk(GuiGraphicsExtractor graphics, Component text,
                        int x, int y, int width, int color) {
        return graphics.textWithWordWrap(font, text, x, y, width, color, false);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) return true;
        if (mode == MODE_HOME) {
            if (overHalf(homeLeft(0), event.x(), event.y())) {
                CompendiumClient.openShelf(CompendiumClient.shelf(), "");
                return true;
            }
            if (overHalf(homeLeft(1), event.x(), event.y())) {
                CompendiumClient.openJournal();
                return true;
            }
            return false;
        }
        if (inside(event.x(), event.y(), leftContentX(), top + CONTROLS_Y, 54, 18)) {
            goBack();
            return true;
        }
        if (pageCount() > 1) {
            if (inside(event.x(), event.y(), rightContentX(), top + CONTROLS_Y, 22, 18)) {
                turnPage(-1);
                return true;
            }
            if (inside(event.x(), event.y(), rightContentX() + CONTENT_WIDTH - 22,
                    top + CONTROLS_Y, 22, 18)) {
                turnPage(1);
                return true;
            }
        }
        if (mode == CompendiumPagePayload.MODE_RECIPES || mode == CompendiumPagePayload.MODE_USAGES) {
            CompendiumPagePayload current = CompendiumClient.page();
            List<Integer> methods = availableMethods(current);
            int start = rightPageX() + (PAPER_WIDTH - METHOD_TAB_SIZE * methods.size()) / 2;
            int active = current == null ? selectedMethod : effectiveMethod(current);
            for (int index = 0; index < methods.size(); index++) {
                int method = methods.get(index);
                int y = top + (method == active ? 8 : 10);
                if (inside(event.x(), event.y(), start + index * METHOD_TAB_SIZE, y,
                        METHOD_TAB_SIZE, METHOD_TAB_SIZE)) {
                    selectedMethod = method;
                    methodChosen = true;
                    page = 0;
                    return true;
                }
            }
        }
        if (mode == CompendiumPagePayload.MODE_SHELF) {
            int y = top + TABS_TOP;
            for (KnowledgeCategory category : KnowledgeCategory.values()) {
                if (event.x() >= leftContentX() && event.x() < leftContentX() + TAB_WIDTH
                        && event.y() >= y && event.y() < y + TAB_HEIGHT) {
                    CompendiumClient.openShelf(category, "");
                    return true;
                }
                y += TAB_HEIGHT;
            }
        }
        for (Hotspot hotspot : hotspots) {
            if (!hotspot.contains(event.x(), event.y()) || !hotspot.navigable()) continue;
            // Left for how it is made, right for what it is used in: the two questions a browser
            // exists to answer, and they work on a shadow too.
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                CompendiumClient.openUsages(hotspot.stack().getItem());
            } else {
                CompendiumClient.openRecipes(hotspot.stack().getItem());
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && mode != MODE_HOME) {
            turnPage(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (search != null && search.isFocused() && event.key() != InputConstants.KEY_ESCAPE) {
            if (event.key() == InputConstants.KEY_RETURN) {
                search.setFocused(false);
                return true;
            }
            return super.keyPressed(event);
        }
        if (event.key() == InputConstants.KEY_BACKSPACE && mode != MODE_HOME) {
            goBack();
            return true;
        }
        Hotspot hovered = hovered();
        if (hovered != null && CompendiumClient.RECIPES.matches(event)) {
            CompendiumClient.openRecipes(hovered.stack().getItem());
            return true;
        }
        if (hovered != null && CompendiumClient.USAGES.matches(event)) {
            CompendiumClient.openUsages(hovered.stack().getItem());
            return true;
        }
        if (CompendiumClient.OPEN.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private Hotspot hovered() {
        for (Hotspot hotspot : hotspots) {
            if (hotspot.contains(lastMouseX, lastMouseY) && hotspot.navigable()) return hotspot;
        }
        return null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
