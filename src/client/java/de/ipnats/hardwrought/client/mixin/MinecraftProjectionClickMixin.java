package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.client.knowledge.MultiblockProjection;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a multiblock projection is being placed, the mouse belongs to it: a right click sets it down,
 * a left click lets it go, and neither builds, breaks or uses anything in the world.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftProjectionClickMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void hardwrought$confirmProjection(CallbackInfo callback) {
        if (MultiblockProjection.placing()) {
            MultiblockProjection.confirm();
            callback.cancel();
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void hardwrought$cancelProjection(CallbackInfoReturnable<Boolean> callback) {
        if (MultiblockProjection.placing()) {
            MultiblockProjection.cancel();
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void hardwrought$noBreakingWhilePlacing(boolean down, CallbackInfo callback) {
        if (MultiblockProjection.placing() || MultiblockProjection.justCancelled()) callback.cancel();
    }
}
