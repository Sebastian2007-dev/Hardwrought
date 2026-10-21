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
import java.util.Objects;

/**
 * The compendium: the book a player keeps, in two halves.
 *
 * <p>Opening it lands on the front page, where the halves are chosen between. <b>Knowledge</b> is
 * the recipe browser — shelves on the left, a search field, R for how a thing is made and U for what
 * it is used in, click an ingredient to walk to it, backspace to walk back step by step, the back
 * button one level up: from a recipe to its shelf, from a shelf to the front page. Its one departure from
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
    /** Where the button into a structure's view starts, beside the back button. */
    private static final int STRUCTURE_BUTTON_OFFSET = 58;
    /** The button into the crafting tree, with a sapling on it. */
    private static final int TREE_BUTTON_WIDTH = 22;
    private static final ItemStack TREE_ICON = new ItemStack(Items.OAK_SAPLING);
    /** Nothing on a page is drawn below this, so it can never run into the controls. */
    private static final int CONTENT_BOTTOM = CONTROLS_Y - 4;

    private static final int TAB_WIDTH = CONTENT_WIDTH - 3;
    private static final int TAB_HEIGHT = 14;
    private static final int MULTIBLOCK_ROW = 20;
    /** The shelves and, after them, the multiblock section, all fitted above the page buttons. */
    private static final int TAB_ROW = Math.min(TAB_HEIGHT,
            (CONTROLS_Y - 4 - (PAPER_TOP + 9)) / (KnowledgeCategory.values().length + 1));
    private static final int TABS_TOP = PAPER_TOP + 9;
    private static final int SLOT = 18;
    private static final int GRID_STEP = 21;
    private static final int GRID_COLUMNS = 6;
    private static final int SEARCH_TOP = PAPER_TOP + 2;
    private static final int SHELF_HEADING_TOP = SEARCH_TOP + 23;
    private static final int GRID_TOP = SHELF_HEADING_TOP + 13;
    /** As many structures to a page as fit above the page buttons; the rest on the pages after. */
    private static final int MULTIBLOCK_ROWS = (CONTENT_BOTTOM - GRID_TOP) / MULTIBLOCK_ROW;
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

    /** The ribbons hang out of the book past its right edge, one under the other. */
    private static final int RIBBON_STEP = 18;
    private static final int RIBBON_WIDTH = 22;
    private static final int RIBBON_TOP = PAPER_TOP + 2;
    /** How far a ribbon is tucked in under the cover. */
    private static final int RIBBON_TUCK = 5;
    private static final int RIBBON_COLOR = 0xFF7A2B22;
    private static final int RIBBON_LIGHT = 0xFFA6453A;
    private static final int RIBBON_DARK = 0xFF4E1A14;
    /** An empty ribbon waiting to be laid in: the same cloth, faded. */
    private static final int RIBBON_SPARE = 0xFF8E6A5C;

    private static final int TEXT_COLOR = 0xFF1B120B;
    private static final int FADED_COLOR = 0xFF4A3522;
    /** A tab that is not selected: quieter than the selected one, but still ink on its darker paper. */
    private static final int TAB_COLOR = 0xFF2E2016;
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
    /** The rows of the journal's contents as drawn this frame: top edge, and the page each leads to. */
    private final List<int[]> chapterRows = new ArrayList<>();
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

    /** Called when something else has already sent the request for a new page, asked with {@code search}. */
    public void showing(int mode, Identifier subject, String search) {
        remember();
        this.mode = mode;
        this.subject = subject;
        this.page = 0;
        cardScroll = 0;
        journalScroll = 0;
        infoScroll = 0;
        if (mode == CompendiumPagePayload.MODE_RECIPES || mode == CompendiumPagePayload.MODE_USAGES) {
            selectedMethod = CompendiumPagePayload.METHOD_CRAFTING;
            methodChosen = false;
        }
        this.journalOpened = false;
        setSearchQuietly(search);
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
            // Ink on paper: the shadow vanilla gives a text field only smears it here.
            search.setTextShadow(false);
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

    /**
     * The back button: one level up rather than one step back. A recipe page goes up to its shelf, as it
     * was last left, search and page and all; a shelf or the journal goes up to the front page that asks
     * which half is wanted. Backspace still walks back through every step, this one included.
     */
    private void goUp() {
        if (mode == CompendiumPagePayload.MODE_RECIPES || mode == CompendiumPagePayload.MODE_USAGES) {
            for (View view : history) {
                if (view.mode() != CompendiumPagePayload.MODE_SHELF) continue;
                remember();
                mode = view.mode();
                subject = view.subject();
                page = view.page();
                setSearchQuietly(view.search());
                CompendiumClient.refresh(mode, subject, view.search());
                rebuildWidgets();
                return;
            }
            CompendiumClient.openShelf(CompendiumClient.shelf(), "");
            return;
        }
        showHome();
    }

    private void turnPage(int direction) {
        int pages = pageCount();
        page = Math.floorMod(page + direction, Math.max(1, pages));
        cardScroll = 0;
        journalScroll = 0;
    }

    /** How far a page of recipe cards taller than the page is scrolled, and by how much it is too tall. */
    private int cardScroll;
    private int cardOverflow;
    /** How many rows a journal note longer than both pages is scrolled, and by how many it is too long. */
    private int journalScroll;
    private int journalOverflow;
    /** How far the lines about the subject are scrolled where they run past the left page, and by how much. */
    private int infoScroll;
    private int infoOverflow;

    /**
     * Whether the left page is kept for what the subject is: what a food brings to a diet, or the
     * lines the book has about it. Where it is found is then listed on the right page alone.
     */
    private boolean leftPageTaken() {
        if (mode == CompendiumPagePayload.MODE_SHELF || mode == CompendiumPagePayload.MODE_JOURNAL
                || mode == MODE_HOME) {
            return false;
        }
        return de.ipnats.hardwrought.client.survival.NutritionScreen.nutrientsOf(subject) != null
                || CompendiumInfo.key(subject) != null;
    }

    private int sourcesPerPage() {
        return leftPageTaken() ? SOURCE_ROWS : SOURCE_ROWS * 2;
    }

    private int pageCount() {
        if (mode == MODE_HOME) return 1;
        CompendiumPagePayload current = CompendiumClient.page();
        if (current == null) return 1;
        if (mode == CompendiumPagePayload.MODE_JOURNAL) {
            // The contents first, then one page to a note, each chapter's deeper looks after its notes.
            return leaves(current.journal()).size() + 1;
        }
        if (mode == CompendiumPagePayload.MODE_SHELF) {
            if (MultiblockSection.open()) {
                return Math.max(1, (de.ipnats.hardwrought.knowledge.Multiblocks.ALL.size() + MULTIBLOCK_ROWS - 1) / MULTIBLOCK_ROWS);
            }
            return Math.max(1, (current.entries().size() + PER_PAGE - 1) / PER_PAGE);
        }
        int method = effectiveMethod(current);
        if (method == CompendiumPagePayload.METHOD_LOOT) {
            int perPage = sourcesPerPage();
            return Math.max(1, (current.sources().size() + perPage - 1) / perPage);
        }
        return Math.max(1, recipePages(recipesFor(current, method)).size());
    }

    private List<CompendiumPagePayload.Recipe> recipesFor(CompendiumPagePayload current, int method) {
        return current.recipes().stream().filter(recipe -> tabOf(current, recipe) == method).toList();
    }

    /**
     * The machines this page's in-world recipes are worked in, in the order they first appear. Each
     * gets a bookmark of its own, with the machine on it, after the fixed ones: the refinery's cuts are
     * under the refinery, the cracker's under the cracker. What is done by hand stays under the log.
     */
    private static List<CompendiumPagePayload.Slot> stations(CompendiumPagePayload current) {
        List<CompendiumPagePayload.Slot> stations = new ArrayList<>();
        java.util.Set<net.minecraft.world.item.Item> seen = new java.util.HashSet<>();
        for (CompendiumPagePayload.Recipe recipe : current.recipes()) {
            if (recipe.method() != CompendiumPagePayload.METHOD_IN_WORLD || recipe.station().isEmpty()) continue;
            if (seen.add(recipe.station().options().getFirst().stack().getItem())) stations.add(recipe.station());
        }
        return stations;
    }

    /** The bookmark a recipe is under: its method, or for a machine's recipe that machine's own. */
    private static int tabOf(CompendiumPagePayload current, CompendiumPagePayload.Recipe recipe) {
        if (recipe.method() != CompendiumPagePayload.METHOD_IN_WORLD || recipe.station().isEmpty()) return recipe.method();
        net.minecraft.world.item.Item machine = recipe.station().options().getFirst().stack().getItem();
        List<CompendiumPagePayload.Slot> stations = stations(current);
        for (int index = 0; index < stations.size(); index++) {
            if (stations.get(index).options().getFirst().stack().is(machine)) return METHOD_COUNT + index;
        }
        return recipe.method();
    }

    /** Every bookmark this page could have: the fixed methods, then one for each machine. */
    private static int tabCount(CompendiumPagePayload current) {
        return METHOD_COUNT + (current == null ? 0 : stations(current).size());
    }

    private boolean methodAvailable(CompendiumPagePayload current, int method) {
        return method == CompendiumPagePayload.METHOD_LOOT
                ? !current.sources().isEmpty() : !recipesFor(current, method).isEmpty();
    }

    /** A bookmark is useful only when there is something behind it. */
    private List<Integer> availableMethods(CompendiumPagePayload current) {
        if (current == null) return List.of();
        List<Integer> methods = new ArrayList<>();
        for (int method = 0; method < tabCount(current); method++) {
            if (methodAvailable(current, method)) methods.add(method);
        }
        return methods;
    }

    private int effectiveMethod(CompendiumPagePayload current) {
        if (methodChosen) return selectedMethod;
        if (methodAvailable(current, selectedMethod)) return selectedMethod;
        for (int method = 0; method < tabCount(current); method++) {
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
        chapterRows.clear();
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        drawRibbons(graphics, mouseX, mouseY);
        if (mode == MODE_HOME) {
            drawHome(graphics, mouseX, mouseY);
            drawTooltip(graphics, mouseX, mouseY);
            return;
        }
        CompendiumPagePayload current = CompendiumClient.page();
        drawBookControls(graphics, mouseX, mouseY);
        if (mode == CompendiumPagePayload.MODE_JOURNAL) {
            ink(graphics, journalHeading(current), leftContentX(), top + PAPER_TOP + 5, FADED_COLOR);
            drawChapterTabs(graphics, current, mouseX, mouseY);
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
                    && (effectiveMethod(current) != CompendiumPagePayload.METHOD_LOOT || leftPageTaken())) {
                int below = headingY + font.split(heading(), CONTENT_WIDTH).size() * LINE_HEIGHT + 6;
                drawProperties(graphics, current, headingX, below);
            }
            if (current == null) {
                ink(graphics, Component.translatable("gui.hardwrought.compendium.loading"),
                        headingX, top + CARDS_TOP + 4, FADED_COLOR);
            } else if (mode == CompendiumPagePayload.MODE_SHELF && MultiblockSection.open()) {
                drawMultiblocks(graphics, mouseX, mouseY);
            } else if (mode == CompendiumPagePayload.MODE_SHELF) {
                drawShelf(graphics, current);
            } else {
                drawRecipes(graphics, current);
            }
        }
        if (pageCount() > 1) {
            Component counter = mode != CompendiumPagePayload.MODE_JOURNAL
                    ? Component.translatable("gui.hardwrought.compendium.page", page + 1, pageCount())
                    : page == 0 ? Component.translatable("gui.hardwrought.compendium.contents")
                    : journalCounter(current);
            // Between the arrows there is room for a few words; a longer counter keeps only its numbers.
            if (font.width(counter) > CONTENT_WIDTH - 52 && mode == CompendiumPagePayload.MODE_JOURNAL && page > 0) {
                counter = journalNumbers(current);
            }
            centeredInk(graphics, counter,
                    // Level with the middle of the page buttons it sits between, not below them on
                    // the edge of the cover.
                    rightPageX() + PAPER_WIDTH / 2, top + CONTROLS_Y + 5, FADED_COLOR);
        }
        drawTooltip(graphics, mouseX, mouseY);
    }

    // ---------------------------------------------------------------- ribbons

    private int ribbonX() {
        return left + PANEL_WIDTH - RIBBON_TUCK;
    }

    private int ribbonY(int index) {
        return top + RIBBON_TOP + index * RIBBON_STEP;
    }

    /** The entry this page is about, where it is about one; null on a shelf, the journal and the front page. */
    private Identifier pageSubject() {
        // A ribbon shows what it marks as an item, so a liquid's own page takes none.
        if (de.ipnats.hardwrought.knowledge.FluidIcons.ofSubject(subject) != null) return null;
        return mode == CompendiumPagePayload.MODE_RECIPES || mode == CompendiumPagePayload.MODE_USAGES ? subject : null;
    }

    /** The page a stack in the book leads to: the item's, or for a liquid or gas its container's or its own. */
    private static Identifier subjectOf(ItemStack stack) {
        var ingredient = de.ipnats.hardwrought.knowledge.FluidIcons.of(stack);
        return ingredient != null ? de.ipnats.hardwrought.knowledge.FluidIcons.subject(ingredient)
                : BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    /** Whether one more ribbon may be laid in at the page that is open. */
    private boolean spareRibbon() {
        Identifier here = pageSubject();
        return here != null && !CompendiumClient.bookmarked(here)
                && CompendiumClient.bookmarks().size() < de.ipnats.hardwrought.core.networking.BookmarkPayloads.MAX;
    }

    /**
     * The ribbons a player has laid into the book, hanging out past its edge on every page so that
     * a marked entry is one click away from anywhere. Each shows what it marks, by the same rule
     * as the rest of the book: a shadow until the thing has been held. The one lying at the open
     * page is drawn out further, and under the last hangs a faded spare wherever the open page
     * could take one.
     */
    private void drawRibbons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<CompendiumPagePayload.Entry> marks = CompendiumClient.bookmarks();
        Identifier here = pageSubject();
        Component hint = Component.translatable("gui.hardwrought.compendium.bookmark.hint")
                .withStyle(ChatFormatting.DARK_GRAY);
        for (int index = 0; index < marks.size(); index++) {
            CompendiumPagePayload.Entry mark = marks.get(index);
            Item item = BuiltInRegistries.ITEM.getValue(mark.id());
            if (item == null || item == Items.AIR) continue;
            int out = mark.id().equals(here) ? 4 : 0;
            int x = ribbonX() + out, y = ribbonY(index);
            ribbon(graphics, ribbonX(), y, RIBBON_WIDTH + out, RIBBON_COLOR);
            ItemStack stack = new ItemStack(item);
            boolean known = mark.level() > 0;
            int itemX = x + RIBBON_TUCK;
            ShadowItem.render(graphics, stack, itemX, y, known);
            List<Component> lines = new ArrayList<>(ShadowItem.tooltip(stack, known));
            lines.add(hint);
            hotspots.add(new Hotspot(itemX, y, stack, known, lines, true));
        }
        if (spareRibbon()) {
            int y = ribbonY(marks.size());
            ribbon(graphics, ribbonX(), y, RIBBON_WIDTH, RIBBON_SPARE);
            centeredInk(graphics, Component.literal("+"), ribbonX() + RIBBON_TUCK + 8, y + 4, 0xFFF2E6D0);
            if (inside(mouseX, mouseY, ribbonX() + RIBBON_TUCK, y, 16, 16)) {
                graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable(
                        "gui.hardwrought.compendium.bookmark.add",
                        CompendiumClient.BOOKMARK.getTranslatedKeyMessage())), mouseX, mouseY);
            }
        }
    }

    /** One length of ribbon: cloth with a lit upper edge, a shadowed lower one and a swallow-tail end. */
    private static void ribbon(GuiGraphicsExtractor graphics, int x, int y, int width, int color) {
        graphics.fill(x, y, x + width, y + 16, color);
        graphics.fill(x, y, x + width, y + 1, RIBBON_LIGHT);
        graphics.fill(x, y + 15, x + width, y + 16, RIBBON_DARK);
        graphics.fill(x + width, y + 1, x + width + 2, y + 6, color);
        graphics.fill(x + width, y + 10, x + width + 2, y + 15, color);
    }

    /** A click on the ribbons: on the spare lays it in, with shift on a ribbon takes that one out. */
    private boolean clickRibbons(MouseButtonEvent event) {
        List<CompendiumPagePayload.Entry> marks = CompendiumClient.bookmarks();
        int x = ribbonX() + RIBBON_TUCK;
        if (spareRibbon() && inside(event.x(), event.y(), x, ribbonY(marks.size()), 16, 16)) {
            CompendiumClient.toggleBookmark(pageSubject());
            return true;
        }
        if (!event.hasShiftDown()) return false;
        for (int index = 0; index < marks.size(); index++) {
            // A little wider than the icon, so the ribbon drawn out for the open page is hit as well.
            if (inside(event.x(), event.y(), x, ribbonY(index), 20, 16)) {
                CompendiumClient.toggleBookmark(marks.get(index).id());
                return true;
            }
        }
        return false;
    }

    private void drawBookControls(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawBookButton(graphics, leftContentX(), top + CONTROLS_Y, 54, 18,
                Component.translatable("gui.hardwrought.compendium.back"), mouseX, mouseY);
        if (mode == CompendiumPagePayload.MODE_JOURNAL) drawLoreButton(graphics, mouseX, mouseY);
        if (structure() != null) {
            drawBookButton(graphics, leftContentX() + STRUCTURE_BUTTON_OFFSET, top + CONTROLS_Y,
                    CONTENT_WIDTH - STRUCTURE_BUTTON_OFFSET, 18,
                    Component.translatable("gui.hardwrought.multiblock.open"), mouseX, mouseY);
        }
        if (treeSubject() != null) {
            int x = treeButtonX(), y = treeButtonY();
            boolean hovered = inside(mouseX, mouseY, x, y, TREE_BUTTON_WIDTH, BUTTON_HEIGHT);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? BUTTON_HOVERED_SPRITE : BUTTON_SPRITE,
                    x, y, TREE_BUTTON_WIDTH, BUTTON_HEIGHT);
            graphics.item(TREE_ICON, x + (TREE_BUTTON_WIDTH - 16) / 2, y + 1);
            if (hovered) {
                graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.hardwrought.tree")),
                        mouseX, mouseY);
            }
        }
        if (pageCount() > 1) {
            drawBookButton(graphics, rightContentX(), top + CONTROLS_Y, 22, 18,
                    Component.literal("‹"), mouseX, mouseY);
            drawBookButton(graphics, rightContentX() + CONTENT_WIDTH - 22,
                    top + CONTROLS_Y, 22, 18, Component.literal("›"), mouseX, mouseY);
        }
    }

    /**
     * Beside the back button on a chapter's pages: on its notes, the way to its deeper looks; on a deeper
     * look, the way back to its notes. Nothing where the chapter has none, or none that can be read yet.
     */
    private void drawLoreButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        CompendiumPagePayload current = CompendiumClient.page();
        if (current == null || current.journal().isEmpty() || page == 0) return;
        List<Leaf> leaves = leaves(current.journal());
        Leaf leaf = leaves.get(Math.clamp(page - 1, 0, leaves.size() - 1));
        ChapterSpan span = null;
        for (ChapterSpan candidate : chapterSpans(current.journal())) {
            if (candidate.holds(page)) span = candidate;
        }
        if (span == null || span.lore() == 0 || span.unknown()) return;
        int x = leftContentX() + STRUCTURE_BUTTON_OFFSET, y = top + CONTROLS_Y, w = CONTENT_WIDTH - STRUCTURE_BUTTON_OFFSET;
        int target;
        Component label;
        if (leaf.lore() == null) {
            target = span.end() - span.lore() + 1;
            label = Component.translatable("gui.hardwrought.compendium.lore.open", span.lore());
        } else {
            target = span.target();
            label = Component.translatable("gui.hardwrought.compendium.lore.back");
        }
        drawBookButton(graphics, x, y, w, BUTTON_HEIGHT, label, mouseX, mouseY);
        chapterRows.add(new int[]{x, y, w, BUTTON_HEIGHT, target});
    }

    /** What a crafting tree opened from this page is about: the page's subject, on a page about one. */
    private Identifier treeSubject() {
        boolean lookup = mode == CompendiumPagePayload.MODE_RECIPES || mode == CompendiumPagePayload.MODE_USAGES;
        return lookup && CompendiumClient.page() != null ? subject : null;
    }

    /** The button into the tree sits at the foot of the left page, above the back button's row. */
    private int treeButtonX() {
        return leftContentX() + CONTENT_WIDTH - TREE_BUTTON_WIDTH;
    }

    private int treeButtonY() {
        return top + CONTROLS_Y - BUTTON_HEIGHT - 3;
    }

    private void drawBookButton(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                Component label, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                hovered ? BUTTON_HOVERED_SPRITE : BUTTON_SPRITE, x, y, width, height);
        if (font.width(label) > width - 8) {
            FormattedCharSequence cut = ellipsis(label.getVisualOrderText(), width - 8);
            ink(graphics, cut, x + (width - font.width(cut)) / 2, y + 5, TEXT_COLOR);
            return;
        }
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
            Component label;
            if (method < METHOD_COUNT) {
                graphics.item(METHOD_ICONS[method], x + 4, y + 3);
                label = Component.translatable(methodKey(method));
            } else {
                // A machine's bookmark carries the machine, as a shadow until the player has held one.
                var machine = knownOptions(stations(current).get(method - METHOD_COUNT)).getFirst();
                boolean known = machine.level() > 0;
                ShadowItem.render(graphics, machine.stack(), x + 4, y + 3, known);
                label = known ? machine.stack().getHoverName()
                        : Component.translatable(methodKey(CompendiumPagePayload.METHOD_IN_WORLD));
            }
            if (inside(mouseX, mouseY, x, y, METHOD_TAB_SIZE, METHOD_TAB_SIZE)) {
                graphics.setComponentTooltipForNextFrame(font, List.of(label), mouseX, mouseY);
            }
        }
    }

    /**
     * The structure this page's subject is built into, once the player has studied a block of every
     * kind in it; null on any other page. The server lists the structure's blocks after the subject.
     */
    private de.ipnats.hardwrought.knowledge.Multiblocks.Multiblock structure() {
        if (mode != CompendiumPagePayload.MODE_RECIPES && mode != CompendiumPagePayload.MODE_USAGES) return null;
        CompendiumPagePayload current = CompendiumClient.page();
        if (current == null || subject == null) return null;
        java.util.Set<Identifier> known = studiedOnPage(current);
        for (var multiblock : de.ipnats.hardwrought.knowledge.Multiblocks.containing(subject)) {
            if (MultiblockScreen.readable(multiblock, known)) return multiblock;
        }
        return null;
    }

    private static java.util.Set<Identifier> studiedOnPage(CompendiumPagePayload current) {
        java.util.Set<Identifier> known = new java.util.HashSet<>();
        for (CompendiumPagePayload.Entry entry : current.entries()) {
            if (entry.level() >= de.ipnats.hardwrought.knowledge.KnowledgeLevel.STUDIED.ordinal()) known.add(entry.id());
        }
        return known;
    }

    /** Opens the structure view of this page, where there is one to open. */
    public boolean openStructure() {
        var multiblock = structure();
        if (multiblock == null) return false;
        minecraft.gui.setScreen(new MultiblockScreen(this, multiblock, studiedOnPage(CompendiumClient.page())));
        return true;
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
        if (page == 0) {
            drawContents(graphics, notes);
            return;
        }
        List<Leaf> leaves = leaves(notes);
        Leaf leaf = leaves.get(Math.min(page - 1, leaves.size() - 1));
        if (leaf.lore() != null) {
            drawLore(graphics, notes, leaves, leaf);
            return;
        }
        CompendiumPagePayload.Note note = notes.get(leaf.note());
        boolean unread = note.state() == Journal.HIDDEN;
        int textLeft = leftContentX();
        Item subjectItem = note.subject() == null ? null : BuiltInRegistries.ITEM.getValue(note.subject());
        if (note.needsAll() && !note.parts().isEmpty()) {
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
            // Scrambled glyphs come out wider than the letters they stand for: kept to the page.
            graphics.enableScissor(leftContentX(), y, leftContentX() + CONTENT_WIDTH, top + CONTENT_BOTTOM);
            for (int line = 0; line < 4; line++) {
                ink(graphics, scrawl(26 + line % 3 * 4), leftContentX(), y + 10 + line * 12,
                        SCRAWL_COLOR);
            }
            graphics.disableScissor();
            return;
        }
        List<FormattedCharSequence> lines = font.split(Component.translatable(textKey(note.id())), wrap);
        int linesPerPage = JOURNAL_LINES;
        int visible = linesPerPage * 2;
        // The note as rows running down the left page and on down the right: its text, the task it sets,
        // and once it has been followed, a blank row and the mark beside its tick.
        List<FormattedCharSequence> rows = new ArrayList<>(lines);
        // What each row of the task is: its heading, or a piece held (true) or still wanted (false).
        java.util.Map<Integer, Boolean> taskRows = new java.util.HashMap<>();
        int taskHeading = -1;
        if (!note.parts().isEmpty()) {
            rows.add(FormattedCharSequence.EMPTY);
            taskHeading = rows.size();
            rows.add(Component.translatable(note.parts().size() == 1 ? "gui.hardwrought.compendium.task"
                    : note.needsAll() ? "gui.hardwrought.compendium.task.all"
                    : "gui.hardwrought.compendium.task.any").getVisualOrderText());
            for (CompendiumPagePayload.Part part : note.parts()) {
                Item item = BuiltInRegistries.ITEM.getValue(part.id());
                if (item == null || item == Items.AIR) continue;
                Component name = new ItemStack(item).getHoverName();
                taskRows.put(rows.size(), part.held());
                rows.add(font.width(name) > CONTENT_WIDTH - 12
                        ? ellipsis(name.getVisualOrderText(), CONTENT_WIDTH - 12) : name.getVisualOrderText());
            }
        }
        int followedFrom = -1;
        if (note.state() == Journal.DONE) {
            List<FormattedCharSequence> followed = font.split(
                    Component.translatable("gui.hardwrought.compendium.followed"), CONTENT_WIDTH - 12);
            int at = lines.size() + 1;
            // Kept on one page where everything fits: a mark split across the spine would read as two things.
            if (at + followed.size() <= visible && at < linesPerPage && at + followed.size() > linesPerPage) {
                at = linesPerPage;
            }
            while (rows.size() < at) rows.add(FormattedCharSequence.EMPTY);
            followedFrom = rows.size();
            rows.addAll(followed);
        }
        // Only a note longer than both pages scrolls; one that fits stands still.
        journalOverflow = Math.max(0, rows.size() - visible);
        journalScroll = Math.clamp(journalScroll, 0, journalOverflow);
        for (int row = journalScroll; row < Math.min(rows.size(), journalScroll + visible); row++) {
            int index = row - journalScroll;
            int x = index < linesPerPage ? leftContentX() : rightContentX();
            int y = top + JOURNAL_TEXT_TOP + index % linesPerPage * JOURNAL_LINE;
            if (followedFrom >= 0 && row >= followedFrom) {
                if (row == followedFrom) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FOLLOWED_SPRITE, x, y, 9, 9);
                ink(graphics, rows.get(row), x + 12, y, DONE_COLOR);
            } else if (row == taskHeading) {
                ink(graphics, rows.get(row), x, y, FADED_COLOR);
            } else if (taskRows.containsKey(row)) {
                boolean held = taskRows.get(row);
                // A tick for what has been held, a ring for what is still to be found or made.
                ink(graphics, Component.literal(held ? "✔" : "•"), x + 2, y, held ? DONE_COLOR : TEXT_COLOR);
                ink(graphics, rows.get(row), x + 12, y, held ? DONE_COLOR : TEXT_COLOR);
            } else {
                ink(graphics, rows.get(row), x, y, TEXT_COLOR);
            }
        }
        if (journalOverflow > 0) {
            int from = top + JOURNAL_TEXT_TOP, to = from + linesPerPage * JOURNAL_LINE;
            int track = to - from;
            int thumb = Math.max(8, track * visible / rows.size());
            int thumbAt = from + (track - thumb) * journalScroll / journalOverflow;
            int barX = rightContentX() + CONTENT_WIDTH + 2;
            graphics.fill(barX, from, barX + 2, to, 0x30000000);
            graphics.fill(barX, thumbAt, barX + 2, thumbAt + thumb, 0xA0000000 | (FADED_COLOR & 0xFFFFFF));
        }
    }

    /** Writing that is there without being readable: the font shuffles it anew every frame. */
    private static Component scrawl(int length) {
        return Component.literal(SCRAWL.substring(0, Math.min(length, SCRAWL.length())))
                .withStyle(ChatFormatting.OBFUSCATED);
    }

    /**
     * Opens the written half on the thought the player is working on: the first one that can be read
     * and is not followed yet. Once per page the server sends, so the arrows keep working afterwards.
     */
    private void landOnCurrentNote(List<CompendiumPagePayload.Note> notes) {
        if (journalOpened) return;
        journalOpened = true;
        int unfollowed = -1;
        for (int index = 0; index < notes.size(); index++) {
            int state = notes.get(index).state();
            if (state == Journal.OPEN) {
                page = pageOfNote(notes, index);
                return;
            }
            if (state != Journal.DONE && unfollowed < 0) unfollowed = index;
        }
        page = pageOfNote(notes, unfollowed >= 0 ? unfollowed : notes.size() - 1);
    }

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII",
            "XIII", "XIV", "XV", "XVI"};
    private static final int CHAPTER_ROW = 13;
    /** Chapters listed on one page of the contents; the rest go on the facing page. */
    private static final int CONTENTS_ROWS = 7;
    private static final int TAB_W = 22;
    /** How far a bookmark stands up out of the top of the book; the open one stands further. */
    private static final int TAB_RISE = 11;

    /**
     * One chapter as the player stands in it: where its thoughts lie in the chain, how many are followed,
     * whether any of them can be read yet, and the page a click on it opens.
     */
    private record ChapterSpan(Identifier id, int number, int start, int end, int total, int done, int lore, boolean unknown,
                               int target) {
        boolean finished() { return done == total(); }
        /** Whether a page of the written half (1 for the first after the contents) lies in this chapter. */
        boolean holds(int leafPage) { return leafPage > start && leafPage <= end; }
    }

    /**
     * One page of the written half after the contents: a note (its index among the notes), or one of
     * its chapter's deeper looks, which follow the chapter's last note.
     */
    private record Leaf(int note, Identifier lore, Identifier chapter) { }

    private static List<Leaf> leaves(List<CompendiumPagePayload.Note> notes) {
        List<Leaf> leaves = new ArrayList<>(notes.size() + 32);
        for (int index = 0; index < notes.size(); index++) {
            Identifier chapter = notes.get(index).chapter();
            leaves.add(new Leaf(index, null, chapter));
            boolean last = index + 1 == notes.size() || !Objects.equals(notes.get(index + 1).chapter(), chapter);
            if (last) {
                for (Identifier lore : Journal.lore(chapter)) leaves.add(new Leaf(-1, lore, chapter));
            }
        }
        return leaves;
    }

    /** The page a note is on. */
    private static int pageOfNote(List<CompendiumPagePayload.Note> notes, int note) {
        List<Leaf> leaves = leaves(notes);
        for (int index = 0; index < leaves.size(); index++) {
            if (leaves.get(index).note() == note) return index + 1;
        }
        return Math.max(0, Math.min(note + 1, leaves.size()));
    }

    /**
     * The chapters as the player stands in them. Only the notes count towards how far a chapter is
     * followed; its deeper looks are there to be read or not.
     */
    private static List<ChapterSpan> chapterSpans(List<CompendiumPagePayload.Note> notes) {
        List<Leaf> leaves = leaves(notes);
        List<ChapterSpan> spans = new ArrayList<>();
        for (int start = 0; start < leaves.size(); ) {
            Identifier chapter = leaves.get(start).chapter();
            int end = start, total = 0, done = 0, hidden = 0, lore = 0, firstOpen = -1, firstUnfollowed = -1;
            while (end < leaves.size() && Objects.equals(leaves.get(end).chapter(), chapter)) {
                Leaf leaf = leaves.get(end);
                if (leaf.lore() != null) {
                    lore++;
                    end++;
                    continue;
                }
                total++;
                int state = notes.get(leaf.note()).state();
                if (state == Journal.DONE) done++;
                else if (firstUnfollowed < 0) firstUnfollowed = end;
                if (state == Journal.OPEN && firstOpen < 0) firstOpen = end;
                if (state == Journal.HIDDEN) hidden++;
                end++;
            }
            int target = firstOpen >= 0 ? firstOpen : firstUnfollowed >= 0 ? firstUnfollowed : start;
            spans.add(new ChapterSpan(chapter, spans.size(), start, end, total, done, lore, hidden == total, target + 1));
            start = end;
        }
        return spans;
    }

    /** The counter under the page arrows as bare numbers, for where its words do not fit. */
    private Component journalNumbers(CompendiumPagePayload current) {
        if (current == null || current.journal().isEmpty()) return Component.empty();
        List<Leaf> leaves = leaves(current.journal());
        Leaf leaf = leaves.get(Math.clamp(page - 1, 0, leaves.size() - 1));
        if (leaf.lore() == null) return Component.literal((leaf.note() + 1) + "/" + current.journal().size());
        List<Identifier> lore = Journal.lore(leaf.chapter());
        return Component.literal((lore.indexOf(leaf.lore()) + 1) + "/" + lore.size());
    }

    /** Under the page arrows: a note's number among the notes, or a deeper look's within its chapter. */
    private Component journalCounter(CompendiumPagePayload current) {
        if (current == null || current.journal().isEmpty()) return Component.empty();
        List<Leaf> leaves = leaves(current.journal());
        Leaf leaf = leaves.get(Math.clamp(page - 1, 0, leaves.size() - 1));
        if (leaf.lore() == null) {
            return Component.translatable("gui.hardwrought.compendium.note", leaf.note() + 1, current.journal().size());
        }
        List<Identifier> lore = Journal.lore(leaf.chapter());
        return Component.translatable("gui.hardwrought.compendium.lore.counter", lore.indexOf(leaf.lore()) + 1, lore.size());
    }

    /**
     * A deeper look at how the things of a chapter work: a heading marked as such, and its text running
     * down the left page and on down the right, scrolled where it is longer than both. It can be read
     * once any note of its chapter can; before that it is scrawl, as the notes are.
     */
    private void drawLore(GuiGraphicsExtractor graphics, List<CompendiumPagePayload.Note> notes, List<Leaf> leaves, Leaf leaf) {
        boolean readable = false;
        for (Leaf other : leaves) {
            if (other.lore() == null && Objects.equals(other.chapter(), leaf.chapter())
                    && notes.get(other.note()).state() != Journal.HIDDEN) {
                readable = true;
                break;
            }
        }
        ink(graphics, Component.translatable("gui.hardwrought.compendium.lore"), leftContentX(), top + PAPER_TOP + 16, FADED_COLOR);
        Component title = readable ? Component.translatable(loreKey(leaf.lore()) + ".title") : scrawl(18);
        List<FormattedCharSequence> titleLines = font.split(title, CONTENT_WIDTH);
        ink(graphics, titleLines.size() > 1 ? ellipsis(titleLines.getFirst(), CONTENT_WIDTH) : titleLines.getFirst(),
                leftContentX(), top + PAPER_TOP + 27, TEXT_COLOR);
        int rule = top + JOURNAL_TEXT_TOP - 8;
        graphics.fill(leftContentX(), rule, leftContentX() + CONTENT_WIDTH, rule + 1, RULE_COLOR);
        graphics.fill(rightContentX(), rule, rightContentX() + CONTENT_WIDTH, rule + 1, RULE_COLOR);
        if (!readable) {
            journalOverflow = 0;
            int y = wrapInk(graphics, Component.translatable("gui.hardwrought.compendium.unread"),
                    leftContentX(), top + JOURNAL_TEXT_TOP, CONTENT_WIDTH, FADED_COLOR);
            graphics.enableScissor(leftContentX(), y, leftContentX() + CONTENT_WIDTH, top + CONTENT_BOTTOM);
            for (int line = 0; line < 4; line++) ink(graphics, scrawl(26 + line % 3 * 4), leftContentX(), y + 10 + line * 12, SCRAWL_COLOR);
            graphics.disableScissor();
            return;
        }
        List<FormattedCharSequence> rows = font.split(Component.translatable(loreKey(leaf.lore()) + ".text"), CONTENT_WIDTH);
        int linesPerPage = JOURNAL_LINES;
        int visible = linesPerPage * 2;
        journalOverflow = Math.max(0, rows.size() - visible);
        journalScroll = Math.clamp(journalScroll, 0, journalOverflow);
        for (int row = journalScroll; row < Math.min(rows.size(), journalScroll + visible); row++) {
            int index = row - journalScroll;
            int x = index < linesPerPage ? leftContentX() : rightContentX();
            int y = top + JOURNAL_TEXT_TOP + index % linesPerPage * JOURNAL_LINE;
            ink(graphics, rows.get(row), x, y, TEXT_COLOR);
        }
        if (journalOverflow > 0) {
            int from = top + JOURNAL_TEXT_TOP, to = from + linesPerPage * JOURNAL_LINE;
            int track = to - from;
            int thumb = Math.max(8, track * visible / rows.size());
            int thumbAt = from + (track - thumb) * journalScroll / journalOverflow;
            int barX = rightContentX() + CONTENT_WIDTH + 2;
            graphics.fill(barX, from, barX + 2, to, 0x30000000);
            graphics.fill(barX, thumbAt, barX + 2, thumbAt + thumb, 0xA0000000 | (FADED_COLOR & 0xFFFFFF));
        }
    }

    private static String loreKey(Identifier lore) {
        return "journal.hardwrought.lore." + lore.getPath();
    }

    /** Above the contents the book's own word for this half; above a note, the chapter it belongs to. */
    private Component journalHeading(CompendiumPagePayload current) {
        if (current == null || page == 0 || current.journal().isEmpty()) {
            return Component.translatable("gui.hardwrought.compendium.journal");
        }
        for (ChapterSpan span : chapterSpans(current.journal())) {
            if (span.id() != null && span.holds(Math.min(page, leaves(current.journal()).size()))) {
                return Component.translatable("gui.hardwrought.compendium.chapter",
                        roman(span.number()), chapterName(span.id()));
            }
        }
        return Component.translatable("gui.hardwrought.compendium.journal");
    }

    private static String roman(int number) {
        return ROMAN[Math.min(number, ROMAN.length - 1)];
    }

    private static Component chapterName(Identifier chapter) {
        return Component.translatable("journal.hardwrought.chapter." + chapter.getPath());
    }

    /**
     * The first page of the written half: the chapters of the way down to the last night, each with how
     * many of its thoughts have been followed, running down the left page and on down the right. A chapter
     * none of whose thoughts has come to mind yet is there only as a scrawl, so the player sees how far the
     * road goes without reading where it leads.
     */
    private void drawContents(GuiGraphicsExtractor graphics, List<CompendiumPagePayload.Note> notes) {
        int rule = top + JOURNAL_TEXT_TOP - 8;
        graphics.fill(leftContentX(), rule, leftContentX() + CONTENT_WIDTH, rule + 1, RULE_COLOR);
        graphics.fill(rightContentX(), rule, rightContentX() + CONTENT_WIDTH, rule + 1, RULE_COLOR);
        ink(graphics, Component.translatable("gui.hardwrought.compendium.contents"),
                leftContentX(), top + PAPER_TOP + 21, TEXT_COLOR);
        int followedAll = 0;
        int rightY = top + JOURNAL_TEXT_TOP - 2;
        for (ChapterSpan span : chapterSpans(notes)) {
            followedAll += span.done();
            boolean onLeft = span.number() < CONTENTS_ROWS;
            int x = onLeft ? leftContentX() : rightContentX();
            int y = top + JOURNAL_TEXT_TOP - 2 + (onLeft ? span.number() : span.number() - CONTENTS_ROWS) * CHAPTER_ROW;
            if (!onLeft) rightY = y + CHAPTER_ROW;
            boolean hovered = !span.unknown() && inside(lastMouseX, lastMouseY, x, y - 2, CONTENT_WIDTH, CHAPTER_ROW);
            int color = span.finished() ? DONE_COLOR : span.unknown() ? SCRAWL_COLOR : hovered ? FADED_COLOR : TEXT_COLOR;
            ink(graphics, Component.literal(roman(span.number()) + "."), x, y, color);
            Component name = span.unknown() || span.id() == null ? scrawl(14) : chapterName(span.id());
            int nameX = x + 25;
            Component count = Component.literal(span.done() + "/" + span.total());
            int countX = x + CONTENT_WIDTH - font.width(count);
            graphics.enableScissor(nameX, y - 1, countX - 3, y + 10);
            ink(graphics, name, nameX, y, color);
            graphics.disableScissor();
            ink(graphics, count, countX, y, span.finished() ? DONE_COLOR : FADED_COLOR);
            if (!span.unknown()) chapterRows.add(new int[]{x, y - 2, CONTENT_WIDTH, CHAPTER_ROW, span.target()});
        }
        int y = wrapInk(graphics, Component.translatable("gui.hardwrought.compendium.contents.progress",
                followedAll, notes.size()), rightContentX(), rightY + 6, CONTENT_WIDTH, TEXT_COLOR);
        if (y + 20 <= top + CONTENT_BOTTOM) {
            wrapInk(graphics, Component.translatable("gui.hardwrought.compendium.contents.hint"),
                    rightContentX(), y + 4, CONTENT_WIDTH, FADED_COLOR);
        }
    }

    /**
     * The chapters as bookmarks standing up out of the top of the book, the contents first: one click from
     * any page of the written half to any chapter. The open chapter's stands out furthest; one finished is
     * green; one not a thought of which has come to mind yet is faded, and leads nowhere.
     */
    private void drawChapterTabs(GuiGraphicsExtractor graphics, CompendiumPagePayload current, int mouseX, int mouseY) {
        if (current == null || current.journal().isEmpty()) return;
        List<ChapterSpan> spans = chapterSpans(current.journal());
        int count = spans.size() + 1;
        int step = Math.min(TAB_W + 2, (PANEL_WIDTH - 24) / count);
        int x0 = left + (PANEL_WIDTH - step * count) / 2;
        for (int index = 0; index < count; index++) {
            ChapterSpan span = index == 0 ? null : spans.get(index - 1);
            boolean open = span == null ? page == 0 : span.holds(page);
            int x = x0 + index * step;
            int rise = open ? TAB_RISE + 3 : TAB_RISE;
            int y = top - rise;
            int w = step - 2;
            boolean dead = span != null && span.unknown();
            boolean hovered = inside(mouseX, mouseY, x, y, w, rise + 3);
            int cloth = dead ? RIBBON_SPARE : span != null && span.finished() ? 0xFF4F6B2E
                    : (hovered || open) ? RIBBON_LIGHT : RIBBON_COLOR;
            graphics.fill(x, y, x + w, top + 3, cloth);
            graphics.fill(x, y, x + w, y + 1, RIBBON_LIGHT);
            graphics.fill(x + w - 1, y, x + w, top + 3, RIBBON_DARK);
            Component label = Component.literal(span == null ? "≡" : roman(span.number()));
            centeredInk(graphics, label, x + w / 2, y + 2, dead ? 0xFFD8CBB8 : 0xFFF2E6D0);
            if (!dead) chapterRows.add(new int[]{x, y, w, rise + 3, span == null ? 0 : span.target()});
            if (hovered) {
                Component name = span == null ? Component.translatable("gui.hardwrought.compendium.contents")
                        : dead ? scrawl(12)
                        : Component.translatable("gui.hardwrought.compendium.chapter", roman(span.number()), chapterName(span.id()));
                List<Component> lines = new ArrayList<>();
                lines.add(name);
                if (span != null) lines.add(Component.literal(span.done() + "/" + span.total()).withStyle(ChatFormatting.GRAY));
                if (span != null && span.lore() > 0 && !dead) {
                    lines.add(Component.translatable("gui.hardwrought.compendium.lore.count", span.lore()).withStyle(ChatFormatting.DARK_GRAY));
                }
                graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            }
        }
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
            return Component.translatable(MultiblockSection.open() ? "gui.hardwrought.compendium.multiblocks"
                    : CompendiumClient.shelf().translationKey());
        }
        CompendiumPagePayload current = CompendiumClient.page();
        Component name = ShadowItem.UNKNOWN_NAME;
        var fluid = de.ipnats.hardwrought.knowledge.FluidIcons.ofSubject(subject);
        if (fluid != null) {
            name = fluid.name();
        } else if (current != null && !current.entries().isEmpty() && current.entries().getFirst().level() > 0) {
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
        boolean studied = !current.entries().isEmpty()
                && current.entries().getFirst().level() >= de.ipnats.hardwrought.knowledge.KnowledgeLevel.STUDIED.ordinal();
        Double melting = de.ipnats.hardwrought.client.survival.MeltingPointTooltip.meltingPoint(subject);
        if (melting != null) {
            graphics.fill(x, y, x + CONTENT_WIDTH, y + 1, RULE_COLOR);
            y += 5;
            String degrees = studied ? String.format(java.util.Locale.ROOT, "%.0f", melting) : "???";
            y = wrapLines(graphics, Component.translatable("tooltip.hardwrought.melting_point", degrees), x, y, TEXT_COLOR);
            if (studied) {
                Item item = BuiltInRegistries.ITEM.getValue(subject);
                Component source = item != null && !de.ipnats.hardwrought.metallurgy.Smelting.isCast(item)
                        ? de.ipnats.hardwrought.client.survival.MeltingPointTooltip.heatNeeded(melting)
                        : de.ipnats.hardwrought.client.survival.MeltingPointTooltip.furnaceNeeded(melting);
                y = wrapLines(graphics, source, x, y, FADED_COLOR);
            }
        }
        var food = de.ipnats.hardwrought.client.survival.NutritionScreen.nutrientsOf(subject);
        if (food != null) y = drawNutrients(graphics, food, studied, x, y);
        drawInfo(graphics, studied, x, y);
    }

    /**
     * What the subject is good for, in a few lines under everything else on the left page. Like the
     * numbers above it, this is what working with a thing teaches: before it is studied the lines are
     * there as scrawl, so the player sees there is something to learn. Text that runs past the page is
     * cut off above the buttons and scrolled with the wheel.
     */
    private void drawInfo(GuiGraphicsExtractor graphics, boolean studied, int x, int y) {
        String key = CompendiumInfo.key(subject);
        if (key == null) {
            infoOverflow = 0;
            return;
        }
        graphics.fill(x, y, x + CONTENT_WIDTH, y + 1, RULE_COLOR);
        y += 5;
        int bottom = treeSubject() != null ? treeButtonY() - 3 : top + CONTENT_BOTTOM;
        if (bottom - y < LINE_HEIGHT) {
            infoOverflow = 0;
            return;
        }
        graphics.enableScissor(x, y, x + CONTENT_WIDTH + 4, bottom);
        if (!studied) {
            infoOverflow = 0;
            for (int line = 0; line < 3; line++) {
                ink(graphics, scrawl(22 + line % 2 * 4), x, y + line * (LINE_HEIGHT + 2), SCRAWL_COLOR);
            }
            graphics.disableScissor();
            return;
        }
        List<FormattedCharSequence> lines = font.split(Component.translatable(key), CONTENT_WIDTH);
        int room = bottom - y;
        infoOverflow = Math.max(0, lines.size() * LINE_HEIGHT - room);
        infoScroll = Math.clamp(infoScroll, 0, infoOverflow);
        int lineY = y - infoScroll;
        for (FormattedCharSequence line : lines) {
            ink(graphics, line, x, lineY, TEXT_COLOR);
            lineY += LINE_HEIGHT;
        }
        graphics.disableScissor();
        if (infoOverflow > 0) {
            int thumb = Math.max(8, room * room / (room + infoOverflow));
            int at = y + (room - thumb) * infoScroll / infoOverflow;
            int barX = x + CONTENT_WIDTH + 2;
            graphics.fill(barX, y, barX + 2, bottom, 0x30000000);
            graphics.fill(barX, at, barX + 2, at + thumb, 0xA0000000 | (FADED_COLOR & 0xFFFFFF));
        }
    }

    /**
     * What a food brings to each of the five nutrients, in points of their 0-100 levels, and the
     * water in it. Found out the way everything else in the book is: by studying it, which eating it
     * does.
     */
    private int drawNutrients(GuiGraphicsExtractor graphics, de.ipnats.hardwrought.core.registry.FoodNutritionDefinition food,
                              boolean studied, int x, int y) {
        graphics.fill(x, y, x + CONTENT_WIDTH, y + 1, RULE_COLOR);
        y += 5;
        ink(graphics, Component.translatable("gui.hardwrought.compendium.nutrients"), x, y, TEXT_COLOR);
        y += LINE_HEIGHT;
        var nutrients = food.nutrients();
        for (de.ipnats.hardwrought.survival.Nutrient nutrient : de.ipnats.hardwrought.survival.Nutrient.values()) {
            double amount = nutrients.get(nutrient);
            String value = studied ? String.format(java.util.Locale.ROOT, "+%.0f", amount) : "???";
            ink(graphics, Component.translatable(nutrient.translationKey()), x + 4, y, FADED_COLOR);
            ink(graphics, Component.literal(value), x + CONTENT_WIDTH - font.width(value), y,
                    studied && amount > 0 ? TEXT_COLOR : FADED_COLOR);
            y += LINE_HEIGHT;
        }
        if (studied && food.hydration() != 0) {
            String water = String.format(java.util.Locale.ROOT, "%+.0f", food.hydration());
            ink(graphics, Component.translatable("gui.hardwrought.compendium.water"), x + 4, y, FADED_COLOR);
            ink(graphics, Component.literal(water), x + CONTENT_WIDTH - font.width(water), y, 0xFF6FA8DC);
            y += LINE_HEIGHT;
        }
        return y + 2;
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
            boolean selected = !MultiblockSection.open() && category == CompendiumClient.shelf();
            drawTab(graphics, Component.translatable(category.translationKey()), y, selected, mouseX, mouseY);
            y += TAB_ROW;
        }
        drawTab(graphics, Component.translatable("gui.hardwrought.compendium.multiblocks"), y, MultiblockSection.open(),
                mouseX, mouseY);
    }

    private void drawTab(GuiGraphicsExtractor graphics, Component label, int y, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= leftContentX() && mouseX < leftContentX() + TAB_WIDTH
                && mouseY >= y && mouseY < y + TAB_ROW;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, selected ? TAB_SELECTED_SPRITE : TAB_SPRITE,
                leftContentX(), y, TAB_WIDTH + (selected ? 3 : 0), TAB_ROW);
        ink(graphics, label, leftContentX() + 6, y + (TAB_ROW - 8) / 2, selected || hovered ? TEXT_COLOR : TAB_COLOR);
    }

    /** The multiblock section: one row a structure, its name and its look where it is known. */
    private void drawMultiblocks(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int y = top + GRID_TOP;
        for (var multiblock : multiblocksOnPage()) {
            boolean readable = MultiblockSection.readable(multiblock);
            boolean hovered = readable && inside(mouseX, mouseY, rightContentX(), y, CONTENT_WIDTH, MULTIBLOCK_ROW);
            if (hovered) graphics.fill(rightContentX() - 2, y - 1, rightContentX() + CONTENT_WIDTH, y + MULTIBLOCK_ROW - 1, 0x20000000);
            ItemStack icon = readable ? multiblockIcon(multiblock) : ItemStack.EMPTY;
            if (!icon.isEmpty()) graphics.item(icon, rightContentX(), y);
            Component name = readable ? MultiblockScreen.name(multiblock) : ShadowItem.UNKNOWN_NAME;
            ink(graphics, name, rightContentX() + 20, y + 4, readable ? TEXT_COLOR : FADED_COLOR);
            y += MULTIBLOCK_ROW;
        }
    }

    /** The structures on the page turned to. */
    private List<de.ipnats.hardwrought.knowledge.Multiblocks.Multiblock> multiblocksOnPage() {
        var all = de.ipnats.hardwrought.knowledge.Multiblocks.ALL;
        int from = Math.min(all.size(), page * MULTIBLOCK_ROWS);
        return all.subList(from, Math.min(all.size(), from + MULTIBLOCK_ROWS));
    }

    /** The structure's face in the list: the first block of its legend the player knows. */
    private static ItemStack multiblockIcon(de.ipnats.hardwrought.knowledge.Multiblocks.Multiblock multiblock) {
        for (var choice : multiblock.legend().values()) {
            for (Identifier id : choice) {
                if (!MultiblockSection.studied().contains(id)) continue;
                Item item = BuiltInRegistries.ITEM.getValue(id);
                if (item != null) return new ItemStack(item);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Opens the structure the row under the mouse shows, if it can be read. */
    private boolean clickMultiblock(double mouseX, double mouseY) {
        int y = top + GRID_TOP;
        for (var multiblock : multiblocksOnPage()) {
            if (inside(mouseX, mouseY, rightContentX(), y, CONTENT_WIDTH, MULTIBLOCK_ROW)) {
                if (!MultiblockSection.readable(multiblock)) return true;
                minecraft.gui.setScreen(new MultiblockScreen(this, multiblock, MultiblockSection.studied()));
                return true;
            }
            y += MULTIBLOCK_ROW;
        }
        return false;
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
        List<CompendiumPagePayload.Recipe> shown = pages.get(Math.min(page, pages.size() - 1));
        // A card taller than the page — a long instruction — is cut off above the controls and
        // scrolled to with the wheel, never drawn over them or off the paper.
        int total = -CARD_GAP;
        for (CompendiumPagePayload.Recipe recipe : shown) total += card(recipe).height() + CARD_GAP;
        int clipBottom = top + CONTENT_BOTTOM;
        cardOverflow = Math.max(0, total - (clipBottom - originY));
        cardScroll = Math.clamp(cardScroll, 0, cardOverflow);
        int hotspotsBefore = hotspots.size();
        graphics.enableScissor(rightContentX() - 2, originY, rightContentX() + CONTENT_WIDTH + 4, clipBottom);
        int y = originY - cardScroll;
        for (CompendiumPagePayload.Recipe recipe : shown) {
            Card card = card(recipe);
            drawCard(graphics, recipe, card, rightContentX(), y);
            y += card.height() + CARD_GAP;
        }
        graphics.disableScissor();
        // What was scrolled out of sight cannot be pointed at either.
        for (int index = hotspots.size() - 1; index >= hotspotsBefore; index--) {
            Hotspot hotspot = hotspots.get(index);
            if (hotspot.y() < originY || hotspot.y() + 16 > clipBottom) hotspots.remove(index);
        }
        if (cardOverflow > 0) {
            int track = clipBottom - originY;
            int thumb = Math.max(8, track * track / (track + cardOverflow));
            int at = originY + (track - thumb) * cardScroll / cardOverflow;
            int barX = rightContentX() + CONTENT_WIDTH + 2;
            graphics.fill(barX, originY, barX + 2, clipBottom, 0x30000000);
            graphics.fill(barX, at, barX + 2, at + thumb, 0xA0000000 | (FADED_COLOR & 0xFFFFFF));
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
        int perPage = sourcesPerPage();
        int first = page * perPage;
        for (int index = 0; index < perPage && first + index < sources.size(); index++) {
            CompendiumPagePayload.Source source = sources.get(first + index);
            int originX = leftPageTaken() || index >= SOURCE_ROWS ? rightContentX() : leftContentX();
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

    /**
     * Draws one recipe slot, cycling through the options where an ingredient is a whole tag. Only the
     * options the player already knows take part: "any log" shown as a log they have seen tells them
     * what is wanted, and a turn through shadows of logs they have never met tells them nothing. Only
     * where they know none of them are the shadows cycled, as the one hint there is.
     */
    private void option(GuiGraphicsExtractor graphics, CompendiumPagePayload.Slot slot, int x, int y) {
        if (slot.isEmpty()) {
            drawSlotBackground(graphics, x, y);
            return;
        }
        List<CompendiumPagePayload.Known> shown = knownOptions(slot);
        int index = (int) ((System.currentTimeMillis() / CYCLE_MILLIS) % shown.size());
        CompendiumPagePayload.Known known = shown.get(index);
        slot(graphics, x, y, known.stack(), known.level() > 0);
    }

    /** The options of a slot the player knows, or all of them where they know none. */
    static List<CompendiumPagePayload.Known> knownOptions(CompendiumPagePayload.Slot slot) {
        List<CompendiumPagePayload.Known> known = slot.options().stream().filter(option -> option.level() > 0).toList();
        return known.isEmpty() ? slot.options() : known;
    }

    private void slot(GuiGraphicsExtractor graphics, int x, int y, ItemStack stack, boolean known) {
        drawSlotBackground(graphics, x, y);
        var ingredient = de.ipnats.hardwrought.knowledge.FluidIcons.of(stack);
        if (ingredient != null) {
            // A liquid or a gas as itself, with how much in the tooltip; a click opens its bucket or
            // canister, or its own page where it is carried in nothing.
            FluidIconRenderer.render(graphics, ingredient, x, y);
            var container = de.ipnats.hardwrought.knowledge.FluidIcons.container(ingredient);
            hotspots.add(new Hotspot(x, y, container == null ? stack : new ItemStack(container), true,
                    List.of(ingredient.name(), ingredient.quantity().copy().withStyle(net.minecraft.ChatFormatting.GRAY)),
                    true));
            return;
        }
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
        if (clickRibbons(event)) return true;
        if (mode == MODE_HOME) {
            // From the front page a ribbon leads straight to its entry, as from any other.
            for (Hotspot hotspot : hotspots) {
                if (!hotspot.contains(event.x(), event.y())) continue;
                if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) CompendiumClient.openUsages(subjectOf(hotspot.stack()));
                else CompendiumClient.openRecipes(subjectOf(hotspot.stack()));
                return true;
            }
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
            goUp();
            return true;
        }
        if (inside(event.x(), event.y(), leftContentX() + STRUCTURE_BUTTON_OFFSET, top + CONTROLS_Y,
                CONTENT_WIDTH - STRUCTURE_BUTTON_OFFSET, 18) && openStructure()) {
            return true;
        }
        if (treeSubject() != null && inside(event.x(), event.y(), treeButtonX(), treeButtonY(),
                TREE_BUTTON_WIDTH, BUTTON_HEIGHT)) {
            minecraft.gui.setScreen(new CraftingTreeScreen(this, treeSubject()));
            return true;
        }
        if (mode == CompendiumPagePayload.MODE_JOURNAL) {
            // A chapter on the contents page, or one of the bookmarks over the book.
            for (int[] row : chapterRows) {
                if (!inside(event.x(), event.y(), row[0], row[1], row[2], row[3])) continue;
                page = row[4];
                journalScroll = 0;
                return true;
            }
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
                        && event.y() >= y && event.y() < y + TAB_ROW) {
                    // Another shelf is asked the same question: what was being looked for stays in the field.
                    MultiblockSection.leave();
                    CompendiumClient.openShelf(category, searchText);
                    return true;
                }
                y += TAB_ROW;
            }
            if (event.x() >= leftContentX() && event.x() < leftContentX() + TAB_WIDTH
                    && event.y() >= y && event.y() < y + TAB_ROW) {
                MultiblockSection.select();
                page = 0;
                return true;
            }
            if (MultiblockSection.open() && clickMultiblock(event.x(), event.y())) return true;
        }
        for (Hotspot hotspot : hotspots) {
            if (!hotspot.contains(event.x(), event.y()) || !hotspot.navigable()) continue;
            // Left for how it is made, right for what it is used in: the two questions a browser
            // exists to answer, and they work on a shadow too.
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                CompendiumClient.openUsages(subjectOf(hotspot.stack()));
            } else {
                CompendiumClient.openRecipes(subjectOf(hotspot.stack()));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && mode != MODE_HOME) {
            // A page of cards too tall for the paper scrolls first, and turns once it is at its end.
            boolean recipePage = mode == CompendiumPagePayload.MODE_RECIPES || mode == CompendiumPagePayload.MODE_USAGES;
            int step = (int) Math.signum(scrollY) * LINE_HEIGHT * 2;
            // The lines about the subject, where they run past the left page, scroll under the pointer there.
            if (recipePage && infoOverflow > 0 && mouseX < rightPageX()
                    && ((step < 0 && infoScroll < infoOverflow) || (step > 0 && infoScroll > 0))) {
                infoScroll = Math.clamp(infoScroll - step, 0, infoOverflow);
                return true;
            }
            if (recipePage && cardOverflow > 0 && mouseX >= rightContentX()
                    && ((step < 0 && cardScroll < cardOverflow) || (step > 0 && cardScroll > 0))) {
                cardScroll = Math.clamp(cardScroll - step, 0, cardOverflow);
                return true;
            }
            // A note too long for both pages likewise: a row at a time, then on to the next note.
            int rows = scrollY > 0 ? -1 : 1;
            if (mode == CompendiumPagePayload.MODE_JOURNAL && journalOverflow > 0
                    && ((rows > 0 && journalScroll < journalOverflow) || (rows < 0 && journalScroll > 0))) {
                journalScroll = Math.clamp(journalScroll + rows, 0, journalOverflow);
                return true;
            }
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
        if (CompendiumClient.BOOKMARK.matches(event)) {
            // What the pointer is on, or else the page that is open.
            // A liquid carried in nothing has no item for a ribbon to show.
            Identifier marked = hovered == null ? pageSubject()
                    : de.ipnats.hardwrought.knowledge.FluidIcons.of(hovered.stack()) != null ? null
                    : BuiltInRegistries.ITEM.getKey(hovered.stack().getItem());
            if (marked != null) {
                CompendiumClient.toggleBookmark(marked);
                return true;
            }
        }
        if (hovered != null && CompendiumClient.RECIPES.matches(event)) {
            CompendiumClient.openRecipes(subjectOf(hovered.stack()));
            return true;
        }
        if (hovered != null && CompendiumClient.USAGES.matches(event)) {
            CompendiumClient.openUsages(subjectOf(hovered.stack()));
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
