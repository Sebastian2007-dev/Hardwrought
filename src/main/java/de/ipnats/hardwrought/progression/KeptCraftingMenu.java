package de.ipnats.hardwrought.progression;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A crafting menu whose grid belongs to the bench rather than to the moment.
 *
 * <p>Server side only. The client opens an ordinary crafting menu and never knows the difference,
 * which is what keeps the vanilla screen, the recipe book and the recipe choice working unchanged.
 * Here the grid is filled from the bench on opening, written back to the bench on every change —
 * so a crash with the menu open loses nothing — and left where it is on closing, instead of being
 * handed back to the player the way a vanilla table does.
 */
public class KeptCraftingMenu extends CraftingMenu {
    private final KeptGridBlockEntity bench;
    /** Set while the grid is being filled from the bench, when writing it back would be pointless. */
    private boolean loading;

    public KeptCraftingMenu(int id, Inventory inventory, ContainerLevelAccess access, KeptGridBlockEntity bench) {
        super(id, inventory, access);
        this.bench = bench;
        loading = true;
        List<ItemStack> kept = bench.contents();
        for (int slot = 0; slot < Math.min(kept.size(), craftSlots.getContainerSize()); slot++) {
            craftSlots.setItem(slot, kept.get(slot));
        }
        loading = false;
        // One recalculation for the whole grid, now that all of it is in place.
        slotsChanged(craftSlots);
    }

    public boolean isAt(KeptGridBlockEntity other) {
        return bench == other;
    }

    @Override
    public void slotsChanged(Container container) {
        if (loading) return;
        super.slotsChanged(container);
        // The field is still null while the vanilla constructor sets the grid up.
        if (bench != null && container == craftSlots && !bench.isRemoved()) {
            bench.store(craftSlots.getItems());
        }
    }

    @Override
    public void removed(Player player) {
        if (!bench.isRemoved()) bench.store(craftSlots.getItems());
        // The grid now lives on the bench — or, if the bench was broken, lies on the ground where it
        // dropped. Either way it must not also go back into the player's inventory.
        craftSlots.clearContent();
        bench.release(player);
        super.removed(player);
    }
}
