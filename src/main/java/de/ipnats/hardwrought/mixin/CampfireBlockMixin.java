package de.ipnats.hardwrought.mixin;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A campfire a player sets down is laid, not burning.
 *
 * <p>Vanilla hands out a lit fire for free, which makes the first night a formality. Laying the wood
 * is the easy half; starting it is the part that should cost something, and the fire-lighting sticks
 * are what it costs.
 *
 * <p>Only placement is touched. A campfire that generates with a village or a ruin is somebody
 * else's fire and stays lit.
 */
@Mixin(CampfireBlock.class)
public abstract class CampfireBlockMixin {
    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void hardwrought$placeUnlit(BlockPlaceContext context,
                                        CallbackInfoReturnable<BlockState> callback) {
        BlockState state = callback.getReturnValue();
        if (state == null || !state.hasProperty(BlockStateProperties.LIT)) return;
        callback.setReturnValue(state.setValue(BlockStateProperties.LIT, false));
    }
}
