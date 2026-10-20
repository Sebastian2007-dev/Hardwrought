package de.ipnats.hardwrought.client.haven;

import de.ipnats.hardwrought.client.electricity.WireRenderer;
import de.ipnats.hardwrought.food.Spoilage;
import de.ipnats.hardwrought.haven.HavenBlocks;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The safe haven on the client: every device takes its wire directly and draws it, and food says how
 * fresh it is.
 */
public final class HavenClient {
    private HavenClient() { }

    public static void initialize() {
        BlockEntityRendererRegistry.register(HavenBlocks.BULB_ENTITY, WireRenderer::new);
        BlockEntityRendererRegistry.register(HavenBlocks.MOTION_LAMP_ENTITY, WireRenderer::new);
        BlockEntityRendererRegistry.register(HavenBlocks.FLOODLIGHT_ENTITY, WireRenderer::new);
        BlockEntityRendererRegistry.register(HavenBlocks.ALARM_BELL_ENTITY, WireRenderer::new);
        BlockEntityRendererRegistry.register(HavenBlocks.CLIMATE_ENTITY, WireRenderer::new);
        BlockEntityRendererRegistry.register(HavenBlocks.FAN_ENTITY, WireRenderer::new);
        BlockEntityRendererRegistry.register(HavenBlocks.COLD_STORE_ENTITY, WireRenderer::new);
        BlockEntityRendererRegistry.register(HavenBlocks.MONITOR_ENTITY, WireRenderer::new);
        BlockEntityRendererRegistry.register(HavenBlocks.CHARGER_ENTITY, WireRenderer::new);
        BlockEntityRendererRegistry.register(HavenBlocks.ASSEMBLER_ENTITY, WireRenderer::new);
        BlockEntityRendererRegistry.register(HavenBlocks.TESLA_TOWER_ENTITY, WireRenderer::new);
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            Long rots = stack.get(Spoilage.ROTS_AT);
            Minecraft minecraft = Minecraft.getInstance();
            if (rots == null || minecraft.level == null) return;
            long now = minecraft.level.getGameTime();
            double fresh = Spoilage.freshness(stack, now);
            if (fresh <= 0.0) {
                lines.add(Component.translatable("tooltip.hardwrought.food.rotten").withStyle(ChatFormatting.DARK_GREEN));
                return;
            }
            String days = String.format(Locale.ROOT, "%.1f", (rots - now) / (double) Spoilage.DAY);
            boolean stale = fresh < 1.0 - Spoilage.STALE_AT;
            lines.add(Component.translatable(stale ? "tooltip.hardwrought.food.stale" : "tooltip.hardwrought.food.fresh", days)
                    .withStyle(stale ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        });
    }
}
