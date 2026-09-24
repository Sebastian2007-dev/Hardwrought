package de.ipnats.hardwrought.equipment;

import de.ipnats.hardwrought.core.CoreRuntime;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The worn squares as a container a menu can sit on: the pack on the back, the lamp on the belt,
 * the gloves on the hands.
 *
 * <p>Read fresh on every sync rather than copied in once, for the same reason the pack's own page is:
 * the player's inventory menu is built inside the {@code ServerPlayer} constructor and then lives as
 * long as the player does. A container that loaded at construction would show what was worn at login
 * for the rest of the session — and would be asking the player what they are wearing at the one
 * moment they are least able to answer.
 */
public class EquipmentContainer extends SimpleContainer {
    public static final int BACKPACK_SLOT = 0;
    public static final int LAMP_SLOT = 1;
    public static final int GLOVES_SLOT = 2;
    public static final int SIZE = 3;

    private final ServerPlayer owner;
    private boolean loading;

    public static EquipmentContainer of(ServerPlayer player) {
        return new EquipmentContainer(player);
    }

    public static EquipmentContainer clientSide() {
        return new EquipmentContainer(null);
    }

    private EquipmentContainer(ServerPlayer owner) {
        super(SIZE);
        this.owner = owner;
    }

    private CoreRuntime runtime() {
        if (owner == null || owner.level() == null) return null;
        return CoreLifecycle.find(owner.level().getServer());
    }

    /** Pulls what is worn in. Called from the menu's own sync, so it cannot go stale. */
    public void refresh() {
        CoreRuntime runtime = runtime();
        if (runtime == null) return;
        loading = true;
        try {
            PlayerEquipment worn = runtime.equipment().equipment(owner);
            if (!ItemStack.matches(getItem(BACKPACK_SLOT), worn.backpack())) {
                setItem(BACKPACK_SLOT, worn.backpack().copy());
            }
            if (!ItemStack.matches(getItem(LAMP_SLOT), worn.lamp())) {
                setItem(LAMP_SLOT, worn.lamp().copy());
            }
            if (!ItemStack.matches(getItem(GLOVES_SLOT), worn.gloves())) {
                setItem(GLOVES_SLOT, worn.gloves().copy());
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
        ItemStack packBefore = equipment.backpack(owner);
        ItemStack packNow = getItem(BACKPACK_SLOT);
        equipment.setEquipment(owner, new PlayerEquipment(packNow.copy(), getItem(LAMP_SLOT).copy(),
                getItem(GLOVES_SLOT).copy()));
        spillShrunkRows(packBefore, packNow);
    }

    /**
     * A pack swapped for a smaller one cannot silently swallow the rows it has stopped having. What
     * was in them lands in the player's hands, or at their feet where they can see it.
     */
    private void spillShrunkRows(ItemStack before, ItemStack after) {
        BackpackTier was = BackpackItem.tierOf(before);
        BackpackTier now = BackpackItem.tierOf(after);
        if (was == null) return;
        int kept = now == null ? 0 : now.slots();
        if (kept >= was.slots()) return;
        NonNullList<ItemStack> carried = BackpackItem.contentsOf(before);
        for (int slot = kept; slot < carried.size(); slot++) {
            ItemStack stack = carried.get(slot);
            if (stack.isEmpty()) continue;
            if (owner.getInventory().add(stack.copy())) continue;
            ItemEntity dropped = new ItemEntity(owner.level(), owner.getX(), owner.getY(),
                    owner.getZ(), stack.copy());
            dropped.setNoPickUpDelay();
            owner.level().addFreshEntity(dropped);
        }
    }

    /** True where this item belongs in that worn square. */
    public static boolean fits(int slot, ItemStack stack) {
        if (stack.isEmpty()) return true;
        return switch (slot) {
            case BACKPACK_SLOT -> BackpackItem.isBackpack(stack);
            case LAMP_SLOT -> stack.getItem()
                    instanceof de.ipnats.hardwrought.environment.SafetyLampItem;
            case GLOVES_SLOT -> stack.is(de.ipnats.hardwrought.core.registry.ModItems.SMITHING_GLOVES);
            default -> false;
        };
    }
}
