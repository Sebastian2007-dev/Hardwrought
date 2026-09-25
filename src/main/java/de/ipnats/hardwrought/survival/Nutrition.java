package de.ipnats.hardwrought.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The five nutrient levels of one body, each 0 to 100 (see {@link Nutrient}).
 *
 * <p>What lacking or having too much of each does:
 * <ul>
 *   <li>Protein: lacking, the body is weak and hits for less; too much, it burns through food faster.
 *   <li>Fat: lacking, the cold gets into the body sooner; too much, the body is slow.
 *   <li>Carbohydrates: lacking, stamina comes back slowly; too much, the body tires sooner.
 *   <li>Vitamins and minerals: lacking, the body cannot hold as much health; too much, a little less.
 *   <li>Fibre: lacking, food does not stay long; too much, less is taken from what is eaten.
 * </ul>
 * All five healthy at once is a balanced diet: stamina comes back faster and the body tires later.
 */
public record Nutrition(double protein, double fat, double carbohydrates, double vitamins, double fiber) {
    public static final Nutrition START = new Nutrition(Nutrient.START, Nutrient.START, Nutrient.START,
            Nutrient.START, Nutrient.START);
    public static final Nutrition NONE = new Nutrition(0, 0, 0, 0, 0);

    public static final Codec<Nutrition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(0, Nutrient.MAX).fieldOf("protein").forGetter(Nutrition::protein),
            Codec.doubleRange(0, Nutrient.MAX).fieldOf("fat").forGetter(Nutrition::fat),
            Codec.doubleRange(0, Nutrient.MAX).fieldOf("carbohydrates").forGetter(Nutrition::carbohydrates),
            Codec.doubleRange(0, Nutrient.MAX).fieldOf("vitamins").forGetter(Nutrition::vitamins),
            Codec.doubleRange(0, Nutrient.MAX).fieldOf("fiber").forGetter(Nutrition::fiber)
    ).apply(instance, Nutrition::new));

    public double get(Nutrient nutrient) {
        return switch (nutrient) {
            case PROTEIN -> protein;
            case FAT -> fat;
            case CARBOHYDRATES -> carbohydrates;
            case VITAMINS -> vitamins;
            case FIBER -> fiber;
        };
    }

    public Nutrient.Status status(Nutrient nutrient) {
        return Nutrient.status(get(nutrient));
    }

    public boolean low(Nutrient nutrient) {
        return status(nutrient) == Nutrient.Status.LOW;
    }

    public boolean high(Nutrient nutrient) {
        return status(nutrient) == Nutrient.Status.HIGH;
    }

    /** Every level healthy at once. */
    public boolean balanced() {
        for (Nutrient nutrient : Nutrient.values()) {
            if (status(nutrient) != Nutrient.Status.HEALTHY) return false;
        }
        return true;
    }

    /** These levels with another set added, scaled by how much of it the body takes in. */
    public Nutrition plus(Nutrition food, double absorbed) {
        return new Nutrition(protein + food.protein * absorbed, fat + food.fat * absorbed,
                carbohydrates + food.carbohydrates * absorbed, vitamins + food.vitamins * absorbed,
                fiber + food.fiber * absorbed).clamped();
    }

    /**
     * One second of the body using them up. Work — anything above resting, in the same units the
     * rest of the metabolism counts — burns carbohydrates first and fat after.
     */
    public Nutrition drained(double seconds, double work) {
        return new Nutrition(
                protein - Nutrient.PROTEIN.drainPerSecond() * seconds,
                fat - (Nutrient.FAT.drainPerSecond() + work * 0.002) * seconds,
                carbohydrates - (Nutrient.CARBOHYDRATES.drainPerSecond() + work * 0.005) * seconds,
                vitamins - Nutrient.VITAMINS.drainPerSecond() * seconds,
                fiber - Nutrient.FIBER.drainPerSecond() * seconds).clamped();
    }

    public Nutrition clamped() {
        return new Nutrition(clamp(protein), clamp(fat), clamp(carbohydrates), clamp(vitamins), clamp(fiber));
    }

    public boolean valid() {
        for (Nutrient nutrient : Nutrient.values()) {
            double value = get(nutrient);
            if (!Double.isFinite(value) || value < 0 || value > Nutrient.MAX) return false;
        }
        return true;
    }

    private static double clamp(double value) {
        return PlayerVitals.clamp(value, 0, Nutrient.MAX);
    }
}
