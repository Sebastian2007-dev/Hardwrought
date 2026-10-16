package de.ipnats.hardwrought.client.fun;

import de.ipnats.hardwrought.wither.SoulSkulls;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

/** Says on a wither skeleton skull whether it is charged, how far, and what charges it. */
public final class SoulSkullTooltip {
    private SoulSkullTooltip() { }

    public static void initialize() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!stack.is(Items.WITHER_SKELETON_SKULL)) return;
            if (SoulSkulls.charged(stack)) {
                lines.add(Component.translatable("tooltip.hardwrought.soul_skull.charged").withStyle(ChatFormatting.AQUA));
            } else if (stack.has(SoulSkulls.CHARGE)) {
                lines.add(Component.translatable("tooltip.hardwrought.soul_skull.charging",
                        stack.get(SoulSkulls.CHARGE) * 100 / SoulSkulls.CHARGE_TICKS).withStyle(ChatFormatting.DARK_AQUA));
            } else {
                lines.add(Component.translatable("tooltip.hardwrought.soul_skull.empty").withStyle(ChatFormatting.GRAY));
            }
        });
    }
}
