package de.ipnats.hardwrought.magic;

import java.util.List;

/**
 * How well a spell was cast (magic specification sections 12 to 16): not a yes or a no, but an
 * accuracy, a stability and a power, and from the stability one of five outcomes.
 *
 * <p>What goes wrong is not left to chance. A medium failure is decided by <em>which</em> rune was
 * drawn worst: a poor core rune is read as the rune it most resembled, a poor auxiliary rune sends
 * the spell astray, a poor amplifier makes it surge. A severe failure turns the spell on its caster, and
 * how much that hurts depends on the spell, not on luck: a light spell gone wrong is a fright, a
 * fireball gone wrong is a fireball.
 *
 * @param accuracy how closely the runes were drawn, on average, 0 to 1
 * @param stability how well the spell holds together, 0 to 1
 * @param power how strong it comes out relative to a perfect plain cast; 1.03 is a fast, clean one
 * @param outcome what happens
 * @param worst the index of the worst drawn rune, which decides a medium failure's kind
 */
public record CastQuality(double accuracy, double stability, double power, Outcome outcome, int worst) {
    /** What a drawn rune does in its spell. */
    public enum Role {
        /** The first element rune. */
        CORE,
        /** Every further element rune. */
        AMPLIFIER,
        /** An auxiliary rune. */
        SIGN
    }

    /** At or above this stability a spell works as drawn. */
    public static final double SUCCESS = 0.68;
    /** At or above this, a minor failure: it works, weaker. */
    public static final double MINOR = 0.5;
    /** At or above this, a medium failure; below it, a severe one. */
    public static final double MEDIUM = 0.32;

    public enum Outcome {
        /** Works as drawn. */
        SUCCESS,
        /** Works, but weaker and shorter. */
        WEAKENED,
        /** The core was read as the rune it most resembled. */
        WRONG_ELEMENT,
        /** An auxiliary rune held badly: the spell goes off in the wrong direction or lands in the wrong place. */
        ASTRAY,
        /** An amplifier ran away with it: stronger than meant, and some of it comes back. */
        SURGE,
        /** The spell turns on its caster. */
        BACKFIRE,
        /** Nothing happens: no rune was read, or Light and Dark cancelled each other. */
        FIZZLE;

        public boolean failed() {
            return this != SUCCESS;
        }

        public String translationKey() {
            return "magic.hardwrought.outcome." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * One drawn rune as it went into the spell.
     *
     * @param role what it does in the spell
     * @param accuracy how well it was drawn
     * @param recognized whether it was clearly read as anything
     * @param millis how long it took to draw
     * @param known whether the caster knew the rune before drawing it
     */
    public record Drawn(Role role, double accuracy, boolean recognized, int millis, boolean known) { }

    /**
     * Judges a cast.
     *
     * @param drawn every stroke, in order, including any that read as no rune
     * @param spell the spell the readable strokes make, {@code null} if none did
     * @param wand what it was cast through
     * @param surroundings how the place helps or hinders this spell's core, 1 when it does neither
     */
    public static CastQuality judge(List<Drawn> drawn, Spell spell, WandTier wand, double surroundings) {
        return judge(drawn, spell, wand, surroundings, 0);
    }

    /**
     * Judges one of several spells drawn together: the wand carries them all at once, so the others'
     * load counts against it too.
     *
     * @param otherLoad the tiers of the other spells cast with this one
     */
    public static CastQuality judge(List<Drawn> drawn, Spell spell, WandTier wand, double surroundings, int otherLoad) {
        if (drawn.isEmpty()) return new CastQuality(0, 0, 0, Outcome.FIZZLE, -1);
        double accuracy = 0;
        int worst = 0;
        double worstAccuracy = Double.MAX_VALUE;
        int unread = 0, unknown = 0;
        boolean fast = true;
        double slowness = 0;
        for (int i = 0; i < drawn.size(); i++) {
            Drawn d = drawn.get(i);
            double a = d.accuracy();
            accuracy += a;
            if (a < worstAccuracy) {
                worstAccuracy = a;
                worst = i;
            }
            if (!d.recognized()) unread++;
            else if (!d.known()) unknown++;
            if (d.millis() > 900) fast = false;
            // Hesitation shows: a rune dragged out over seconds holds together worse.
            if (d.millis() > 2500) slowness += Math.min(0.1, (d.millis() - 2500) / 25000.0);
        }
        accuracy /= drawn.size();
        if (spell == null) return new CastQuality(accuracy, 0, 0, Outcome.FIZZLE, worst);

        int overload = Math.max(0, spell.tier() + otherLoad - wand.tier());
        double stability = accuracy + spell.steadiness() + wand.steadiness() - 0.15 * overload - slowness;
        // Experimenting is dangerous (section 5): runes the caster does not know hold badly, and a
        // stroke that read as nothing at all shakes the whole spell.
        stability *= Math.pow(0.8, unknown) * Math.pow(0.6, unread);
        stability = Math.max(0, Math.min(1, stability));

        double power = (0.4 + 0.63 * accuracy) * surroundings;
        if (fast && accuracy >= 0.85) power += 0.03;
        if (spell.cancelled()) return new CastQuality(accuracy, stability, power, Outcome.FIZZLE, worst);

        Outcome outcome;
        if (stability >= SUCCESS) outcome = Outcome.SUCCESS;
        else if (stability >= MINOR) outcome = Outcome.WEAKENED;
        else if (stability >= MEDIUM) outcome = switch (drawn.get(worst).role()) {
            case CORE -> Outcome.WRONG_ELEMENT;
            case SIGN -> Outcome.ASTRAY;
            case AMPLIFIER -> Outcome.SURGE;
        };
        else outcome = Outcome.BACKFIRE;
        return new CastQuality(accuracy, stability, power, outcome, worst);
    }
}
