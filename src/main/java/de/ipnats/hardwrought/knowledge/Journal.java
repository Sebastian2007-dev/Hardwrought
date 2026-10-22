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
 * up a thing they have never heard of. That is what this chain is for: written notes that say what the
 * problem is and roughly where the answer lies, and stop there — from the first flint edge all the way
 * to the ritual at the end of the game.
 *
 * <p>Every entry is deliberately short of an answer. It names the material, the place or the act,
 * never the grid. Finding the pattern is still the player's work, and the knowledge side is where
 * they go once they have the first piece in hand.
 *
 * <p>An entry the player is not ready for is not hidden away; it is drawn as unreadable scrawl, the
 * same bargain the black shadows on the knowledge side strike. The shape of what is ahead stays
 * visible, its content does not.
 *
 * <p>The notes are gathered into chapters, each one a stage of the game, so that a player can see how
 * far they have come and turn to where they are.
 */
public final class Journal {
    /** Not yet thought: the player has seen nothing that would prompt it. */
    public static final int HIDDEN = 0;
    /** Readable, and not yet acted on. */
    public static final int OPEN = 1;
    /** Readable, and the player has since held what it points at. */
    public static final int DONE = 2;

    /** A stage of the game, and the thoughts that belong to it. Its title is {@code journal.hardwrought.chapter.<id>}. */
    public record Chapter(Identifier id, List<JournalEntry> entries) {
        public Chapter {
            entries = List.copyOf(entries);
        }
    }

    /**
     * The chain, chapter by chapter, in the order the game can actually be played: nothing is asked for
     * before the bench that makes it can be had. Each entry opens the moment the one before it is followed,
     * or earlier, once the player has come across something it lists in {@code after}, so a player who
     * follows the book from the top is walked from bare hands to the ritual one step at a time.
     */
    private static final List<Chapter> CHAPTERS = List.of(
            // Everything here is made in the grid a player carries: there is no bench yet, and the first
            // one wants an iron axe.
            chapter("bare_hands",
                    entry("first_edge", List.of(id("flint_hatchet"), id("flint_pickaxe"), id("flint_dagger"),
                            id("flint_shovel"), id("flint_sword"), id("flint_hoe")), List.of()),
                    // Ultra only: nobody hands the player a pack there, so carrying anything is the next problem.
                    ultra("something_to_carry", List.of(id("starter_backpack")), List.of(id("green_fibre"))),
                    allOf("fire_by_friction", List.of(vanilla("campfire"), id("lighting_sticks")), List.of(id("flint_hatchet"))),
                    entry("fibre_and_cord", List.of(id("leaf_string")), List.of(id("green_fibre"))),
                    entry("light_against_the_dark", List.of(id("unlit_torch"), vanilla("torch")),
                            List.of(vanilla("coal"), vanilla("charcoal"))),
                    entry("firmer_than_flint", List.of(id("stone_pickaxe"), id("stone_hatchet")),
                            List.of(vanilla("cobblestone"), id("cobblestone_piece"))),
                    entry("heat_that_holds", List.of(id("brick_furnace")), List.of(vanilla("clay_ball"), vanilla("brick")))),
            // From the ore in the rock to the iron axe that hews the first bench.
            chapter("first_iron",
                    entry("rust_in_the_rock", List.of(vanilla("raw_iron")), List.of(id("stone_pickaxe"))),
                    allOf("at_the_anvil", List.of(id("smithing_gloves"), id("wooden_anvil"), id("wooden_hammer")),
                            List.of(vanilla("raw_iron"))),
                    entry("the_first_bar", List.of(vanilla("iron_ingot")), List.of(id("wooden_anvil"))),
                    entry("an_edge_of_iron", List.of(vanilla("iron_axe")), List.of(vanilla("iron_ingot"))),
                    entry("bench_from_the_trunk", List.of(id("hewn_workbench")), List.of(vanilla("iron_axe")))),
            // The hewn bench: three by three, but wood, stone and fibre only. Bronze nails make it a real one.
            chapter("bench_and_bronze",
                    ultra("more_on_my_back", List.of(id("frame_backpack")), List.of(id("hewn_workbench"))),
                    entry("something_to_drink", List.of(id("water_bowl")), List.of(id("hewn_workbench"), vanilla("bowl"))),
                    allOf("breaking_it_finer", List.of(id("starter_crusher"), id("hand_crank"), id("shaft")),
                            List.of(id("hewn_workbench"))),
                    entry("two_soft_metals", List.of(id("bronze_ingot")), List.of(id("copper_powder"), id("tin_powder"))),
                    entry("nails_for_the_bench", List.of(id("nailed_workbench")), List.of(id("bronze_ingot"), id("bronze_nails")))),
            // The nailed bench makes everything; what it is good for first.
            chapter("the_workshop",
                    entry("tending_wounds", List.of(id("bandage"), id("chamomile_tea"), id("willow_tea")),
                            List.of(id("nailed_workbench"))),
                    entry("reading_the_rock", List.of(id("atlas")), List.of(vanilla("writable_book"))),
                    entry("clean_water", List.of(id("sand_filter")), List.of(id("nailed_workbench"))),
                    // Sand only takes the river out of the water; fired clay is what makes it wholly safe.
                    entry("pure_water", List.of(id("ceramic_filter")), List.of(id("sand_filter"))),
                    entry("fire_and_water", List.of(id("forge")), List.of(id("nailed_workbench"))),
                    entry("a_stronger_arm", List.of(id("water_wheel"), id("windmill")), List.of(id("forge")))),
            // Steel is made only in a bath of molten metal, and the way to the Nether runs through steel.
            chapter("smeltery_and_steel",
                    allOf("a_bath_of_metal", List.of(id("smeltery_controller"), id("smeltery_drain"), id("casting_table")),
                            List.of(id("refractory_brick"), id("fireclay"))),
                    entry("harder_than_iron", List.of(id("steel_pickaxe")), List.of(id("steel_ingot")))),
            // Fire under a drum of water: the first power that is not wind, water or an arm.
            chapter("steam",
                    allOf("raising_steam", List.of(id("firebox"), id("boiler")), List.of(id("refractory_brick"), id("iron_plate"))),
                    entry("the_safety_valve", List.of(id("safety_valve")), List.of(id("boiler"))),
                    allOf("steam_at_work", List.of(id("steam_cylinder"), id("steam_engine")), List.of(id("boiler"))),
                    entry("valves_and_whistles", List.of(id("gas_valve"), id("steam_whistle")), List.of(id("steam_engine")))),
            // Magic is the only thing the Witch King cannot walk through, and a golden wand is an offering.
            chapter("runes_and_wands",
                    entry("marks_in_old_stone", List.of(id("wooden_wand")), List.of(id("rune_stone"), id("pointed_stick"))),
                    entry("a_ring_of_light", List.of(id("iron_wand")), List.of(id("wooden_wand"))),
                    entry("a_golden_wand", List.of(id("golden_wand")), List.of(vanilla("amethyst_shard"))),
                    entry("runes_on_the_blade", List.of(id("lapis_lazuli_powder")),
                            List.of(vanilla("enchanting_table"), vanilla("lapis_lazuli")))),
            // An electric motor and a bucket of diesel lie on the altar at the end; this is where they come from.
            chapter("oil_and_current",
                    allOf("current_from_turning", List.of(id("dynamo"), id("copper_coil"), id("electric_lamp")),
                            List.of(id("water_wheel"), id("windmill"))),
                    entry("the_motor", List.of(id("electric_motor")), List.of(id("redstone_coil"))),
                    entry("work_on_current", List.of(id("plate_press"), id("basic_crusher"), id("electric_furnace"), id("sawmill")),
                            List.of(id("electric_motor"))),
                    entry("moving_it_along", List.of(id("item_pipe_bronze"), id("item_pipe_iron")), List.of(id("basic_crusher"))),
                    entry("black_gold", List.of(id("drilling_rig")), List.of(id("water_wheel"), id("windmill"))),
                    allOf("the_column", List.of(id("refinery_base"), id("refinery_stage")), List.of(id("drilling_rig"))),
                    entry("the_heavy_cut", List.of(id("diesel_bucket")), List.of(id("refinery_stage")))),
            chapter("the_atom",
                    entry("rock_that_ticks", List.of(id("geiger_counter")), List.of(id("raw_uranium"), id("uranium_ore"))),
                    entry("yellowcake", List.of(id("yellowcake")), List.of(id("uranium_powder"))),
                    entry("the_fluorine", List.of(id("hydrofluoric_acid")), List.of(id("fluorite"))),
                    allOf("spinning_it_apart", List.of(id("centrifuge_controller"), id("gas_centrifuge")), List.of(id("yellowcake"))),
                    entry("zirconium", List.of(id("zircaloy_ingot")), List.of(id("zircon"))),
                    allOf("the_fuel", List.of(id("fuel_rod"), id("fuel_assembly")), List.of(id("uranium_dioxide"))),
                    allOf("the_pile", List.of(id("reactor_controller"), id("control_rod_drive"), id("fuel_channel")),
                            List.of(id("fuel_assembly"))),
                    entry("steam_and_current", List.of(id("turbine_controller")), List.of(id("reactor_controller"))),
                    entry("fertile_thorium", List.of(id("thorium_breeder_rod")), List.of(id("thorium_dioxide")))),
            chapter("the_nether",
                    entry("through_the_obsidian", List.of(vanilla("obsidian")), List.of(id("steel_pickaxe"), id("cast_obsidian"))),
                    entry("the_red_country", List.of(vanilla("netherrack")), List.of(vanilla("obsidian"))),
                    entry("the_ash_grove", List.of(id("ashcork_bark")), List.of(id("ashcork_log"))),
                    entry("a_way_home", List.of(id("provisional_portal_stabilizer")), List.of(id("blackstone_piece"))),
                    entry("a_steady_gate", List.of(id("portal_stabilizer")), List.of(vanilla("blaze_rod")))),
            chapter("the_wither",
                    entry("skulls_of_the_fortress", List.of(vanilla("wither_skeleton_skull")), List.of(vanilla("nether_brick"))),
                    entry("souls_in_the_skull", List.of(vanilla("nether_star")), List.of(vanilla("wither_skeleton_skull")))),
            chapter("the_underworld",
                    entry("the_seal", List.of(id("seal_stone")), List.of(vanilla("nether_star"), vanilla("echo_shard"))),
                    entry("pale_light", List.of(id("pale_shard")), List.of(id("seal_stone"))),
                    entry("in_the_stone_wastes", List.of(id("adamantium_ingot")), List.of(id("raw_adamantium"))),
                    entry("finer_than_netherite", List.of(id("mithril_upgrade_smithing_template"),
                            id("adamant_upgrade_smithing_template")), List.of(vanilla("netherite_ingot"), id("mithril_ingot"))),
                    entry("the_witch_king", List.of(id("witch_king_mantle")), List.of(id("pale_shard")))),
            chapter("the_end",
                    entry("into_the_void", List.of(vanilla("ender_eye")), List.of(id("witch_king_mantle"), vanilla("ender_pearl"))),
                    entry("the_dragon", List.of(vanilla("dragon_egg")), List.of(vanilla("end_stone"))),
                    entry("raised_again", List.of(id("dragon_heart")), List.of(vanilla("dragon_egg")))),
            chapter("the_ritual",
                    entry("crystal_of_the_void", List.of(id("void_crystal_block")), List.of(id("void_crystal_shard"))),
                    entry("a_temple", List.of(id("ritual_altar")), List.of(id("dragon_heart"))),
                    entry("eight_offerings", List.of(id("ritual_pedestal")), List.of(id("ritual_altar"))),
                    allOf("the_last_night", List.of(id("ritual_altar"), id("ritual_pedestal"), id("dragon_heart")),
                            List.of(id("ritual_pedestal"))))
    );

    /** Every entry, chapter after chapter. */
    private static final List<JournalEntry> CHAIN = CHAPTERS.stream().flatMap(chapter -> chapter.entries().stream()).toList();

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

    public static List<Chapter> chapters() {
        return CHAPTERS;
    }

    /** The chapter an entry belongs to. */
    public static Chapter chapterOf(JournalEntry entry) {
        for (Chapter chapter : CHAPTERS) {
            if (chapter.entries().contains(entry)) return chapter;
        }
        return CHAPTERS.getFirst();
    }

    /**
     * Where one player stands on one entry. An entry counts as followed the moment the player has
     * held any of the things it points at — or all of them, for an entry that needs all — however
     * they came by it, because the book is a record of what is true, not of who did the work.
     */
    public static int state(PlayerKnowledge knowledge, JournalEntry entry) {
        if (followed(knowledge, entry)) return DONE;
        JournalEntry previous = previous(entry);
        // The first thought is there from the first page; every other one the moment the one before is followed.
        if (previous == null || followed(knowledge, previous)) return OPEN;
        for (Identifier seen : entry.after()) {
            if (knowledge.level(seen).named()) return OPEN;
        }
        return HIDDEN;
    }

    /**
     * The thoughts this one item takes part in, as the running world reads the chain: what holding it for
     * the first time may have just followed.
     */
    public static List<JournalEntry> about(Identifier item) {
        List<JournalEntry> entries = new ArrayList<>();
        for (JournalEntry entry : CHAIN) {
            if ((!entry.ultraOnly() || ultra) && entry.teaches().contains(item)) entries.add(entry);
        }
        return entries;
    }

    /** The entry after this one as the running world reads the chain, or null for the last. */
    public static JournalEntry next(JournalEntry entry) {
        boolean found = false;
        for (JournalEntry candidate : CHAIN) {
            if (found && (!candidate.ultraOnly() || ultra)) return candidate;
            if (candidate == entry) found = true;
        }
        return null;
    }

    /** The entry before this one as the running world reads the chain, or null for the first. */
    private static JournalEntry previous(JournalEntry entry) {
        JournalEntry previous = null;
        for (JournalEntry candidate : CHAIN) {
            if (candidate == entry) return previous;
            if (!candidate.ultraOnly() || ultra) previous = candidate;
        }
        return null;
    }

    /** Whether the player holds what the entry asks for: any one of it, or all of it. */
    public static boolean followed(PlayerKnowledge knowledge, JournalEntry entry) {
        if (entry.teaches().isEmpty()) return false;
        for (Identifier taught : entry.teaches()) {
            boolean held = knowledge.level(taught).named();
            if (held && !entry.needsAll()) return true;
            if (!held && entry.needsAll()) return false;
        }
        return entry.needsAll();
    }

    /**
     * The deeper looks a chapter offers once it is reached: optional pages after its notes that set out
     * how the things of that stage actually work, which the notes themselves leave the player to find.
     * Nothing hangs on reading them. Each has its text under {@code journal.hardwrought.lore.<id>.title}
     * and {@code .text}; they are the client's alone and never travel over the wire.
     */
    private static final java.util.Map<String, List<String>> LORE = java.util.Map.ofEntries(
            // LORE-START
            java.util.Map.entry("bare_hands", List.of("bare_hands_what_gives_way", "bare_hands_thirst_and_water", "bare_hands_fire_and_light", "bare_hands_wounds_and_sickness")),
            java.util.Map.entry("first_iron", List.of("first_iron_heat", "first_iron_anvil_work", "first_iron_quality_and_quench", "first_iron_sounding_the_rock")),
            java.util.Map.entry("bench_and_bronze", List.of("bench_and_bronze_the_benches", "bench_and_bronze_the_water_bowl", "bench_and_bronze_crusher_and_crank", "bench_and_bronze_tin_and_bronze")),
            java.util.Map.entry("the_workshop", List.of("the_workshop_wounds_and_illness", "the_workshop_water_and_filters", "the_workshop_forge_and_temper", "the_workshop_shafts_and_strength")),
            java.util.Map.entry("smeltery_and_steel", List.of("smeltery_and_steel_building", "smeltery_and_steel_heat_and_melting", "smeltery_and_steel_casting_and_grinding", "smeltery_and_steel_steel")),
            java.util.Map.entry("steam", List.of("steam_raising_steam", "steam_engines_and_valves", "steam_when_it_goes_wrong")),
            java.util.Map.entry("runes_and_wands", List.of("runes_and_wands_runes_and_grammar", "runes_and_wands_wand_and_judgement", "runes_and_wands_sigils", "runes_and_wands_rune_writing")),
            java.util.Map.entry("oil_and_current", List.of("oil_and_current_dynamo_and_line", "oil_and_current_machines_and_safety", "oil_and_current_pipes_and_pumps", "oil_and_current_well_and_column")),
            java.util.Map.entry("the_atom", List.of("the_atom_fuel_chain", "the_atom_running_a_reactor", "the_atom_radiation", "the_atom_when_it_goes_wrong")),
            java.util.Map.entry("the_nether", List.of("the_nether_crossing", "the_nether_heat", "the_nether_fresh_start", "the_nether_stabilisers")),
            java.util.Map.entry("the_wither", List.of("the_wither_skulls", "the_wither_fight")),
            java.util.Map.entry("the_underworld", List.of("the_underworld_ways", "the_underworld_realms", "the_underworld_metals", "the_underworld_witch_king")),
            java.util.Map.entry("the_end", List.of("the_end_arrival", "the_end_crystals_stages", "the_end_dragon_moves", "the_end_landing_rematch")),
            java.util.Map.entry("the_ritual", List.of("the_ritual_void_crystal", "the_ritual_temple", "the_ritual_offerings", "the_ritual_last_night"))
            // LORE-END
    );

    /** The deeper looks of a chapter, in reading order; none for a chapter without any. */
    public static List<Identifier> lore(Identifier chapter) {
        if (chapter == null) return List.of();
        return LORE.getOrDefault(chapter.getPath(), List.of()).stream().map(Journal::id).toList();
    }

    /** The whole chain as this player reads it, hidden entries included. */
    public static List<CompendiumPagePayload.Note> page(PlayerKnowledge knowledge) {
        List<CompendiumPagePayload.Note> notes = new ArrayList<>(CHAIN.size());
        for (Chapter chapter : CHAPTERS) {
            for (JournalEntry entry : chapter.entries()) {
                if (notes.size() >= CompendiumPagePayload.MAX_NOTES) break;
                if (entry.ultraOnly() && !ultra) continue;
                int state = state(knowledge, entry);
                notes.add(new CompendiumPagePayload.Note(entry.id(), subject(knowledge, entry, state),
                        state, parts(knowledge, entry, state), chapter.id(), entry.needsAll()));
            }
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
     * What the thought asks for, each piece with whether the player has held it: the task drawn under the
     * note. Nothing for an unread thought, for the same reason it has no icon.
     */
    private static List<CompendiumPagePayload.Part> parts(PlayerKnowledge knowledge, JournalEntry entry,
                                                          int state) {
        if (state == HIDDEN) return List.of();
        List<CompendiumPagePayload.Part> parts = new ArrayList<>();
        for (Identifier taught : entry.teaches()) {
            if (parts.size() >= CompendiumPagePayload.MAX_PARTS) break;
            parts.add(new CompendiumPagePayload.Part(taught, knowledge.level(taught).named()));
        }
        return parts;
    }

    private static Chapter chapter(String name, JournalEntry... entries) {
        return new Chapter(id(name), List.of(entries));
    }

    /** A thought that belongs only to an Ultra world. */
    private static JournalEntry ultra(String name, List<Identifier> teaches, List<Identifier> after) {
        return new JournalEntry(id(name), teaches, after, false, true);
    }

    private static JournalEntry entry(String name, List<Identifier> teaches, List<Identifier> after) {
        return new JournalEntry(id(name), teaches, after);
    }

    /** A thought followed only once every one of the things it points at has been held. */
    private static JournalEntry allOf(String name, List<Identifier> teaches, List<Identifier> after) {
        return new JournalEntry(id(name), teaches, after, true);
    }

    private static Identifier id(String path) {
        return Hardwrought.id(path);
    }

    private static Identifier vanilla(String path) {
        return Identifier.withDefaultNamespace(path);
    }
}
