package de.ipnats.hardwrought.client.survival;

import de.ipnats.hardwrought.core.networking.MeltingPointPayload;
import de.ipnats.hardwrought.metallurgy.Smelting;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.Map;

/** How hot a metal is and the range in which it can be worked, said briefly on the item itself. */
public final class MeltingPointTooltip {
    private static Map<Identifier, Double> points = Map.of();

    private MeltingPointTooltip() { }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(MeltingPointPayload.TYPE,
                (payload, context) -> points = payload.points());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> points = Map.of());
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.isEmpty()) return;
            Double melting = points.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (melting == null) return;
            lines.add(Component.translatable("tooltip.hardwrought.melting_point",
                    String.format(Locale.ROOT, "%.0f", melting)).withStyle(ChatFormatting.DARK_GRAY));
            // Powder and mixture are melted and cast; everything else metal is heated and forged, and
            // what matters for that is the working heat rather than the melting point.
            if (de.ipnats.hardwrought.metallurgy.Smelting.isCast(stack.getItem())) {
                lines.add(furnaceNeeded(melting).withStyle(ChatFormatting.DARK_GRAY));
            } else {
                double from = melting * de.ipnats.hardwrought.smithing.Smithing.WORKING_MIN;
                double to = melting * de.ipnats.hardwrought.smithing.Smithing.WORKING_MAX;
                if (de.ipnats.hardwrought.smithing.Smithing.isIngot(stack.getItem())) {
                    lines.add(Component.translatable("gui.hardwrought.forging.optimal_max",
                            String.format(Locale.ROOT, "%.0f", to)).withStyle(ChatFormatting.DARK_GRAY));
                } else {
                    lines.add(Component.translatable("gui.hardwrought.forging.workable",
                            String.format(Locale.ROOT, "%.0f", from), String.format(Locale.ROOT, "%.0f", to))
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        });
    }

    /** The melting point the server gave for this item, or null where it has none. */
    public static Double meltingPoint(Identifier item) {
        return points.get(item);
    }

    /** What gets a piece to working heat: a brick furnace, a furnace or a forge with a bellows, or more. */
    public static net.minecraft.network.chat.MutableComponent heatNeeded(double workingMin) {
        String source = workingMin <= Smelting.BRICK_FURNACE_MAX_C ? "brick_furnace"
                : workingMin <= Smelting.FURNACE_MAX_C ? "furnace" : "blast_furnace";
        return Component.translatable("tooltip.hardwrought.heats_in." + source);
    }

    /** Which furnace is the coldest one that still melts something this hot. */
    public static net.minecraft.network.chat.MutableComponent furnaceNeeded(double melting) {
        String furnace = melting <= Smelting.BRICK_FURNACE_MAX_C ? "brick_furnace"
                : melting <= Smelting.FURNACE_MAX_C ? "furnace" : "blast_furnace";
        return Component.translatable("tooltip.hardwrought.melts_in." + furnace);
    }
}
