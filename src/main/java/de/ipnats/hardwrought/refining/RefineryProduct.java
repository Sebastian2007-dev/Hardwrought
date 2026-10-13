package de.ipnats.hardwrought.refining;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * The liquids that come out of refining crude oil, heaviest first. Each is a real liquid with a bucket,
 * that creeps the thicker it is and burns. Their fluids, blocks and buckets are filled in by
 * {@link RefineryFluids} as they are registered.
 *
 * @param color its colour, which its look is tinted with
 * @param tickDelay ticks between two steps of its spread: bitumen barely moves, gasoline runs like water
 * @param drag how much of its speed a body wading through it keeps, across
 * @param burnTicks how long a bucket of it burns in a furnace; a lava bucket is 20000
 */
public enum RefineryProduct implements StringRepresentable {
    /** What is left at the bottom of the column: road tar, roofing, waterproofing. */
    BITUMEN("bitumen", 0x2B2118, 40, 0.4, 6000),
    /** Fuel oil for ships and furnaces, and what the cracker breaks into lighter fuels. */
    HEAVY_OIL("heavy_oil", 0x4A2E18, 30, 0.5, 24000),
    DIESEL("diesel", 0xC8961E, 12, 0.75, 20000),
    /** Lamp oil and lamp-like engines. */
    KEROSENE("kerosene", 0xE6D98C, 8, 0.8, 16000),
    /** The light cut, which the reformer makes into gasoline. */
    NAPHTHA("naphtha", 0xEDE7CF, 6, 0.85, 12000),
    GASOLINE("gasoline", 0xE8823A, 5, 0.85, 18000),
    /** The still's light cut: what boils off first over an open fire, a rough naphtha. */
    LIGHT_FRACTION("light_fraction", 0xD8C890, 6, 0.85, 8000),
    /** The still's middle cut, a rough mix of kerosene and diesel. */
    FUEL_FRACTION("fuel_fraction", 0xB07A2A, 10, 0.78, 14000);

    private final String name;
    private final int color;
    private final int tickDelay;
    private final double drag;
    private final int burnTicks;

    FlowingFluid source;
    FlowingFluid flowing;
    LiquidBlock block;
    Item bucket;

    RefineryProduct(String name, int color, int tickDelay, double drag, int burnTicks) {
        this.burnTicks = burnTicks;
        this.name = name;
        this.color = color;
        this.tickDelay = tickDelay;
        this.drag = drag;
    }

    public int color() {
        return color;
    }

    public int tickDelay() {
        return tickDelay;
    }

    public int burnTicks() {
        return burnTicks;
    }

    public double drag() {
        return drag;
    }

    public FlowingFluid source() {
        return source;
    }

    public FlowingFluid flowing() {
        return flowing;
    }

    public LiquidBlock block() {
        return block;
    }

    public Item bucket() {
        return bucket;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
