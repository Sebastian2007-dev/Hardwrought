package de.ipnats.hardwrought.client.chemistry;

import de.ipnats.hardwrought.chemistryddd.StillMenu;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** The client half of Milestone 11: the still's screen, and the purity said on what has one. */
public final class ChemistryClient {
    private ChemistryClient() { }

    public static void initialize() {
        MenuScreens.register(StillMenu.TYPE, StillScreen::new);
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            Float purity = stack.get(ModDataComponents.PURITY);
            if (purity == null) return;
            lines.add(Component.translatable("tooltip.hardwrought.purity",
                    String.format(Locale.ROOT, "%.1f", purity * 100)).withStyle(ChatFormatting.GRAY));
        });
    }
}
