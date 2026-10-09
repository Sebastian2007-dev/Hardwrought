package de.ipnats.hardwrought.electricity;

import com.mojang.serialization.Codec;
import net.minecraft.world.item.Item;

/**
 * How thick a strung conductor is. A thin wire is cheap and reaches less far; it also has four times
 * the resistance of a cable and carries a quarter of the current before it starts to cook.
 */
public enum Gauge {
    WIRE("wire", 0.04, 4.0, 16.0),
    CABLE("cable", 0.01, 16.0, 24.0);

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

    /** How far it can be strung between two supports, in blocks. */
    public double span() {
        return span;
    }

    public Item item() {
        return this == WIRE ? ElectricBlocks.COPPER_WIRE : ElectricBlocks.COPPER_CABLE;
    }

    /** How many coils a run of this length takes. */
    public int coils(double length) {
        return Math.max(1, (int) Math.ceil(length / BLOCKS_PER_COIL));
    }

    private static Gauge byName(String name) {
        return CABLE.name.equals(name) ? CABLE : WIRE;
    }
}
