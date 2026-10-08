package de.ipnats.hardwrought.client.fx;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

/**
 * What an effect does to the view itself: the camera shakes, the screen flashes, and the edges of
 * the view glow in the effect's colour. All of it runs on real time, so it stays smooth whatever
 * the frame rate.
 */
public final class FxScreen {
    private static final Identifier VIGNETTE = Identifier.withDefaultNamespace("textures/misc/vignette.png");
    /** The vanilla vignette texture, added onto the screen instead of darkening it. */
    private static final RenderPipeline GLOW_VIGNETTE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(Hardwrought.id("pipeline/fx_glow_vignette"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                    .build());
    /** Degrees the view turns at the strongest shake. */
    private static final float MAX_YAW = 4.5f, MAX_PITCH = 3.5f;

    private static float trauma;
    private static long lastTraumaUpdate = Util.getMillis();

    private static final Pulse flash = new Pulse();
    private static final Pulse vignette = new Pulse();
    private static final Pulse aberration = new Pulse();
    private static final Pulse saturation = new Pulse();

    /** A colour that flares up and fades out over a given time. */
    private static final class Pulse {
        int color;
        float strength;
        long start;
        long duration = 1;

        void trigger(int color, float strength, int millis) {
            // A weaker pulse does not cut short a stronger one that is still bright.
            if (current() > strength) return;
            this.color = color;
            this.strength = strength;
            this.start = Util.getMillis();
            this.duration = Math.max(1, millis);
        }

        float current() {
            float t = (Util.getMillis() - start) / (float) duration;
            if (t >= 1) return 0;
            // A fast rise, then a long soft tail.
            float rise = Math.min(1, t * 12);
            return strength * rise * (1 - t) * (1 - t);
        }
    }

    private FxScreen() { }

    /** Adds shake; strengths add up to at most 1. */
    public static void shake(float amount) {
        decay();
        trauma = Mth.clamp(trauma + amount, 0, 1);
    }

    /** Shake that fades with the distance from the camera. */
    public static void shake(Vec3 at, float amount, float radius) {
        shake(amount * proximity(at, radius));
    }

    public static void flash(int color, float strength, int millis) {
        flash.trigger(color, Mth.clamp(strength, 0, 1), millis);
    }

    public static void flash(Vec3 at, int color, float strength, int millis, float radius) {
        float near = proximity(at, radius);
        if (near > 0) flash(color, strength * near, millis);
    }

    public static void vignette(int color, float strength, int millis) {
        vignette.trigger(color, Mth.clamp(strength, 0, 1), millis);
    }

    public static void vignette(Vec3 at, int color, float strength, int millis, float radius) {
        float near = proximity(at, radius);
        if (near > 0) vignette(color, strength * near, millis);
    }

    /** Splits the whole view into its colours for a moment, strongest at the edges. */
    public static void aberration(float strength, int millis) {
        aberration.trigger(0, Mth.clamp(strength, 0, 2), millis);
    }

    public static void aberration(Vec3 at, float strength, int millis, float radius) {
        float near = proximity(at, radius);
        if (near > 0) aberration(strength * near, millis);
    }

    /** Makes the colours of the view more vivid for a moment. */
    public static void saturation(float strength, int millis) {
        saturation.trigger(0, Mth.clamp(strength, 0, 1), millis);
    }

    public static void saturation(Vec3 at, float strength, int millis, float radius) {
        float near = proximity(at, radius);
        if (near > 0) saturation(strength * near, millis);
    }

    static float aberration() {
        return aberration.current();
    }

    static float saturation() {
        return saturation.current();
    }

    /** 1 at the camera, falling to 0 at the given radius. */
    public static float proximity(Vec3 at, float radius) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameRenderer == null) return 0;
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();
        float t = 1 - (float) (camera.distanceTo(at) / radius);
        return Mth.clamp(t, 0, 1);
    }

    public static void clear() {
        trauma = 0;
        flash.strength = 0;
        vignette.strength = 0;
        aberration.strength = 0;
        saturation.strength = 0;
    }

    private static void decay() {
        long now = Util.getMillis();
        trauma = Math.max(0, trauma - (now - lastTraumaUpdate) / 1000f * 1.1f);
        lastTraumaUpdate = now;
    }

    public static boolean shaking() {
        decay();
        return trauma > 0.001f;
    }

    /** The shake as yaw and pitch offsets in degrees; squared, so small shakes stay subtle. */
    public static float shakeYaw() {
        float s = trauma * trauma;
        float t = Util.getMillis() / 1000f;
        return MAX_YAW * s * (Mth.sin(t * 37.1f) * 0.55f + Mth.sin(t * 61.7f + 1.3f) * 0.3f + Mth.sin(t * 97.3f + 2.9f) * 0.15f);
    }

    public static float shakePitch() {
        float s = trauma * trauma;
        float t = Util.getMillis() / 1000f;
        return MAX_PITCH * s * (Mth.sin(t * 43.9f + 0.7f) * 0.55f + Mth.sin(t * 71.3f + 2.1f) * 0.3f + Mth.sin(t * 89.9f) * 0.15f);
    }

    static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        float glow = vignette.current();
        if (glow > 0.004f) {
            int color = ARGB.scaleRGB(ARGB.opaque(vignette.color), glow);
            graphics.blit(GLOW_VIGNETTE, VIGNETTE, 0, 0, 0, 0, width, height, width, height, color);
        }
        float white = flash.current();
        if (white > 0.004f) {
            graphics.fill(0, 0, width, height, ARGB.color(Math.round(white * 255), flash.color));
        }
    }
}
