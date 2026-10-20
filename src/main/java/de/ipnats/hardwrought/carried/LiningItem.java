package de.ipnats.hardwrought.carried;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * An armour lining (stage E4): a heated one of nichrome mesh, or a cooled one of thermoelectric pads,
 * with a regulator and a cell. Clicked onto a chestplate in an inventory it goes into it — a lining
 * already there comes out onto the cursor — and the chestplate worn keeps its wearer's body in its band
 * at the cell's expense (see {@link CarriedPower}). The cell is charged with the chestplate in a charging
 * station.
 */
public class LiningItem extends PoweredItem {
    private final boolean heats;

    public LiningItem(boolean heats, Properties properties) {
        super(properties);
        this.heats = heats;
    }

    /** True for the heated lining, false for the cooled. */
    public boolean heats() {
        return heats;
    }

    /** Whether a lining goes into this: a chestplate. */
    public static boolean lines(ItemStack armour) {
        Equippable equippable = armour.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST && !(armour.getItem() instanceof PoweredItem);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction action, Player player) {
        ItemStack armour = slot.getItem();
        if (action != ClickAction.PRIMARY || !lines(armour) || !slot.allowModification(player) || self.getCount() != 1) return false;
        ItemStack old = armour.getOrDefault(Cells.LINING, ItemStack.EMPTY);
        armour.set(Cells.LINING, self.copy());
        slot.setChanged();
        self.setCount(0);
        if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false, net.minecraft.util.Prediction.SERVER_ONLY);
        player.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.6f, 1.2f);
        return true;
    }
}
