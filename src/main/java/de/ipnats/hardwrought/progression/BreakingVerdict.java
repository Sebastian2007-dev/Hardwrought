package de.ipnats.hardwrought.progression;

import java.util.Locale;

/**
 * What happens when this player hits this block with what they are holding, per specification
 * section 71.1.
 *
 * <p>Vanilla lets almost anything be broken by hand given enough time, which is why the first
 * minutes of a world are spent punching a tree. Hardwrought separates three questions that vanilla
 * answers together: whether the block breaks at all, how much it costs to break, and whether
 * anything useful comes out of it (section 71.1.10).
 *
 * <pre>
 *   proper tool        normal speed, normal drops, normal effort
 *   improvised tool    slow, exhausting, hard on the tool, and the block may be ruined
 *   bare hands         loose material only — solid material does not yield to fists at all
 * </pre>
 *
 * @param speedFactor     what the breaking speed is multiplied by; zero is no progress at all
 * @param staminaFactor   what the effort of the block is multiplied by
 * @param toolDamage      extra durability the attempt costs beyond vanilla's own wear
 * @param yield           what comes out of the block
 */
public enum BreakingVerdict {
    /** The right tool for the material: vanilla behaviour, which is the point of having one. */
    PROPER("proper", 1.0, 1.0, 0, Yield.NORMAL),
    /** Loose material — plants, fibres, snow, berries. Section 71.1.1: hands are enough. */
    LOOSE("loose", 1.0, 0.5, 0, Yield.NORMAL),
    /**
     * Section 71.1.2: soil moved by hand. It works, it is slow, it costs a great deal of stamina,
     * and what comes up is a handful of loose material rather than a clean block.
     */
    DIGGABLE("diggable", 0.30, 4.0, 0, Yield.HANDFUL),
    /**
     * A tool, but the wrong one — an axe against stone, a pick against a beam. Slow, exhausting,
     * hard on the tool, and the material often does not survive being prised out.
     */
    IMPROVISED("improvised", 0.25, 3.0, 2, Yield.DAMAGED),
    /** Section 71.1.7: glass gives way to a fist, and there is nothing left to pick up. */
    SHATTERS("shatters", 1.0, 1.0, 0, Yield.NOTHING),
    /** Section 71.1: solid material and bare hands. No progress, however long anyone punches it. */
    IMPOSSIBLE("impossible", 0.0, 0.0, 0, Yield.NOTHING);

    /** What is left of the block after it gives way. */
    public enum Yield {
        /** Whatever the block normally drops. */
        NORMAL,
        /** Loose material instead of the block: section 71.1.2 digs dirt into handfuls of it. */
        HANDFUL,
        /** Normally the drop, but often nothing: the wrong tool ruins what it takes out. */
        DAMAGED,
        /** Nothing usable at all. */
        NOTHING
    }

    /** How often the wrong tool ruins what it breaks. */
    public static final double DAMAGED_LOSS_CHANCE = 0.5;

    private final String serializedName;
    private final double speedFactor;
    private final double staminaFactor;
    private final int toolDamage;
    private final Yield yield;

    BreakingVerdict(String serializedName, double speedFactor, double staminaFactor, int toolDamage,
                    Yield yield) {
        this.serializedName = serializedName;
        this.speedFactor = speedFactor;
        this.staminaFactor = staminaFactor;
        this.toolDamage = toolDamage;
        this.yield = yield;
    }

    public String serializedName() {
        return serializedName;
    }

    public double speedFactor() {
        return speedFactor;
    }

    public double staminaFactor() {
        return staminaFactor;
    }

    public int toolDamage() {
        return toolDamage;
    }

    public Yield yield() {
        return yield;
    }

    /** True where the block gives way at all. */
    public boolean breaks() {
        return speedFactor > 0;
    }

    /** True where Hardwrought has to take the drops over from vanilla to change them. */
    public boolean interceptsDrops() {
        return yield != Yield.NORMAL;
    }

    public static BreakingVerdict byName(String name) {
        if (name == null) return null;
        for (BreakingVerdict verdict : values()) {
            if (verdict.serializedName.equals(name.toLowerCase(Locale.ROOT))) return verdict;
        }
        return null;
    }
}
