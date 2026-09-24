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
            // Ultra only: nobody hands the player a pack there, so carrying anything at all is the
            // very first problem. Cord from leaves, a needle from a stick, cloth, and a pack of cloth.
            new JournalEntry(Hardwrought.id("something_to_carry"),
                    List.of(Hardwrought.id("starter_backpack")), List.of(), false, true),
            new JournalEntry(Hardwrought.id("more_on_my_back"),
                    List.of(Hardwrought.id("frame_backpack")), List.of(Hardwrought.id("starter_backpack")), false, true),
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
            // Metal is worked hot, on something that takes a blow, by hands that can hold it: all three.
            new JournalEntry(Hardwrought.id("at_the_anvil"),
                    List.of(Hardwrought.id("smithing_gloves"), Hardwrought.id("wooden_anvil"),
                            Hardwrought.id("hammer")),
                    List.of(Hardwrought.id("brick_furnace"), vanilla("raw_iron")), true),
            new JournalEntry(Hardwrought.id("rust_in_the_rock"),
                    List.of(Hardwrought.id("iron_hatchet"), Hardwrought.id("iron_pickaxe")),
                    List.of(vanilla("iron_ingot"))),
            new JournalEntry(Hardwrought.id("fire_and_water"),
                    List.of(Hardwrought.id("forge"), Hardwrought.id("bellows")),
                    List.of(Hardwrought.id("iron_pickaxe_head"), Hardwrought.id("iron_axe_head"),
                            Hardwrought.id("iron_sword_blade"), Hardwrought.id("iron_pickaxe"),
                            Hardwrought.id("iron_hatchet"))),
            // Metals only mix once they are ground, and grinding is the first work done by a machine.
            // The crusher, the crank that turns it and the shaft that carries the turning: all three,
            // each shown on its own, because any one of them alone grinds nothing.
            new JournalEntry(Hardwrought.id("breaking_it_finer"),
                    List.of(Hardwrought.id("starter_crusher"), Hardwrought.id("hand_crank"),
                            Hardwrought.id("shaft")),
                    List.of(vanilla("raw_copper"), Hardwrought.id("raw_tin")), true),
            new JournalEntry(Hardwrought.id("two_soft_metals"),
                    List.of(Hardwrought.id("bronze_ingot"), Hardwrought.id("bronze_mixture"),
                            Hardwrought.id("bronze_hatchet")),
                    List.of(Hardwrought.id("copper_powder"), Hardwrought.id("tin_powder"))),
            new JournalEntry(Hardwrought.id("nails_for_the_bench"),
                    List.of(Hardwrought.id("nailed_workbench"), Hardwrought.id("bronze_nails"),
                            Hardwrought.id("hammer")),
                    List.of(Hardwrought.id("bronze_ingot")))
    );

    /** Whether the running world is an Ultra one; set when the server starts. */
    private static volatile boolean ultra;

    private Journal() { }

    /** Called when a server starts: entries for Ultra worlds are only read in one. */
    public static void setUltra(boolean value) {
        ultra = value;
    }

    /** The chain as written, whatever any player knows of it. Visible for tests. */
    public static List<JournalEntry> chain() {
        return CHAIN;
    }

    /**
     * Where one player stands on one entry. An entry counts as followed the moment the player has
     * held any of the things it points at — or all of them, for an entry that needs all — however
     * they came by it, because the book is a record of what is true, not of who did the work.
     */
    public static int state(PlayerKnowledge knowledge, JournalEntry entry) {
        if (followed(knowledge, entry)) return DONE;
        if (entry.after().isEmpty()) return OPEN;
        for (Identifier seen : entry.after()) {
            if (knowledge.level(seen).named()) return OPEN;
        }
        return HIDDEN;
    }

    /** Whether the player holds what the entry asks for: any one of it, or all of it. */
    private static boolean followed(PlayerKnowledge knowledge, JournalEntry entry) {
        if (entry.teaches().isEmpty()) return false;
        for (Identifier taught : entry.teaches()) {
            boolean held = knowledge.level(taught).named();
            if (held && !entry.needsAll()) return true;
            if (!held && entry.needsAll()) return false;
        }
        return entry.needsAll();
    }

    /** The whole chain as this player reads it, hidden entries included. */
    public static List<CompendiumPagePayload.Note> page(PlayerKnowledge knowledge) {
        List<CompendiumPagePayload.Note> notes = new ArrayList<>(CHAIN.size());
        for (JournalEntry entry : CHAIN) {
            if (notes.size() >= CompendiumPagePayload.MAX_NOTES) break;
            if (entry.ultraOnly() && !ultra) continue;
            int state = state(knowledge, entry);
            notes.add(new CompendiumPagePayload.Note(entry.id(), subject(knowledge, entry, state),
                    state, parts(knowledge, entry, state)));
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
        if (entry.needsAll()) {
            // Part of the way there: point at the piece still missing, which is drawn as a shadow
            // and so says exactly what the player has yet to find.
            for (Identifier taught : entry.teaches()) {
                if (!knowledge.level(taught).named()) return taught;
            }
        }
        return entry.subject();
    }

    /**
     * Every piece of a thought that takes several things together, each with whether the player
     * has held it. Nothing for an unread thought, for the same reason it has no icon.
     */
    private static List<CompendiumPagePayload.Part> parts(PlayerKnowledge knowledge, JournalEntry entry,
                                                          int state) {
        if (state == HIDDEN || !entry.needsAll()) return List.of();
        List<CompendiumPagePayload.Part> parts = new ArrayList<>();
        for (Identifier taught : entry.teaches()) {
            parts.add(new CompendiumPagePayload.Part(taught, knowledge.level(taught).named()));
        }
        return parts;
    }

    private static Identifier vanilla(String path) {
        return Identifier.withDefaultNamespace(path);
    }
}
