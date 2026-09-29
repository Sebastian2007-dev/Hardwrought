package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.client.environment.Darkness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Darkness, moonlight and dark adaptation; see {@link Darkness}. At the tail only, which vanilla
 * reaches once per tick after filling in every input — the early returns leave the state untouched.
 */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private GameRenderer renderer;

    @Inject(method = "extract", at = @At("TAIL"))
    private void hardwrought$darkness(LightmapRenderState state, float partialTick, CallbackInfo callback) {
        if (minecraft.level == null) return;
        Darkness.apply(state, minecraft.level, renderer.mainCamera(), partialTick);
    }
}
