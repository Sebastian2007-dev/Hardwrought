package de.ipnats.hardwrought.client.smithing;

import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.smithing.ForgeQuality;
import de.ipnats.hardwrought.smithing.ForgingState;
import de.ipnats.hardwrought.smithing.Heat;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * What a piece of metal says about itself: how hot it is, how far along the anvil it has got, and —
 * once it is a finished part or tool — how well it was made and what that does to it.
 */
public final class SmithingTooltip {
    private SmithingTooltip() { }

    public static void initialize() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.isEmpty()) return;
            Minecraft client = Minecraft.getInstance();
            long now = client.level == null ? 0 : client.level.getGameTime();

            if (stack.has(ModDataComponents.HEAT)) {
                double celsius = Heat.of(stack, now);
                if (celsius > Heat.COLD_BELOW) {
                    lines.add(Component.translatable("tooltip.hardwrought.heat",
                            String.format(Locale.ROOT, "%.0f", celsius)).withStyle(celsius > 500
                            ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
                }
            }
            int reach = de.ipnats.hardwrought.smithing.Hammers.reach(stack);
            if (reach > 0) {
                lines.add(Component.translatable("tooltip.hardwrought.hammer_reach", reach)
                        .withStyle(ChatFormatting.GRAY));
            }
            ForgingState state = stack.get(ModDataComponents.FORGING_STATE);
            if (state != null) {
                var result = BuiltInRegistries.ITEM.getValue(state.result());
                lines.add(Component.translatable("tooltip.hardwrought.forging_into",
                        new net.minecraft.world.item.ItemStack(result).getHoverName(),
                        Math.round(state.progress() * 100)).withStyle(ChatFormatting.GRAY));
            }
            ForgeQuality quality = ForgeQuality.of(stack);
            if (quality != null) {
                lines.add(Component.translatable("tooltip.hardwrought.craftsmanship",
                        Math.round(quality.craftsmanship() * 100)).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("tooltip.hardwrought.treatment."
                        + quality.treatment().getSerializedName()).withStyle(ChatFormatting.DARK_GRAY));
                lines.add(Component.translatable("tooltip.hardwrought.forged_stats",
                        percent(quality.speedFactor()), percent(quality.durabilityFactor()),
                        percent(quality.damageFactor())).withStyle(ChatFormatting.DARK_GRAY));
            }
        });
    }

    private static String percent(double factor) {
        return String.valueOf(Math.round(factor * 100));
    }
}
