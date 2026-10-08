package de.ipnats.hardwrought.client.mixin.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sodium draws terrain with its own shaders, so the dynamic lights in the overridden vanilla terrain
 * shader never reach it. Its terrain pipelines are built here with vanilla's global uniforms bound as
 * well (which carry the lights, see {@code GlobalSettingsUniformMixin}), and pointed at Hardwrought's
 * copy of its shader, which adds the lights per pixel.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap = false)
public abstract class SodiumShaderChunkRendererMixin {
    private static final String TERRAIN = "blocks/block_layer_opaque";
    private static final Identifier LIT_TERRAIN = Hardwrought.id("sodium/block_layer_opaque");

    @WrapOperation(method = {"createShader", "createOITShader"}, at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withVertexShader(Lnet/minecraft/resources/Identifier;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"))
    private RenderPipeline.Builder hardwrought$litVertexShader(RenderPipeline.Builder builder, Identifier shader,
                                                               Operation<RenderPipeline.Builder> original) {
        if (!isTerrain(shader)) return original.call(builder, shader);
        return original.call(builder, LIT_TERRAIN).withBindGroupLayout(BindGroupLayouts.GLOBALS);
    }

    @WrapOperation(method = {"createShader", "createOITShader"}, at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withFragmentShader(Lnet/minecraft/resources/Identifier;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"))
    private RenderPipeline.Builder hardwrought$litFragmentShader(RenderPipeline.Builder builder, Identifier shader,
                                                                 Operation<RenderPipeline.Builder> original) {
        return original.call(builder, isTerrain(shader) ? LIT_TERRAIN : shader);
    }

    private static boolean isTerrain(Identifier shader) {
        return shader.getNamespace().equals("sodium") && shader.getPath().equals(TERRAIN);
    }
}
