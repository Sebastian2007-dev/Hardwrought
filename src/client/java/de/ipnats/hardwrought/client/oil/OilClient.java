package de.ipnats.hardwrought.client.oil;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.oil.ModFluids;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;

/** How crude oil looks: its own still and flowing textures, opaque and untinted, like lava's. */
public final class OilClient {
    private OilClient() { }

    public static void initialize() {
        FluidRenderingRegistry.register(ModFluids.CRUDE_OIL, ModFluids.FLOWING_CRUDE_OIL, new FluidModel.Unbaked(
                new Material(Hardwrought.id("block/crude_oil_still")),
                new Material(Hardwrought.id("block/crude_oil_flow")), null, null));
    }
}
