package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.client.fx.FxScreen;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Screen shake from the FX module; see {@link FxScreen}. Only the camera turns, never the player,
 * and it happens before the frustum is built from the camera, so culling follows the shaken view.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private float xRot;
    @Shadow private float yRot;

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void hardwrought$shake(float partialTicks, CallbackInfo callback) {
        if (!FxScreen.shaking()) return;
        setRotation(yRot + FxScreen.shakeYaw(), xRot + FxScreen.shakePitch());
    }
}
