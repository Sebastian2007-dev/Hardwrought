package de.ipnats.hardwrought.core.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

public record FoodNutritionDefinition(Identifier item, double calories, double protein,
                                      double carbohydrates, double fat, double micronutrients,
                                      double hydration) {
    private static final Codec<Double> NON_NEGATIVE = Codec.DOUBLE.validate(value ->
            Double.isFinite(value) && value >= 0 && value <= 10_000
                    ? DataResult.success(value) : DataResult.error(() -> "Expected a finite non-negative value"));
    private static final Codec<Double> HYDRATION = Codec.DOUBLE.validate(value ->
            Double.isFinite(value) && value >= -100 && value <= 100
                    ? DataResult.success(value) : DataResult.error(() -> "Invalid hydration value"));
    public static final Codec<FoodNutritionDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("item").forGetter(FoodNutritionDefinition::item),
            NON_NEGATIVE.fieldOf("calories").forGetter(FoodNutritionDefinition::calories),
            NON_NEGATIVE.fieldOf("protein").forGetter(FoodNutritionDefinition::protein),
            NON_NEGATIVE.fieldOf("carbohydrates").forGetter(FoodNutritionDefinition::carbohydrates),
            NON_NEGATIVE.fieldOf("fat").forGetter(FoodNutritionDefinition::fat),
            NON_NEGATIVE.fieldOf("micronutrients").forGetter(FoodNutritionDefinition::micronutrients),
            HYDRATION.optionalFieldOf("hydration", 0.0).forGetter(FoodNutritionDefinition::hydration)
    ).apply(instance, FoodNutritionDefinition::new));

    public FoodNutritionDefinition {
        if (item == null || !valid(calories) || !valid(protein) || !valid(carbohydrates)
                || !valid(fat) || !valid(micronutrients) || !Double.isFinite(hydration)
                || hydration < -100 || hydration > 100) {
            throw new IllegalArgumentException("Invalid food nutrition definition");
        }
    }

    private static boolean valid(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 10_000;
    }
}
