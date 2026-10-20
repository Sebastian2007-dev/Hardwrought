package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.survival.Ultra;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In an Ultra world nothing breaks at a touch; see {@link Ultra#slowBreaking}. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateDestroyMixin {
    @Inject(method = "getDestroyProgress", at = @At("RETURN"), cancellable = true)
    private void hardwrought$nothingAtATouch(Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> info) {
        float progress = info.getReturnValueF();
        float slowed = Ultra.slowBreaking(progress, (BlockState) (Object) this, player);
        if (slowed != progress) info.setReturnValue(slowed);
    }
}
