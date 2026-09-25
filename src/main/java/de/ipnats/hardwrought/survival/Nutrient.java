package de.ipnats.hardwrought.survival;

/**
 * The five things a diet has to balance. Each is a level from 0 to 100 that the body uses up over
 * time and food fills again, every food by its own measure (see
 * {@link de.ipnats.hardwrought.core.registry.FoodNutritionDefinition}).
 *
 * <p>Between {@link #LOW} and {@link #HIGH} a level is healthy. Below it the body lacks it, above it
 * the body has too much of it, and both have their own cost (see {@link Nutrition}); all five healthy
 * at once is a balanced diet, and that is worth something too. Energy is not one of the five: how
 * much a player has eaten is the vanilla hunger bar, and what they have eaten is this.
 *
 * <p>The rates are sized against the day: a level halfway up the healthy band falls into lack after
 * about two Minecraft days of eating nothing that has it, so living on one food catches up with a
 * player within four or five days, not within the hour.
 */
public enum Nutrient {
    PROTEIN("protein", 0.0075, 0xFFC96B5B),
    FAT("fat", 0.006, 0xFFE0C063),
    CARBOHYDRATES("carbohydrates", 0.009, 0xFFD89A4E),
    VITAMINS("vitamins", 0.005, 0xFF7FC46A),
    FIBER("fiber", 0.008, 0xFF9C7E58);

    public static final double MAX = 100.0;
    /** Below this the body lacks the nutrient. */
    public static final double LOW = 30.0;
    /** Above this it has too much of it. */
    public static final double HIGH = 80.0;
    /** Where a new body starts: comfortably inside the band, with a day or so to go on. */
    public static final double START = 55.0;

    private final String id;
    private final double drainPerSecond;
    private final int color;

    Nutrient(String id, double drainPerSecond, int color) {
        this.id = id;
        this.drainPerSecond = drainPerSecond;
        this.color = color;
    }

    public String id() {
        return id;
    }

    /** What the body uses of it per second, doing nothing. Work adds to fat and carbohydrates. */
    public double drainPerSecond() {
        return drainPerSecond;
    }

    /** The colour its bar is drawn in. */
    public int color() {
        return color;
    }

    public String translationKey() {
        return "nutrient.hardwrought." + id;
    }

    /** Lacking, healthy or too much, for a level. */
    public static Status status(double level) {
        if (level < LOW) return Status.LOW;
        if (level > HIGH) return Status.HIGH;
        return Status.HEALTHY;
    }

    public enum Status {
        LOW, HEALTHY, HIGH;

        public String translationKey() {
            return "nutrient.hardwrought.status." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** What lacking or having too much of this does, as a translation key; null while healthy. */
    public String effectKey(Status status) {
        return status == Status.HEALTHY ? null
                : "nutrient.hardwrought." + id + "." + status.name().toLowerCase(java.util.Locale.ROOT);
    }
}
