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

    /**
     * What going over the allowance costs. The load these are multiplied by is the fraction past
     * the limit, so a player at twice their capacity has a load of 1.0 and pays the whole of it.
     *
     * <p>These used to be gentle, and for a good reason at the time: the old 45 kg allowance put a
     * fresh player over the limit with one stack of cobblestone, so a hard penalty punished nobody
     * but the innocent. The allowance was fixed; the gentleness outlived its cause and left
     * overloading as something a player could simply choose to ignore. Being over the limit is a
     * decision now — leave it behind or carry it home — and it is priced like one. It is still not
     * a wall: at the cap a player walks at half speed and clears a block from a standing jump,
     * which is slow and miserable rather than stuck.
     */
    public static final double OVERLOAD_SPEED_PER_LOAD = 0.45;
    public static final double OVERLOAD_SPEED_CAP = 0.50;
    public static final double OVERLOAD_JUMP_PER_LOAD = 0.40;
    public static final double OVERLOAD_JUMP_CAP = 0.45;
    /** Stamina drained per tick per point of load while on the move. */
    public static final double OVERLOAD_STAMINA_PER_LOAD = 0.007;
    /** Extra kcal per second per point of load: carrying it home is work, and work is eaten. */
    public static final double OVERLOAD_ENERGY_PER_LOAD = 2.0;
    /** How much slower the reserve comes back under load: at the limit it refills half as fast. */
    public static final double OVERLOAD_RECOVERY_PER_LOAD = 2.0;

    private CarryWeight() { }

    /** How far past the allowance a player is, as a fraction of it; zero while they are inside it. */
    public static double load(double carriedKg, double capacityKg) {
        if (capacityKg <= 0) return 0;
        return Math.max(0, carriedKg / capacityKg - 1.0);
    }

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
        if (stack.getItem() instanceof BlockItem placed) return solid(placed) ? 0.35 : 0.05;
        if (stack.has(DataComponents.FOOD)) return 0.25;
        return 0.10;
    }

    /**
     * Whether what this item sets down is a block in the sense the weight means: something with a
     * body. Seeds, a sapling, a torch and a length of rail are placed as blocks too, but nobody
     * carries a block of them — a stack of wheat seeds weighed as much as a stack of stone.
     */
    private static boolean solid(BlockItem item) {
        return !item.getBlock().defaultBlockState()
                .getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)
                .isEmpty();
    }

    public static double perItem(ItemStack stack) { return perItem(stack, Map.of()); }
}
