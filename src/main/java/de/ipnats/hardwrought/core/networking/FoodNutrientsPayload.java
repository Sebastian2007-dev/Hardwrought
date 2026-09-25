package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.FoodNutritionDefinition;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What every food brings to the five nutrients, sent once when a player joins, so the compendium can
 * say it. The numbers come from a datapack and therefore live on the server.
 */
public record FoodNutrientsPayload(Map<Identifier, FoodNutritionDefinition> foods) implements CustomPacketPayload {
    public static final Type<FoodNutrientsPayload> TYPE = new Type<>(Hardwrought.id("food_nutrients_v1"));
    public static final int MAX_ENTRIES = 4096;

    public static final StreamCodec<RegistryFriendlyByteBuf, FoodNutrientsPayload> CODEC = new StreamCodec<>() {
        @Override
        public FoodNutrientsPayload decode(RegistryFriendlyByteBuf buffer) {
            int count = Math.min(buffer.readVarInt(), MAX_ENTRIES);
            Map<Identifier, FoodNutritionDefinition> foods = new LinkedHashMap<>(count);
            for (int index = 0; index < count; index++) {
                Identifier item = buffer.readIdentifier();
                try {
                    foods.put(item, new FoodNutritionDefinition(item, buffer.readDouble(), buffer.readDouble(),
                            buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
                } catch (IllegalArgumentException ignored) {
                    // A value out of range from a modified server is dropped, not trusted.
                }
            }
            return new FoodNutrientsPayload(foods);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, FoodNutrientsPayload payload) {
            int count = Math.min(payload.foods.size(), MAX_ENTRIES);
            buffer.writeVarInt(count);
            payload.foods.values().stream().limit(count).forEach(food -> {
                buffer.writeIdentifier(food.item());
                buffer.writeDouble(food.protein());
                buffer.writeDouble(food.fat());
                buffer.writeDouble(food.carbohydrates());
                buffer.writeDouble(food.vitamins());
                buffer.writeDouble(food.fiber());
                buffer.writeDouble(food.hydration());
            });
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
