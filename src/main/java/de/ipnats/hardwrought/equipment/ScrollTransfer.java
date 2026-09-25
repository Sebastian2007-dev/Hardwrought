package de.ipnats.hardwrought.equipment;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Moving items one at a time with the scroll wheel, the way Mouse Tweaks does it.
 *
 * <p>Scrolling down over a slot sends one item from it to the other side; scrolling up pulls one more
 * of the same kind in from the other side. The other side of a chest, a barrel or the pack's page is
 * the player's own grid, the main rows before the belt; the other side of the player's own grid is
 * whatever is open with it, or, with nothing open, the belt for the main rows and the main rows for
 * the belt. Every slot keeps its own rules — a closed row, a fuel place, a result slot — because every
 * move asks the slot first.
 */
public final class ScrollTransfer {
    private static final int BELT = 9;
    private static final int MAIN_END = 36;

    private ScrollTransfer() { }

    /** One notch over this slot. Returns whether an item moved. */
    public static boolean scroll(ServerPlayer player, int containerId, int slotIndex, boolean push) {
        if (player.isSpectator()) return false;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != containerId || slotIndex < 0 || slotIndex >= menu.slots.size()) return false;
        if (!menu.getCarried().isEmpty()) return false;
        Slot source = menu.slots.get(slotIndex);
        if (!source.isActive() || !source.hasItem() || !source.allowModification(player)) return false;
        if (source.container instanceof ResultContainer) return false;
        List<Slot> others = otherSide(player, menu, source);
        boolean moved = push ? pushOne(player, source, others) : pullOne(player, source, others);
        if (moved) menu.broadcastChanges();
        return moved;
    }

    private static boolean pushOne(ServerPlayer player, Slot source, List<Slot> targets) {
        if (!source.mayPickup(player)) return false;
        ItemStack one = source.getItem().copyWithCount(1);
        Slot into = null;
        for (Slot target : targets) {
            ItemStack there = target.getItem();
            if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, one)
                    && there.getCount() < target.getMaxStackSize(there) && target.mayPlace(one)) {
                into = target;
                break;
            }
        }
        if (into == null) {
            for (Slot target : targets) {
                if (!target.hasItem() && target.mayPlace(one)) {
                    into = target;
                    break;
                }
            }
        }
        if (into == null) return false;
        ItemStack taken = source.remove(1);
        if (taken.isEmpty()) return false;
        if (into.hasItem()) into.getItem().grow(1);
        else into.set(taken);
        into.setChanged();
        source.setChanged();
        return true;
    }

    private static boolean pullOne(ServerPlayer player, Slot into, List<Slot> sources) {
        ItemStack here = into.getItem();
        if (here.getCount() >= into.getMaxStackSize(here)) return false;
        for (Slot source : sources) {
            ItemStack there = source.getItem();
            if (there.isEmpty() || !ItemStack.isSameItemSameComponents(there, here) || !source.mayPickup(player)) continue;
            if (!into.mayPlace(there)) return false;
            ItemStack taken = source.remove(1);
            if (taken.isEmpty()) continue;
            here.grow(1);
            into.setChanged();
            source.setChanged();
            return true;
        }
        return false;
    }

    /** The slots on the other side of this one, in the order they are filled. */
    private static List<Slot> otherSide(ServerPlayer player, AbstractContainerMenu menu, Slot source) {
        List<Slot> external = new ArrayList<>();
        List<Slot> main = new ArrayList<>();
        List<Slot> belt = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot == source || !slot.isActive() || !slot.allowModification(player)) continue;
            Container container = slot.container;
            if (container instanceof Inventory) {
                int index = slot.getContainerSlot();
                if (index < BELT) belt.add(slot);
                else if (index < MAIN_END) main.add(slot);
            } else if (!(container instanceof ResultContainer) && !(container instanceof CraftingContainer)) {
                external.add(slot);
            }
        }
        if (!(source.container instanceof Inventory)) {
            List<Slot> own = new ArrayList<>(main);
            own.addAll(belt);
            return own;
        }
        if (!external.isEmpty()) return external;
        return source.getContainerSlot() < BELT ? main : belt;
    }
}
