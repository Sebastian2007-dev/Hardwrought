package de.ipnats.hardwrought.client.smithing;

import de.ipnats.hardwrought.core.networking.ForgingPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Opens the anvil when the server says the piece in hand can be worked there. */
public final class SmithingClient {
    private SmithingClient() { }

    public static void initialize() {
        HeatLook.initialize();
        SmithingTooltip.initialize();
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                de.ipnats.hardwrought.core.registry.ModBlockEntities.FORGE, ForgeRenderer::new);
        net.minecraft.client.gui.screens.MenuScreens.register(de.ipnats.hardwrought.smithing.ForgeMenu.TYPE, ForgeScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(ForgingPayloads.Open.TYPE, (payload, context) ->
                net.minecraft.client.Minecraft.getInstance().gui.setScreen(new ForgingScreen(payload.anvil(), payload.results())));
    }
}
