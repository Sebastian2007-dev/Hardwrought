package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.environment.LightBurn;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lights burn down: a torch, lantern, campfire, candle or jack o'lantern is handed random ticks so its
 * time can be watched, starts burning when it is lit, and goes out when its time is up. See {@link LightBurn}.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBurnMixin {
    @Inject(method = "isRandomlyTicking", at = @At("RETURN"), cancellable = true)
    private void hardwrought$burnsDown(CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ() && LightBurn.burns((BlockState) (Object) this)) callback.setReturnValue(true);
    }

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void hardwrought$burnOut(ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo callback) {
        if (LightBurn.tick((BlockState) (Object) this, level, pos)) callback.cancel();
    }

    @Inject(method = "onPlace", at = @At("TAIL"))
    private void hardwrought$lit(Level level, BlockPos pos, BlockState oldState, boolean movedByPiston, CallbackInfo callback) {
        LightBurn.placed(level, pos, oldState, (BlockState) (Object) this);
    }
}
