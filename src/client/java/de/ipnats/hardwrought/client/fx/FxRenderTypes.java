package de.ipnats.hardwrought.client.fx;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The render types of the FX module. Every primitive is a quad whose look is computed entirely in
 * its fragment shader, so no textures are involved: a vertex carries its position, a colour, where
 * on the primitive it lies ({@code UV0}) and two free parameters ({@code UV3}).
 *
 * <p>Light is additive and never writes depth, so overlapping effects brighten each other instead of
 * cutting holes. Each type also has the pipelines for improved transparency, which vanilla uses in
 * place of the plain ones whenever that option is on.
 */
public final class FxRenderTypes {
    public static final VertexFormat FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("UV3", GpuFormat.RG32_FLOAT)
            .build();

    private static final RenderPipeline.Snippet SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withVertexShader(Hardwrought.id("core/fx"))
            .withVertexBinding(0, FORMAT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .buildSnippet();

    /** The same light drawn again into the HDR target that the bloom is made from. */
    private static final Map<RenderType, RenderPipeline> BLOOM = new HashMap<>();

    public static final RenderType GLOW = additive("glow");
    public static final RenderType RING = additive("ring");
    public static final RenderType BEAM = additive("beam");
    public static final RenderType RUNE = additive("rune");
    public static final RenderType SHIELD = additive("shield");
    public static final RenderType SPHERE = additive("sphere");
    public static final RenderType SMOKE = translucent("smoke");

    /** Draws into the distortion field, not the scene; see {@code fx_distort.fsh}. */
    public static final RenderPipeline DISTORT = RenderPipelines.register(RenderPipeline.builder(SNIPPET)
            .withLocation(Hardwrought.id("pipeline/fx_distort"))
            .withFragmentShader(Hardwrought.id("core/fx_distort"))
            .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.ADDITIVE), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL))
            .build());

    private FxRenderTypes() { }

    /** Loads the class, so the pipelines are registered before the shaders are first compiled. */
    public static void initialize() { }

    /** The bloom pipeline of a type of light, or null for the types that do not glow. */
    public static RenderPipeline bloom(RenderType type) {
        return BLOOM.get(type);
    }

    private static RenderType additive(String name) {
        RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(SNIPPET)
                .withLocation(Hardwrought.id("pipeline/fx_" + name))
                .withFragmentShader(Hardwrought.id("core/fx_" + name))
                .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                .build());
        OitPipelineSet oit = RenderPipelines.register(OitPipelineSet.builder("hardwrought_fx_" + name,
                RenderPipeline.builder(SNIPPET)
                        .withFragmentShader(Hardwrought.id("core/fx_" + name))
                        .withShaderDefine("OIT_ADDITIVE")).build());
        RenderType type = RenderType.create("hardwrought_fx_" + name,
                RenderSetup.builder(pipeline).setOitPipelines(oit).createRenderSetup());
        BLOOM.put(type, RenderPipelines.register(RenderPipeline.builder(SNIPPET)
                .withLocation(Hardwrought.id("pipeline/fx_" + name + "_bloom"))
                .withFragmentShader(Hardwrought.id("core/fx_" + name))
                .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.LIGHTNING), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL))
                .build()));
        return type;
    }

    private static RenderType translucent(String name) {
        RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(SNIPPET)
                .withLocation(Hardwrought.id("pipeline/fx_" + name))
                .withFragmentShader(Hardwrought.id("core/fx_" + name))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .build());
        OitPipelineSet oit = RenderPipelines.register(OitPipelineSet.builder("hardwrought_fx_" + name,
                RenderPipeline.builder(SNIPPET)
                        .withFragmentShader(Hardwrought.id("core/fx_" + name))).build());
        return RenderType.create("hardwrought_fx_" + name,
                RenderSetup.builder(pipeline).setOitPipelines(oit).sortOnUpload().createRenderSetup());
    }
}
