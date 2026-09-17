package de.ipnats.hardwrought.survival;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.Map;

public final class CarryWeight {
    public static final double BASE_CAPACITY_KG = 45.0;

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
        if (stack.getItem() instanceof BlockItem) return 0.75;
        if (stack.has(DataComponents.FOOD)) return 0.35;
        return 0.20;
    }

    public static double perItem(ItemStack stack) { return perItem(stack, Map.of()); }
}
