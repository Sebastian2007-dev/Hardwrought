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

    /**
     * How much stronger than at rest the body burns a nutrient it has this much of: nothing extra up
     * to {@link Nutrient#START}, then more and more, up to {@value #EXCESS_BURN} times as much again
     * at the very top.
     */
    static final double EXCESS_BURN = 4.0;
    /** A food is taken in bite by bite, so that one big meal cannot jump a level past what it would settle at. */
    private static final int BITES = 8;

    /**
     * The share of a food's measure a body with this level takes in: all of it up to
     * {@link Nutrient#START}, then less and less, and nothing at all of what it is already full of.
     *
     * <p>Together with {@link #burn} this is what makes a diet hold steady. How much a player eats is
     * set by hunger, not by what the body needs of each nutrient; if every bite simply added its
     * measure, any way of eating either starved a level or drove it to the top. This way each level
     * settles: a mixed diet comes to rest inside the healthy band however much of it is eaten, and
     * only living on one kind of food pushes that food's nutrients over it.
     */
    public static double uptake(double level) {
        if (level <= Nutrient.START) return 1.0;
        return Math.max(0, (Nutrient.MAX - level) / (Nutrient.MAX - Nutrient.START));
    }

    /** How many times its resting rate the body uses up a nutrient it has this much of; see {@link #uptake}. */
    public static double burn(double level) {
        return 1.0 + EXCESS_BURN * Math.max(0, level - Nutrient.START) / (Nutrient.MAX - Nutrient.START);
    }

    private static double fed(double level, double food, double absorbed) {
        double bite = food * absorbed / BITES;
        if (bite <= 0) return level;
        for (int i = 0; i < BITES; i++) level += bite * uptake(level);
        return level;
    }

    /** These levels with a food added, scaled by how much of it the body takes in; see {@link #uptake}. */
    public Nutrition plus(Nutrition food, double absorbed) {
        return new Nutrition(fed(protein, food.protein, absorbed), fed(fat, food.fat, absorbed),
                fed(carbohydrates, food.carbohydrates, absorbed), fed(vitamins, food.vitamins, absorbed),
                fed(fiber, food.fiber, absorbed)).clamped();
    }

    /**
     * One second of the body using them up, the faster the more it has of each (see {@link #burn}).
     * Work — anything above resting, in the same units the rest of the metabolism counts — burns
     * carbohydrates first and fat after.
     */
    public Nutrition drained(double seconds, double work) {
        return new Nutrition(
                protein - Nutrient.PROTEIN.drainPerSecond() * burn(protein) * seconds,
                fat - (Nutrient.FAT.drainPerSecond() * burn(fat) + work * 0.002) * seconds,
                carbohydrates - (Nutrient.CARBOHYDRATES.drainPerSecond() * burn(carbohydrates) + work * 0.005) * seconds,
                vitamins - Nutrient.VITAMINS.drainPerSecond() * burn(vitamins) * seconds,
                fiber - Nutrient.FIBER.drainPerSecond() * burn(fiber) * seconds).clamped();
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
