package de.ipnats.hardwrought.client.fx;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.fx.FxPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/**
 * The FX module on the client: a particle and effect engine with its own shaders, real light from
 * effects, and screen effects. Effects arrive from the server as {@link FxPayload}s, or are started
 * directly through {@link FxPresets} and {@link #engine()}.
 */
public final class FxClient {
    private static final FxEngine ENGINE = new FxEngine();

    private FxClient() { }

    public static FxEngine engine() {
        return ENGINE;
    }

    public static void initialize() {
        FxRenderTypes.initialize();
        FxPost.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || !client.isPaused()) ENGINE.tick(client.level);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ENGINE.clear();
            FxScreen.clear();
            FxLighting.clear();
        });
        LevelExtractionEvents.END_EXTRACTION.register(FxRenderer::extract);
        LevelRenderEvents.COLLECT_SUBMITS.register(FxRenderer::submit);
        LevelRenderEvents.END_MAIN.register(FxPost::render);
        HudElementRegistry.addLast(Hardwrought.id("fx_screen"), FxScreen::render);
        ClientPlayNetworking.registerGlobalReceiver(FxPayload.TYPE, (payload, context) -> FxPresets.play(
                payload.effect(), payload.pos(), payload.target(), payload.color(), payload.scale(), payload.entity()));
    }
}
