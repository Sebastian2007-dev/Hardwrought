package de.ipnats.hardwrought.client.machinery;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.ipnats.hardwrought.machinery.KineticBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;

/**
 * Draws the turning part of a gear, a water wheel or a windmill: one block-state model, laid flat
 * about the local Y axis the way the shaft bar is, turned onto the part's axle and spun by its angle.
 * A model can reach only a block past its own on every side, so the sails — wider than that — are
 * drawn from a smaller model scaled up.
 */
public class KineticPartRenderer<T extends KineticBlockEntity> implements BlockEntityRenderer<T, KineticPartRenderer.State> {
    private final Function<BlockState, Direction.Axis> axis;
    /** The moving part to draw for this block, or null where nothing is drawn. */
    private final Function<BlockState, Block> part;
    private final float scale;
    /** Degrees added on every other block, so that neighbouring gears' teeth sit between each other. */
    private final float mesh;

    public static class State extends BlockEntityRenderState {
        float angle;
        Direction.Axis axis = Direction.Axis.Y;
        final MovingBlockRenderState part = new MovingBlockRenderState();
    }

    public KineticPartRenderer(Function<BlockState, Direction.Axis> axis, Function<BlockState, Block> part,
                               float scale, float mesh) {
        this.axis = axis;
        this.part = part;
        this.scale = scale;
        this.mesh = mesh;
    }

    public static <T extends KineticBlockEntity> BlockEntityRendererProvider<T, State> of(
            Function<BlockState, Direction.Axis> axis, Function<BlockState, Block> part, float scale, float mesh) {
        return context -> new KineticPartRenderer<>(axis, part, scale, mesh);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        // Wheels and sails reach well past their hub, and are still to be seen when it is not.
        return true;
    }

    @Override
    public void extractRenderState(T entity, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTick, camera, crumbling);
        BlockPos pos = entity.getBlockPos();
        Block drawn = part.apply(entity.getBlockState());
        state.part.lightEngine = null;
        if (drawn == null) return;
        state.axis = axis.apply(entity.getBlockState());
        float offset = ((pos.getX() + pos.getY() + pos.getZ()) & 1) == 0 ? 0.0f : mesh;
        state.angle = entity.angle() + entity.degreesPerTick() * partialTick + offset;
        state.part.blockPos = pos;
        state.part.randomSeedPos = pos;
        state.part.blockState = drawn.defaultBlockState();
        if (entity.getLevel() instanceof ClientLevel client) {
            state.part.biome = client.getBiome(pos);
            state.part.cardinalLighting = client.cardinalLighting();
            state.part.lightEngine = client.getLightEngine();
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.part.lightEngine == null) return;
        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        switch (state.axis) {
            case X -> pose.rotateDegrees(Axis.ZP, 90.0f);
            case Z -> pose.rotateDegrees(Axis.XP, 90.0f);
            case Y -> { }
        }
        pose.rotateDegrees(Axis.YP, state.angle);
        pose.scale(scale, 1.0f, scale);
        pose.translate(-0.5f, -0.5f, -0.5f);
        collector.submitMovingBlock(pose, state.part, 0);
        pose.popPose();
    }
}
