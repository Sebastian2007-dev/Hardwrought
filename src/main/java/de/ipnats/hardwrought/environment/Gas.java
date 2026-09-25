package de.ipnats.hardwrought.environment;

import net.minecraft.core.Direction;

/**
 * The gases that exist as blocks in the world (see {@link GasBlock}).
 *
 * <p>A gas block has eight places, and every unit of any gas takes one of them. What a unit means
 * differs per gas, so that the eight steps land on the thresholds that matter for that gas: one unit
 * of carbon dioxide is a percent of the air, so eight are lethal; one unit of methane is two percent,
 * so three to seven are inside its flammability window; carbon monoxide is poisonous in parts per
 * million, so one unit is two hundred of them.
 *
 * <p>Heavier than air sinks, lighter rises. Carbon dioxide (44 g/mol) pools in the low places, methane
 * (16 g/mol) collects against the roof, and carbon monoxide (28 g/mol, a shade lighter than air and
 * warm from the fire that made it) drifts slowly upward.
 *
 * <p>Carbon dioxide does not stay down for ever. Left lying, it breaks down over about a day into a
 * lighter form that rises (see {@link GasBlock}); breathed, that is still carbon dioxide. Everything
 * light that reaches open sky gathers under the clouds, and the rain brings it down again.
 */
public enum Gas {
    CARBON_DIOXIDE("carbon_dioxide", 44, 0.01),
    /** Carbon dioxide that has broken down far enough to rise. Breathed, it is carbon dioxide still. */
    DECAYED_CARBON_DIOXIDE("decayed_carbon_dioxide", 20, 0.01),
    CARBON_MONOXIDE("carbon_monoxide", 28, 0.0002),
    METHANE("methane", 16, 0.02);

    /** Every unit of any gas takes one of these. */
    public static final int CAPACITY = 8;
    /** Molar mass of air: what is heavier sinks, what is lighter rises. */
    private static final int AIR = 29;

    private final String id;
    private final int molarMass;
    private final double fractionPerUnit;

    Gas(String id, int molarMass, double fractionPerUnit) {
        this.id = id;
        this.molarMass = molarMass;
        this.fractionPerUnit = fractionPerUnit;
    }

    public String id() {
        return id;
    }

    /** Share of the air one unit of this gas stands for. */
    public double fractionPerUnit() {
        return fractionPerUnit;
    }

    /** Where this gas goes when nothing holds it: down if it is heavier than air, up if lighter. */
    public Direction drift() {
        return molarMass > AIR ? Direction.DOWN : Direction.UP;
    }

    /** Whether this gas is heavier than the other, and so settles beneath it. */
    public boolean heavierThan(Gas other) {
        return molarMass > other.molarMass;
    }

    /** Carbon monoxide is barely lighter than air and only drifts; the others move on every step. */
    public boolean sluggish() {
        return this == CARBON_MONOXIDE;
    }

    public static Gas byId(String id) {
        for (Gas gas : values()) if (gas.id.equals(id)) return gas;
        return null;
    }
}
