package de.ipnats.hardwrought.client.survival;

import de.ipnats.hardwrought.core.networking.ItemWeightPayload;
import de.ipnats.hardwrought.survival.CarryWeight;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;

/**
 * What a thing weighs, said on the item itself.
 *
 * <p>Carry weight is only a fair rule if a player can see it coming. The tooltip gives the weight of
 * one, and of the stack when there is more than one, so the decision about what to leave behind can
 * be made in the chest rather than discovered on the walk home.
 */
public final class WeightTooltip {
    private static Map<Identifier, Double> weights = Map.of();

    private WeightTooltip() { }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(ItemWeightPayload.TYPE,
                (payload, context) -> weights = payload.weights());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> weights = Map.of());
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.isEmpty()) return;
            double each = CarryWeight.perItem(stack, weights);
            if (each <= 0) return;
            Component line = stack.getCount() > 1
                    ? Component.translatable("tooltip.hardwrought.weight_stack",
                            format(each * stack.getCount()), format(each))
                    : Component.translatable("tooltip.hardwrought.weight", format(each));
            lines.add(line.copy().withStyle(ChatFormatting.DARK_GRAY));
        });
    }

    /** The table the server sent, empty until it arrives or after disconnecting. */
    public static Map<Identifier, Double> weights() {
        return weights;
    }

    private static String format(double kilograms) {
        return String.format(Locale.ROOT, "%.2f", kilograms);
    }
}
