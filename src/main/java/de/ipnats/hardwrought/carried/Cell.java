package de.ipnats.hardwrought.carried;

/**
 * The three cells a carried device runs on (docs/electricity-roadmap.md, stage E4), and the trade
 * between them: what they hold, how fast they take a charge, what they weigh — and carry weight counts.
 *
 * <p>A lead-acid pack is first and cheap, and heavy. A nickel–iron cell holds twice as much, all but
 * never wears, and charges slowly. A silver–zinc cell holds four times as much at a quarter of the
 * weight, charges fast, and wears out: after {@link #cycles} full charges it holds nothing.
 */
public enum Cell {
    LEAD_ACID("lead_acid_pack", 36_000.0, 60.0, 2.5, 0),
    NICKEL_IRON("nickel_iron_cell", 72_000.0, 20.0, 2.0, 0),
    SILVER_ZINC("silver_zinc_cell", 144_000.0, 150.0, 0.6, 300);

    private final String id;
    private final double joules;
    private final double chargeWatts;
    private final double kilograms;
    private final int cycles;

    Cell(String id, double joules, double chargeWatts, double kilograms, int cycles) {
        this.id = id;
        this.joules = joules;
        this.chargeWatts = chargeWatts;
        this.kilograms = kilograms;
        this.cycles = cycles;
    }

    public String id() {
        return id;
    }

    /** What it holds new, in watt-seconds. */
    public double joules() {
        return joules;
    }

    /** The most it takes from a charger, in watts. */
    public double chargeWatts() {
        return chargeWatts;
    }

    public double kilograms() {
        return kilograms;
    }

    /** How many full charges it lasts; 0 for one that does not wear. */
    public int cycles() {
        return cycles;
    }

    /** What it holds after this many full charges. */
    public double capacity(double worn) {
        return cycles == 0 ? joules : joules * Math.max(0.0, 1.0 - worn / cycles);
    }
}
