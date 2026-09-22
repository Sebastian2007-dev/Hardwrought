package de.ipnats.hardwrought.knowledge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.networking.CompendiumPagePayload;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where a thing comes from when nobody makes it: what drops it, what it is broken out of, and what
 * chest it turns up in.
 *
 * <p>A recipe browser that only knows recipes is a browser that shrugs at a feather. Most of the
 * early game is not crafted at all, so the compendium reads the loot tables as well and shows their
 * answer alongside the recipes.
 *
 * <p>Loot tables keep their contents private, but they can serialise themselves, so this walks the
 * JSON the table produces through its own codec rather than reaching into its fields with a mixin.
 * That costs one pass over every table, done once and only when somebody first asks, and it keeps
 * working when the internals move.
 */
public final class LootSources {
    /** What kind of place an item comes from. The ordinal travels in the packet. */
    public static final int KIND_MOB = 0;
    public static final int KIND_BLOCK = 1;
    public static final int KIND_CONTAINER = 2;

    private Map<Item, List<CompendiumPagePayload.Source>> byItem;
    private int indexedTables = -1;

    /** Where this item is found, in the order mobs, blocks, containers. */
    public List<CompendiumPagePayload.Source> of(MinecraftServer server, Item item, PlayerKnowledge known) {
        index(server);
        List<CompendiumPagePayload.Source> sources = byItem.getOrDefault(item, List.of());
        if (sources.isEmpty()) return List.of();
        List<CompendiumPagePayload.Source> answer = new ArrayList<>(sources.size());
        for (CompendiumPagePayload.Source source : sources) {
            if (answer.size() >= CompendiumPagePayload.MAX_SOURCES) break;
            // Only a block is an item the player may or may not have seen; a mob and a chest are
            // places in the world, and the compendium does not pretend not to know where things are.
            int level = source.kind() == KIND_BLOCK ? known.level(source.id()).ordinal() : 0;
            answer.add(new CompendiumPagePayload.Source(source.kind(), source.id(), level));
        }
        return List.copyOf(answer);
    }

    // ---------------------------------------------------------------- the index

    private void index(MinecraftServer server) {
        HolderLookup.Provider lookup = server.reloadableRegistries().lookup();
        HolderLookup.RegistryLookup<LootTable> tables = lookup.lookupOrThrow(Registries.LOOT_TABLE);
        List<Holder.Reference<LootTable>> all = tables.listElements().toList();
        if (byItem != null && all.size() == indexedTables) return;

        Map<Identifier, Block> blockTables = new HashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            block.getLootTable().ifPresent(key -> blockTables.put(key.identifier(), block));
        }
        Map<Identifier, EntityType<?>> mobTables = new HashMap<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            type.getDefaultLootTable().ifPresent(key -> mobTables.put(key.identifier(), type));
        }

        var ops = lookup.createSerializationContext(JsonOps.INSTANCE);
        Map<Item, Set<CompendiumPagePayload.Source>> found = new IdentityHashMap<>();
        for (Holder.Reference<LootTable> holder : all) {
            Identifier id = holder.key().identifier();
            CompendiumPagePayload.Source source = describe(id, blockTables, mobTables);
            if (source == null) continue;
            for (Item item : contents(holder.value(), ops, lookup)) {
                // A block that drops itself is not a discovery, it is the block.
                if (source.kind() == KIND_BLOCK && matches(source.id(), item)) continue;
                found.computeIfAbsent(item, key -> new LinkedHashSet<>()).add(source);
            }
        }
        Map<Item, List<CompendiumPagePayload.Source>> sorted = new IdentityHashMap<>();
        found.forEach((item, sources) -> {
            List<CompendiumPagePayload.Source> ordered = new ArrayList<>(sources);
            ordered.sort((left, right) -> left.kind() != right.kind()
                    ? Integer.compare(left.kind(), right.kind())
                    : left.id().compareTo(right.id()));
            sorted.put(item, List.copyOf(ordered));
        });
        byItem = sorted;
        indexedTables = all.size();
    }

    private static CompendiumPagePayload.Source describe(Identifier table, Map<Identifier, Block> blocks,
                                                         Map<Identifier, EntityType<?>> mobs) {
        EntityType<?> mob = mobs.get(table);
        if (mob != null) {
            return new CompendiumPagePayload.Source(KIND_MOB,
                    BuiltInRegistries.ENTITY_TYPE.getKey(mob), 0);
        }
        Block block = blocks.get(table);
        if (block != null) {
            Identifier id = BuiltInRegistries.ITEM.getKey(block.asItem());
            return id == null ? null : new CompendiumPagePayload.Source(KIND_BLOCK, id, 0);
        }
        return new CompendiumPagePayload.Source(KIND_CONTAINER, table, 0);
    }

    private static boolean matches(Identifier sourceItem, Item item) {
        return sourceItem.equals(BuiltInRegistries.ITEM.getKey(item));
    }

    /**
     * Every item one loot table can produce. Read out of the table serialised through its own codec,
     * which is the only description of its contents it offers.
     */
    private static Set<Item> contents(LootTable table, com.mojang.serialization.DynamicOps<JsonElement> ops,
                                      HolderLookup.Provider lookup) {
        Set<Item> items = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            LootTable.DIRECT_CODEC.encodeStart(ops, table).result()
                    .ifPresent(json -> walk(json, items, lookup));
        } catch (RuntimeException failure) {
            Hardwrought.LOGGER.debug("Loot table could not be read for the compendium", failure);
        }
        return items;
    }

    private static void walk(JsonElement element, Set<Item> items, HolderLookup.Provider lookup) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> walk(child, items, lookup));
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        String type = string(object, "type");
        String name = string(object, "name");
        if (name != null && ("minecraft:item".equals(type) || "item".equals(type))) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(name));
            if (item != null) items.add(item);
        } else if (name != null && ("minecraft:tag".equals(type) || "tag".equals(type))) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(name));
            lookup.lookupOrThrow(Registries.ITEM).get(tag)
                    .ifPresent(set -> set.forEach(holder -> items.add(holder.value())));
        }
        // A pool nests entries, a function nests items, a nested table nests everything. The walk
        // does not need to know which is which, only that item entries look the same wherever they are.
        for (var entry : object.entrySet()) walk(entry.getValue(), items, lookup);
    }

    private static String string(JsonObject object, String field) {
        if (!object.has(field)) return null;
        JsonElement value = object.get(field);
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

}
