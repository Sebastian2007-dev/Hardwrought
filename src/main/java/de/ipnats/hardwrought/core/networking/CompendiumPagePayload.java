package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One page of the compendium, answered by the server.
 *
 * <p>The server answers rather than the client looking things up, because the client must not be
 * told what the player has not found out. Every stack on the page therefore travels with the level
 * the player has reached on it, and the browser draws the unknown ones as a black shadow with a name
 * of question marks — section 81 in the specification. The recipe itself is not hidden: a player who
 * has an iron ingot may look up what it is used in, and the things they have never held are shapes
 * in the dark rather than absent rows.
 *
 * @param mode    what was asked for: a shelf, how a thing is made, or what it is used in
 * @param subject the shelf being shown, or the entry being looked up
 * @param entries for a shelf: what stands on it, each with how well this player knows it
 * @param recipes for a lookup: the recipes, already resolved to the stacks the browser draws
 * @param sources for a lookup: where the thing is found when nobody makes it — what drops it, what
 *                it is broken out of, what chest it turns up in
 * @param journal for the journal: the whole chain of written thoughts, each with how far this
 *                player has come along it
 */
public record CompendiumPagePayload(int mode, Identifier subject, List<Entry> entries,
                                    List<Recipe> recipes, List<Source> sources,
                                    List<Note> journal) implements CustomPacketPayload {
    public static final Type<CompendiumPagePayload> TYPE = new Type<>(Hardwrought.id("compendium_page_v1"));

    /** Shelf mode: everything on one shelf, known and unknown alike. */
    public static final int MODE_SHELF = 0;
    /** How the subject is made. */
    public static final int MODE_RECIPES = 1;
    /** What the subject is used in. */
    public static final int MODE_USAGES = 2;
    /** The written half of the book: what the player has worked out that they might try next. */
    public static final int MODE_JOURNAL = 3;

    /** Manufacturing-method bookmarks shown across the top edge of a lookup page. */
    public static final int METHOD_CRAFTING = 0;
    public static final int METHOD_LOOT = 1;
    public static final int METHOD_COOKING = 2;
    public static final int METHOD_SMITHING = 3;
    public static final int METHOD_SMELTING = 4;
    public static final int METHOD_IN_WORLD = 5;

    /** Never send more than this in one page, so a browser cannot be used to flood a connection. */
    public static final int MAX_ENTRIES = 1024;
    public static final int MAX_RECIPES = 64;
    /** A crafting grid slot, a furnace slot: nothing the browser draws needs more than this. */
    public static final int MAX_SLOTS = 16;
    /** Iron turns up in a great many chests; the browser shows the first of them, not all. */
    public static final int MAX_SOURCES = 16;
    /** How many alternatives one slot may cycle through, for an ingredient that is a whole tag. */
    public static final int MAX_OPTIONS = 16;
    /** The journal is a handwritten chain, not a registry dump; it never grows past this. */
    public static final int MAX_NOTES = 64;

    /** One line of a shelf: what it is, and how well it is known. */
    public record Entry(Identifier id, int level) { }

    /**
     * One written thought.
     *
     * <p>Only the name travels. The title and the paragraph under it are looked up from the name in
     * the language file on the client, so a player cannot read the rest of the chain out of a packet
     * they were sent for the first page.
     *
     * @param id      names the entry, and with it the two translation keys the browser reads
     * @param subject what the entry points at, so the browser can draw it and jump to its recipe;
     *                null while the entry is still unreadable
     * @param state   one of {@code Journal.HIDDEN}, {@code OPEN} or {@code DONE}
     */
    public record Note(Identifier id, Identifier subject, int state) { }

    /**
     * One place a thing is found.
     *
     * @param kind  a mob, a block that drops it, or a container it is stocked in
     * @param id    the entity type, the block item, or the loot table
     * @param level for a block, what the player knows of it; a mob and a chest are places, not items
     */
    public record Source(int kind, Identifier id, int level) { }

    /** One stack the browser draws, with what the player knows about the item behind it. */
    public record Known(ItemStack stack, int level) { }

    /**
     * One slot of a recipe. An ingredient written as a tag has several options and the browser
     * cycles them the way a recipe book does; an empty list is a blank square.
     */
    public record Slot(List<Known> options) {
        public static final Slot EMPTY = new Slot(List.of());

        public Slot {
            options = List.copyOf(options);
        }

        public boolean isEmpty() {
            return options.isEmpty();
        }
    }

    /**
     * One recipe as the browser draws it.
     *
     * @param id      the recipe, so a page can be told apart and logged
     * @param inputs  the ingredients in reading order, blanks included for a shaped grid
     * @param width   the grid width where the recipe has a shape, otherwise zero
     * @param height  the grid height where the recipe has a shape, otherwise zero
     * @param result  what comes out
     * @param station what it is made on, empty for the inventory grid
     * @param method  one of the manufacturing-method bookmarks above
     */
    public record Recipe(Identifier id, List<Slot> inputs, int width, int height, Slot result,
                         Slot station, int method) {
        public Recipe {
            inputs = List.copyOf(inputs);
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, CompendiumPagePayload> CODEC =
            new StreamCodec<>() {
        @Override
        public CompendiumPagePayload decode(RegistryFriendlyByteBuf buffer) {
            int mode = buffer.readVarInt();
            Identifier subject = buffer.readIdentifier();
            int entryCount = Math.min(buffer.readVarInt(), MAX_ENTRIES);
            List<Entry> entries = new ArrayList<>(entryCount);
            for (int index = 0; index < entryCount; index++) {
                entries.add(new Entry(buffer.readIdentifier(), buffer.readVarInt()));
            }
            int recipeCount = Math.min(buffer.readVarInt(), MAX_RECIPES);
            List<Recipe> recipes = new ArrayList<>(recipeCount);
            for (int index = 0; index < recipeCount; index++) {
                Identifier id = buffer.readIdentifier();
                int slotCount = Math.min(buffer.readVarInt(), MAX_SLOTS);
                List<Slot> inputs = new ArrayList<>(slotCount);
                for (int slot = 0; slot < slotCount; slot++) inputs.add(decodeSlot(buffer));
                recipes.add(new Recipe(id, inputs, buffer.readVarInt(), buffer.readVarInt(),
                        decodeSlot(buffer), decodeSlot(buffer), buffer.readVarInt()));
            }
            int sourceCount = Math.min(buffer.readVarInt(), MAX_SOURCES);
            List<Source> sources = new ArrayList<>(sourceCount);
            for (int index = 0; index < sourceCount; index++) {
                sources.add(new Source(buffer.readVarInt(), buffer.readIdentifier(), buffer.readVarInt()));
            }
            int noteCount = Math.min(buffer.readVarInt(), MAX_NOTES);
            List<Note> journal = new ArrayList<>(noteCount);
            for (int index = 0; index < noteCount; index++) {
                Identifier note = buffer.readIdentifier();
                Identifier points = buffer.readBoolean() ? buffer.readIdentifier() : null;
                journal.add(new Note(note, points, buffer.readVarInt()));
            }
            return new CompendiumPagePayload(mode, subject, entries, recipes, sources, journal);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, CompendiumPagePayload page) {
            buffer.writeVarInt(page.mode);
            buffer.writeIdentifier(page.subject);
            buffer.writeVarInt(page.entries.size());
            for (Entry entry : page.entries) {
                buffer.writeIdentifier(entry.id());
                buffer.writeVarInt(entry.level());
            }
            buffer.writeVarInt(page.recipes.size());
            for (Recipe recipe : page.recipes) {
                buffer.writeIdentifier(recipe.id());
                buffer.writeVarInt(recipe.inputs().size());
                for (Slot slot : recipe.inputs()) encodeSlot(buffer, slot);
                buffer.writeVarInt(recipe.width());
                buffer.writeVarInt(recipe.height());
                encodeSlot(buffer, recipe.result());
                encodeSlot(buffer, recipe.station());
                buffer.writeVarInt(recipe.method());
            }
            buffer.writeVarInt(page.sources.size());
            for (Source source : page.sources) {
                buffer.writeVarInt(source.kind());
                buffer.writeIdentifier(source.id());
                buffer.writeVarInt(source.level());
            }
            buffer.writeVarInt(page.journal.size());
            for (Note note : page.journal) {
                buffer.writeIdentifier(note.id());
                buffer.writeBoolean(note.subject() != null);
                if (note.subject() != null) buffer.writeIdentifier(note.subject());
                buffer.writeVarInt(note.state());
            }
        }

        private Slot decodeSlot(RegistryFriendlyByteBuf buffer) {
            int count = Math.min(buffer.readVarInt(), MAX_OPTIONS);
            if (count == 0) return Slot.EMPTY;
            List<Known> options = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                options.add(new Known(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readVarInt()));
            }
            return new Slot(options);
        }

        private void encodeSlot(RegistryFriendlyByteBuf buffer, Slot slot) {
            buffer.writeVarInt(slot.options().size());
            for (Known known : slot.options()) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, known.stack());
                buffer.writeVarInt(known.level());
            }
        }
    };

    public CompendiumPagePayload {
        entries = List.copyOf(entries);
        recipes = List.copyOf(recipes);
        sources = List.copyOf(sources);
        journal = List.copyOf(journal);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
