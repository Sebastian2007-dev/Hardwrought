package de.ipnats.hardwrought.client.ritual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import de.ipnats.hardwrought.client.mixin.ItemLayerRenderStateAccessor;
import de.ipnats.hardwrought.client.mixin.ItemStackRenderStateAccessor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An item drawn as a ghost: its own model, every quad of it see-through and tinted, lit by itself. What
 * is wanted somewhere and not there yet looks like this.
 */
public final class GhostItem {
    private GhostItem() { }

    /** Draws the resolved item as a ghost of this colour, alpha included. */
    public static void submit(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector collector, int argb) {
        ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) item;
        ItemStackRenderState.LayerRenderState[] layers = state.hardwrought$layers();
        QuadInstance instance = new QuadInstance();
        instance.setColor(argb);
        instance.setLightCoords(0xF000F0);
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        for (int i = 0; i < state.hardwrought$activeLayerCount() && i < layers.length; i++) {
            ItemLayerRenderStateAccessor layer = (ItemLayerRenderStateAccessor) layers[i];
            ItemQuads quads = layer.hardwrought$quads();
            if (quads == null || quads.isEmpty()) continue;
            // Each quad drawn from the sheet its own texture lies on: blocks and items lie on different ones.
            Map<Identifier, List<BakedQuad>> bySheet = new LinkedHashMap<>();
            for (BakedQuad quad : quads.all()) {
                bySheet.computeIfAbsent(quad.materialInfo().sprite().atlasLocation(), sheet -> new ArrayList<>()).add(quad);
            }
            pose.pushPose();
            layer.hardwrought$applyTransform(pose.last());
            for (Map.Entry<Identifier, List<BakedQuad>> sheet : bySheet.entrySet()) {
                List<BakedQuad> drawn = sheet.getValue();
                collector.order(1).submitCustomGeometry(pose, RenderTypes.itemTranslucent(sheet.getKey()), (at, vertices) -> {
                    for (BakedQuad quad : drawn) vertices.putBakedQuad(at, quad, instance);
                });
            }
            pose.popPose();
        }
    }
}
