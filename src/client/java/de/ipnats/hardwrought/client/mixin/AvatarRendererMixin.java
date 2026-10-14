package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.client.survival.BodyMarks;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries what shows of a player's body into the render state (see {@link BodyMarks}).
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void hardwrought$extractBodyLook(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo callback) {
        BodyMarks.extract(entity, state);
    }
}
