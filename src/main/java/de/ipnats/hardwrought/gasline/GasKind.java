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
    CHLORINE("chlorine"),
    /** Out of a reactor's boiling core, into a turbine. Water again once it has done its work. */
    STEAM("steam"),
    /**
     * Uranium hexafluoride: uranium as a gas, the only form in which its isotopes can be spun apart.
     * Natural, as the chemical reactor makes it out of yellowcake.
     */
    URANIUM_HEXAFLUORIDE("uranium_hexafluoride"),
    /** The same, spun out of a centrifuge cascade with three to five parts in a hundred of uranium-235. */
    ENRICHED_HEXAFLUORIDE("enriched_hexafluoride");

    private final String name;

    GasKind(String name) {
        this.name = name;
    }

    /** Whether it burns: in a burner, a burning stack, a furnace. Only the fuel gases do. */
    public boolean burns() {
        return this == NATURAL_GAS || this == LPG;
    }

    public String translationKey() {
        return "gas.hardwrought." + name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
