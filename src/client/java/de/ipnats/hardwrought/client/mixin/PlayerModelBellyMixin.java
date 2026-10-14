package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.client.survival.BodyMarks;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A stuffed player's belly: the torso, and only the torso, is drawn deeper, and a little wider, the
 * fuller they are. The jacket layer goes with it. The pose is reset every frame before this runs, so a
 * player who has digested it all is drawn as before.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelBellyMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void hardwrought$belly(AvatarRenderState state, CallbackInfo callback) {
        BodyMarks.Look look = BodyMarks.look(state);
        if (look == null || look.fullness() <= 0) return;
        PlayerModel model = (PlayerModel) (Object) this;
        model.body.zScale = 1.0f + BodyMarks.DEEPEST * look.fullness();
        model.body.xScale = 1.0f + BodyMarks.WIDEST * look.fullness();
    }
}
