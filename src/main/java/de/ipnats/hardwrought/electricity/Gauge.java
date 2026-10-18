package de.ipnats.hardwrought.electricity;

import com.mojang.serialization.Codec;
import net.minecraft.world.item.Item;

/**
 * How thick a strung conductor is. A thin wire is cheap and reaches less far; it also has four times
 * the resistance of a cable and carries a quarter of the current before it starts to cook.
 */
public enum Gauge {
    WIRE("wire", 0.04, 4.0, 16.0),
    CABLE("cable", 0.01, 16.0, 24.0),
    /** Thin wire in rubber: it does not bite and may be laid through a wall, but spans less. */
    INSULATED("insulated", 0.04, 4.0, 12.0);

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
        return this == INSULATED;
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
