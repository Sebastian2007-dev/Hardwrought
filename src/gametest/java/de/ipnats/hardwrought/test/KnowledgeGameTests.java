package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.networking.CompendiumPagePayload;
import de.ipnats.hardwrought.core.networking.CompendiumRequestPayload;
import de.ipnats.hardwrought.core.networking.KnowledgeNotePayload;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.knowledge.Journal;
import de.ipnats.hardwrought.knowledge.JournalEntry;
import de.ipnats.hardwrought.knowledge.KnowledgeCategory;
import de.ipnats.hardwrought.knowledge.KnowledgeLevel;
import de.ipnats.hardwrought.knowledge.KnowledgeSystem;
import de.ipnats.hardwrought.knowledge.LootSources;
import de.ipnats.hardwrought.knowledge.PlayerKnowledge;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Milestone 8, specification sections 79 to 83: what a player knows, and what the compendium is
 * therefore allowed to show them.
 */
public final class KnowledgeGameTests {
    private static final Identifier LEAF_STRING = Hardwrought.id("leaf_string");
    private static final Identifier COMPENDIUM = Hardwrought.id("compendium");

    @GameTest
    public void holdingDiscoversAndWorkingStudies(GameTestHelper helper) {
        KnowledgeSystem knowledge = CoreLifecycle.require(helper.getLevel().getServer()).knowledge();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        knowledge.forget(player.getUUID());

        helper.assertTrue(knowledge.level(player, Items.IRON_INGOT) == KnowledgeLevel.UNKNOWN,
                "Section 81: a thing never held is unknown");
        helper.assertTrue(knowledge.discover(player, Items.IRON_INGOT), "Discovering it is news");
        helper.assertTrue(!knowledge.discover(player, Items.IRON_INGOT), "Discovering it twice is not");
        helper.assertTrue(knowledge.level(player, Items.IRON_INGOT) == KnowledgeLevel.DISCOVERED,
                "Holding a thing makes its name real and no more");

        helper.assertTrue(knowledge.study(player, Items.IRON_INGOT), "Working with it is news again");
        helper.assertTrue(knowledge.level(player, Items.IRON_INGOT) == KnowledgeLevel.STUDIED,
                "Section 82: working with a thing is what turns seeing it into knowing it");
        helper.assertTrue(!knowledge.study(player, Items.IRON_INGOT), "Studying it twice is not news");

        // Studying without ever discovering first still leaves a discovered thing behind.
        helper.assertTrue(knowledge.study(player, Items.COAL), "A first act of work is news");
        helper.assertTrue(knowledge.knowledge(player).discovered().contains(
                        BuiltInRegistries.ITEM.getKey(Items.COAL)),
                "Studying implies having discovered");
        knowledge.forget(player.getUUID());
        helper.succeed();
    }

    @GameTest
    public void learningSomethingIsWorthTellingThePlayerAbout(GameTestHelper helper) {
        KnowledgeSystem knowledge = CoreLifecycle.require(helper.getLevel().getServer()).knowledge();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        knowledge.forget(player.getUUID());
        helper.assertTrue(knowledge.pendingNotes(player.getUUID()).isEmpty(),
                "Nothing has been learned, so there is nothing to say");

        knowledge.discover(player, Items.IRON_INGOT);
        helper.assertTrue(knowledge.pendingNotes(player.getUUID()).size() == 1,
                "Finding something is worth a toast");
        helper.assertTrue(knowledge.pendingNotes(player.getUUID()).getFirst().level()
                        == KnowledgeLevel.DISCOVERED.ordinal(),
                "The toast says how well it is now known");

        // Discovering and then studying in one breath is one learning with a better ending.
        knowledge.study(player, Items.IRON_INGOT);
        helper.assertTrue(knowledge.pendingNotes(player.getUUID()).size() == 1,
                "One thing learned is one line, however it was learned");
        helper.assertTrue(knowledge.pendingNotes(player.getUUID()).getFirst().level()
                        == KnowledgeLevel.STUDIED.ordinal(),
                "The later level replaces the earlier one");

        knowledge.study(player, Items.COAL);
        helper.assertTrue(knowledge.pendingNotes(player.getUUID()).size() == 2,
                "Two things learned are two lines of the same toast");
        // Learning the same thing twice is not news, so it does not queue a second time.
        helper.assertTrue(!knowledge.study(player, Items.COAL), "Studying it again is not news");
        helper.assertTrue(knowledge.pendingNotes(player.getUUID()).size() == 2,
                "And it does not queue a second toast either");

        for (int index = 0; index < KnowledgeNotePayload.MAX_NOTES * 2; index++) {
            knowledge.study(player, BuiltInRegistries.ITEM.byId(index + 1));
        }
        helper.assertTrue(knowledge.pendingNotes(player.getUUID()).size() <= KnowledgeNotePayload.MAX_NOTES,
                "A player who learns a great deal at once still gets a toast, not a wall of them");

        knowledge.flushNotes();
        helper.assertTrue(knowledge.pendingNotes(player.getUUID()).isEmpty(),
                "What has been said is not said twice");
        knowledge.forget(player.getUUID());
        helper.succeed();
    }

    @GameTest
    public void forgettingAndReloadingKeepTheSameSets(GameTestHelper helper) {
        PlayerKnowledge empty = PlayerKnowledge.empty();
        helper.assertTrue(empty.level(LEAF_STRING) == KnowledgeLevel.UNKNOWN, "An empty book knows nothing");
        PlayerKnowledge known = PlayerKnowledge.empty();
        known.study(LEAF_STRING);
        helper.assertTrue(known.level(LEAF_STRING) == KnowledgeLevel.STUDIED, "Studying is recorded");
        helper.assertTrue(known.level(COMPENDIUM) == KnowledgeLevel.UNKNOWN,
                "One entry says nothing about the next");
        helper.assertTrue(empty.level(LEAF_STRING) == KnowledgeLevel.UNKNOWN,
                "Two players do not share one book");
        helper.succeed();
    }

    @GameTest
    public void knowledgeSurvivesBeingWrittenOutAndReadBack(GameTestHelper helper) {
        PlayerKnowledge before = PlayerKnowledge.empty();
        before.discover(LEAF_STRING);
        before.study(COMPENDIUM);

        var written = PlayerKnowledge.CODEC.encodeStart(NbtOps.INSTANCE, before).result();
        helper.assertTrue(written.isPresent(), "Section 79: what a player knows has to be writable");
        PlayerKnowledge after = PlayerKnowledge.CODEC.parse(NbtOps.INSTANCE, written.get())
                .result().orElse(null);
        helper.assertTrue(after != null, "And readable again");

        helper.assertTrue(after.level(LEAF_STRING) == KnowledgeLevel.DISCOVERED,
                "What was merely held is still merely held after a reload");
        helper.assertTrue(after.level(COMPENDIUM) == KnowledgeLevel.STUDIED,
                "What was worked with is still worked with after a reload");
        helper.assertTrue(after.level(BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT))
                        == KnowledgeLevel.UNKNOWN,
                "And a reload does not invent knowledge that was never there");

        // An older save has no knowledge field at all, and has to load as a player who knows nothing
        // rather than refusing to load.
        PlayerKnowledge old = PlayerKnowledge.CODEC.parse(NbtOps.INSTANCE, new CompoundTag())
                .result().orElse(null);
        helper.assertTrue(old != null && old.count() == 0,
                "A save written before this milestone loads as an empty book");
        helper.succeed();
    }

    @GameTest
    public void shelvesShowUnknownEntriesWithoutNamingThem(GameTestHelper helper) {
        KnowledgeSystem knowledge = CoreLifecycle.require(helper.getLevel().getServer()).knowledge();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        knowledge.forget(player.getUUID());

        List<KnowledgeSystem.Entry> page = knowledge.page(player, KnowledgeCategory.METALLURGY, "");
        helper.assertTrue(!page.isEmpty(), "Section 80: a shelf shows what is on it, known or not");
        helper.assertTrue(page.stream().allMatch(entry -> entry.level() == KnowledgeLevel.UNKNOWN),
                "A player who has found nothing sees only shadows");

        // Search is the one thing knowledge gates outright: a name cannot be typed before it is real.
        helper.assertTrue(knowledge.page(player, KnowledgeCategory.METALLURGY, "iron").isEmpty(),
                "Section 81: an undiscovered entry cannot be searched for by name");
        knowledge.study(player, Items.IRON_INGOT);
        helper.assertTrue(knowledge.page(player, KnowledgeCategory.METALLURGY, "iron_ingot").stream()
                        .anyMatch(entry -> entry.id().equals(BuiltInRegistries.ITEM.getKey(Items.IRON_INGOT))),
                "Once it is known, it can be looked up by name");
        knowledge.forget(player.getUUID());
        helper.succeed();
    }

    @GameTest
    public void everyItemStandsOnExactlyOneShelf(GameTestHelper helper) {
        KnowledgeSystem knowledge = CoreLifecycle.require(helper.getLevel().getServer()).knowledge();
        int shelved = 0;
        for (KnowledgeCategory category : KnowledgeCategory.values()) shelved += knowledge.shelf(category).size();
        int registered = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) registered++;
        }
        helper.assertTrue(shelved == registered,
                "Every item belongs to one shelf: " + shelved + " shelved of " + registered);
        helper.assertTrue(KnowledgeCategory.of(Items.IRON_INGOT) == KnowledgeCategory.METALLURGY,
                "An ingot is metallurgy");
        helper.assertTrue(KnowledgeCategory.of(Items.APPLE) == KnowledgeCategory.AGRICULTURE,
                "Food is agriculture");
        helper.assertTrue(KnowledgeCategory.of(Items.IRON_PICKAXE) == KnowledgeCategory.CRAFTING,
                "A tool is crafting");
        helper.succeed();
    }

    @GameTest
    public void theBrowserAnswersBothDirections(GameTestHelper helper) {
        var runtime = CoreLifecycle.require(helper.getLevel().getServer());
        KnowledgeSystem knowledge = runtime.knowledge();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        knowledge.forget(player.getUUID());

        CompendiumPagePayload made = knowledge.compendium().answer(player, knowledge,
                new CompendiumRequestPayload(CompendiumPagePayload.MODE_RECIPES, COMPENDIUM, ""));
        helper.assertTrue(made.mode() == CompendiumPagePayload.MODE_RECIPES, "The answer matches the question");
        helper.assertTrue(!made.recipes().isEmpty(), "The compendium itself is something that can be made");
        CompendiumPagePayload.Recipe recipe = made.recipes().getFirst();
        helper.assertTrue(recipe.result().options().getFirst().stack().is(ModItems.COMPENDIUM),
                "What comes out of the recipe is what was asked about");
        helper.assertTrue(recipe.inputs().stream().anyMatch(slot -> slot.options().stream()
                        .anyMatch(known -> known.stack().is(ModItems.LEAF_STRING))),
                "Leaf string is one of the things that goes into it");

        CompendiumPagePayload used = knowledge.compendium().answer(player, knowledge,
                new CompendiumRequestPayload(CompendiumPagePayload.MODE_USAGES, LEAF_STRING, ""));
        helper.assertTrue(used.recipes().stream().anyMatch(view -> view.result().options().stream()
                        .anyMatch(known -> known.stack().is(ModItems.COMPENDIUM))),
                "Section 83: what a thing is used in is the other half of the browser");
        knowledge.forget(player.getUUID());
        helper.succeed();
    }

    @GameTest
    public void inWorldWorkAppearsAsItsOwnRecipeMethod(GameTestHelper helper) {
        KnowledgeSystem knowledge = CoreLifecycle.require(helper.getLevel().getServer()).knowledge();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        knowledge.forget(player.getUUID());

        CompendiumPagePayload page = made(knowledge, player, ModBlocks.HEWN_WORKBENCH.asItem());
        CompendiumPagePayload.Recipe hewing = page.recipes().stream()
                .filter(recipe -> recipe.id().equals(Hardwrought.id("hewn_workbench_in_world")))
                .findFirst().orElse(null);
        helper.assertTrue(hewing != null, "A bench hewn in the world still has a compendium recipe");
        helper.assertTrue(hewing.method() == CompendiumPagePayload.METHOD_IN_WORLD,
                "It is filed under the in-world bookmark rather than ordinary crafting");
        helper.assertTrue(hewing.inputs().size() == 2
                        && hewing.inputs().stream().allMatch(slot -> !slot.isEmpty()),
                "The page shows both the timber and the suitable hatchet");

        CompendiumPagePayload logs = knowledge.compendium().answer(player, knowledge,
                new CompendiumRequestPayload(CompendiumPagePayload.MODE_USAGES,
                        BuiltInRegistries.ITEM.getKey(Items.OAK_LOG), ""));
        helper.assertTrue(logs.recipes().stream().anyMatch(recipe ->
                        recipe.method() == CompendiumPagePayload.METHOD_IN_WORLD),
                "Looking up a log's uses points back to the hewing process");
        CompendiumPagePayload campfireUses = knowledge.compendium().answer(player, knowledge,
                new CompendiumRequestPayload(CompendiumPagePayload.MODE_USAGES,
                        BuiltInRegistries.ITEM.getKey(Items.CAMPFIRE), ""));
        helper.assertTrue(campfireUses.recipes().stream().filter(recipe ->
                        recipe.method() == CompendiumPagePayload.METHOD_IN_WORLD).count() >= 2,
                "A campfire shows both lighting it and boiling a waterskin as in-world work");
        knowledge.forget(player.getUUID());
        helper.succeed();
    }

    @GameTest
    public void thingsNobodyMakesStillHaveAnOrigin(GameTestHelper helper) {
        KnowledgeSystem knowledge = CoreLifecycle.require(helper.getLevel().getServer()).knowledge();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        knowledge.forget(player.getUUID());

        // The reason this exists: a browser that only knows recipes shrugs at a feather.
        CompendiumPagePayload feather = made(knowledge, player, Items.FEATHER);
        helper.assertTrue(feather.recipes().isEmpty(), "Nobody crafts a feather");
        helper.assertTrue(!feather.sources().isEmpty(), "And yet a feather comes from somewhere");
        helper.assertTrue(feather.sources().stream().anyMatch(source ->
                        source.kind() == LootSources.KIND_MOB
                                && source.id().equals(Identifier.withDefaultNamespace("chicken"))),
                "A chicken is where a feather comes from");

        CompendiumPagePayload flint = made(knowledge, player, Items.FLINT);
        helper.assertTrue(flint.sources().stream().anyMatch(source ->
                        source.kind() == LootSources.KIND_BLOCK
                                && source.id().equals(Identifier.withDefaultNamespace("gravel"))),
                "Flint is broken out of gravel");

        // A block that drops itself is not a discovery, it is the block.
        CompendiumPagePayload dirt = made(knowledge, player, Items.DIRT);
        helper.assertTrue(dirt.sources().stream().noneMatch(source ->
                        source.kind() == LootSources.KIND_BLOCK
                                && source.id().equals(Identifier.withDefaultNamespace("dirt"))),
                "Dirt is not listed as the place dirt comes from");

        // A block a player has never held is a shadow here too.
        helper.assertTrue(flint.sources().stream()
                        .filter(source -> source.kind() == LootSources.KIND_BLOCK)
                        .allMatch(source -> source.level() == KnowledgeLevel.UNKNOWN.ordinal()),
                "Section 81 holds for a source as much as for an ingredient");
        knowledge.study(player, Items.GRAVEL);
        helper.assertTrue(made(knowledge, player, Items.FLINT).sources().stream()
                        .anyMatch(source -> source.id().equals(Identifier.withDefaultNamespace("gravel"))
                                && source.level() == KnowledgeLevel.STUDIED.ordinal()),
                "Once the gravel is known, the browser names it");

        for (Item item : new Item[]{Items.FEATHER, Items.FLINT, Items.DIAMOND, Items.LEATHER}) {
            helper.assertTrue(made(knowledge, player, item).sources().size()
                            <= CompendiumPagePayload.MAX_SOURCES,
                    "Iron turns up in a great many chests, and the page still fits in a packet");
        }
        // Usage is the other question; where a thing comes from is no answer to it.
        CompendiumPagePayload used = knowledge.compendium().answer(player, knowledge,
                new CompendiumRequestPayload(CompendiumPagePayload.MODE_USAGES,
                        BuiltInRegistries.ITEM.getKey(Items.FEATHER), ""));
        helper.assertTrue(used.sources().isEmpty(), "What it is used in does not list where it is found");
        knowledge.forget(player.getUUID());
        helper.succeed();
    }

    private static CompendiumPagePayload made(KnowledgeSystem knowledge, ServerPlayer player, Item item) {
        return knowledge.compendium().answer(player, knowledge, new CompendiumRequestPayload(
                CompendiumPagePayload.MODE_RECIPES, BuiltInRegistries.ITEM.getKey(item), ""));
    }

    @GameTest
    public void recipesTravelWithTheKnowledgeOfEveryStackInThem(GameTestHelper helper) {
        var runtime = CoreLifecycle.require(helper.getLevel().getServer());
        KnowledgeSystem knowledge = runtime.knowledge();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        knowledge.forget(player.getUUID());

        CompendiumRequestPayload request = new CompendiumRequestPayload(
                CompendiumPagePayload.MODE_RECIPES, COMPENDIUM, "");
        CompendiumPagePayload blind = knowledge.compendium().answer(player, knowledge, request);
        helper.assertTrue(blind.recipes().getFirst().inputs().stream()
                        .flatMap(slot -> slot.options().stream())
                        .allMatch(known -> known.level() == KnowledgeLevel.UNKNOWN.ordinal()),
                "A recipe is shown to a player who knows none of it, as shadows");
        helper.assertTrue(!blind.entries().isEmpty()
                        && blind.entries().getFirst().level() == KnowledgeLevel.UNKNOWN.ordinal(),
                "The page says the subject itself is unknown, so the browser does not name it");

        knowledge.study(player, ModItems.LEAF_STRING);
        CompendiumPagePayload seeing = knowledge.compendium().answer(player, knowledge, request);
        helper.assertTrue(seeing.recipes().getFirst().inputs().stream()
                        .flatMap(slot -> slot.options().stream())
                        .anyMatch(known -> known.stack().is(ModItems.LEAF_STRING)
                                && known.level() == KnowledgeLevel.STUDIED.ordinal()),
                "What the player has worked with is drawn as itself");
        knowledge.forget(player.getUUID());
        helper.succeed();
    }

    @GameTest
    public void aPageNeverExceedsWhatTheProtocolAllows(GameTestHelper helper) {
        KnowledgeSystem knowledge = CoreLifecycle.require(helper.getLevel().getServer()).knowledge();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        knowledge.forget(player.getUUID());
        for (KnowledgeCategory category : KnowledgeCategory.values()) {
            CompendiumPagePayload page = knowledge.compendium().answer(player, knowledge,
                    new CompendiumRequestPayload(CompendiumPagePayload.MODE_SHELF,
                            Hardwrought.id(category.serializedName()), ""));
            helper.assertTrue(page.entries().size() <= CompendiumPagePayload.MAX_ENTRIES,
                    "A shelf page stays inside the packet budget: " + category);
        }
        CompendiumPagePayload usages = knowledge.compendium().answer(player, knowledge,
                new CompendiumRequestPayload(CompendiumPagePayload.MODE_USAGES,
                        BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), ""));
        helper.assertTrue(usages.recipes().size() <= CompendiumPagePayload.MAX_RECIPES,
                "Planks are used in a great many things, and the page still fits in a packet");
        helper.assertTrue(usages.recipes().stream().allMatch(recipe ->
                        recipe.inputs().size() <= CompendiumPagePayload.MAX_SLOTS),
                "No recipe view carries more slots than the browser can draw");
        knowledge.forget(player.getUUID());
        helper.succeed();
    }

    @GameTest
    public void unknownSubjectsAndNonsenseRequestsAnswerEmpty(GameTestHelper helper) {
        KnowledgeSystem knowledge = CoreLifecycle.require(helper.getLevel().getServer()).knowledge();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CompendiumPagePayload page = knowledge.compendium().answer(player, knowledge,
                new CompendiumRequestPayload(CompendiumPagePayload.MODE_RECIPES,
                        Hardwrought.id("no_such_item_at_all"), ""));
        helper.assertTrue(page.recipes().isEmpty() && page.entries().isEmpty(),
                "A request for something that does not exist answers nothing rather than failing");
        CompendiumPagePayload shelf = knowledge.compendium().answer(player, knowledge,
                new CompendiumRequestPayload(CompendiumPagePayload.MODE_SHELF,
                        Hardwrought.id("no_such_shelf"), ""));
        helper.assertTrue(!shelf.entries().isEmpty(),
                "An unknown shelf falls back to one that exists rather than showing a blank screen");
        helper.succeed();
    }

    @GameTest
    public void everyThoughtPointsAtSomethingThatExists(GameTestHelper helper) {
        // The chain is written as identifiers, so a renamed item would otherwise turn a note into a
        // blank page months after the rename, and nothing would say so.
        for (JournalEntry entry : Journal.chain()) {
            helper.assertTrue(!entry.teaches().isEmpty(), entry.id() + " points at something");
            for (Identifier taught : entry.teaches()) {
                helper.assertTrue(BuiltInRegistries.ITEM.getValue(taught) != Items.AIR,
                        entry.id() + " points at a registered item: " + taught);
            }
            for (Identifier seen : entry.after()) {
                helper.assertTrue(BuiltInRegistries.ITEM.getValue(seen) != Items.AIR,
                        entry.id() + " waits on a registered item: " + seen);
            }
        }
        helper.succeed();
    }

    @GameTest
    public void theChainOpensOneThoughtAtATime(GameTestHelper helper) {
        KnowledgeSystem knowledge = CoreLifecycle.require(helper.getLevel().getServer()).knowledge();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        knowledge.forget(player.getUUID());

        List<CompendiumPagePayload.Note> blank = journal(knowledge, player);
        helper.assertTrue(blank.size() == Journal.chain().size(),
                "Every thought is on the page, read or not");
        helper.assertTrue(blank.getFirst().state() == Journal.OPEN,
                "The first thought is there the moment the book is opened");
        helper.assertTrue(blank.get(1).state() == Journal.HIDDEN,
                "The second one is not");
        helper.assertTrue(blank.get(1).subject() == null,
                "An unread thought does not name what it is about; the icon would give it away");

        // Section 81 again, from the written side: acting on a note is what opens the next one.
        knowledge.study(player, ModItems.FLINT_HATCHET);
        List<CompendiumPagePayload.Note> after = journal(knowledge, player);
        helper.assertTrue(after.getFirst().state() == Journal.DONE,
                "A thought the player has acted on is marked as followed");
        helper.assertTrue(after.getFirst().subject() != null
                        && after.getFirst().subject().equals(Hardwrought.id("flint_hatchet")),
                "And it shows the one of its options the player actually holds");
        helper.assertTrue(after.get(1).state() == Journal.OPEN,
                "The next thought follows from it");
        helper.assertTrue(after.get(2).state() == Journal.HIDDEN,
                "The one after that still does not");
        knowledge.forget(player.getUUID());
        helper.succeed();
    }

    private static List<CompendiumPagePayload.Note> journal(KnowledgeSystem knowledge,
                                                            ServerPlayer player) {
        return knowledge.compendium().answer(player, knowledge, new CompendiumRequestPayload(
                CompendiumPagePayload.MODE_JOURNAL, Hardwrought.id("book"), "")).journal();
    }
}
