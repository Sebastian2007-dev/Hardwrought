package de.ipnats.hardwrought.client.fx;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The FX module's own post-processing, run at the end of the main pass:
 * <ol>
 *   <li>The light of every effect is drawn again, into an HDR target, tested against the scene's
 *       depth so walls hide it.</li>
 *   <li>That target is blurred down and up a chain of ever smaller textures (dual Kawase), which
 *       gives a soft, wide glow around the magic and nothing else: selective bloom.</li>
 *   <li>Shockwaves, heat and swirls are drawn into a distortion field of screen offsets.</li>
 *   <li>The scene is composited through the distortion field, split into colours where the air
 *       bends hardest, with the bloom laid over it.</li>
 * </ol>
 * When nothing glows or bends, the whole pass is skipped.
 */
public final class FxPost {
    private static final int BLOOM_LEVELS = 5;
    private static final float BLOOM_STRENGTH = 0.9f;
    private static final int VERTEX_SIZE = 32;

    private static final RenderPipeline BLOOM_DOWN = post("bloom_down", GpuFormat.RGBA16_FLOAT, Optional.empty(), false, "InSampler");
    private static final RenderPipeline BLOOM_UP = post("bloom_up", GpuFormat.RGBA16_FLOAT, Optional.of(BlendFunction.ADDITIVE), false, "InSampler");
    private static final RenderPipeline COMPOSITE = post("composite", GpuFormat.RGBA8_UNORM, Optional.empty(), true,
            "SceneSampler", "BloomSampler", "DistortSampler");

    /** A texture with its view, remade whenever the window changes size. */
    private static final class Target {
        final String label;
        final GpuFormat format;
        GpuTexture texture;
        GpuTextureView view;

        Target(String label, GpuFormat format) {
            this.label = label;
            this.format = format;
        }

        void ensure(int width, int height) {
            if (texture != null && texture.getWidth(0) == width && texture.getHeight(0) == height) return;
            close();
            texture = RenderSystem.getDevice().createTexture(() -> "Hardwrought FX " + label,
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST,
                    format, width, height, 1, 1);
            view = RenderSystem.getDevice().createTextureView(texture);
        }

        void close() {
            if (view != null) view.close();
            if (texture != null) texture.close();
            view = null;
            texture = null;
        }
    }

    private static final Target glow = new Target("glow", GpuFormat.RGBA16_FLOAT);
    private static final Target distort = new Target("distortion", GpuFormat.RGBA16_FLOAT);
    private static final Target scene = new Target("scene", GpuFormat.RGBA8_UNORM);
    private static final Target[] bloom = new Target[BLOOM_LEVELS];

    static {
        for (int i = 0; i < BLOOM_LEVELS; i++) bloom[i] = new Target("bloom " + i, GpuFormat.RGBA16_FLOAT);
    }

    private static GpuBuffer vertices;
    private static GpuBuffer compositeUniform;

    private FxPost() { }

    /** Loads the class, so the post pipelines are registered before the shaders are first compiled. */
    static void initialize() { }

    private static RenderPipeline post(String name, GpuFormat format, Optional<BlendFunction> blend, boolean uniform,
                                       String... samplers) {
        BindGroupLayout.Builder layout = BindGroupLayout.builder();
        for (String sampler : samplers) layout.withUniform(sampler, UniformType.COMBINED_IMAGE_SAMPLER);
        if (uniform) layout.withUniform("FxComposite", UniformType.UNIFORM_BUFFER);
        return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Hardwrought.id("pipeline/fx_post_" + name))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Hardwrought.id("post/fx_" + name))
                .withBindGroupLayout(layout.build())
                .withColorTargetState(new ColorTargetState(blend, format, ColorTargetState.WRITE_ALL))
                .build());
    }

    /** One range of the uploaded vertices, drawn with one pipeline. */
    private record Batch(RenderPipeline pipeline, int firstVertex, int vertexCount) { }

    static void render(LevelRenderContext context) {
        Map<RenderType, FxRenderer.Quads> quads = FxRenderer.quads();
        FxRenderer.Quads warp = FxRenderer.distortion();
        float aberration = FxScreen.aberration();
        float saturation = FxScreen.saturation();

        List<Batch> glowBatches = new ArrayList<>();
        int vertexCount = 0;
        for (Map.Entry<RenderType, FxRenderer.Quads> entry : quads.entrySet()) {
            RenderPipeline pipeline = FxRenderTypes.bloom(entry.getKey());
            int count = entry.getValue().size / 8;
            if (pipeline == null || count == 0) continue;
            glowBatches.add(new Batch(pipeline, vertexCount, count));
            vertexCount += count;
        }
        Batch warpBatch = warp.size > 0 ? new Batch(FxRenderTypes.DISTORT, vertexCount, warp.size / 8) : null;
        if (warpBatch != null) vertexCount += warpBatch.vertexCount;
        if (glowBatches.isEmpty() && warpBatch == null && aberration < 0.001f && Math.abs(saturation) < 0.001f) return;

        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (main.getColorTexture() == null || main.getDepthTextureView() == null) return;
        int width = main.width, height = main.height;
        glow.ensure(width, height);
        distort.ensure(width, height);
        scene.ensure(width, height);
        for (int i = 0; i < BLOOM_LEVELS; i++) {
            bloom[i].ensure(Math.max(1, width >> (i + 1)), Math.max(1, height >> (i + 1)));
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        upload(encoder, quads, glowBatches, warp, vertexCount);
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        int maxQuads = 0;
        for (Batch batch : glowBatches) maxQuads = Math.max(maxQuads, batch.vertexCount / 4);
        if (warpBatch != null) maxQuads = Math.max(maxQuads, warpBatch.vertexCount / 4);
        GpuBuffer indexBuffer = indices.getBuffer(Math.max(6, maxQuads * 6));
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
        Vector4fc clear = new Vector4f(0, 0, 0, 0);

        // 1. The light of the effects, again, into the HDR target.
        try (RenderPass pass = encoder.createRenderPass(() -> "Hardwrought FX glow", glow.view, Optional.of(clear),
                main.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertices.slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            for (Batch batch : glowBatches) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(batch.pipeline));
                pass.drawIndexed(batch.vertexCount / 4 * 6, 1, 0, batch.firstVertex, 0);
            }
        }
        // 2. The distortion field.
        try (RenderPass pass = encoder.createRenderPass(() -> "Hardwrought FX distortion", distort.view, Optional.of(clear),
                main.getDepthTextureView(), OptionalDouble.empty())) {
            if (warpBatch != null) {
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                pass.setVertexBuffer(0, vertices.slice());
                pass.setIndexBuffer(indexBuffer, indices.type());
                pass.setPipeline(RenderSystem.getCompiledPipeline(warpBatch.pipeline));
                pass.drawIndexed(warpBatch.vertexCount / 4 * 6, 1, 0, warpBatch.firstVertex, 0);
            }
        }
        // 3. Bloom: down the chain, then back up, each level added onto the next larger one.
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        GpuTextureView source = glow.view;
        for (int i = 0; i < BLOOM_LEVELS; i++) {
            fullscreen(encoder, BLOOM_DOWN, bloom[i].view, Optional.of(clear), linear, false, "InSampler", source);
            source = bloom[i].view;
        }
        for (int i = BLOOM_LEVELS - 1; i > 0; i--) {
            fullscreen(encoder, BLOOM_UP, bloom[i - 1].view, Optional.empty(), linear, false, "InSampler", bloom[i].view);
        }
        // 4. The scene through the distortion, with the bloom over it.
        encoder.copyTextureToTexture(main.getColorTexture(), scene.texture, 0, 0, 0, 0, 0, width, height);
        writeCompositeUniform(encoder, aberration, saturation);
        fullscreen(encoder, COMPOSITE, main.getColorTextureView(), Optional.empty(), linear, true,
                "SceneSampler", scene.view, "BloomSampler", bloom[0].view, "DistortSampler", distort.view);
    }

    private static void fullscreen(CommandEncoder encoder, RenderPipeline pipeline, GpuTextureView output,
                                   Optional<Vector4fc> clear, GpuSampler sampler, boolean uniform, Object... inputs) {
        try (RenderPass pass = encoder.createRenderPass(() -> "Hardwrought FX " + pipeline.getLocation(), output,
                clear, null, OptionalDouble.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            for (int i = 0; i < inputs.length; i += 2) {
                pass.setUniform((String) inputs[i], (GpuTextureView) inputs[i + 1], sampler);
            }
            if (uniform) pass.setUniform("FxComposite", compositeUniform);
            pass.draw(3, 1, 0, 0);
        }
    }

    private static void writeCompositeUniform(CommandEncoder encoder, float aberration, float saturation) {
        if (compositeUniform == null) {
            compositeUniform = RenderSystem.getDevice().createBuffer(() -> "Hardwrought FX composite",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, 16).putVec4(BLOOM_STRENGTH, aberration, saturation, 0).get();
            encoder.writeToBuffer(compositeUniform.slice(), data);
        }
    }

    /** Every glowing and bending quad of the frame in one vertex buffer, in the order of the batches. */
    private static void upload(CommandEncoder encoder, Map<RenderType, FxRenderer.Quads> quads, List<Batch> glowBatches,
                               FxRenderer.Quads warp, int vertexCount) {
        long bytes = (long) vertexCount * VERTEX_SIZE;
        if (vertices == null || vertices.size() < bytes) {
            if (vertices != null) vertices.close();
            long size = Math.max(64 * 1024, Long.highestOneBit(bytes) << 1);
            vertices = RenderSystem.getDevice().createBuffer(() -> "Hardwrought FX post vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, size);
        }
        ByteBuffer data = MemoryUtil.memAlloc((int) bytes);
        try {
            for (Map.Entry<RenderType, FxRenderer.Quads> entry : quads.entrySet()) {
                if (FxRenderTypes.bloom(entry.getKey()) != null) put(data, entry.getValue());
            }
            put(data, warp);
            data.flip();
            encoder.writeToBuffer(vertices.slice(0, bytes), data);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static void put(ByteBuffer data, FxRenderer.Quads quads) {
        float[] v = quads.data;
        for (int i = 0; i < quads.size; i += 8) {
            data.putFloat(v[i]).putFloat(v[i + 1]).putFloat(v[i + 2]);
            int color = Float.floatToRawIntBits(v[i + 3]);
            data.put((byte) (color >> 16)).put((byte) (color >> 8)).put((byte) color).put((byte) (color >>> 24));
            data.putFloat(v[i + 4]).putFloat(v[i + 5]).putFloat(v[i + 6]).putFloat(v[i + 7]);
        }
    }

    static void close() {
        glow.close();
        distort.close();
        scene.close();
        for (Target target : bloom) target.close();
    }
}
