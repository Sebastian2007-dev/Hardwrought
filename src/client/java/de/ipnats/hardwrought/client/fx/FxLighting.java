package de.ipnats.hardwrought.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import de.ipnats.hardwrought.client.environment.DynamicLight;
import de.ipnats.hardwrought.client.environment.PlacedLights;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The dynamic lights of the current frame, as the shaders see them: appended to vanilla's global
 * uniform buffer (see {@code GlobalSettingsUniformMixin} and {@code minecraft:globals.glsl}).
 *
 * <p>A placed light is sent with a negative radius: the shader then checks it against vanilla's
 * block light, so that it does not shine through walls (see {@code hardwrought:dynamic_light.glsl}).
 */
public final class FxLighting {
    public static final int MAX_LIGHTS = 64;
    /** An ivec4 of counts, then a vec4 of position and radius and a vec4 of colour per light. */
    public static final int UBO_SIZE = 16 + MAX_LIGHTS * 16 * 2;
    private static final ByteBuffer data = MemoryUtil.memAlloc(UBO_SIZE);

    private static final float[] positions = new float[MAX_LIGHTS * 4];
    private static final float[] colors = new float[MAX_LIGHTS * 4];
    private static int count;
    /** Placed lights stand in for most of vanilla's block light; the shader knows this as HW_PLACED_INTENSITY. */
    private static final float PLACED_INTENSITY = 1.2f;

    /** A light this frame. The weight leaves out the flicker, so the order does not change with every breath of a flame. */
    private record Candidate(float x, float y, float z, float radius, int color, float intensity,
                             float weight, boolean placed) { }

    private FxLighting() { }

    /** Picks the lights that matter most to this frame: bright, large and close to the camera. */
    static void extract(FxEngine fx, Vec3 camera, float partial) {
        count = 0;
        List<DynamicLight.Carried> carried = DynamicLight.carried();
        List<PlacedLights.Placed> placed = PlacedLights.placed();
        if (fx.lights.isEmpty() && carried.isEmpty() && placed.isEmpty()) return;
        float seconds = Util.getMillis() / 1000f;
        List<Candidate> candidates = new ArrayList<>();
        for (DynamicLight.Carried light : carried) {
            if (light.carrier().isRemoved()) continue;
            Vec3 at = light.position(partial).subtract(camera);
            float radius = light.level() * 1.15f;
            float steady = 1.6f;
            float intensity = steady * (light.flame() ? flicker(seconds, light.carrier().getId()) : 1);
            candidates.add(new Candidate((float) at.x, (float) at.y, (float) at.z, radius, light.color(),
                    intensity, weight(steady, radius, at.length()), false));
        }
        for (PlacedLights.Placed light : placed) {
            Vec3 at = light.position().subtract(camera);
            double distance = at.length();
            // Fades out towards the edge of the search, so a light leaving it does not blink out.
            float fade = (float) Math.clamp((PlacedLights.RANGE - distance) / 6, 0, 1);
            if (fade <= 0) continue;
            float radius = light.level();
            float steady = PLACED_INTENSITY * fade;
            float intensity = steady * (light.flame() ? flicker(seconds, light.seed()) : 1);
            candidates.add(new Candidate((float) at.x, (float) at.y, (float) at.z, radius, light.color(),
                    intensity, weight(steady, radius, distance), true));
        }
        for (FxLight light : fx.lights) {
            float intensity = light.currentIntensity(partial, seconds);
            if (intensity <= 0.01f || light.radius <= 0.1f) continue;
            Vec3 at = light.position(partial).subtract(camera);
            double distance = at.length();
            if (distance > light.radius + 96) continue;
            candidates.add(new Candidate((float) at.x, (float) at.y, (float) at.z, light.radius, light.color,
                    intensity, weight(intensity, light.radius, distance), false));
        }
        candidates.sort(Comparator.comparingDouble(candidate -> -candidate.weight));
        for (Candidate light : candidates) {
            if (count >= MAX_LIGHTS) break;
            put(light);
        }
    }

    private static float weight(float intensity, float radius, double distance) {
        return (float) (intensity * radius / (1 + Math.max(0, distance - radius)));
    }

    private static void put(Candidate light) {
        int i = count * 4;
        positions[i] = light.x;
        positions[i + 1] = light.y;
        positions[i + 2] = light.z;
        positions[i + 3] = light.placed ? -light.radius : light.radius;
        colors[i] = ARGB.red(light.color) / 255f;
        colors[i + 1] = ARGB.green(light.color) / 255f;
        colors[i + 2] = ARGB.blue(light.color) / 255f;
        colors[i + 3] = light.intensity;
        count++;
    }

    /** A flame breathes: slow swells with a quick flutter on top. */
    private static float flicker(float seconds, int seed) {
        return 0.9f + 0.06f * (float) Math.sin(seconds * 7.3 + seed)
                + 0.04f * (float) Math.sin(seconds * 23.1 + seed * 3);
    }

    public static void clear() {
        count = 0;
    }

    /** Writes the lights into the global uniform buffer, after vanilla's own part of it. */
    public static void write(GpuBuffer buffer, int offset) {
        data.clear();
        data.putInt(count).putInt(0).putInt(0).putInt(0);
        data.asFloatBuffer().put(positions).put(colors);
        data.position(UBO_SIZE);
        data.flip();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(offset, UBO_SIZE), data);
    }
}
