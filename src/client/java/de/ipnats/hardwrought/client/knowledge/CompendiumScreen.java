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

    private static final int PANEL_WIDTH = 338;
    private static final int PANEL_HEIGHT = 226;
    private static final int TAB_WIDTH = 132;
    private static final int TAB_HEIGHT = 16;
    private static final int SLOT = 18;
    private static final int GRID_COLUMNS = 7;
    private static final int GRID_ROWS = 8;
    private static final int PER_PAGE = GRID_COLUMNS * GRID_ROWS;
    private static final int CARD_HEIGHT = 62;
    private static final int SOURCE_HEIGHT = 20;
    private static final int PAGE_EDGE = 8;
    private static final int PAGE_GUTTER = 14;
    private static final int PAGE_WIDTH = (PANEL_WIDTH - PAGE_EDGE * 2 - PAGE_GUTTER) / 2;
    private static final int CONTENT_MARGIN = 10;
    private static final int CONTENT_WIDTH = PAGE_WIDTH - CONTENT_MARGIN * 2;
    private static final int METHOD_TAB_SIZE = 24;
    private static final int METHOD_COUNT = 6;
    /** How long one option of a tag ingredient is shown before the next one takes its turn. */
    private static final long CYCLE_MILLIS = 1000L;

    /** The two halves on the front page, side by side with a gutter between them. */
    private static final int HALF_WIDTH = 140;
    private static final int HALF_HEIGHT = 122;
    private static final int HALF_TOP = 52;
    private static final int HALF_GAP = 18;

    private static final Identifier COVER_SPRITE = sprite("cover");
    private static final Identifier LEFT_PAGE_SPRITE = sprite("page_left");
    private static final Identifier RIGHT_PAGE_SPRITE = sprite("page_right");
    private static final Identifier BINDING_SPRITE = sprite("binding");
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
    private static final Identifier[] METHOD_SPRITES = {
            sprite("method_crafting"), sprite("method_loot"), sprite("method_cooking"),
            sprite("method_smithing"), sprite("method_smelting"), sprite("method_in_world")
    };

    private static final int TEXT_COLOR = 0xFF241D16;
    private static final int FADED_COLOR = 0xFF514331;
    private static final int RULE_COLOR = 0x80524130;
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

    private int leftPageX() { return left + PAGE_EDGE; }
    private int rightPageX() { return left + PANEL_WIDTH / 2 + PAGE_GUTTER / 2; }
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
            search = new EditBox(font, rightContentX() + 4, top + 12, CONTENT_WIDTH - 8, 16,
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
            return Math.max(1, (current.sources().size() + 13) / 14);
        }
        return Math.max(1, (recipesFor(current, method).size() + 1) / 2);
    }

    private List<CompendiumPagePayload.Recipe> recipesFor(CompendiumPagePayload current, int method) {
        return current.recipes().stream().filter(recipe -> recipe.method() == method).toList();
    }

    private boolean methodAvailable(CompendiumPagePayload current, int method) {
        return method == CompendiumPagePayload.METHOD_LOOT
                ? !current.sources().isEmpty() : !recipesFor(current, method).isEmpty();
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
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, COVER_SPRITE,
                left - 4, top - 5, PANEL_WIDTH + 8, PANEL_HEIGHT + 10);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LEFT_PAGE_SPRITE,
                leftPageX(), top, PAGE_WIDTH, PANEL_HEIGHT);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, RIGHT_PAGE_SPRITE,
                rightPageX(), top, PAGE_WIDTH, PANEL_HEIGHT);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BINDING_SPRITE,
                left + PANEL_WIDTH / 2 - 4, top + 5, 8, PANEL_HEIGHT - 10);
        if (mode == CompendiumPagePayload.MODE_SHELF) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SEARCH_SPRITE,
                    rightContentX(), top + 9, CONTENT_WIDTH, 20);
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
            graphics.text(font, Component.translatable("gui.hardwrought.compendium.journal"),
                    leftContentX(), top + 12, FADED_COLOR);
            if (current == null) {
                graphics.text(font, Component.translatable("gui.hardwrought.compendium.loading"),
                        leftContentX(), top + 34, FADED_COLOR);
            } else {
                drawJournal(graphics, current);
            }
        } else {
            drawTabs(graphics, mouseX, mouseY);
            if (mode != CompendiumPagePayload.MODE_SHELF) drawMethodTabs(graphics, current, mouseX, mouseY);
            int headingX = mode == CompendiumPagePayload.MODE_SHELF ? rightContentX() : leftContentX();
            int headingY = mode == CompendiumPagePayload.MODE_SHELF ? top + 34 : top + 28;
            graphics.textWithWordWrap(font, heading(), headingX, headingY, CONTENT_WIDTH, TEXT_COLOR);
            if (current == null) {
                graphics.text(font, Component.translatable("gui.hardwrought.compendium.loading"),
                        headingX, top + 56, FADED_COLOR);
            } else if (mode == CompendiumPagePayload.MODE_SHELF) {
                drawShelf(graphics, current);
            } else {
                drawRecipes(graphics, current);
            }
        }
        graphics.centeredText(font, Component.translatable(
                        mode == CompendiumPagePayload.MODE_JOURNAL
                                ? "gui.hardwrought.compendium.note" : "gui.hardwrought.compendium.page",
                        page + 1, pageCount()),
                rightPageX() + PAGE_WIDTH / 2, top + PANEL_HEIGHT - 17, FADED_COLOR);
        drawTooltip(graphics, mouseX, mouseY);
    }

    private void drawBookControls(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawBookButton(graphics, leftContentX(), top + PANEL_HEIGHT - 27, 54, 18,
                Component.translatable("gui.hardwrought.compendium.back"), mouseX, mouseY);
        drawBookButton(graphics, leftContentX() + 62, top + PANEL_HEIGHT - 27, 22, 18,
                Component.literal("‹"), mouseX, mouseY);
        drawBookButton(graphics, rightPageX() + PAGE_WIDTH - CONTENT_MARGIN - 22,
                top + PANEL_HEIGHT - 27, 22, 18, Component.literal("›"), mouseX, mouseY);
    }

    private void drawBookButton(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                Component label, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                hovered ? BUTTON_HOVERED_SPRITE : BUTTON_SPRITE, x, y, width, height);
        graphics.centeredText(font, label, x + width / 2, y + 5, TEXT_COLOR);
    }

    private void drawMethodTabs(GuiGraphicsExtractor graphics, CompendiumPagePayload current,
                                int mouseX, int mouseY) {
        int start = left + (PANEL_WIDTH - METHOD_TAB_SIZE * METHOD_COUNT) / 2;
        int active = current == null ? selectedMethod : effectiveMethod(current);
        for (int method = 0; method < METHOD_COUNT; method++) {
            int x = start + method * METHOD_TAB_SIZE;
            int y = top - (method == active ? 8 : 13);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, METHOD_SPRITES[method],
                    x, y, METHOD_TAB_SIZE, METHOD_TAB_SIZE);
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
        graphics.centeredText(font, Component.translatable("gui.hardwrought.compendium"),
                leftPageX() + PAGE_WIDTH / 2, top + 24, TEXT_COLOR);
        graphics.centeredText(font, Component.translatable("gui.hardwrought.compendium.subtitle"),
                rightPageX() + PAGE_WIDTH / 2, top + 24, FADED_COLOR);
        drawHalf(graphics, mouseX, mouseY, homeLeft(0), "knowledge", false);
        drawHalf(graphics, mouseX, mouseY, homeLeft(1), "journal", true);
        graphics.centeredText(font, Component.translatable("gui.hardwrought.compendium.keys"),
                rightPageX() + PAGE_WIDTH / 2, top + PANEL_HEIGHT - 20, FADED_COLOR);
    }

    /** Where one of the two halves starts. Both together are centred in the panel. */
    private int homeLeft(int half) {
        int span = HALF_WIDTH * 2 + HALF_GAP;
        return left + (PANEL_WIDTH - span) / 2 + half * (HALF_WIDTH + HALF_GAP);
    }

    private void drawHalf(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, String name,
                          boolean written) {
        int y = top + HALF_TOP;
        boolean hovered = overHalf(x, mouseX, mouseY);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                hovered ? HALF_HOVERED_SPRITE : HALF_SPRITE, x, y, HALF_WIDTH, HALF_HEIGHT);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                written ? THOUGHTS_EMBLEM_SPRITE : KNOWLEDGE_EMBLEM_SPRITE,
                x + HALF_WIDTH / 2 - 16, y + 16, 32, 32);
        graphics.centeredText(font, Component.translatable("gui.hardwrought.compendium." + name),
                x + HALF_WIDTH / 2, y + 56, TEXT_COLOR);
        graphics.textWithWordWrap(font,
                Component.translatable("gui.hardwrought.compendium." + name + ".about"),
                x + 10, y + 72, HALF_WIDTH - 20, FADED_COLOR);
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
            graphics.text(font, Component.translatable("gui.hardwrought.compendium.no_notes"),
                    leftContentX(), top + 34, FADED_COLOR);
            return;
        }
        landOnCurrentNote(notes);
        CompendiumPagePayload.Note note = notes.get(Math.min(page, notes.size() - 1));
        boolean unread = note.state() == Journal.HIDDEN;
        int textLeft = leftContentX();
        Item subjectItem = note.subject() == null ? null : BuiltInRegistries.ITEM.getValue(note.subject());
        if (subjectItem != null) {
            // Drawn by the same rule as everything else in the book: the thing a note is pointing at
            // is a black shadow until the player has held it, and its own icon afterwards.
            ItemStack stack = new ItemStack(subjectItem);
            boolean known = note.state() == Journal.DONE;
            drawSlotBackground(graphics, leftContentX(), top + 28);
            ShadowItem.render(graphics, stack, leftContentX(), top + 28, known);
            hotspots.add(new Hotspot(leftContentX(), top + 28, stack, known, null, true));
            textLeft = leftContentX() + 22;
        }
        graphics.text(font, unread ? scrawl(18) : Component.translatable(titleKey(note.id())),
                textLeft, top + 32, note.state() == Journal.DONE ? DONE_COLOR : TEXT_COLOR);
        graphics.fill(leftContentX(), top + 51, leftContentX() + CONTENT_WIDTH, top + 52, RULE_COLOR);
        graphics.fill(rightContentX(), top + 51, rightContentX() + CONTENT_WIDTH, top + 52, RULE_COLOR);
        int wrap = CONTENT_WIDTH;
        if (unread) {
            // Not a blank page. The player is meant to see that there is more written ahead, only
            // not to read it — the same bargain the black shadows on the knowledge side strike.
            int y = graphics.textWithWordWrap(font,
                    Component.translatable("gui.hardwrought.compendium.unread"),
                    leftContentX(), top + 60, wrap, FADED_COLOR);
            for (int line = 0; line < 4; line++) {
                graphics.text(font, scrawl(26 + line % 3 * 4), leftContentX(), y + 10 + line * 12,
                        SCRAWL_COLOR);
            }
            return;
        }
        List<FormattedCharSequence> lines = font.split(Component.translatable(textKey(note.id())), wrap);
        int linesPerPage = 13;
        for (int index = 0; index < Math.min(lines.size(), linesPerPage * 2); index++) {
            int x = index < linesPerPage ? leftContentX() : rightContentX();
            graphics.text(font, lines.get(index), x, top + 60 + index % linesPerPage * 10, TEXT_COLOR);
        }
        int followedIndex = Math.min(lines.size() + 1, linesPerPage * 2 - 1);
        int followedX = followedIndex < linesPerPage ? leftContentX() : rightContentX();
        int y = top + 60 + followedIndex % linesPerPage * 10;
        if (note.state() == Journal.DONE) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FOLLOWED_SPRITE,
                    followedX, y, 9, 9);
            graphics.text(font, Component.translatable("gui.hardwrought.compendium.followed"),
                    followedX + 12, y, DONE_COLOR);
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

    private void drawTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (mode != CompendiumPagePayload.MODE_SHELF) return;
        int y = top + 36;
        for (KnowledgeCategory category : KnowledgeCategory.values()) {
            boolean selected = mode == CompendiumPagePayload.MODE_SHELF
                    && category == CompendiumClient.shelf();
            boolean hovered = mouseX >= leftContentX() && mouseX < leftContentX() + TAB_WIDTH
                    && mouseY >= y && mouseY < y + TAB_HEIGHT;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    selected ? TAB_SELECTED_SPRITE : TAB_SPRITE,
                    leftContentX(), y, TAB_WIDTH + (selected ? 3 : 0), TAB_HEIGHT);
            graphics.text(font, Component.translatable(category.translationKey()), leftContentX() + 6, y + 4,
                    selected || hovered ? TEXT_COLOR : FADED_COLOR, false);
            y += TAB_HEIGHT;
        }
    }

    private void drawShelf(GuiGraphicsExtractor graphics, CompendiumPagePayload current) {
        List<CompendiumPagePayload.Entry> entries = current.entries();
        int first = page * PER_PAGE;
        int originX = rightContentX();
        int originY = top + 55;
        for (int index = 0; index < PER_PAGE && first + index < entries.size(); index++) {
            CompendiumPagePayload.Entry entry = entries.get(first + index);
            Item item = BuiltInRegistries.ITEM.getValue(entry.id());
            if (item == null) continue;
            int x = originX + (index % GRID_COLUMNS) * SLOT;
            int y = originY + (index / GRID_COLUMNS) * SLOT;
            slot(graphics, x, y, new ItemStack(item), entry.level() > 0);
        }
        if (entries.isEmpty()) {
            graphics.text(font, Component.translatable("gui.hardwrought.compendium.empty"),
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
        int originY = top + 48;
        if (recipes.isEmpty()) {
            // A feather has no recipe, and saying so is still worth a line — just not the only one.
            graphics.text(font, Component.translatable(current.sources().isEmpty()
                            ? "gui.hardwrought.compendium.no_recipes"
                            : "gui.hardwrought.compendium.not_crafted"),
                    rightContentX(), originY, FADED_COLOR);
            return;
        }
        int first = page * 2;
        for (int index = 0; index < 2 && first + index < recipes.size(); index++) {
            drawCard(graphics, recipes.get(first + index), rightContentX(), originY + index * CARD_HEIGHT);
        }
    }

    /** Where the thing is found when nobody makes it: what drops it, what it is broken out of. */
    private void drawSources(GuiGraphicsExtractor graphics, CompendiumPagePayload current) {
        List<CompendiumPagePayload.Source> sources = current.sources();
        if (sources.isEmpty()) {
            graphics.textWithWordWrap(font, Component.translatable("gui.hardwrought.compendium.no_recipes"),
                    rightContentX(), top + 54, CONTENT_WIDTH, FADED_COLOR);
            return;
        }
        graphics.text(font, Component.translatable("gui.hardwrought.compendium.sources"),
                rightContentX(), top + 30, FADED_COLOR);
        int first = page * 14;
        for (int index = 0; index < 14 && first + index < sources.size(); index++) {
            CompendiumPagePayload.Source source = sources.get(first + index);
            int originX = index < 7 ? leftContentX() : rightContentX();
            int y = top + 50 + index % 7 * SOURCE_HEIGHT;
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
                graphics.text(font, labelLines.getFirst(), originX + 22, y + 4, TEXT_COLOR);
            }
        }
    }

    private void drawCard(GuiGraphicsExtractor graphics, CompendiumPagePayload.Recipe recipe, int x, int y) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CARD_SPRITE,
                x, y, CONTENT_WIDTH, CARD_HEIGHT - 4);
        int columns = recipe.width() > 0 ? recipe.width() : Math.min(3, Math.max(1, recipe.inputs().size()));
        int gridX = x + 5;
        for (int index = 0; index < recipe.inputs().size(); index++) {
            int slotX = gridX + (index % columns) * SLOT;
            int slotY = y + 2 + (index / columns) * SLOT;
            option(graphics, recipe.inputs().get(index), slotX, slotY);
        }
        int arrowX = gridX + 3 * SLOT + 5;
        graphics.text(font, Component.literal("→"), arrowX, y + 22, TEXT_COLOR);
        option(graphics, recipe.result(), arrowX + 13, y + 18);
        if (!recipe.station().isEmpty()) {
            option(graphics, recipe.station(), x + CONTENT_WIDTH - 20, y + 2);
        }
        if (recipe.method() == CompendiumPagePayload.METHOD_IN_WORLD) {
            graphics.textWithWordWrap(font,
                    Component.translatable("recipe." + recipe.id().getNamespace() + "."
                            + recipe.id().getPath() + ".instruction"),
                    x + 5, y + 42, CONTENT_WIDTH - 10, FADED_COLOR);
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
        if (inside(event.x(), event.y(), leftContentX(), top + PANEL_HEIGHT - 27, 54, 18)) {
            goBack();
            return true;
        }
        if (inside(event.x(), event.y(), leftContentX() + 62, top + PANEL_HEIGHT - 27, 22, 18)) {
            turnPage(-1);
            return true;
        }
        if (inside(event.x(), event.y(), rightPageX() + PAGE_WIDTH - CONTENT_MARGIN - 22,
                top + PANEL_HEIGHT - 27, 22, 18)) {
            turnPage(1);
            return true;
        }
        if (mode == CompendiumPagePayload.MODE_RECIPES || mode == CompendiumPagePayload.MODE_USAGES) {
            int start = left + (PANEL_WIDTH - METHOD_TAB_SIZE * METHOD_COUNT) / 2;
            CompendiumPagePayload current = CompendiumClient.page();
            int active = current == null ? selectedMethod : effectiveMethod(current);
            for (int method = 0; method < METHOD_COUNT; method++) {
                int y = top - (method == active ? 8 : 13);
                if (inside(event.x(), event.y(), start + method * METHOD_TAB_SIZE, y,
                        METHOD_TAB_SIZE, METHOD_TAB_SIZE)) {
                    selectedMethod = method;
                    methodChosen = true;
                    page = 0;
                    return true;
                }
            }
        }
        if (mode == CompendiumPagePayload.MODE_SHELF) {
            int y = top + 36;
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
