package de.ipnats.hardwrought.client.knowledge;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Draws a structure of blocks into the compendium, turned the way the reader has turned it, the way
 * vanilla draws a block item: full light, and the light of the inventory falling on it from the front.
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
        for (MultiblockRenderState.Placed placed : state.blocks()) {
            BlockModelRenderState model = new BlockModelRenderState();
            resolver.update(model, placed.state(), BlockDisplayContext.create());
            if (model.isEmpty()) continue;
            pose.pushPose();
            pose.translate(placed.pos().getX(), placed.pos().getY(), placed.pos().getZ());
            model.submit(pose, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
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
