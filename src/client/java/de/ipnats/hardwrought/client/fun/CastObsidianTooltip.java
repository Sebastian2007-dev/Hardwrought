package de.ipnats.hardwrought.client.fun;

import de.ipnats.hardwrought.nether.CastObsidian;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Says on the item why cast obsidian is no portal frame, and what makes it one. */
public final class CastObsidianTooltip {
    private CastObsidianTooltip() { }

    public static void initialize() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!CastObsidian.tempers(stack)) return;
            lines.add(Component.translatable("tooltip.hardwrought.cast_obsidian.brittle").withStyle(ChatFormatting.GOLD));
            lines.add(Component.translatable("tooltip.hardwrought.cast_obsidian.temper", CastObsidian.TEMPER_CELSIUS)
                    .withStyle(ChatFormatting.GRAY));
        });
    }
}
