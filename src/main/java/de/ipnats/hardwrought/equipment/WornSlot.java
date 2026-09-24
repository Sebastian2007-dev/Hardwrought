package de.ipnats.hardwrought.equipment;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One square of the worn strap beside the inventory: the pack, the lamp, or the gloves.
 *
 * <p>Takes one kind of thing and never more than one of it. The pack square is the one place in the
 * game where a player may end up wearing nothing, and emptying it is allowed on purpose — being
 * without a pack is a step in an upgrade, not a state the menu has to forbid.
 *
 * <p>Whether the square is open at all is a question only the client can answer, because folding the
 * strap away is something the player does to their own screen and the server is never told about it.
 * The server therefore answers yes always, which is what keeps a click valid; the client answers with
 * the fold, which is what stops the square being drawn or hit when it is folded away.
 */
public class WornSlot extends Slot {
    private static final Identifier BACKPACK_ICON = Identifier.fromNamespaceAndPath(
            "hardwrought", "equipment/slot_backpack");
    private static final Identifier LAMP_ICON = Identifier.fromNamespaceAndPath(
            "hardwrought", "equipment/slot_lamp");
    private static final Identifier GLOVES_ICON = Identifier.fromNamespaceAndPath(
            "hardwrought", "equipment/slot_gloves");
    /** Whether this square is open. On the server this is always true. */
    @FunctionalInterface
    public interface Fold {
        boolean isOpen();
    }

    private final Fold fold;

    public WornSlot(EquipmentContainer container, int index, int x, int y, Fold fold) {
        super(container, index, x, y);
        this.fold = fold;
    }

    @Override
    public boolean isActive() {
        return fold.isOpen();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return fold.isOpen() && EquipmentContainer.fits(getContainerSlot(), stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        return fold.isOpen();
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public Identifier getNoItemIcon() {
        return switch (getContainerSlot()) {
            case EquipmentContainer.BACKPACK_SLOT -> BACKPACK_ICON;
            case EquipmentContainer.LAMP_SLOT -> LAMP_ICON;
            default -> GLOVES_ICON;
        };
    }
}
