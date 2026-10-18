package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.environment.Torches;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A torch set down burns on for what it had left in the hand, not a fresh day; see {@link Torches}.
 * The stack is still whole here: it is only shrunk once the block stands.
 */
@Mixin(BlockItem.class)
public abstract class TorchPlacingMixin {
    @Inject(method = "placeBlock", at = @At("RETURN"))
    private void hardwrought$burnOn(BlockPlaceContext context, BlockState state,
                                    CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ() || !(context.getLevel() instanceof ServerLevel level)) return;
        Torches.placed(level, context.getClickedPos(), context.getItemInHand());
    }
}
