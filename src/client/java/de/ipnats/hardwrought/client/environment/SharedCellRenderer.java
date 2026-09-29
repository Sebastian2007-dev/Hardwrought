package de.ipnats.hardwrought.client.environment;

import com.mojang.blaze3d.vertex.PoseStack;
import de.ipnats.hardwrought.building.SharedCell;
import de.ipnats.hardwrought.building.SharedCellBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Draws both blocks of a shared cell with their own models, the way a block riding a piston is
 * drawn: the half block where it stands, and the thin one inside it — a carpet lifted onto the half
 * block's floor.
 */
public class SharedCellRenderer implements BlockEntityRenderer<SharedCellBlockEntity, SharedCellRenderer.State> {
    public static class State extends BlockEntityRenderState {
        final MovingBlockRenderState host = new MovingBlockRenderState();
        final MovingBlockRenderState insert = new MovingBlockRenderState();
        float insertLift;
    }

    public SharedCellRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SharedCellBlockEntity cell, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(cell, state, partialTick, camera, crumbling);
        state.host.lightEngine = null;
        if (cell.host().isAir() || !(cell.getLevel() instanceof ClientLevel level)) return;
        BlockPos pos = cell.getBlockPos();
        fill(state.host, cell.host(), pos, level);
        fill(state.insert, cell.insert(), pos, level);
        state.insertLift = cell.insert().getBlock() instanceof CarpetBlock
                ? (float) Math.max(0, SharedCell.carpetFloor(cell.host())) : 0f;
    }

    private static void fill(MovingBlockRenderState part, BlockState block, BlockPos pos, ClientLevel level) {
        part.blockPos = pos;
        part.randomSeedPos = pos;
        part.blockState = block;
        part.biome = level.getBiome(pos);
        part.cardinalLighting = level.cardinalLighting();
        part.lightEngine = level.getLightEngine();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.host.lightEngine == null) return;
        collector.submitMovingBlock(pose, state.host, 0);
        pose.pushPose();
        pose.translate(0.0f, state.insertLift, 0.0f);
        collector.submitMovingBlock(pose, state.insert, 0);
        pose.popPose();
    }
}
