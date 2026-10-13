package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.client.environment.SeasonFog;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An autumn morning closing in. Specification section 24.
 *
 * <p>Vanilla has already worked out where the fog starts and ends by the time this runs, including
 * whatever the player's head is underwater in; all this does is pull both distances in. Taking the
 * computed value and shortening it rather than writing a figure of our own means the fog still
 * behaves properly in a cave, in lava and under water, and that a player with a short render
 * distance does not end up with fog that starts beyond what they can see anyway.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void hardwrought$autumnFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker,
                                       float darkenWorldAmount, ClientLevel level,
                                       CallbackInfoReturnable<FogData> callback) {
        SeasonFog.close(callback.getReturnValue(), renderDistanceInChunks * 16.0f);
    }
}
