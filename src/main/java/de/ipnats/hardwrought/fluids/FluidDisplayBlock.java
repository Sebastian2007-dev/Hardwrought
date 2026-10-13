package de.ipnats.hardwrought.fluids;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.function.Supplier;

/**
 * Never placed: the look of a liquid as a plain cube, which a tank's renderer stretches to the height
 * the tank is full. One state per liquid a tank can show; a new liquid adds one here and a model.
 */
public class FluidDisplayBlock extends Block {
    public enum Shown implements StringRepresentable {
        WATER("water", () -> Fluids.WATER),
        LAVA("lava", () -> Fluids.LAVA),
        CRUDE_OIL("crude_oil", () -> de.ipnats.hardwrought.oil.ModFluids.CRUDE_OIL),
        BITUMEN("bitumen", () -> de.ipnats.hardwrought.refining.RefineryProduct.BITUMEN.source()),
        HEAVY_OIL("heavy_oil", () -> de.ipnats.hardwrought.refining.RefineryProduct.HEAVY_OIL.source()),
        DIESEL("diesel", () -> de.ipnats.hardwrought.refining.RefineryProduct.DIESEL.source()),
        KEROSENE("kerosene", () -> de.ipnats.hardwrought.refining.RefineryProduct.KEROSENE.source()),
        NAPHTHA("naphtha", () -> de.ipnats.hardwrought.refining.RefineryProduct.NAPHTHA.source()),
        GASOLINE("gasoline", () -> de.ipnats.hardwrought.refining.RefineryProduct.GASOLINE.source()),
        LIGHT_FRACTION("light_fraction", () -> de.ipnats.hardwrought.refining.RefineryProduct.LIGHT_FRACTION.source()),
        FUEL_FRACTION("fuel_fraction", () -> de.ipnats.hardwrought.refining.RefineryProduct.FUEL_FRACTION.source());

        private final String name;
        private final Supplier<Fluid> fluid;

        Shown(String name, Supplier<Fluid> fluid) {
            this.name = name;
            this.fluid = fluid;
        }

        /** The look for a liquid, still or flowing; null for one with no look of its own here. */
        public static Shown of(Fluid fluid) {
            for (Shown shown : values()) {
                if (shown.fluid.get().isSame(fluid)) return shown;
            }
            return null;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Shown> FLUID = EnumProperty.create("fluid", Shown.class);

    public FluidDisplayBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FLUID, Shown.WATER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FLUID);
    }
}
