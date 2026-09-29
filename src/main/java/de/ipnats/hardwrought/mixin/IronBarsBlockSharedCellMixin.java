package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.building.SharedCellBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A pane beside a pane set into a stair or a slab joins it, as it would a free-standing one. */
@Mixin(IronBarsBlock.class)
public abstract class IronBarsBlockSharedCellMixin {
    @Inject(method = "attachsTo", at = @At("HEAD"), cancellable = true)
    private void hardwrought$joinSharedPane(BlockState state, boolean faceSturdy, CallbackInfoReturnable<Boolean> callback) {
        if (state.getBlock() instanceof SharedCellBlock && state.getValue(SharedCellBlock.CONNECTS)) {
            callback.setReturnValue(true);
        }
    }
}
