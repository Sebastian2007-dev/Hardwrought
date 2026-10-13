package de.ipnats.hardwrought.magic;

/**
 * The shape a spell acts in, which is where a combination of runes can take over (see {@link Combos}).
 * It follows from the auxiliary runes: a trap's square, a field's spiral, a burst's triangle come
 * first, whatever else is drawn; then where the spell goes.
 */
public enum SpellForm {
    /** No auxiliary rune: poured from the hand at what is in front. */
    STREAM,
    /** Sent along an arrow, acting where it lands. */
    BOLT,
    /** Inside a circle, worked on the caster. */
    SELF,
    /** A triangle: all round where it acts. */
    BURST,
    /** A spiral: lingering where it acts, or around its caster, acting again and again. */
    FIELD,
    /** A square: lying in wait until someone steps on it. */
    TRAP;

    /** The form a drawn spell takes; {@code null} for a shield or a gate, which no combination changes. */
    public static SpellForm of(Spell spell) {
        if (spell.shield() || spell.gate()) return null;
        return of(spell.placement(), spell.burst(), spell.linger(), spell.trap());
    }

    static SpellForm of(Spell.Placement placement, boolean burst, boolean linger, boolean trap) {
        if (trap) return TRAP;
        if (linger) return FIELD;
        if (burst) return BURST;
        return switch (placement) {
            case SELF -> SELF;
            case ARROW -> BOLT;
            case FRONT -> STREAM;
            // A step through nothing arrives as it always does.
            case SHIFT, PORTAL -> null;
        };
    }
}
