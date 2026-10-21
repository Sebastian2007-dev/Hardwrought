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
        net.minecraft.client.gui.screens.MenuScreens.register(de.ipnats.hardwrought.refining.RefineryMenu.TYPE,
                de.ipnats.hardwrought.client.refining.RefineryScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(de.ipnats.hardwrought.materials.ChemMenu.TYPE,
                de.ipnats.hardwrought.client.materials.ChemScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(de.ipnats.hardwrought.nuclear.ReactorMenu.TYPE,
                de.ipnats.hardwrought.client.nuclear.ReactorScreen::new);
        FluidRenderingRegistry.register(ModFluids.CRUDE_OIL, ModFluids.FLOWING_CRUDE_OIL, new FluidModel.Unbaked(
                new Material(Hardwrought.id("block/crude_oil_still")),
                new Material(Hardwrought.id("block/crude_oil_flow")), null, null));
        // The liquids of refining, each in its own colour.
        for (de.ipnats.hardwrought.refining.RefineryProduct product : de.ipnats.hardwrought.refining.RefineryProduct.values()) {
            String name = product.getSerializedName();
            FluidRenderingRegistry.register(product.source(), product.flowing(), new FluidModel.Unbaked(
                    new Material(Hardwrought.id("block/fluid/" + name + "_still")),
                    new Material(Hardwrought.id("block/fluid/" + name + "_flow")), null, null));
        }
    }
}
