package de.ipnats.hardwrought.equipment;

import de.ipnats.hardwrought.core.CoreRuntime;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * The inside of the pack a player is wearing, as a container a menu can put slots on.
 *
 * <p>Always the full three rows, whatever the pack holds. A menu whose slot count changed with the
 * pack would have to be rebuilt every time one was swapped, and the rows a pack has not earned are
 * simply not active — which is also what makes a better pack read as *more of the same thing* rather
 * than as a different screen.
 *
 * <p>Read fresh rather than loaded once. The player's own inventory menu is built in the
 * {@code ServerPlayer} constructor and then lives as long as the player does, so a container that
 * copied the pack in at construction would show whatever was in it at login and never change again —
 * and would be reading the pack at the one moment the player is least ready to be asked anything.
 * {@link #refresh()} is called from the menu's own sync instead, and writes go straight back.
 *
 * <p>On the client it is a plain buffer that the server's slot packets fill in. Nothing here reaches
 * for the pack on that side, because the client is not allowed to know what it has not been sent.
 */
public class BackpackContainer extends SimpleContainer {
    public static final int SIZE = BackpackTier.MAIN_ROWS * BackpackTier.COLUMNS;

    private final ServerPlayer owner;
    private boolean loading;

    /** The server's view: reads the worn pack on every sync and writes every change back into it. */
    public static BackpackContainer of(ServerPlayer player) {
        return new BackpackContainer(player);
    }

    /** The client's view: an empty buffer the server fills in slot by slot. */
    public static BackpackContainer clientSide() {
        return new BackpackContainer(null);
    }

    private BackpackContainer(ServerPlayer owner) {
        super(SIZE);
        this.owner = owner;
    }

    /**
     * The runtime, or null where there is not one to ask yet. Everything here has to tolerate that:
     * this container is built while a player is being constructed, which happens during world load.
     */
    private CoreRuntime runtime() {
        if (owner == null || owner.level() == null) return null;
        return CoreLifecycle.find(owner.level().getServer());
    }

    /** Pulls the worn pack in. Called from the menu's own sync, so it cannot go stale. */
    public void refresh() {
        CoreRuntime runtime = runtime();
        if (runtime == null) return;
        loading = true;
        try {
            NonNullList<ItemStack> stored = BackpackItem.contentsOf(runtime.equipment().backpack(owner));
            for (int slot = 0; slot < SIZE; slot++) {
                ItemStack wanted = slot < stored.size() ? stored.get(slot) : ItemStack.EMPTY;
                if (!ItemStack.matches(getItem(slot), wanted)) setItem(slot, wanted);
            }
        } finally {
            loading = false;
        }
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (loading || owner == null) return;
        CoreRuntime runtime = runtime();
        if (runtime == null) return;
        var equipment = runtime.equipment();
        ItemStack pack = equipment.backpack(owner).copy();
        BackpackTier tier = BackpackItem.tierOf(pack);
        if (tier == null) return;
        NonNullList<ItemStack> items = NonNullList.withSize(tier.slots(), ItemStack.EMPTY);
        for (int slot = 0; slot < items.size(); slot++) items.set(slot, getItem(slot));
        BackpackItem.setContents(pack, items);
        equipment.setBackpack(owner, pack);
    }
}
