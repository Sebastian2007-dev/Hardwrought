package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.client.survival.BodyMarks;
import de.ipnats.hardwrought.fun.StampiEasterEgg;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.decoration.Mannequin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries what shows of a player's body into the render state (see {@link BodyMarks}), and keeps the
 * "NPC" label off the easter egg's makers ({@link StampiEasterEgg}). Every sleeper is drawn lying down.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void hardwrought$extractBodyLook(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo callback) {
        // A sleeper lies down, whatever pose came over the wire: a player who had just died and come back
        // was sent the others with their sleeping place, but saw them standing.
        if (entity.isSleeping()) state.pose = net.minecraft.world.entity.Pose.SLEEPING;
        BodyMarks.extract(entity, state);
        de.ipnats.hardwrought.client.ritual.AscensionOutline.extract(entity, state, partialTick);
        // The easter egg's makers pass for players: their name over their head, no "NPC" under it.
        if (entity instanceof Mannequin && entity.getCustomName() != null
                && StampiEasterEgg.isMaker(entity.getCustomName().getString())) {
            state.scoreText = null;
        }
    }
}
