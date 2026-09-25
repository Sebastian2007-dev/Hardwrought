package de.ipnats.hardwrought.core.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.ipnats.hardwrought.survival.Nutrition;
import net.minecraft.resources.Identifier;

/**
 * What one food brings to each of the five nutrients (see {@link de.ipnats.hardwrought.survival.Nutrient}),
 * in points of the 0-100 levels, and the water in it. How filling it is stays the vanilla food value.
 *
 * <p>Read from {@code data/<namespace>/hardwrought/food_nutrition}. A food without a file here is
 * guessed from its vanilla values, so a food from another mod still feeds, if blandly.
 */
public record FoodNutritionDefinition(Identifier item, double protein, double fat, double carbohydrates,
                                      double vitamins, double fiber, double hydration) {
    /** The most one food may bring to one nutrient. */
    public static final double MAX_PER_FOOD = 60.0;
    private static final Codec<Double> AMOUNT = Codec.DOUBLE.validate(value ->
            Double.isFinite(value) && value >= 0 && value <= MAX_PER_FOOD
                    ? DataResult.success(value) : DataResult.error(() -> "Expected 0 to " + MAX_PER_FOOD));
    private static final Codec<Double> HYDRATION = Codec.DOUBLE.validate(value ->
            Double.isFinite(value) && value >= -100 && value <= 100
                    ? DataResult.success(value) : DataResult.error(() -> "Invalid hydration value"));
    public static final Codec<FoodNutritionDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("item").forGetter(FoodNutritionDefinition::item),
            AMOUNT.optionalFieldOf("protein", 0.0).forGetter(FoodNutritionDefinition::protein),
            AMOUNT.optionalFieldOf("fat", 0.0).forGetter(FoodNutritionDefinition::fat),
            AMOUNT.optionalFieldOf("carbohydrates", 0.0).forGetter(FoodNutritionDefinition::carbohydrates),
            AMOUNT.optionalFieldOf("vitamins", 0.0).forGetter(FoodNutritionDefinition::vitamins),
            AMOUNT.optionalFieldOf("fiber", 0.0).forGetter(FoodNutritionDefinition::fiber),
            HYDRATION.optionalFieldOf("hydration", 0.0).forGetter(FoodNutritionDefinition::hydration)
    ).apply(instance, FoodNutritionDefinition::new));

    public FoodNutritionDefinition {
        if (item == null || !valid(protein) || !valid(fat) || !valid(carbohydrates) || !valid(vitamins)
                || !valid(fiber) || !Double.isFinite(hydration) || hydration < -100 || hydration > 100) {
            throw new IllegalArgumentException("Invalid food nutrition definition");
        }
    }

    public Nutrition nutrients() {
        return new Nutrition(protein, fat, carbohydrates, vitamins, fiber);
    }

    /**
     * A guess for a food nobody wrote down: mostly carbohydrate, a little of the rest, scaled by how
     * filling vanilla says it is.
     */
    public static FoodNutritionDefinition guess(Identifier item, int vanillaNutrition) {
        double n = Math.max(0, Math.min(20, vanillaNutrition));
        return new FoodNutritionDefinition(item, n * 0.6, n * 0.4, n * 1.2, n * 0.4, n * 0.4, 0);
    }

    private static boolean valid(double value) {
        return Double.isFinite(value) && value >= 0 && value <= MAX_PER_FOOD;
    }
}
