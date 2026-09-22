package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.equipment.BackpackTier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps what a player picks up out of the grid they have not earned yet.
 *
 * <p>Closing the main grid in the menu is only half of the rule. Picking something up never looks at
 * a menu — it asks the inventory for the first place the item will go — so without this a player
 * with no pack would keep filling twenty-seven slots they could neither see nor reach, and would
 * find them again the moment they wove one. The belt fills, and once the belt is full the ground
 * keeps the rest, which is exactly what carrying nothing should feel like.
 *
 * <p>Only the server is gated. The client runs the same code to predict a pickup, but it is not told
 * what the player is wearing, and a prediction that disagrees for one tick is corrected by the very
 * next inventory packet — whereas guessing here would be a rule the client could be wrong about.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Shadow @Final public net.minecraft.world.entity.player.Player player;

    @Inject(method = "getFreeSlot", at = @At("RETURN"), cancellable = true)
    private void hardwrought$noFreeSlotBehindTheBelt(CallbackInfoReturnable<Integer> info) {
        if (info.getReturnValue() >= BackpackTier.COLUMNS && hardwrought$mainClosed()) {
            info.setReturnValue(-1);
        }
    }

    @Inject(method = "getSlotWithRemainingSpace", at = @At("RETURN"), cancellable = true)
    private void hardwrought$noRoomBehindTheBelt(ItemStack stack, CallbackInfoReturnable<Integer> info) {
        if (info.getReturnValue() >= BackpackTier.COLUMNS && hardwrought$mainClosed()) {
            info.setReturnValue(-1);
        }
    }

    @Unique
    private boolean hardwrought$mainClosed() {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        var runtime = CoreLifecycle.find(serverPlayer.level().getServer());
        return runtime != null && runtime.equipment().tier(serverPlayer) == null;
    }
}
