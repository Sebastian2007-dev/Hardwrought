package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.equipment.EquipmentContainer;
import de.ipnats.hardwrought.equipment.WornSlot;
import de.ipnats.hardwrought.equipment.WornStrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hangs the worn strap off the player's own inventory.
 *
 * <p>It belongs here rather than in a screen of its own. What a player is wearing is part of the
 * picture of the player — the armour, the shield and the pack are one glance, not two — and a screen
 * that has to be opened to see whether you are wearing a pack is a screen nobody opens.
 *
 * <p>The strap folds away so it never has to fight the inventory for room, and it is the only part of
 * this whose state lives on the client alone: folding is something the player does to their own view.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {
    @Unique private EquipmentContainer hardwrought$worn;

    protected InventoryMenuMixin(net.minecraft.world.inventory.MenuType<?> type, int containerId) {
        super(type, containerId);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void hardwrought$addWornStrap(Inventory inventory, boolean active, Player owner,
                                          CallbackInfo info) {
        hardwrought$worn = owner instanceof ServerPlayer serverPlayer
                ? EquipmentContainer.of(serverPlayer) : EquipmentContainer.clientSide();
        // broadcastChanges lives on AbstractContainerMenu, so the refresh is hung off the hook that
        // is already there rather than injected into a method this class does not declare.
        ((de.ipnats.hardwrought.equipment.CarriedInventoryMenu) this).hardwrought$trackWorn(hardwrought$worn);
        boolean client = owner.level() != null && owner.level().isClientSide();
        for (int index = 0; index < EquipmentContainer.SIZE; index++) {
            addSlot(new WornSlot(hardwrought$worn, index, WornStrap.SLOT_X, WornStrap.slotY(index),
                    // The server has no idea whether the strap is folded, and must not pretend to.
                    client ? WornStrap::shown : () -> true));
        }
    }
}
