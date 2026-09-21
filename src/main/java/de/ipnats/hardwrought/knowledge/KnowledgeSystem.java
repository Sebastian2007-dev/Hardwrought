package de.ipnats.hardwrought.knowledge;

import de.ipnats.hardwrought.core.debug.DiagnosticRegistry;
import de.ipnats.hardwrought.core.save.CoreSaveData;
import de.ipnats.hardwrought.core.simulation.SimulationScheduler;
import de.ipnats.hardwrought.core.simulation.SimulationTier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.EnumMap;
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
 * <p>Bounded like every other system here: one inventory scan per player per slow pass, forty-one
 * slots, no registry sweeps in a tick job.
 */
public final class KnowledgeSystem {
    private final MinecraftServer server;
    private final CoreSaveData save;
    /** The registry does not change after start-up, so the shelves are sorted once. */
    private Map<KnowledgeCategory, List<Identifier>> shelves;
    /** The recipe browser behind the R and U keys, indexed on first use. */
    private final Compendium compendium = new Compendium();

    public KnowledgeSystem(MinecraftServer server, CoreSaveData save, SimulationScheduler scheduler) {
        this.server = server;
        this.save = save;
        scheduler.register("hardwrought:knowledge_discovery", SimulationTier.SLOW, this::tickDiscovery);
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
        return true;
    }

    /** Operator and test entry point; ordinary play fills this in through holding and working. */
    public void forget(UUID player) {
        save.setKnowledge(player, PlayerKnowledge.empty());
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
     * Anything a player is carrying has been held, and anything held is discovered. One pass over
     * the inventory rather than an event on every pickup: it costs the same, it cannot miss an item
     * that arrived some other way, and it needs no hook into vanilla at all.
     */
    private void tickDiscovery() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            PlayerKnowledge knowledge = knowledge(player);
            boolean news = false;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.isEmpty()) continue;
                news |= knowledge.discover(idOf(stack.getItem()));
            }
            if (news) save.setKnowledge(player.getUUID(), knowledge);
        }
    }

    // ---------------------------------------------------------------- diagnostics

    public void registerDiagnostics(DiagnosticRegistry registry) {
        registry.register("hardwrought:knowledge", DiagnosticRegistry.Channel.STRUCTURE, (level, pos) -> {
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
