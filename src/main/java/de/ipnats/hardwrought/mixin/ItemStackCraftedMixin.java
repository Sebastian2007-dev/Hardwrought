package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Section 82: making a thing is one of the ways of coming to know it.
 *
 * <p>Vanilla tells an item stack when it has been crafted, and that one call covers every grid in
 * the game — the inventory square, a workbench, a hewn bench, anything a later milestone adds. It is
 * a far smaller thing to hook than every menu that might contain a result slot.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackCraftedMixin {
    @Inject(method = "onCraftedBy", at = @At("HEAD"))
    private void hardwrought$studyWhatWasMade(Player player, int count, CallbackInfo callback) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        var runtime = CoreLifecycle.find(serverPlayer.level().getServer());
        if (runtime == null) return;
        runtime.knowledge().study(serverPlayer, ((ItemStack) (Object) this).getItem());
    }
}
