package de.ipnats.hardwrought.client.mixin.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Binds vanilla's global uniforms, and with them the dynamic lights, whenever Sodium binds its own
 * for a terrain draw; see {@link SodiumShaderChunkRendererMixin}.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class SodiumDefaultChunkRendererMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/RenderPass;setUniform(Ljava/lang/String;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;)V"))
    private void hardwrought$bindLights(RenderPass pass, String name, GpuBufferSlice value, Operation<Void> original) {
        original.call(pass, name, value);
        if (!"u_Globals".equals(name)) return;
        GpuBuffer globals = RenderSystem.getGlobalSettingsUniform();
        if (globals != null) pass.setUniform("Globals", globals);
    }
}
