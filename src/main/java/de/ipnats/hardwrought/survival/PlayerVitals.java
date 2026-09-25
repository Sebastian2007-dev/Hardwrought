package de.ipnats.hardwrought.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Persistent, server-authoritative survival values for one player.
 *
 * <p>How full a player is lives on the vanilla hunger bar; what they have been eating lives here, as
 * {@link Nutrition}. Worlds saved while hunger was still counted in calories keep loading: the old
 * energy and nutrient fields are simply no longer read, and the diet starts from the middle.
 */
public record PlayerVitals(double stamina, double hydration, Nutrition nutrition,
                           double fatigue, double bodyTemperature, double wetness, double stress) {
    public static final double MAX_STAMINA = 100.0;
    public static final double MAX_HYDRATION = 100.0;
    private static final Codec<PlayerVitals> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.fieldOf("stamina").forGetter(PlayerVitals::stamina),
            Codec.DOUBLE.fieldOf("hydration").forGetter(PlayerVitals::hydration),
            Nutrition.CODEC.optionalFieldOf("nutrition", Nutrition.START).forGetter(PlayerVitals::nutrition),
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
        return new PlayerVitals(100, 100, Nutrition.START, 15, 37, 0, 0);
    }

    public PlayerVitals normalized() {
        return new PlayerVitals(clamp(stamina, 0, 100), clamp(hydration, 0, 100),
                nutrition == null ? Nutrition.START : nutrition.clamped(), clamp(fatigue, 0, 100),
                clamp(bodyTemperature, 30, 43), clamp(wetness, 0, 1), clamp(stress, 0, 100));
    }

    public PlayerVitals withStress(double value) {
        return new PlayerVitals(stamina, hydration, nutrition, fatigue, bodyTemperature, wetness, value).normalized();
    }

    public PlayerVitals withFatigue(double value) {
        return new PlayerVitals(stamina, hydration, nutrition, value, bodyTemperature, wetness, stress).normalized();
    }

    public PlayerVitals withStamina(double value) {
        return new PlayerVitals(value, hydration, nutrition, fatigue, bodyTemperature, wetness, stress).normalized();
    }

    public PlayerVitals withHydration(double value) {
        return new PlayerVitals(stamina, value, nutrition, fatigue, bodyTemperature, wetness, stress).normalized();
    }

    public PlayerVitals withNutrition(Nutrition value) {
        return new PlayerVitals(stamina, hydration, value, fatigue, bodyTemperature, wetness, stress).normalized();
    }

    public PlayerVitals drink(double amount) {
        return withHydration(hydration + amount);
    }

    /** What eating something brings: its nutrients, as much of them as the body takes in, and its water. */
    public PlayerVitals eat(Nutrition food, double water, double absorbed) {
        return new PlayerVitals(stamina, hydration + water, nutrition.plus(food, absorbed), fatigue,
                bodyTemperature, wetness, stress).normalized();
    }

    public static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private boolean valid() {
        return finite(stamina, hydration, fatigue, bodyTemperature, wetness, stress)
                && nutrition != null && nutrition.valid()
                && stamina >= 0 && stamina <= 100 && hydration >= 0 && hydration <= 100
                && fatigue >= 0 && fatigue <= 100
                && bodyTemperature >= 30 && bodyTemperature <= 43 && wetness >= 0 && wetness <= 1
                && stress >= 0 && stress <= 100;
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
