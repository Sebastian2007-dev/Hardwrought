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

public final class ItemWeightDefinitions extends SimpleReloadListener<Map<Identifier, Double>> {
    private static final String DIRECTORY = "hardwrought/item_weights";
    public static final DataResourceStore.Key<Map<Identifier, Double>> KEY = new DataResourceStore.Key<>();

    @Override
    protected Map<Identifier, Double> prepare(PreparableReloadListener.SharedState state) {
        Map<Identifier, Double> result = new LinkedHashMap<>();
        state.resourceManager().listResources(DIRECTORY, id -> id.getPath().endsWith(".json"))
                .forEach((path, resource) -> {
                    try (var reader = resource.openAsReader()) {
                        ItemWeightDefinition definition = ItemWeightDefinition.CODEC.parse(JsonOps.INSTANCE,
                                JsonParser.parseReader(reader)).getOrThrow(message ->
                                new IllegalArgumentException(path + ": " + message));
                        definition.weights().forEach((item, mass) -> {
                            if (result.put(item, mass) != null) {
                                throw new IllegalArgumentException("Duplicate item weight for " + item);
                            }
                        });
                    } catch (IOException | RuntimeException exception) {
                        throw new IllegalStateException("Invalid item weight definition " + path, exception);
                    }
                });
        return Map.copyOf(result);
    }

    @Override
    protected void apply(Map<Identifier, Double> prepared, PreparableReloadListener.SharedState state) {
        state.get(DataResourceLoader.DATA_RESOURCE_STORE_KEY).put(KEY, prepared);
    }
}
