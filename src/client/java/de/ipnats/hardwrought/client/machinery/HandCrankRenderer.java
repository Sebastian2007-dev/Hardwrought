package de.ipnats.hardwrought.client.machinery;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.machinery.HandCrankBlock;
import de.ipnats.hardwrought.machinery.HandCrankBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the hand crank's handle going round. Same split as the shaft: the bearing is the block
 * model, the handle is {@code hand_crank_handle} drawn here and turned about the axle.
 */
public class HandCrankRenderer implements BlockEntityRenderer<HandCrankBlockEntity, HandCrankRenderState> {
    public HandCrankRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public HandCrankRenderState createRenderState() {
        return new HandCrankRenderState();
    }

    @Override
    public void extractRenderState(HandCrankBlockEntity crank, HandCrankRenderState state, float partialTick,
                                   Vec3 camera, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(crank, state, partialTick, camera, crumbling);
        state.facing = crank.getBlockState().getValue(HandCrankBlock.FACING);
        state.angle = crank.angle() + crank.degreesPerTick() * partialTick;

        Level level = crank.getLevel();
        BlockPos pos = crank.getBlockPos();
        state.handle.blockPos = pos;
        state.handle.randomSeedPos = pos;
        state.handle.blockState = ModBlocks.HAND_CRANK_HANDLE.defaultBlockState();
        if (level instanceof ClientLevel client) {
            state.handle.biome = client.getBiome(pos);
            state.handle.cardinalLighting = client.cardinalLighting();
            state.handle.lightEngine = client.getLightEngine();
        }
    }

    @Override
    public void submit(HandCrankRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        if (state.handle.lightEngine == null) return;
        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        orient(pose, state.facing);
        // The handle model is built facing north, so its axle is the local Z.
        pose.rotateDegrees(Axis.ZP, state.angle);
        pose.translate(-0.5f, -0.5f, -0.5f);
        // Zero: no outline. The light comes from the render state, as for the shaft.
        collector.submitMovingBlock(pose, state.handle, 0);
        pose.popPose();
    }

    /** Turns the north-facing model to face the way the crank does, matching the blockstate file. */
    private static void orient(PoseStack pose, Direction facing) {
        switch (facing) {
            case NORTH -> { }
            case SOUTH -> pose.rotateDegrees(Axis.YP, 180.0f);
            case EAST -> pose.rotateDegrees(Axis.YP, -90.0f);
            case WEST -> pose.rotateDegrees(Axis.YP, 90.0f);
            case UP -> pose.rotateDegrees(Axis.XP, 90.0f);
            case DOWN -> pose.rotateDegrees(Axis.XP, -90.0f);
        }
    }
}
