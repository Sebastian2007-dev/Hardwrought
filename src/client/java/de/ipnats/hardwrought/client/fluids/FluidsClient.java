package de.ipnats.hardwrought.client.fluids;

import com.mojang.blaze3d.vertex.PoseStack;
import de.ipnats.hardwrought.fluids.FluidBlocks;
import de.ipnats.hardwrought.fluids.FluidDisplayBlock;
import de.ipnats.hardwrought.fluids.FluidTankBlock;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

/** The tank's liquid, behind its glass. */
public final class FluidsClient {
    private FluidsClient() { }

    public static void initialize() {
        BlockEntityRendererRegistry.register(FluidBlocks.TANK_ENTITY, TankRenderer::new);
    }

    /**
     * The liquid in a tank: a cube of its look, as wide as the inside of the drum and as high as the
     * tank is full. A liquid with no look of its own here is not drawn.
     */
    public static class TankRenderer implements BlockEntityRenderer<FluidTankBlock.Entity, TankRenderer.State> {
        /** The drum's wall and lids, in blocks: the liquid stands inside them. */
        private static final float WALL = 2.5f / 16f, LID = 1f / 16f;

        public static class State extends BlockEntityRenderState {
            MovingBlockRenderState block;
            float fill;
            /** Where the liquid's box starts and ends inside the block: to the edge where another tank joins. */
            float x0, x1, y0, y1, z0, z1;
        }

        public TankRenderer(BlockEntityRendererProvider.Context context) { }

        @Override
        public State createRenderState() {
            return new State();
        }

        @Override
        public void extractRenderState(FluidTankBlock.Entity tank, State state, float partialTick, Vec3 camera,
                                       ModelFeatureRenderer.CrumblingOverlay crumbling) {
            BlockEntityRenderer.super.extractRenderState(tank, state, partialTick, camera, crumbling);
            state.block = null;
            state.fill = tank.fill();
            FluidDisplayBlock.Shown shown = state.fill > 0 ? FluidDisplayBlock.Shown.of(tank.fluid().getFluid()) : null;
            if (shown == null || !(tank.getLevel() instanceof ClientLevel client)) return;
            MovingBlockRenderState block = new MovingBlockRenderState();
            block.blockPos = tank.getBlockPos();
            block.randomSeedPos = tank.getBlockPos();
            block.blockState = FluidBlocks.DISPLAY.defaultBlockState().setValue(FluidDisplayBlock.FLUID, shown);
            block.biome = client.getBiome(tank.getBlockPos());
            block.cardinalLighting = client.cardinalLighting();
            block.lightEngine = client.getLightEngine();
            state.block = block;
            var here = tank.getBlockState();
            state.x0 = FluidTankBlock.joined(here, net.minecraft.core.Direction.WEST) ? 0 : WALL;
            state.x1 = FluidTankBlock.joined(here, net.minecraft.core.Direction.EAST) ? 1 : 1 - WALL;
            state.z0 = FluidTankBlock.joined(here, net.minecraft.core.Direction.NORTH) ? 0 : WALL;
            state.z1 = FluidTankBlock.joined(here, net.minecraft.core.Direction.SOUTH) ? 1 : 1 - WALL;
            state.y0 = FluidTankBlock.joined(here, net.minecraft.core.Direction.DOWN) ? 0 : LID;
            // A full tank under another runs on up into it without a gap.
            state.y1 = FluidTankBlock.joined(here, net.minecraft.core.Direction.UP) && state.fill >= 0.999f ? 1 : 1 - LID;
        }

        @Override
        public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            if (state.block == null || state.fill <= 0) return;
            pose.pushPose();
            pose.translate(state.x0, state.y0, state.z0);
            pose.scale(state.x1 - state.x0, (state.y1 - state.y0) * state.fill, state.z1 - state.z0);
            collector.submitMovingBlock(pose, state.block, 0);
            pose.popPose();
        }
    }
}
