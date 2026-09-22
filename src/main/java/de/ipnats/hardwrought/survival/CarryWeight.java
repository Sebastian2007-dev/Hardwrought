package de.ipnats.hardwrought.survival;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * What a player is carrying, and how much of it they can carry before it tells.
 *
 * <p>The numbers here are game numbers, not physics. A Minecraft block is a cubic metre of stone and
 * would weigh a couple of tonnes; what matters is that a working trip — a few stacks, tools, food —
 * costs nothing, and that hauling a full inventory of rubble is felt.
 */
public final class CarryWeight {
    /**
     * What a player carries without noticing. Sized against a working trip — three stacks of blocks,
     * four tools and a stack of bread, 78 kg of it — so that the ordinary load costs nothing at all.
     * Below this there is no penalty of any kind.
     */
    public static final double BASE_CAPACITY_KG = 85.0;

    private CarryWeight() { }

    public static double calculate(ServerPlayer player, Map<Identifier, Double> definitions) {
        double total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) total += perItem(stack, definitions) * stack.getCount();
        }
        return total;
    }

    public static double perItem(ItemStack stack, Map<Identifier, Double> definitions) {
        Double configured = definitions.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (configured != null) return configured;
        if (stack.has(DataComponents.EQUIPPABLE)) return 2.5;
        if (stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON)) return 1.8;
        // A stack of blocks is 22 kg rather than 48: one stack used to be over the old limit by
        // itself, which made every player permanently overloaded without ever being told why.
        if (stack.getItem() instanceof BlockItem) return 0.35;
        if (stack.has(DataComponents.FOOD)) return 0.25;
        return 0.10;
    }

    public static double perItem(ItemStack stack) { return perItem(stack, Map.of()); }
}
