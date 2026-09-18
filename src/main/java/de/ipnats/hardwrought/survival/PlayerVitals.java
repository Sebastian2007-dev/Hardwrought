package de.ipnats.hardwrought.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.ipnats.hardwrought.core.registry.FoodNutritionDefinition;

/** Persistent, server-authoritative survival values for one player. */
public record PlayerVitals(double stamina, double hydration, double calories, double protein,
                           double carbohydrates, double fat, double micronutrients,
                           double fatigue, double bodyTemperature, double wetness, double stress) {
    public static final double MAX_STAMINA = 100.0;
    public static final double MAX_HYDRATION = 100.0;
    public static final double MAX_CALORIES = 2400.0;
    private static final Codec<PlayerVitals> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("stamina").forGetter(PlayerVitals::stamina),
            Codec.DOUBLE.fieldOf("hydration").forGetter(PlayerVitals::hydration),
            Codec.DOUBLE.fieldOf("calories").forGetter(PlayerVitals::calories),
            Codec.DOUBLE.fieldOf("protein").forGetter(PlayerVitals::protein),
            Codec.DOUBLE.fieldOf("carbohydrates").forGetter(PlayerVitals::carbohydrates),
            Codec.DOUBLE.fieldOf("fat").forGetter(PlayerVitals::fat),
            Codec.DOUBLE.fieldOf("micronutrients").forGetter(PlayerVitals::micronutrients),
            Codec.DOUBLE.fieldOf("fatigue").forGetter(PlayerVitals::fatigue),
            Codec.DOUBLE.fieldOf("body_temperature").forGetter(PlayerVitals::bodyTemperature),
            Codec.DOUBLE.fieldOf("wetness").forGetter(PlayerVitals::wetness),
            // Added after Milestone 1, so worlds saved before it keep loading.
            Codec.DOUBLE.optionalFieldOf("stress", 0.0).forGetter(PlayerVitals::stress)
    ).apply(instance, PlayerVitals::new));
    public static final Codec<PlayerVitals> CODEC = RAW_CODEC.validate(value -> value.valid()
            ? com.mojang.serialization.DataResult.success(value)
            : com.mojang.serialization.DataResult.error(() -> "Invalid survival value"));

    public static PlayerVitals defaults() {
        return new PlayerVitals(100, 100, 2000, 70, 260, 70, 100, 15, 37, 0, 0);
    }

    public PlayerVitals normalized() {
        return new PlayerVitals(clamp(stamina, 0, 100), clamp(hydration, 0, 100),
                clamp(calories, 0, 2400), clamp(protein, 0, 120), clamp(carbohydrates, 0, 360),
                clamp(fat, 0, 120), clamp(micronutrients, 0, 100), clamp(fatigue, 0, 100),
                clamp(bodyTemperature, 30, 43), clamp(wetness, 0, 1), clamp(stress, 0, 100));
    }

    public PlayerVitals withStress(double value) {
        return new PlayerVitals(stamina, hydration, calories, protein, carbohydrates, fat,
                micronutrients, fatigue, bodyTemperature, wetness, value).normalized();
    }

    public PlayerVitals withFatigue(double value) {
        return new PlayerVitals(stamina, hydration, calories, protein, carbohydrates, fat,
                micronutrients, value, bodyTemperature, wetness, stress).normalized();
    }

    public PlayerVitals withStamina(double value) {
        return new PlayerVitals(value, hydration, calories, protein, carbohydrates, fat,
                micronutrients, fatigue, bodyTemperature, wetness, stress).normalized();
    }

    public PlayerVitals drink(double amount) {
        return new PlayerVitals(stamina, hydration + amount, calories, protein, carbohydrates, fat,
                micronutrients, fatigue, bodyTemperature, wetness, stress).normalized();
    }

    public PlayerVitals eat(int nutrition, float saturation) {
        return new PlayerVitals(stamina, hydration + nutrition * 0.35, calories + nutrition * 115.0,
                protein + nutrition * 2.0, carbohydrates + nutrition * 5.0,
                fat + saturation * 1.5, micronutrients + nutrition * 0.6,
                fatigue, bodyTemperature, wetness, stress).normalized();
    }

    public PlayerVitals eat(FoodNutritionDefinition food) {
        return new PlayerVitals(stamina, hydration + food.hydration(), calories + food.calories(),
                protein + food.protein(), carbohydrates + food.carbohydrates(), fat + food.fat(),
                micronutrients + food.micronutrients(), fatigue, bodyTemperature, wetness, stress).normalized();
    }

    public static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private boolean valid() {
        return finite(stamina, hydration, calories, protein, carbohydrates, fat, micronutrients,
                fatigue, bodyTemperature, wetness, stress)
                && stamina >= 0 && stamina <= 100 && hydration >= 0 && hydration <= 100
                && calories >= 0 && calories <= 2400 && protein >= 0 && protein <= 120
                && carbohydrates >= 0 && carbohydrates <= 360 && fat >= 0 && fat <= 120
                && micronutrients >= 0 && micronutrients <= 100 && fatigue >= 0 && fatigue <= 100
                && bodyTemperature >= 30 && bodyTemperature <= 43 && wetness >= 0 && wetness <= 1
                && stress >= 0 && stress <= 100;
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
