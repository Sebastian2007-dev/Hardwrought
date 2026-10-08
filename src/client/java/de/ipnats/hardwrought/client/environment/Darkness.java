package de.ipnats.hardwrought.client.environment;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.MoonPhase;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Sections 20 to 22: true darkness, moonlight that depends on the phase, and eyes that adapt.
 *
 * <p>Everything here only changes the lightmap the client draws with. The light engine, mob
 * spawning and every other gameplay rule keep reading the real light levels, so no two players can
 * disagree about what the world is, only about how well they see it.
 *
 * <ul>
 *   <li><b>True darkness.</b> Vanilla gives the overworld a faint ambient glow that the brightness
 *   slider then lifts further, so an unlit cave is never black. Here that glow is gone: where there is
 *   neither sky nor block light, nothing is visible until the eyes have adapted. Dimensions that are
 *   meant to glow — the Nether, the End — keep their own ambient light.</li>
 *   <li><b>Moonlight.</b> The night sky light is scaled by the moon: a full moon lights as vanilla
 *   does, a new moon not at all, and cloud cover hides most of what there is.</li>
 *   <li><b>Dark adaptation.</b> In the dark the eyes open up over about half a minute; bright light
 *   closes them again within a second or two. The adapted view is dim and bluish, and it is the same
 *   whatever the brightness slider says — the slider still brightens lit places, but it cannot turn a
 *   moonless night into dusk.</li>
 * </ul>
 */
public final class Darkness {
    /** Vanilla night sky factor from {@code minecraft:day}; the moon scales this part only. */
    private static final float NIGHT_SKY_FACTOR = 0.24f;
    /** Day and dusk keyframes of the vanilla {@code sky_light_factor} track, in ticks of the day. */
    private static final int DUSK_START = 11270, DUSK_END = 13140, DAWN_START = 22860, DAWN_END = 24730;
    /** How much of the moonlight a full overcast keeps. */
    private static final float OVERCAST_MOONLIGHT = 0.3f;

    /** A dimension whose ambient light is at most this bright is meant to be dark when unlit. */
    private static final float DARK_DIMENSION_AMBIENT = 0.05f;
    /** Never exactly zero: the brightness curve in the lightmap shader divides by the brightest channel. */
    private static final float UNADAPTED_AMBIENT = 0.002f;
    /** What fully adapted eyes see in total darkness, as displayed, before tint. */
    private static final float ADAPTED_DISPLAY = 0.085f;
    /** Rod vision: grey with a blue cast. The brightest channel must be 1. */
    private static final Vector3fc SCOTOPIC_TINT = new Vector3f(0.72f, 0.8f, 1.0f);

    /** Seconds for the eyes to get most of the way to adapted, and back again. */
    private static final float ADAPT_SECONDS = 12f, RECOVER_SECONDS = 1f;
    /** Light at the eyes, as the shader computes it, below which they adapt fully and above which not at all. */
    private static final float DARK_AT = 0.02f, BRIGHT_AT = 0.2f;
    private static final float SECONDS_PER_UPDATE = 1f / 20f;

    private static float adaptation;

    private Darkness() { }

    public static void initialize() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> adaptation = 0);
    }

    /** How far the eyes have adapted to the dark, from 0 to 1. */
    public static float adaptation() { return adaptation; }

    /** Applied once per tick, after vanilla has filled in the lightmap inputs. */
    public static void apply(LightmapRenderState state, ClientLevel level, Camera camera, float partialTick) {
        if (level.dimensionType().hasSkyLight()) {
            MoonPhase phase = camera.attributeProbe().getValue(EnvironmentAttributes.MOON_PHASE, partialTick);
            state.skyFactor *= skyScale(nightFraction(level.getDefaultClockTime()), phase, level.getRainLevel(partialTick));
        }
        adaptation = adapt(adaptation, lightAtEyes(level, camera.blockPosition(), state));
        if (maxComponent(state.ambientColor) <= DARK_DIMENSION_AMBIENT) {
            float ambient = Math.max(UNADAPTED_AMBIENT, ambientFor(ADAPTED_DISPLAY * adaptation, state.brightness));
            state.ambientColor = new Vector3f(SCOTOPIC_TINT).mul(ambient);
        }
    }

    /** 0 by day, 1 by night, ramping along the same keyframes the vanilla sky light uses. */
    public static float nightFraction(long clockTime) {
        int time = (int) Math.floorMod(clockTime, 24000L);
        if (time < DAWN_END - 24000) time += 24000;
        if (time <= DUSK_START) return 0;
        if (time < DUSK_END) return (time - DUSK_START) / (float) (DUSK_END - DUSK_START);
        if (time <= DAWN_START) return 1;
        if (time < DAWN_END) return 1 - (time - DAWN_START) / (float) (DAWN_END - DAWN_START);
        return 0;
    }

    /** Share of the vanilla night sky light a moon of this phase gives. */
    static float moonlight(MoonPhase phase) {
        return switch (phase) {
            case FULL_MOON -> 1.0f;
            case WANING_GIBBOUS, WAXING_GIBBOUS -> 0.6f;
            case THIRD_QUARTER, FIRST_QUARTER -> 0.3f;
            case WANING_CRESCENT, WAXING_CRESCENT -> 0.1f;
            case NEW_MOON -> 0.0f;
        };
    }

    /** Factor on the vanilla sky factor: 1 by day, the moon's share by night. */
    public static float skyScale(float night, MoonPhase phase, float rain) {
        float moon = moonlight(phase) * Mth.lerp(rain, 1f, OVERCAST_MOONLIGHT);
        return Mth.lerp(night, 1f, moon);
    }

    /** The eyes step toward adapted in the dark and back toward unadapted in light — the latter much faster. */
    static float adapt(float current, float lightAtEyes) {
        float target = 1f - Mth.clamp((lightAtEyes - DARK_AT) / (BRIGHT_AT - DARK_AT), 0f, 1f);
        float seconds = target > current ? ADAPT_SECONDS : RECOVER_SECONDS;
        float step = 1f - (float) Math.exp(-SECONDS_PER_UPDATE / seconds);
        return current + (target - current) * step;
    }

    /** The light reaching the eyes, on the scale the lightmap shader adds up before its brightness curve. */
    private static float lightAtEyes(ClientLevel level, BlockPos eyes, LightmapRenderState state) {
        // A carried light shines in the shaders rather than as block light, so it is added here by hand.
        int blockLight = Math.max(level.getBrightness(LightLayer.BLOCK, eyes), DynamicLight.ownLevel());
        float block = shaderBrightness(blockLight) * state.blockFactor;
        float sky = shaderBrightness(level.getBrightness(LightLayer.SKY, eyes)) * state.skyFactor;
        return Math.max(block, sky);
    }

    /** {@code get_brightness} of {@code lightmap.fsh}. */
    private static float shaderBrightness(int lightLevel) {
        float level = lightLevel / 15f;
        return level / (4f - 3f * level);
    }

    /**
     * The ambient value that the shader's brightness curve turns into {@code display}. The curve only
     * ever brightens, so the answer lies between 0 and {@code display}; it is monotonic, so halving finds it.
     */
    static float ambientFor(float display, float brightness) {
        float low = 0, high = display;
        for (int i = 0; i < 20; i++) {
            float mid = (low + high) / 2;
            if (displayed(mid, brightness) < display) low = mid; else high = mid;
        }
        return (low + high) / 2;
    }

    /** The shader's {@code mix(color, notGamma(color), BrightnessFactor)} for the brightest channel. */
    private static float displayed(float value, float brightness) {
        float inverted = 1f - value;
        float curved = 1f - inverted * inverted * inverted * inverted;
        return Mth.lerp(brightness, value, curved);
    }

    private static float maxComponent(Vector3fc color) {
        return Math.max(color.x(), Math.max(color.y(), color.z()));
    }
}
