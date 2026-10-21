package de.ipnats.hardwrought.electricity;

import com.mojang.serialization.Codec;
import net.minecraft.world.item.Item;

/**
 * How thick a strung conductor is. A thin wire is cheap and reaches less far; it also has four times
 * the resistance of a cable and carries a quarter of the current before it starts to cook. A power
 * station wants more: the heavy cable carries eighty amperes, the overhead conductor spans far, the
 * high-power cable carries three hundred.
 */
public enum Gauge {
    WIRE("wire", 0.04, 4.0, 16.0),
    CABLE("cable", 0.01, 16.0, 24.0),
    /** Thin wire in rubber: it does not bite and may be laid through a wall, but spans less. */
    INSULATED("insulated", 0.04, 4.0, 12.0),
    /**
     * Four cables' worth of copper in one rubber sheath: what a power station is wired with inside, from
     * the generator to the transformer. Insulated, heavy, and short in span.
     */
    HEAVY_CABLE("heavy_cable", 0.002, 80.0, 16.0),
    /**
     * Aluminium strands round a steel core, bare, as overhead lines are strung: light, so it spans far
     * between masts, and made for high voltage, where the current is small.
     */
    OVERHEAD("overhead", 0.006, 40.0, 48.0),
    /**
     * The power station's own feeder: thick copper in oil-filled paper under a lead sheath, as the
     * cables between a station and its substation are laid. Three hundred amperes, insulated, heavy.
     */
    POWER_CABLE("power_cable", 0.0008, 300.0, 24.0);

    public static final Codec<Gauge> CODEC = Codec.STRING.xmap(Gauge::byName, Gauge::serializedName);
    /** One coil in the inventory strings this many blocks. */
    public static final double BLOCKS_PER_COIL = 8.0;

    private final String name;
    private final double ohmsPerBlock;
    private final double amps;
    private final double span;

    Gauge(String name, double ohmsPerBlock, double amps, double span) {
        this.name = name;
        this.ohmsPerBlock = ohmsPerBlock;
        this.amps = amps;
        this.span = span;
    }

    public String serializedName() {
        return name;
    }

    /** Resistance of one block of it, in ohms. */
    public double ohmsPerBlock() {
        return ohmsPerBlock;
    }

    /** The current it carries for good. Above this it heats, and in the end burns through. */
    public double amps() {
        return amps;
    }

    /** Whether it is sheathed: it bites nobody and needs no clear air between its ends. */
    public boolean insulated() {
        return this == INSULATED || this == HEAVY_CABLE || this == POWER_CABLE;
    }

    /** How far it can be strung between two supports, in blocks. */
    public double span() {
        return span;
    }

    public Item item() {
        return switch (this) {
            case WIRE -> ElectricBlocks.COPPER_WIRE;
            case CABLE -> ElectricBlocks.COPPER_CABLE;
            case INSULATED -> ElectricBlocks.INSULATED_WIRE;
            case HEAVY_CABLE -> ElectricBlocks.HEAVY_POWER_CABLE;
            case OVERHEAD -> ElectricBlocks.OVERHEAD_CONDUCTOR;
            case POWER_CABLE -> ElectricBlocks.HIGH_POWER_CABLE;
        };
    }

    /** How many coils a run of this length takes. */
    public int coils(double length) {
        return Math.max(1, (int) Math.ceil(length / BLOCKS_PER_COIL));
    }

    private static Gauge byName(String name) {
        for (Gauge gauge : values()) if (gauge.name.equals(name)) return gauge;
        return WIRE;
    }
}
