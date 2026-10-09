package de.ipnats.hardwrought.client.electricity;

import de.ipnats.hardwrought.electricity.ElectricBlocks;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;

/** The client half of electricity: every point of a network draws the wires strung from it, and a machine has a screen. */
public final class ElectricityClient {
    private ElectricityClient() { }

    public static void initialize() {
        net.minecraft.client.gui.screens.MenuScreens.register(de.ipnats.hardwrought.electricity.MachineMenu.TYPE, MachineScreen::new);
        BlockEntityRendererRegistry.register(ElectricBlocks.INSULATOR_ENTITY, WireRenderer::new);
        BlockEntityRendererRegistry.register(ElectricBlocks.LAMP_ENTITY, WireRenderer::new);
        BlockEntityRendererRegistry.register(ElectricBlocks.DYNAMO_ENTITY, WireRenderer::new);
        BlockEntityRendererRegistry.register(ElectricBlocks.MAST_ENTITY, WireRenderer::new);
        BlockEntityRendererRegistry.register(ElectricBlocks.COIL_ENTITY, WireRenderer::new);
        BlockEntityRendererRegistry.register(ElectricBlocks.BATTERY_ENTITY, WireRenderer::new);
        BlockEntityRendererRegistry.register(ElectricBlocks.MACHINE_ENTITY, WireRenderer::new);
    }
}
