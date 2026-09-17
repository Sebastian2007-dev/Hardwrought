package de.ipnats.hardwrought.core.registry;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.v1.DataResourceLoader;
import net.fabricmc.fabric.api.resource.v1.DataResourceStore;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MaterialDefinitions extends SimpleReloadListener<Map<Identifier, MaterialDefinition>> {
    private static final String DIRECTORY = "hardwrought/materials";
    public static final DataResourceStore.Key<Map<Identifier, MaterialDefinition>> KEY = new DataResourceStore.Key<>();

    @Override
    protected Map<Identifier, MaterialDefinition> prepare(PreparableReloadListener.SharedState state) {
        Map<Identifier, MaterialDefinition> result = new LinkedHashMap<>();
        state.resourceManager().listResources(DIRECTORY, id -> id.getPath().endsWith(".json"))
                .forEach((path, resource) -> {
                    Identifier id = Identifier.fromNamespaceAndPath(path.getNamespace(),
                            path.getPath().substring(DIRECTORY.length() + 1, path.getPath().length() - 5));
                    try (var reader = resource.openAsReader()) {
                        MaterialDefinition value = MaterialDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                                .getOrThrow(message -> new IllegalArgumentException(path + ": " + message));
                        result.put(id, value);
                    } catch (IOException | RuntimeException exception) {
                        // Abort the complete reload; never expose a partially updated material table.
                        throw new IllegalStateException("Invalid material definition " + path, exception);
                    }
                });
        return Map.copyOf(result);
    }

    @Override
    protected void apply(Map<Identifier, MaterialDefinition> prepared, PreparableReloadListener.SharedState state) {
        state.get(DataResourceLoader.DATA_RESOURCE_STORE_KEY).put(KEY, prepared);
    }
}
