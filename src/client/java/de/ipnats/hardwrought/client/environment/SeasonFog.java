package de.ipnats.hardwrought.client.environment;

import de.ipnats.hardwrought.environment.SeasonState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.renderer.fog.FogData;

/**
 * The client half of the autumn fog: taking the server's figure and drawing it.
 *
 * <p>How thick the fog is was decided by the server, so that two players standing together see the
 * same morning. What happens here is only the drawing of it, and one piece of kindness in that:
 * the thickness is eased towards its target over a couple of seconds rather than snapped to it,
 * because the packet arrives twice a second and a fog that stepped would be worse than no fog.
 */
public final class SeasonFog {
    /** The furthest a player can see when the fog is at its thickest, in blocks. */
    private static final float THICKEST_VIEW = 26.0f;
    /** How much of the way to the target the drawn value moves each frame. */
    private static final float EASE = 0.02f;

    private static float drawn;

    private SeasonFog() { }

    public static void initialize() {
        // The season packet itself is taken by SeasonClient, which also owns the repaint it causes.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> drawn = 0);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> drawn = 0);
    }

    /** How thick the fog is being drawn at this instant, 0 to 1. */
    public static float drawn() {
        return drawn;
    }

    /**
     * Pull vanilla's fog distances in by however much fog there is. Both the environmental fog and
     * the render-distance fog are shortened: the first is what the player sees close to, the second
     * is what stops the far terrain simply ending in mid-air.
     */
    public static void close(FogData fog, float renderDistanceInBlocks) {
        float target = SeasonState.fog();
        drawn += (target - drawn) * EASE;
        if (drawn < 0.01f) {
            drawn = Math.max(0, drawn);
            return;
        }
        float reach = Math.max(THICKEST_VIEW, renderDistanceInBlocks * (1.0f - drawn));
        // Never push the fog further out than vanilla had it: this closes in, it never opens up.
        fog.environmentalEnd = Math.min(fog.environmentalEnd, reach);
        fog.environmentalStart = Math.min(fog.environmentalStart, fog.environmentalEnd * 0.15f);
        fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, reach);
        fog.renderDistanceStart = Math.min(fog.renderDistanceStart, fog.renderDistanceEnd * 0.55f);
        fog.skyEnd = Math.min(fog.skyEnd, reach);
        fog.cloudEnd = Math.min(fog.cloudEnd, reach);
    }
}
