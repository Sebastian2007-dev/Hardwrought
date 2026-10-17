package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.client.fx.FxScreen;
import de.ipnats.hardwrought.client.ritual.RitualCutscene;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Screen shake from the FX module; see {@link FxScreen}. Only the camera turns, never the player,
 * and it happens before the frustum is built from the camera, so culling follows the shaken view.
 *
 * <p>The ritual's cutscene ({@link RitualCutscene}) takes the camera the same way, away from the
 * player's eyes, while it runs.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private float xRot;
    @Shadow private float yRot;
    @Shadow private boolean detached;
    @Shadow private Vec3 position;

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Shadow protected abstract void setPosition(Vec3 position);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void hardwrought$shake(float partialTicks, CallbackInfo callback) {
        RitualCutscene.Shot shot = RitualCutscene.shot(partialTicks, position, yRot, xRot);
        if (shot != null) {
            setPosition(shot.position());
            setRotation(shot.yaw(), shot.pitch());
            if (shot.detached()) detached = true;
        }
        if (!FxScreen.shaking()) return;
        setRotation(yRot + FxScreen.shakeYaw(), xRot + FxScreen.shakePitch());
    }
}
