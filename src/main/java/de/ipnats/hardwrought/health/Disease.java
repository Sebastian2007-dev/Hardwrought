package de.ipnats.hardwrought.health;

import com.mojang.serialization.Codec;

import java.util.Locale;

/**
 * The diseases a body can catch. Each has a course: unnoticed while it takes hold, then the symptoms,
 * a height, and a way back — or, neglected, a turn for the worse until it is critical. Its name changes
 * with that: a cold that is let go becomes pneumonia, an inflamed wound blood poisoning.
 *
 * @param incubation seconds before anything is felt
 * @param course     seconds over which the body's own fight against it wins; the disease grows less
 *                   and less over this time, so a body that is looked after gets well by itself
 * @param growth     how fast it grows at first, in load a second
 * @param recovery   how fast a body that is looked after throws it off, in load a second
 */
public enum Disease {
    /** From bad water and bad food: the stomach and the gut. Costs water. */
    GASTRO(60, 600, 0.25, 0.05),
    /** From being cold and wet too long. Fever, a cough, short breath. */
    COLD(120, 900, 0.20, 0.04),
    /** From a burn or a wound left dirty. Fever, a weak arm, and it eats at health. */
    WOUND(90, 900, 0.20, 0.04);

    public static final Codec<Disease> CODEC = Codec.STRING.xmap(
            name -> Disease.valueOf(name.toUpperCase(Locale.ROOT)), Disease::serializedName);

    private final int incubation;
    private final int course;
    private final double growth;
    private final double recovery;

    Disease(int incubation, int course, double growth, double recovery) {
        this.incubation = incubation;
        this.course = course;
        this.growth = growth;
        this.recovery = recovery;
    }

    public int incubation() {
        return incubation;
    }

    public int course() {
        return course;
    }

    public double growth() {
        return growth;
    }

    public double recovery() {
        return recovery;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** What it is called at this stage: the worse form once it has become severe. */
    public String translationKey(Stage stage) {
        return "disease.hardwrought." + serializedName() + (stage.ordinal() >= Stage.SEVERE.ordinal() ? ".severe" : "");
    }

    /** Where a disease stands. */
    public enum Stage {
        /** Caught, and nothing felt yet. */
        INCUBATING,
        /** Getting better. */
        RECOVERING,
        /** Felt. */
        SYMPTOMS,
        /** At its height, and in its worse form. */
        SEVERE,
        /** Close to killing. */
        CRITICAL;

        public String translationKey() {
            return "disease.hardwrought.stage." + name().toLowerCase(Locale.ROOT);
        }
    }
}
