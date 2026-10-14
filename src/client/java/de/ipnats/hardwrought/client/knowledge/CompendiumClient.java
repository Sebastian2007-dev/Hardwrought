package de.ipnats.hardwrought.client.knowledge;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.client.HardwroughtKeys;
import de.ipnats.hardwrought.core.networking.BookmarkPayloads;
import de.ipnats.hardwrought.core.networking.CompendiumPagePayload;
import de.ipnats.hardwrought.core.networking.CompendiumRequestPayload;
import de.ipnats.hardwrought.core.networking.KnowledgeNotePayload;
import de.ipnats.hardwrought.knowledge.KnowledgeCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The client half of the compendium: the keys that open it and the page the server last sent.
 *
 * <p>Nothing here decides what a player knows. The client asks and draws; the server answers with a
 * page in which every stack already carries its knowledge level. That is what keeps a packet sniffer
 * from reading the whole tech tree off the wire before the player has found a single ingot.
 */
public final class CompendiumClient {
    /** Opens the compendium on its front page, where its two halves are chosen between. */
    public static final KeyMapping OPEN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.hardwrought.compendium", InputConstants.Type.KEYBOARD, InputConstants.KEY_B,
            HardwroughtKeys.CATEGORY));
    /** Over an item, anywhere: how is this made. */
    public static final KeyMapping RECIPES = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.hardwrought.compendium_recipes", InputConstants.Type.KEYBOARD, InputConstants.KEY_R,
            HardwroughtKeys.CATEGORY));
    /** Over an item, anywhere: what is this used in. */
    public static final KeyMapping USAGES = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.hardwrought.compendium_usages", InputConstants.Type.KEYBOARD, InputConstants.KEY_U,
            HardwroughtKeys.CATEGORY));

    /** Over an entry or on its page: lay a ribbon there, or take it out. */
    public static final KeyMapping BOOKMARK = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.hardwrought.compendium_bookmark", InputConstants.Type.KEYBOARD, InputConstants.KEY_K,
            HardwroughtKeys.CATEGORY));

    /** The front page and the journal belong to no shelf and no item, so they answer to this. */
    public static final Identifier BOOK = Identifier.fromNamespaceAndPath("hardwrought", "book");

    private static CompendiumPagePayload page;
    /** The crafting tree the server last sent, or null while one is on its way. */
    private static CompendiumPagePayload tree;
    /**
     * Which recipe the player chose for a thing in a crafting tree, by its index among the thing's
     * recipes, or -1 to treat it as found rather than made. Kept for the session, the way a recipe
     * viewer remembers it, so every tree that reaches the same thing follows the same choice.
     */
    private static final java.util.Map<Identifier, Integer> treeChoices = new java.util.HashMap<>();
    /** Whether a crafting tree counts what the player already carries. */
    private static boolean treeUsesInventory = true;
    private static KnowledgeCategory shelf = KnowledgeCategory.MATERIALS;
    /** The ribbons in the book, as the server last listed them. */
    private static java.util.List<CompendiumPagePayload.Entry> bookmarks = java.util.List.of();

    private CompendiumClient() { }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(CompendiumPagePayload.TYPE, (payload, context) -> {
            // A tree is drawn over the page it was opened from, which must still be there to go back to.
            if (payload.mode() == CompendiumPagePayload.MODE_TREE) tree = payload;
            else page = payload;
        });
        ClientPlayNetworking.registerGlobalReceiver(KnowledgeNotePayload.TYPE, (payload, context) ->
                // The toast is read by the renderer, so it is only ever written from the same thread.
                context.client().execute(() -> {
                    var toasts = context.client().gui.toastManager();
                    for (KnowledgeNotePayload.Note note : payload.notes()) {
                        KnowledgeToast.addOrUpdate(toasts, note);
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(BookmarkPayloads.Marks.TYPE, (payload, context) ->
                context.client().execute(() -> bookmarks = payload.entries()));
        de.ipnats.hardwrought.knowledge.CompendiumItem.opener = CompendiumClient::openHome;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Only fires with no screen open; inside a screen the container mixin and the compendium
            // screen itself handle the same three keys.
            while (OPEN.consumeClick()) openHome();
            while (RECIPES.consumeClick()) lookAtHeldItem(client, CompendiumPagePayload.MODE_RECIPES);
            while (USAGES.consumeClick()) lookAtHeldItem(client, CompendiumPagePayload.MODE_USAGES);
        });
    }

    private static void reset() {
        page = null;
        tree = null;
        treeChoices.clear();
        shelf = KnowledgeCategory.MATERIALS;
        bookmarks = java.util.List.of();
    }

    /** The ribbons in the book, oldest first. */
    public static java.util.List<CompendiumPagePayload.Entry> bookmarks() {
        return bookmarks;
    }

    public static boolean bookmarked(Identifier entry) {
        for (CompendiumPagePayload.Entry mark : bookmarks) if (mark.id().equals(entry)) return true;
        return false;
    }

    /** Lays a ribbon at an entry, or takes out the one that is there. The server keeps them. */
    public static void toggleBookmark(Identifier entry) {
        if (entry != null && ClientPlayNetworking.canSend(BookmarkPayloads.Toggle.TYPE)) {
            ClientPlayNetworking.send(new BookmarkPayloads.Toggle(entry));
        }
    }

    /** The crafting tree the server last sent, or null while one is still on its way. */
    public static CompendiumPagePayload tree() {
        return tree;
    }

    /** Asks for everything that goes into one thing; the answer arrives in {@link #tree()}. */
    public static void requestTree(Identifier subject) {
        if (subject == null || !ClientPlayNetworking.canSend(CompendiumRequestPayload.TYPE)) return;
        tree = null;
        ClientPlayNetworking.send(new CompendiumRequestPayload(CompendiumPagePayload.MODE_TREE, subject, ""));
    }

    /** The recipe chosen for a thing in a crafting tree, or null where the player has not chosen. */
    public static Integer treeChoice(Identifier thing) {
        return treeChoices.get(thing);
    }

    public static void chooseInTree(Identifier thing, int choice) {
        treeChoices.put(thing, choice);
    }

    public static boolean treeUsesInventory() {
        return treeUsesInventory;
    }

    public static void setTreeUsesInventory(boolean uses) {
        treeUsesInventory = uses;
    }

    /** The page the server last sent, or null while one is still on its way. */
    public static CompendiumPagePayload page() {
        return page;
    }

    /** The shelf the browser returns to when it is opened without a subject. */
    public static KnowledgeCategory shelf() {
        return shelf;
    }

    public static void setShelf(KnowledgeCategory category) {
        if (category != null) shelf = category;
    }

    // ---------------------------------------------------------------- asking

    /**
     * Opens the book where it is meant to be opened: on the page that asks which half is wanted.
     *
     * <p>Nothing is asked of the server for it. The front page says the same thing to every player,
     * and a round trip before the book even appears would be felt on a laggy connection.
     */
    public static void openHome() {
        // The front page asks the server for nothing else, so the ribbons are asked for by name.
        if (ClientPlayNetworking.canSend(BookmarkPayloads.Ask.TYPE)) ClientPlayNetworking.send(new BookmarkPayloads.Ask());
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() instanceof CompendiumScreen screen) screen.showHome();
        else client.gui.setScreen(new CompendiumScreen(CompendiumScreen.MODE_HOME, BOOK));
    }

    /** Opens the written half: the chain of thoughts, as far as this player has come along it. */
    public static void openJournal() {
        open(CompendiumPagePayload.MODE_JOURNAL, BOOK, "");
    }

    /** Opens the browser on one shelf, optionally filtered by what is typed in the search field. */
    public static void openShelf(KnowledgeCategory category, String search) {
        setShelf(category);
        open(CompendiumPagePayload.MODE_SHELF,
                Identifier.fromNamespaceAndPath("hardwrought", shelf.serializedName()), search);
    }

    /** Opens the browser on how one item is made. */
    public static void openRecipes(Item item) {
        openRecipes(BuiltInRegistries.ITEM.getKey(item));
    }

    /** Opens the browser on how one thing is made: an item, or a liquid or gas with a page of its own. */
    public static void openRecipes(Identifier id) {
        if (id != null) open(CompendiumPagePayload.MODE_RECIPES, id, "");
    }

    /** Opens the browser on what one item is used in. */
    public static void openUsages(Item item) {
        openUsages(BuiltInRegistries.ITEM.getKey(item));
    }

    /** Opens the browser on what one thing is used in: an item, or a liquid or gas with a page of its own. */
    public static void openUsages(Identifier id) {
        if (id != null) open(CompendiumPagePayload.MODE_USAGES, id, "");
    }

    /**
     * Sends a request and makes sure the browser is the screen looking at the answer. Called from
     * the inventory as well, which is why it opens the screen rather than assuming it is open.
     */
    public static void open(int mode, Identifier subject, String search) {
        if (!ClientPlayNetworking.canSend(CompendiumRequestPayload.TYPE)) return;
        page = null;
        ClientPlayNetworking.send(new CompendiumRequestPayload(mode, subject, search));
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() instanceof CompendiumScreen screen) screen.showing(mode, subject, search);
        else client.gui.setScreen(new CompendiumScreen(mode, subject));
    }

    /** Refreshes the open page without disturbing the browser history. */
    public static void refresh(int mode, Identifier subject, String search) {
        if (!ClientPlayNetworking.canSend(CompendiumRequestPayload.TYPE)) return;
        page = null;
        ClientPlayNetworking.send(new CompendiumRequestPayload(mode, subject, search));
    }

    private static void lookAtHeldItem(Minecraft client, int mode) {
        if (client.player == null) return;
        ItemStack held = client.player.getMainHandItem();
        if (held.isEmpty()) held = client.player.getOffhandItem();
        if (held.isEmpty()) {
            openHome();
            return;
        }
        if (mode == CompendiumPagePayload.MODE_USAGES) openUsages(held.getItem());
        else openRecipes(held.getItem());
    }

}
