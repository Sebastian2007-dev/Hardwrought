package de.ipnats.hardwrought.oil;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.FlowingFluid;

/** The mod's own liquids. They are registered before the blocks and buckets that hold them. */
public final class ModFluids {
    public static final FlowingFluid CRUDE_OIL = register("crude_oil", new CrudeOilFluid.Source());
    public static final FlowingFluid FLOWING_CRUDE_OIL = register("flowing_crude_oil", new CrudeOilFluid.Flowing());

    private ModFluids() { }

    private static <T extends FlowingFluid> T register(String name, T fluid) {
        return Registry.register(BuiltInRegistries.FLUID, Hardwrought.id(name), fluid);
    }

    public static void initialize() {
        // Loading the class registers the fluids.
    }
}
