package de.ipnats.hardwrought.client.witchking;

import de.ipnats.hardwrought.witchking.WitchKingContent;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.NoopRenderer;

/** The Witch King on the client: his model, and his magic drawn by the FX module. */
public final class WitchKingClient {
    private WitchKingClient() { }

    public static void initialize() {
        ModelLayerRegistry.registerModelLayer(WitchKingRenderer.LAYER, WitchKingModel::createBodyLayer);
        EntityRendererRegistry.register(WitchKingContent.WITCH_KING, WitchKingRenderer::new);
        // Swords and souls have no model: they are light, drawn by WitchKingFx.
        EntityRendererRegistry.register(WitchKingContent.HEX_BLADE, NoopRenderer::new);
        EntityRendererRegistry.register(WitchKingContent.SOUL_ORB, NoopRenderer::new);
        // One sits on the throne and its seat on nothing to see.
        EntityRendererRegistry.register(WitchKingContent.SEAT_MOUNT, NoopRenderer::new);
        WitchKingFx.initialize();
        MantleClient.initialize();
    }
}
