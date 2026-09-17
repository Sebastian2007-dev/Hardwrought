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

public final class FoodNutritionDefinitions extends SimpleReloadListener<Map<Identifier, FoodNutritionDefinition>> {
    private static final String DIRECTORY = "hardwrought/food_nutrition";
    public static final DataResourceStore.Key<Map<Identifier, FoodNutritionDefinition>> KEY = new DataResourceStore.Key<>();

    @Override
    protected Map<Identifier, FoodNutritionDefinition> prepare(PreparableReloadListener.SharedState state) {
        Map<Identifier, FoodNutritionDefinition> result = new LinkedHashMap<>();
        state.resourceManager().listResources(DIRECTORY, id -> id.getPath().endsWith(".json"))
                .forEach((path, resource) -> {
                    try (var reader = resource.openAsReader()) {
                        FoodNutritionDefinition definition = FoodNutritionDefinition.CODEC.parse(JsonOps.INSTANCE,
                                JsonParser.parseReader(reader)).getOrThrow(message ->
                                new IllegalArgumentException(path + ": " + message));
                        if (result.put(definition.item(), definition) != null) {
                            throw new IllegalArgumentException("Duplicate food nutrition for " + definition.item());
                        }
                    } catch (IOException | RuntimeException exception) {
                        throw new IllegalStateException("Invalid food nutrition definition " + path, exception);
                    }
                });
        return Map.copyOf(result);
    }

    @Override
    protected void apply(Map<Identifier, FoodNutritionDefinition> prepared, PreparableReloadListener.SharedState state) {
        state.get(DataResourceLoader.DATA_RESOURCE_STORE_KEY).put(KEY, prepared);
    }
}
