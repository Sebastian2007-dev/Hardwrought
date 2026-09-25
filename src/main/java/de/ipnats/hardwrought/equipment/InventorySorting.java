package de.ipnats.hardwrought.equipment;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sorting a grid with a middle click, in survival. Creative keeps the middle click for copying.
 *
 * <p>The grid is the one under the cursor: the belt or the main grid of the player's own inventory
 * — never both, so what is at hand stays at hand — a chest, or the pack's page. Only the slots that
 * are open take part, so a row the pack does not open stays empty. Small grids — a furnace, a
 * crafting grid — are left alone: sorting three slots only moves fuel into the ore.
 */
public final class InventorySorting {
    /** Fewest slots a container other than the player's own needs before it is worth sorting. */
    private static final int MIN_SLOTS = 9;
    private static final int BELT = 9;
    private static final int MAIN_END = 36;

    private static final Comparator<ItemStack> ORDER = Comparator
            .<ItemStack>comparingInt(stack -> BuiltInRegistries.ITEM.getId(stack.getItem()))
            .thenComparing(stack -> stack.getHoverName().getString())
            .thenComparing(Comparator.<ItemStack>comparingInt(ItemStack::getCount).reversed());

    private InventorySorting() { }

    /** Sorts the grid the given slot belongs to. Returns false where there was nothing to sort. */
    public static boolean sort(ServerPlayer player, int containerId, int slotIndex) {
        if (player.isCreative() || player.isSpectator()) return false;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != containerId || slotIndex < 0 || slotIndex >= menu.slots.size()) return false;
        if (!menu.getCarried().isEmpty()) return false;
        List<Slot> group = group(player, menu, menu.slots.get(slotIndex));
        if (group.size() < 2) return false;

        List<ItemStack> stacks = new ArrayList<>();
        for (Slot slot : group) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) merge(stacks, stack.copy());
        }
        stacks.sort(ORDER);
        for (Slot slot : group) slot.set(ItemStack.EMPTY);

        int next = 0;
        for (ItemStack stack : stacks) {
            while (!stack.isEmpty() && next < group.size()) {
                Slot slot = group.get(next++);
                if (!slot.mayPlace(stack)) continue;
                slot.set(stack.split(Math.min(stack.getCount(), slot.getMaxStackSize(stack))));
            }
            // Only a slot that refuses what it held a moment ago can leave anything over.
            if (!stack.isEmpty() && !player.getInventory().add(stack)) player.spawnAtLocation(player.level(), stack);
        }
        menu.broadcastChanges();
        return true;
    }

    /** The open slots that share a grid with this one, in the menu's order. */
    private static List<Slot> group(ServerPlayer player, AbstractContainerMenu menu, Slot target) {
        Container container = target.container;
        if (container instanceof CraftingContainer || container instanceof ResultContainer) return List.of();
        boolean own = container instanceof Inventory;
        int region = own ? region(target.getContainerSlot()) : 0;
        if (region < 0) return List.of();
        List<Slot> group = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.container != container || !slot.isActive()) continue;
            if (own && region(slot.getContainerSlot()) != region) continue;
            if (!slot.mayPickup(player) || !slot.allowModification(player)) continue;
            group.add(slot);
        }
        if (!own && group.size() < MIN_SLOTS) return List.of();
        return group;
    }

    /** 0 for the belt, 1 for the main grid, -1 for armour and the off hand, which are not sorted. */
    private static int region(int containerSlot) {
        if (containerSlot < BELT) return 0;
        return containerSlot < MAIN_END ? 1 : -1;
    }

    /** Tops up stacks of the same thing before starting a new one. */
    private static void merge(List<ItemStack> stacks, ItemStack stack) {
        for (ItemStack existing : stacks) {
            if (stack.isEmpty()) return;
            if (!ItemStack.isSameItemSameComponents(existing, stack)) continue;
            int room = existing.getMaxStackSize() - existing.getCount();
            if (room <= 0) continue;
            int moved = Math.min(room, stack.getCount());
            existing.grow(moved);
            stack.shrink(moved);
        }
        if (!stack.isEmpty()) stacks.add(stack);
    }
}
