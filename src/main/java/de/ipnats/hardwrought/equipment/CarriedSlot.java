package de.ipnats.hardwrought.equipment;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A slot that is only there some of the time.
 *
 * <p>Two things in this mod need that. A main-grid slot is closed while the player has no pack, and
 * every slot of the pack's own page is closed while the player is looking at their own grid instead.
 * Both are the same idea — the slot exists, the menu keeps its shape and its indices, and the slot
 * simply is not open — which is what lets the two grids share one set of coordinates without either
 * side having to rebuild the menu when a pack changes.
 *
 * <p>{@code isActive} alone would not be enough: it stops the slot being drawn and clicked, but
 * vanilla's own shift-click walks the slot list asking only {@code mayPlace}. A closed slot has to
 * refuse both, or a hopper and a shift-click would quietly fill an inventory the player cannot see.
 *
 * <p>One thing gets through a closed square of the player's own grid: a pack. A player who has just
 * made their first one has nowhere else to put it, so a pack may be laid in a closed square that is
 * empty, and taken out of one, while the own grid is the one shown ({@link #packDock}). The square
 * still shows as closed ({@link #shut}).
 */
public class CarriedSlot extends Slot {
    /** Whether this slot is open at this moment. Asked every frame; must stay cheap. */
    @FunctionalInterface
    public interface Gate {
        boolean isOpen();
    }

    private final Gate gate;
    /** Whether a pack may pass through this square while it is closed; null where it never may. */
    private final Gate packDock;

    public CarriedSlot(Container container, int index, int x, int y, Gate gate) {
        this(container, index, x, y, gate, null);
    }

    public CarriedSlot(Container container, int index, int x, int y, Gate gate, Gate packDock) {
        super(container, index, x, y);
        this.gate = gate;
        this.packDock = packDock;
    }

    /** Whether the square is closed, whatever may still pass through it: drawn shaded. */
    public boolean shut() {
        return !gate.isOpen();
    }

    private boolean docking() {
        return !gate.isOpen() && packDock != null && packDock.isOpen();
    }

    @Override
    public boolean isActive() {
        return gate.isOpen() || docking();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (gate.isOpen()) return super.mayPlace(stack);
        return docking() && stack.getItem() instanceof BackpackItem && !hasItem() && super.mayPlace(stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        if (gate.isOpen()) return super.mayPickup(player);
        return docking() && getItem().getItem() instanceof BackpackItem && super.mayPickup(player);
    }
}
