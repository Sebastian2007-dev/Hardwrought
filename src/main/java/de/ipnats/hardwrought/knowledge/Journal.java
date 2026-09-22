package de.ipnats.hardwrought.knowledge;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.networking.CompendiumPagePayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The second half of the compendium: what the player has been thinking, in the order they thought it.
 *
 * <p>The knowledge side answers questions a player already knows to ask — how is this made, what is
 * this for. It cannot tell anyone that a standing log can be hewn into a bench, because nobody looks
 * up a thing they have never heard of. That is what this chain is for: a handful of written notes
 * that say what the problem is and roughly where the answer lies, and stop there.
 *
 * <p>Every entry is deliberately short of an answer. It names the material, the place or the act,
 * never the grid. Finding the pattern is still the player's work, and the knowledge side is where
 * they go once they have the first piece in hand.
 *
 * <p>An entry the player is not ready for is not hidden away; it is drawn as unreadable scrawl, the
 * same bargain the black shadows on the knowledge side strike. The shape of what is ahead stays
 * visible, its content does not.
 */
public final class Journal {
    /** Not yet thought: the player has seen nothing that would prompt it. */
    public static final int HIDDEN = 0;
    /** Readable, and not yet acted on. */
    public static final int OPEN = 1;
    /** Readable, and the player has since held what it points at. */
    public static final int DONE = 2;

    /**
     * The chain, in reading order. Each entry opens on something the previous one leads to, so a
     * player who follows the book from the top is walked from bare hands to iron without ever being
     * handed a recipe.
     */
    private static final List<JournalEntry> CHAIN = List.of(
            // Nothing is required: a player holding the book has already stripped leaves for it.
            new JournalEntry(Hardwrought.id("first_edge"),
                    List.of(Hardwrought.id("flint_hatchet"), Hardwrought.id("flint_pickaxe"),
                            Hardwrought.id("flint_dagger"), Hardwrought.id("flint_shovel"),
                            Hardwrought.id("flint_sword"), Hardwrought.id("flint_hoe")),
                    List.of()),
            new JournalEntry(Hardwrought.id("bench_from_the_trunk"),
                    List.of(Hardwrought.id("hewn_workbench")),
                    List.of(Hardwrought.id("flint_hatchet"), Hardwrought.id("flint_shard"))),
            new JournalEntry(Hardwrought.id("firmer_than_flint"),
                    List.of(Hardwrought.id("stone_hatchet"), Hardwrought.id("stone_pickaxe"),
                            Hardwrought.id("cobblestone_piece")),
                    List.of(vanilla("cobblestone"), Hardwrought.id("flint_pickaxe"))),
            new JournalEntry(Hardwrought.id("heat_that_holds"),
                    List.of(Hardwrought.id("brick_furnace"), vanilla("brick")),
                    List.of(vanilla("clay_ball"), vanilla("clay"), Hardwrought.id("cobblestone_piece"))),
            new JournalEntry(Hardwrought.id("rust_in_the_rock"),
                    List.of(Hardwrought.id("iron_hatchet"), Hardwrought.id("iron_pickaxe")),
                    List.of(Hardwrought.id("brick_furnace"), vanilla("raw_iron"))),
            new JournalEntry(Hardwrought.id("two_soft_metals"),
                    List.of(Hardwrought.id("bronze_ingot"), Hardwrought.id("bronze_hatchet")),
                    List.of(vanilla("copper_ingot"), Hardwrought.id("raw_tin")))
    );

    private Journal() { }

    /** The chain as written, whatever any player knows of it. Visible for tests. */
    public static List<JournalEntry> chain() {
        return CHAIN;
    }

    /**
     * Where one player stands on one entry. An entry counts as followed the moment the player has
     * held any of the things it points at — however they came by it, because the book is a record of
     * what is true, not of who did the work.
     */
    public static int state(PlayerKnowledge knowledge, JournalEntry entry) {
        for (Identifier taught : entry.teaches()) {
            if (knowledge.level(taught).named()) return DONE;
        }
        if (entry.after().isEmpty()) return OPEN;
        for (Identifier seen : entry.after()) {
            if (knowledge.level(seen).named()) return OPEN;
        }
        return HIDDEN;
    }

    /** The whole chain as this player reads it, hidden entries included. */
    public static List<CompendiumPagePayload.Note> page(PlayerKnowledge knowledge) {
        List<CompendiumPagePayload.Note> notes = new ArrayList<>(CHAIN.size());
        for (JournalEntry entry : CHAIN) {
            if (notes.size() >= CompendiumPagePayload.MAX_NOTES) break;
            int state = state(knowledge, entry);
            notes.add(new CompendiumPagePayload.Note(entry.id(), subject(knowledge, entry, state),
                    state));
        }
        return List.copyOf(notes);
    }

    /**
     * Which of the things an entry points at is drawn beside it.
     *
     * <p>A thought the player has not had yet has none: its icon would name its subject, and that is
     * exactly what the scrawl is hiding. A followed entry shows the one the player actually holds
     * rather than the first in the list, so that the browser may draw it as itself — an entry whose
     * icon is a black shadow is one there is still work in, and that has to stay true.
     */
    private static Identifier subject(PlayerKnowledge knowledge, JournalEntry entry, int state) {
        if (state == HIDDEN) return null;
        if (state == DONE) {
            for (Identifier taught : entry.teaches()) {
                if (knowledge.level(taught).named()) return taught;
            }
        }
        return entry.subject();
    }

    private static Identifier vanilla(String path) {
        return Identifier.withDefaultNamespace(path);
    }
}
