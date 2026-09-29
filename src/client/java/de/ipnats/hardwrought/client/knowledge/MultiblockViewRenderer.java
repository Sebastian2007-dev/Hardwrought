package de.ipnats.hardwrought.client.knowledge;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a structure of blocks into the compendium, turned the way the reader has turned it, the way
 * vanilla draws a block item: full light, and the light of the inventory falling on it from the front.
 *
 * <p>Blocks that are only there for orientation, the layers beside the one looked at, are drawn after
 * the others as bare faces through the translucent sheet, faded by their own alpha.
 */
public class MultiblockViewRenderer extends PictureInPictureRenderer<MultiblockRenderState> {
    private static final int FULL_BRIGHT = 0xF000F0;

    @Override
    public Class<MultiblockRenderState> getRenderStateClass() {
        return MultiblockRenderState.class;
    }

    @Override
    protected void renderToTexture(MultiblockRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        Minecraft client = Minecraft.getInstance();
        client.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
        BlockModelResolver resolver = new BlockModelResolver(client.getModelManager());
        // The picture's y runs down the screen; a block's runs up.
        pose.scale(1.0f, -1.0f, -1.0f);
        pose.rotateDegrees(Axis.XP, state.pitch());
        pose.rotateDegrees(Axis.YP, state.yaw());
        pose.translate(-state.centerX(), -state.centerY(), -state.centerZ());
        List<MultiblockRenderState.Placed> faded = new ArrayList<>();
        for (MultiblockRenderState.Placed placed : state.blocks()) {
            if (placed.alpha() < 1.0f) {
                faded.add(placed);
                continue;
            }
            BlockModelRenderState model = new BlockModelRenderState();
            resolver.update(model, placed.state(), BlockDisplayContext.create());
            if (model.isEmpty()) continue;
            pose.pushPose();
            pose.translate(placed.pos().getX(), placed.pos().getY(), placed.pos().getZ());
            model.submit(pose, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        if (faded.isEmpty()) return;
        // Ordered after the solid blocks, so they show through what is drawn over them.
        var afterwards = collector.order(1);
        RandomSource random = RandomSource.create(42L);
        for (MultiblockRenderState.Placed placed : faded) {
            List<BlockStateModelPart> parts = new ArrayList<>();
            random.setSeed(42L);
            client.getModelManager().getBlockStateModelSet().get(placed.state()).collectParts(random, parts);
            QuadInstance instance = new QuadInstance();
            instance.setColor(((int) (placed.alpha() * 255) << 24) | 0xFFFFFF);
            instance.setLightCoords(FULL_BRIGHT);
            instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
            pose.pushPose();
            pose.translate(placed.pos().getX(), placed.pos().getY(), placed.pos().getZ());
            afterwards.submitCustomGeometry(pose, Sheets.translucentBlockItemSheet(), (at, vertices) -> {
                for (BlockStateModelPart part : parts) {
                    for (Direction side : Direction.values()) {
                        for (BakedQuad quad : part.getQuads(side)) vertices.putBakedQuad(at, quad, instance);
                    }
                    for (BakedQuad quad : part.getQuads(null)) vertices.putBakedQuad(at, quad, instance);
                }
            });
            pose.popPose();
        }
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0f;
    }

    @Override
    protected String getTextureLabel() {
        return "hardwrought multiblock view";
    }
}
