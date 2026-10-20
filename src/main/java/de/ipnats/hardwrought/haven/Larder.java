package de.ipnats.hardwrought.haven;

import de.ipnats.hardwrought.food.Spoilage;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The inside of a cold store — an ice box, a refrigerator, a freezer: three rows of food, opened as a
 * chest is, which go off at a share of their speed for as long as the store is cold (see
 * {@link Spoilage#keep}).
 */
public final class Larder {
    public static final int SLOTS = 27;

    private final SimpleContainer items;
    private long kept = Long.MIN_VALUE;

    public Larder(BlockEntity owner) {
        this.items = new SimpleContainer(SLOTS) {
            @Override
            public boolean stillValid(Player player) {
                return Container.stillValidBlockEntity(owner, player);
            }

            @Override
            public void setChanged() {
                super.setChanged();
                owner.setChanged();
            }
        };
    }

    public SimpleContainer items() {
        return items;
    }

    public AbstractContainerMenu menu(int id, Inventory inventory) {
        return ChestMenu.threeRows(id, inventory, items);
    }

    /** Whether anything in it goes off. */
    public boolean holdsFood() {
        for (ItemStack stack : items.getItems()) if (Spoilage.perishable(stack)) return true;
        return false;
    }

    /**
     * Since it was last looked at, the food in it went off at this share of its speed: its marks move
     * back by the rest; what is new is marked, and what has rotted after all is turned.
     */
    public void keep(long now, double speed) {
        long ticks = kept == Long.MIN_VALUE ? 0 : now - kept;
        kept = now;
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = items.getItem(slot);
            if (!Spoilage.perishable(stack)) continue;
            Spoilage.mark(stack, now);
            if (speed < 1.0) Spoilage.keep(stack, ticks, speed);
        }
        Spoilage.sweep(items, now);
    }

    public void load(ValueInput input) {
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, list);
        for (int slot = 0; slot < SLOTS; slot++) items.setItem(slot, list.get(slot));
        kept = input.getLongOr("kept", Long.MIN_VALUE);
    }

    public void save(ValueOutput output) {
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int slot = 0; slot < SLOTS; slot++) list.set(slot, items.getItem(slot));
        ContainerHelper.saveAllItems(output, list);
        output.putLong("kept", kept);
    }
}
