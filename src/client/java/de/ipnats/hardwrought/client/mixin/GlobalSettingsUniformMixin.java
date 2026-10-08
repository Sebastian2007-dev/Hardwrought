package de.ipnats.hardwrought.client.mixin;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import de.ipnats.hardwrought.client.fx.FxLighting;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hardwrought's dynamic lights ride along in vanilla's global uniform buffer, which is bound to
 * every world pipeline already: the buffer is made larger, and the lights are written after
 * vanilla's own values each frame. See {@link FxLighting} and {@code minecraft:globals.glsl}.
 */
@Mixin(GlobalSettingsUniform.class)
public abstract class GlobalSettingsUniformMixin {
    @Shadow @Final private GpuBuffer buffer;

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/device/GpuDevice;createBuffer(Ljava/util/function/Supplier;IJ)Lcom/mojang/renderpearl/api/buffers/GpuBuffer;"),
            index = 2)
    private long hardwrought$roomForLights(long size) {
        return GlobalSettingsUniform.UBO_SIZE + FxLighting.UBO_SIZE;
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void hardwrought$writeLights(int width, int height, double glintAlpha, long gameTime, float worldPartialTicks,
                                         int menuBlurRadius, Vec3 cameraPos, boolean useRgss, CallbackInfo callback) {
        FxLighting.write(buffer, GlobalSettingsUniform.UBO_SIZE);
    }
}
