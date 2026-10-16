package de.ipnats.hardwrought.client.underworld;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.client.fx.FxRenderTypes;
import de.ipnats.hardwrought.underworld.UnderworldGate;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The floor of the Underworld shaft, drawn with a shader of its own ({@code underworld_shaft.fsh}): the
 * shaft going on down below the pit, mist and rising souls in it. Each block draws its own square, but
 * tells the shader where in the whole opening it lies, so the squares join into one view down.
 */
public final class ShaftRenderer implements BlockEntityRenderer<UnderworldGate.ShaftEntity, ShaftRenderer.State> {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Hardwrought.id("pipeline/underworld_shaft"))
            .withVertexShader(Hardwrought.id("core/fx"))
            .withFragmentShader(Hardwrought.id("core/underworld_shaft"))
            .withVertexBinding(0, FxRenderTypes.FORMAT)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build());
    private static final RenderType TYPE = RenderType.create("hardwrought_underworld_shaft", RenderSetup.builder(PIPELINE).createRenderSetup());
    /** The surface lies at the top of the shaft block, the floor of the pit. */
    private static final float SURFACE = 0.999f;
    private static final int MOST = 8;

    /** Loads the class, so the pipeline is registered before the shaders are first compiled. */
    public static void initialize() { }

    public static final class State extends BlockEntityRenderState {
        /** Where this block lies in the opening, from its north-west corner, and the opening's size. */
        int x, z, width = 1, depth = 1;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(UnderworldGate.ShaftEntity shaft, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(shaft, state, partialTicks, camera, breakProgress);
        Level level = shaft.getLevel();
        BlockPos pos = shaft.getBlockPos();
        if (level == null) return;
        state.x = run(level, pos, -1, 0);
        state.z = run(level, pos, 0, -1);
        state.width = state.x + 1 + run(level, pos, 1, 0);
        state.depth = state.z + 1 + run(level, pos, 0, 1);
    }

    /** How many shaft blocks follow on from this one in a direction. */
    private static int run(Level level, BlockPos from, int dx, int dz) {
        int count = 0;
        while (count < MOST && level.getBlockState(from.offset(dx * (count + 1), 0, dz * (count + 1))).is(UnderworldGate.SHAFT)) count++;
        return count;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float x0 = state.x, z0 = state.z, w = state.width, d = state.depth;
        collector.submitCustomGeometry(pose, TYPE, (at, buffer) -> {
            buffer.addVertex(at, 0, SURFACE, 0).setColor(-1).setUv(x0, z0).setUv3(w, d);
            buffer.addVertex(at, 0, SURFACE, 1).setColor(-1).setUv(x0, z0 + 1).setUv3(w, d);
            buffer.addVertex(at, 1, SURFACE, 1).setColor(-1).setUv(x0 + 1, z0 + 1).setUv3(w, d);
            buffer.addVertex(at, 1, SURFACE, 0).setColor(-1).setUv(x0 + 1, z0).setUv3(w, d);
        });
    }
}
