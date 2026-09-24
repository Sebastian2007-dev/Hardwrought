package de.ipnats.hardwrought.knowledge;

import de.ipnats.hardwrought.core.debug.DiagnosticRegistry;
import de.ipnats.hardwrought.core.networking.KnowledgeNotePayload;
import de.ipnats.hardwrought.core.save.CoreSaveData;
import de.ipnats.hardwrought.core.simulation.SimulationScheduler;
import de.ipnats.hardwrought.core.simulation.SimulationTier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Milestone-8 knowledge: specification sections 79 to 83. What a player has found out, and what the
 * compendium is therefore allowed to tell them.
 *
 * <p>Two ways in, and both of them are things a player was going to do anyway. Holding something
 * <em>discovers</em> it: the name becomes real. Working with it — breaking rock with it, crafting it,
 * eating it — <em>studies</em> it, and only then does the compendium show what it weighs, what it is
 * made of and what it is for. Section 81 is the reason for the split: an unknown material is an entry
 * full of question marks, not an absent one.
 *
 * <p>Section 82 counts experimenting among the ways to learn, and that is what the other two ways in
 * are for. An ingot is the case that needs them: there is nothing a player can break, cook or craft
 * <em>with</em> a bar of iron, so on breaking and crafting alone it would stay a page of question
 * marks for ever. Keeping a thing in the main hand for a few seconds examines it, and carrying it
 * around from one inventory pass to the next studies it too — slower, and without saying so.
 *
 * <p>Bounded like every other system here: one inventory scan per player per slow pass, forty-one
 * slots, no registry sweeps in a tick job.
 */
public final class KnowledgeSystem {
    /**
     * How many examination passes the same item has to stay in the main hand before it counts as
     * studied. On the medium tier that is a shade over three seconds: long enough that flicking
     * across the hotbar teaches nothing, short enough to be an act rather than a chore.
     */
    public static final int EXAMINATION_PASSES = 3;

    private final MinecraftServer server;
    private final CoreSaveData save;
    /** The registry does not change after start-up, so the shelves are sorted once. */
    private Map<KnowledgeCategory, List<Identifier>> shelves;
    /** The recipe browser behind the R and U keys, indexed on first use. */
    private final Compendium compendium = new Compendium();
    /** Learnings waiting to be shown, gathered so a single act does not fire a burst of toasts. */
    private final Map<UUID, List<KnowledgeNotePayload.Note>> pending = new HashMap<>();
    /** What each player is currently turning over in their hand, and for how long. */
    private final Map<UUID, Examination> examinations = new HashMap<>();

    public KnowledgeSystem(MinecraftServer server, CoreSaveData save, SimulationScheduler scheduler) {
        this.server = server;
        this.save = save;
        scheduler.register("hardwrought:knowledge_discovery", SimulationTier.SLOW, this::tickDiscovery);
        scheduler.register("hardwrought:knowledge_examination", SimulationTier.MEDIUM, this::examineHeldItems);
        scheduler.register("hardwrought:knowledge_notes", SimulationTier.FAST, this::flushNotes);
    }

    // ---------------------------------------------------------------- what a player knows

    public PlayerKnowledge knowledge(ServerPlayer player) {
        return save.knowledge(player.getUUID());
    }

    public KnowledgeLevel level(ServerPlayer player, ItemLike item) {
        return knowledge(player).level(idOf(item));
    }

    /** Section 81: holding a thing makes its name real. Returns true where that was news. */
    public boolean discover(ServerPlayer player, ItemLike item) {
        Identifier id = idOf(item);
        if (id == null) return false;
        PlayerKnowledge knowledge = knowledge(player);
        if (!knowledge.discover(id)) return false;
        save.setKnowledge(player.getUUID(), knowledge);
        note(player.getUUID(), id, KnowledgeLevel.DISCOVERED);
        return true;
    }

    /**
     * Section 82: working with a thing is what turns having seen it into knowing it. Returns true
     * where that was news, which is what a message or a sound would be worth reacting to.
     */
    public boolean study(ServerPlayer player, ItemLike item) {
        Identifier id = idOf(item);
        if (id == null) return false;
        PlayerKnowledge knowledge = knowledge(player);
        if (!knowledge.study(id)) return false;
        save.setKnowledge(player.getUUID(), knowledge);
        note(player.getUUID(), id, KnowledgeLevel.STUDIED);
        return true;
    }

    /**
     * Operator entry point: every entry of every shelf studied at once. Silent, unlike
     * {@link #study}: a thousand toasts are not information. Returns how many entries were news.
     */
    public int studyAll(ServerPlayer player) {
        PlayerKnowledge knowledge = knowledge(player);
        int learned = 0;
        for (KnowledgeCategory category : KnowledgeCategory.values()) {
            for (Identifier id : shelf(category)) {
                if (knowledge.study(id)) learned++;
            }
        }
        if (learned > 0) save.setKnowledge(player.getUUID(), knowledge);
        return learned;
    }

    /** Operator and test entry point; ordinary play fills this in through holding and working. */
    public void forget(UUID player) {
        save.setKnowledge(player, PlayerKnowledge.empty());
        pending.remove(player);
        examinations.remove(player);
    }

    /** Drops what a leaving player had not been shown yet. */
    public void removePlayer(UUID player) {
        pending.remove(player);
        examinations.remove(player);
    }

    /** The recipe browser: how a thing is made, and what it is used in. */
    public Compendium compendium() {
        return compendium;
    }

    // ---------------------------------------------------------------- the shelves

    /** Every entry that belongs on one shelf, in registry order, whether the player knows it or not. */
    public List<Identifier> shelf(KnowledgeCategory category) {
        if (shelves == null) shelves = sortShelves();
        return shelves.getOrDefault(category, List.of());
    }

    /**
     * What the compendium shows a player on one shelf: everything on it, each with what this player
     * knows about it. Unknown entries are part of the answer — an empty shelf teaches nothing about
     * what is still out there.
     */
    public List<Entry> page(ServerPlayer player, KnowledgeCategory category, String search) {
        PlayerKnowledge knowledge = knowledge(player);
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Entry> page = new ArrayList<>();
        for (Identifier id : shelf(category)) {
            KnowledgeLevel level = knowledge.level(id);
            // A player cannot search for what they have never seen; that is the whole point of a
            // compendium that has to be filled in.
            if (!needle.isEmpty() && (!level.named() || !id.getPath().contains(needle))) continue;
            page.add(new Entry(id, level));
        }
        return List.copyOf(page);
    }

    /** One line of the compendium. */
    public record Entry(Identifier id, KnowledgeLevel level) {
        public Entry {
            if (id == null || level == null) throw new IllegalArgumentException("Invalid entry");
        }
    }

    private Map<KnowledgeCategory, List<Identifier>> sortShelves() {
        Map<KnowledgeCategory, List<Identifier>> sorted = new EnumMap<>(KnowledgeCategory.class);
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == net.minecraft.world.item.Items.AIR) continue;
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) continue;
            sorted.computeIfAbsent(KnowledgeCategory.of(item), key -> new ArrayList<>()).add(id);
        }
        sorted.replaceAll((category, entries) -> List.copyOf(entries));
        return Map.copyOf(sorted);
    }

    // ---------------------------------------------------------------- upkeep

    /**
     * Anything a player is carrying has been held, and anything carried long enough has been looked
     * at. One pass over the inventory rather than an event on every pickup: it costs the same, it
     * cannot miss an item that arrived some other way, and it needs no hook into vanilla at all.
     *
     * <p>The two levels fall out of the two passes. A thing seen for the first time is discovered;
     * a thing still in the bag one pass later has been carried around for ten seconds and more, and
     * that is study. Nothing extra is remembered to make that work — the discovered set already
     * <em>is</em> the record of having seen it before.
     *
     * <p>Silent, unlike the acts. Picking up a stack of gravel is not a moment, and a player walking
     * out of a cave with thirty new things should not be handed thirty toasts about it. Breaking,
     * crafting, eating and examining still announce themselves, because each of those was a decision.
     */
    private void tickDiscovery() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) carry(player);
    }

    /** One player's share of the carrying pass. Returns true where this pass taught them anything. */
    public boolean carry(ServerPlayer player) {
        if (player == null || player.isSpectator()) return false;
        PlayerKnowledge knowledge = knowledge(player);
        boolean news = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            Identifier id = idOf(stack.getItem());
            if (id == null) continue;
            news |= knowledge.level(id) == KnowledgeLevel.UNKNOWN
                    ? knowledge.discover(id) : knowledge.study(id);
        }
        if (news) save.setKnowledge(player.getUUID(), knowledge);
        return news;
    }

    /**
     * Section 82, experimenting: an item kept in the main hand across {@link #EXAMINATION_PASSES}
     * passes has been looked at properly, and is studied. Switching slots, emptying the hand or
     * swapping for something else starts the count again, so this rewards holding one thing rather
     * than owning many. One main-hand read per player per pass, and the entry is left marked
     * afterwards so a player who never puts the item down does not re-study it every second.
     *
     * <p>Public because the scheduler and the tests both drive it.
     */
    public void examineHeldItems() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) examine(player);
    }

    /** One player's share of {@link #examineHeldItems()}. Returns true where this pass taught them. */
    public boolean examine(ServerPlayer player) {
        if (player == null || player.isSpectator()) return false;
        ItemStack held = player.getMainHandItem();
        Identifier id = held.isEmpty() ? null : idOf(held.getItem());
        if (id == null) {
            examinations.remove(player.getUUID());
            return false;
        }
        Examination examination = examinations.get(player.getUUID());
        if (examination == null || !id.equals(examination.item)) {
            examinations.put(player.getUUID(), new Examination(id));
            return false;
        }
        if (examination.finished) return false;
        if (++examination.passes < EXAMINATION_PASSES) return false;
        examination.finished = true;
        return study(player, held.getItem());
    }

    /** One item being turned over in one player's hand. */
    private static final class Examination {
        private final Identifier item;
        private int passes;
        private boolean finished;

        private Examination(Identifier item) {
            this.item = item;
        }
    }

    // ---------------------------------------------------------------- telling the player

    /**
     * Records one learning for the toast in the corner of the screen. Discovering an item and then
     * studying it in the same breath is one thing learned, not two, so the later level replaces the
     * earlier note rather than adding to it.
     */
    private void note(UUID player, Identifier id, KnowledgeLevel level) {
        List<KnowledgeNotePayload.Note> notes = pending.computeIfAbsent(player, key -> new ArrayList<>());
        notes.removeIf(note -> note.id().equals(id));
        if (notes.size() >= KnowledgeNotePayload.MAX_NOTES) notes.removeFirst();
        notes.add(new KnowledgeNotePayload.Note(id, level.ordinal()));
    }

    /** What this player has learned but not yet been told about. Visible for tests. */
    public List<KnowledgeNotePayload.Note> pendingNotes(UUID player) {
        return List.copyOf(pending.getOrDefault(player, List.of()));
    }

    /**
     * Sends the gathered learnings and forgets them. Running on the fast tier rather than on each
     * event is what turns breaking a block with a new pick into one toast with two entries instead
     * of two toasts racing each other.
     */
    public void flushNotes() {
        if (pending.isEmpty()) return;
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            iterator.remove();
            if (entry.getValue().isEmpty()) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !ServerPlayNetworking.canSend(player, KnowledgeNotePayload.TYPE)) continue;
            ServerPlayNetworking.send(player, new KnowledgeNotePayload(entry.getValue()));
        }
    }

    // ---------------------------------------------------------------- diagnostics

    public void registerDiagnostics(DiagnosticRegistry registry) {
        registry.register("hardwrought:knowledge", DiagnosticRegistry.Channel.KNOWLEDGE, (level, pos) -> {
            int total = 0;
            for (KnowledgeCategory category : KnowledgeCategory.values()) total += shelf(category).size();
            return String.format(Locale.ROOT, "compendium: %d entries on %d shelves", total,
                    KnowledgeCategory.values().length);
        });
    }

    private static Identifier idOf(ItemLike item) {
        return item == null ? null : BuiltInRegistries.ITEM.getKey(item.asItem());
    }
}
