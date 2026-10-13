package de.ipnats.hardwrought.gasline;

import net.minecraft.util.StringRepresentable;

/**
 * The gases that go through gas lines. A store holds one at a time; lines carry each between the
 * stores that hold it or hold nothing. Measured in units, the same units the air's gases are counted in,
 * so a gas let out of a line is that many units of firedamp in the room.
 */
public enum GasKind implements StringRepresentable {
    NATURAL_GAS("natural_gas"),
    /** Propane and butane off the top of a refinery column, or out of a cracker. */
    LPG("lpg"),
    /** Out of the electrolysis of salt: what silicone is made with. It does not burn, and it is poison. */
    CHLORINE("chlorine");

    private final String name;

    GasKind(String name) {
        this.name = name;
    }

    /** Whether it burns: in a burner, a burning stack, a furnace. Chlorine does not. */
    public boolean burns() {
        return this != CHLORINE;
    }

    public String translationKey() {
        return "gas.hardwrought." + name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
