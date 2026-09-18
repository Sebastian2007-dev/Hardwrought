package de.ipnats.hardwrought.core.registry;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.v1.DataResourceLoader;
import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Loads datapack files that describe one profile for a group of registry entries and indexes them
 * per entry — items for the combat profiles, biomes for the water ones. A profile with an empty list
 * or an entry claimed twice aborts the whole reload, so the server never runs with a partially
 * updated table.
 */
public abstract class ProfileDefinitions<T> extends SimpleReloadListener<Map<Identifier, T>> {
    private final String directory;
    private final Codec<T> codec;
    private final Function<T, List<Identifier>> entries;

    protected ProfileDefinitions(String directory, Codec<T> codec, Function<T, List<Identifier>> entries) {
        this.directory = directory;
        this.codec = codec;
        this.entries = entries;
    }

    protected abstract DataResourceStore.Key<Map<Identifier, T>> key();

    @Override
    protected Map<Identifier, T> prepare(PreparableReloadListener.SharedState state) {
        Map<Identifier, T> result = new LinkedHashMap<>();
        state.resourceManager().listResources(directory, id -> id.getPath().endsWith(".json"))
                .forEach((path, resource) -> {
                    try (var reader = resource.openAsReader()) {
                        T value = codec.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                                .getOrThrow(message -> new IllegalArgumentException(path + ": " + message));
                        List<Identifier> claimed = entries.apply(value);
                        if (claimed.isEmpty()) throw new IllegalArgumentException("Profile lists no entries");
                        for (Identifier entry : claimed) {
                            if (result.put(entry, value) != null) {
                                throw new IllegalArgumentException("Duplicate profile for " + entry);
                            }
                        }
                    } catch (IOException | RuntimeException exception) {
                        throw new IllegalStateException("Invalid profile definition " + path, exception);
                    }
                });
        return Map.copyOf(result);
    }

    @Override
    protected void apply(Map<Identifier, T> prepared, PreparableReloadListener.SharedState state) {
        state.get(DataResourceLoader.DATA_RESOURCE_STORE_KEY).put(key(), prepared);
    }
}
