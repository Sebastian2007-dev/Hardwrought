package de.ipnats.hardwrought.client.smeltery;

import de.ipnats.hardwrought.smeltery.SmelteryBlocks;
import de.ipnats.hardwrought.smeltery.SmelteryMenu;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

/** The smeltery's screen and its renderers. */
public final class SmelteryClient {
    private SmelteryClient() { }

    public static void initialize() {
        MenuScreens.register(SmelteryMenu.TYPE, SmelteryScreen::new);
        BlockEntityRendererRegistry.register(SmelteryBlocks.CONTROLLER_ENTITY, SmelteryRenderers.Controller::new);
        BlockEntityRendererRegistry.register(SmelteryBlocks.FAUCET_ENTITY, SmelteryRenderers.Faucet::new);
        BlockEntityRendererRegistry.register(SmelteryBlocks.CASTING_TABLE_ENTITY, SmelteryRenderers.Table::new);
    }
}
