package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.end.EndRules;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No block goes down in the End while its dragon lives; see {@code EndRules}. Both sides ask, so the client shows none. */
@Mixin(BlockItem.class)
public abstract class EndBuildingMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void hardwrought$noBuildingInTheEnd(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> info) {
        if (!EndRules.buildingForbidden(context.getPlayer())) return;
        EndRules.refuse(context.getPlayer());
        info.setReturnValue(InteractionResult.FAIL);
    }
}
