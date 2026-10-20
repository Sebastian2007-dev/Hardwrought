package de.ipnats.hardwrought.magic;

/**
 * What a wand can carry (magic specification sections 17 to 20). The material decides it: wood
 * carries little, iron more, gold a little more again and, above all, holds the runes steadier.
 *
 * <p>Mithril, light and magically conductive, carries more again and steadier (docs/magic-roadmap.md,
 * stage M2). Void crystal carries the most, and holds what is too much for it: overload costs it half
 * the stability it costs any other wand.
 *
 * @param tier the most complex spell that passes through it safely; see {@link Spell#tier()}
 * @param steadiness what it adds to every spell's stability
 * @param durability how much wear it takes before it breaks
 */
public enum WandTier {
    WOOD(2, 0.0, 64),
    IRON(4, 0.04, 250),
    GOLD(5, 0.12, 120),
    MITHRIL(6, 0.18, 500),
    VOID(8, 0.25, 300);

    private final int tier;
    private final double steadiness;
    private final int durability;

    WandTier(int tier, double steadiness, int durability) {
        this.tier = tier;
        this.steadiness = steadiness;
        this.durability = durability;
    }

    public int tier() {
        return tier;
    }

    public double steadiness() {
        return steadiness;
    }

    public int durability() {
        return durability;
    }

    /** What every tier of overload costs a spell's stability through this wand. */
    public double overloadCost() {
        return this == VOID ? 0.075 : 0.15;
    }

    /** How much one cast wears the wand: a little always, much more for every tier it is overloaded. */
    public static int wear(int overload) {
        return 1 + 6 * Math.max(0, overload);
    }

    /** Overloaded this far or further, a wand breaks whatever wear it has left (section 19). */
    public static final int SHATTER = 4;
}
