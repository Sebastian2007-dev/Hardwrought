package de.ipnats.hardwrought.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the engine's state into quads once per frame. The quads are built during extraction, with
 * the camera of that frame and the motion interpolated to its partial tick, and submitted as custom
 * geometry during the main pass. Positions are relative to the camera, as vanilla expects.
 */
public final class FxRenderer {
    /** Effects farther away than this are not drawn at all. */
    private static final double MAX_DISTANCE = 192;

    /** The vertices of one render type for the current frame: x y z colour u v p1 p2. */
    static final class Quads {
        private static final int FLOATS = 8;
        float[] data = new float[FLOATS * 4 * 256];
        int size;

        void vertex(float x, float y, float z, int color, float u, float v, float p1, float p2) {
            if (size + FLOATS > data.length) data = Arrays.copyOf(data, data.length * 2);
            data[size] = x;
            data[size + 1] = y;
            data[size + 2] = z;
            data[size + 3] = Float.intBitsToFloat(color);
            data[size + 4] = u;
            data[size + 5] = v;
            data[size + 6] = p1;
            data[size + 7] = p2;
            size += FLOATS;
        }

        void write(PoseStack.Pose pose, VertexConsumer consumer) {
            for (int i = 0; i < size; i += FLOATS) {
                consumer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                        .setColor(Float.floatToRawIntBits(data[i + 3]))
                        .setUv(data[i + 4], data[i + 5])
                        .setUv3(data[i + 6], data[i + 7]);
            }
        }
    }

    private static final Map<RenderType, Quads> QUADS = new LinkedHashMap<>();
    /** What bends the view this frame; drawn by {@link FxPost} into the distortion field, not the scene. */
    private static final Quads DISTORT = new Quads();

    static {
        // Smoke first, so the light of the same effect is drawn over it rather than behind it.
        for (RenderType type : List.of(FxRenderTypes.SMOKE, FxRenderTypes.RUNE, FxRenderTypes.RING,
                FxRenderTypes.SHIELD, FxRenderTypes.SPHERE, FxRenderTypes.BEAM, FxRenderTypes.GLOW)) {
            QUADS.put(type, new Quads());
        }
    }

    private static Vec3 camera = Vec3.ZERO;
    private static final Vector3f right = new Vector3f(), up = new Vector3f(), forward = new Vector3f();

    private FxRenderer() { }

    static void extract(LevelExtractionContext context) {
        QUADS.values().forEach(quads -> quads.size = 0);
        DISTORT.size = 0;
        FxEngine fx = FxClient.engine();
        if (fx.level() == null) {
            FxLighting.clear();
            return;
        }
        Camera cam = context.camera();
        camera = cam.position();
        cam.rotation().transform(1, 0, 0, right);
        cam.rotation().transform(0, 1, 0, up);
        Vector3fc look = cam.forwardVector();
        forward.set(look);
        float partial = context.deltaTracker().getGameTimeDeltaPartialTick(false);
        FxLighting.extract(fx, camera, partial);

        for (FxParticle particle : fx.particles) particle(particle, partial);
        for (FxBeam beam : fx.beams) beam(beam, partial);
        for (FxDecal decal : fx.decals) decal(decal, partial);
    }

    static void submit(LevelRenderContext context) {
        for (Map.Entry<RenderType, Quads> entry : QUADS.entrySet()) {
            Quads quads = entry.getValue();
            if (quads.size == 0) continue;
            context.submitNodeCollector().submitCustomGeometry(context.poseStack(), entry.getKey(), quads::write);
        }
    }

    static Map<RenderType, Quads> quads() {
        return QUADS;
    }

    static Quads distortion() {
        return DISTORT;
    }

    // ---------------------------------------------------------------------------------------------

    static float envelope(float t, float fadeIn, float fadeOut) {
        float a = fadeIn > 0 && t < fadeIn ? t / fadeIn : 1;
        if (t > fadeOut && fadeOut < 1) a *= Math.max(0, (1 - t) / (1 - fadeOut));
        return Mth.clamp(a, 0, 1);
    }

    private static int withAlpha(int rgb, float alpha) {
        return ARGB.color(Mth.clamp(Math.round(alpha * 255), 0, 255), rgb);
    }

    /** False when the point is far away or well behind the camera. */
    private static boolean visible(float x, float y, float z, float radius) {
        if (x * x + y * y + z * z > MAX_DISTANCE * MAX_DISTANCE) return false;
        return x * forward.x() + y * forward.y() + z * forward.z() > -radius - 1;
    }

    private static void particle(FxParticle p, float partial) {
        float t = Mth.clamp((p.age + partial) / p.life, 0, 1);
        float x = (float) (Mth.lerp(partial, p.prevX, p.x) - camera.x);
        float y = (float) (Mth.lerp(partial, p.prevY, p.y) - camera.y);
        float z = (float) (Mth.lerp(partial, p.prevZ, p.z) - camera.z);
        float size = Mth.lerp(t, p.sizeStart, p.sizeEnd);
        if (size <= 0.001f || !visible(x, y, z, size * 4)) return;

        float alpha = p.alpha * envelope(t, p.fadeIn, p.fadeOut);
        if (p.twinkle > 0) {
            alpha *= 1 - p.twinkle * (0.5f + 0.5f * Mth.sin(p.seed * 13 + (p.age + partial) * 1.9f));
        }
        if (alpha <= 0.003f) return;
        int rgb = ARGB.srgbLerp(t, p.colorStart, p.colorEnd);

        switch (p.shape) {
            case GLOW -> billboard(QUADS.get(FxRenderTypes.GLOW), x, y, z, size, withAlpha(rgb, alpha), p.hot, p.glint);
            case SMOKE -> {
                int lit = ARGB.scaleRGB(rgb, Math.min(1, Math.max(p.brightness, p.emissive * (1 - t) * (1 - t))));
                float dissolve = Math.max(0, (t - 0.35f) / 0.65f) * 0.95f;
                billboard(QUADS.get(FxRenderTypes.SMOKE), x, y, z, size, withAlpha(lit, alpha), p.seed % 10, dissolve);
            }
            case HEAT -> billboard(DISTORT, x, y, z, size, withAlpha(0xFFFFFF, alpha), p.hot, 1);
            case SPARK -> {
                float vx = (float) p.vx * p.stretch, vy = (float) p.vy * p.stretch, vz = (float) p.vz * p.stretch;
                float length = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (length < size * 0.5f) {
                    billboard(QUADS.get(FxRenderTypes.GLOW), x, y, z, size, withAlpha(rgb, alpha), p.hot, 0);
                } else {
                    streak(QUADS.get(FxRenderTypes.GLOW), x, y, z, vx, vy, vz, length, size, withAlpha(rgb, alpha), p.hot);
                }
            }
        }
    }

    private static void billboard(Quads quads, float x, float y, float z, float size, int color, float p1, float p2) {
        float rx = right.x * size, ry = right.y * size, rz = right.z * size;
        float ux = up.x * size, uy = up.y * size, uz = up.z * size;
        quads.vertex(x - rx - ux, y - ry - uy, z - rz - uz, color, -1, -1, p1, p2);
        quads.vertex(x - rx + ux, y - ry + uy, z - rz + uz, color, -1, 1, p1, p2);
        quads.vertex(x + rx + ux, y + ry + uy, z + rz + uz, color, 1, 1, p1, p2);
        quads.vertex(x + rx - ux, y + ry - uy, z + rz - uz, color, 1, -1, p1, p2);
    }

    /** A quad from the particle back along its motion, turned to face the camera around that line. */
    private static void streak(Quads quads, float x, float y, float z, float vx, float vy, float vz, float length,
                               float size, int color, float hot) {
        float ax = vx / length, ay = vy / length, az = vz / length;
        float cx = x - vx * 0.5f, cy = y - vy * 0.5f, cz = z - vz * 0.5f;
        // Across the streak: perpendicular to both its direction and the line of sight.
        float sx = ay * cz - az * cy, sy = az * cx - ax * cz, sz = ax * cy - ay * cx;
        float s = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (s < 1.0e-5f) {
            billboard(quads, x, y, z, size, color, hot, 0);
            return;
        }
        sx = sx / s * size;
        sy = sy / s * size;
        sz = sz / s * size;
        float half = length * 0.5f + size;
        float lx = ax * half, ly = ay * half, lz = az * half;
        quads.vertex(cx - lx - sx, cy - ly - sy, cz - lz - sz, color, -1, -1, hot, 0);
        quads.vertex(cx - lx + sx, cy - ly + sy, cz - lz + sz, color, -1, 1, hot, 0);
        quads.vertex(cx + lx + sx, cy + ly + sy, cz + lz + sz, color, 1, 1, hot, 0);
        quads.vertex(cx + lx - sx, cy + ly - sy, cz + lz - sz, color, 1, -1, hot, 0);
    }

    private static void beam(FxBeam beam, float partial) {
        float t = Mth.clamp((beam.age + partial) / beam.life, 0, 1);
        float alpha = beam.alpha * envelope(t, beam.fadeIn, beam.fadeOut);
        if (alpha <= 0.003f) return;
        Quads quads = QUADS.get(FxRenderTypes.BEAM);
        if (!beam.lightning) {
            ribbon(quads, List.of(beam.from, beam.to), beam.width, withAlpha(beam.color, alpha), beam.turbulence, beam.speed);
            return;
        }
        // A bolt flickers: every re-fork it flares up again and then dims.
        float flare = 1 - (beam.age % beam.reshape + partial) / beam.reshape * 0.45f;
        for (int i = 0; i < beam.paths.size(); i++) {
            boolean trunk = i == 0;
            float width = beam.width * (trunk ? 1 : 0.55f);
            float a = alpha * flare * (trunk ? 1 : 0.7f);
            ribbon(quads, beam.paths.get(i), width, withAlpha(beam.color, a), beam.turbulence, beam.speed);
        }
    }

    private static void ribbon(Quads quads, List<Vec3> points, float width, int color, float p1, float p2) {
        float along = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 a = points.get(i), b = points.get(i + 1);
            float ax = (float) (a.x - camera.x), ay = (float) (a.y - camera.y), az = (float) (a.z - camera.z);
            float bx = (float) (b.x - camera.x), by = (float) (b.y - camera.y), bz = (float) (b.z - camera.z);
            float dx = bx - ax, dy = by - ay, dz = bz - az;
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0e-4f) continue;
            float mx = (ax + bx) * 0.5f, my = (ay + by) * 0.5f, mz = (az + bz) * 0.5f;
            float sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
            float s = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (s < 1.0e-5f) {
                along += length;
                continue;
            }
            sx = sx / s * width;
            sy = sy / s * width;
            sz = sz / s * width;
            // Overlap the segments a little, so the joints of a bent path leave no gaps.
            float ex = dx / length * width * 0.5f, ey = dy / length * width * 0.5f, ez = dz / length * width * 0.5f;
            float u0 = along, u1 = along + length;
            quads.vertex(ax - ex - sx, ay - ey - sy, az - ez - sz, color, u0, -1, p1, p2);
            quads.vertex(ax - ex + sx, ay - ey + sy, az - ez + sz, color, u0, 1, p1, p2);
            quads.vertex(bx + ex + sx, by + ey + sy, bz + ez + sz, color, u1, 1, p1, p2);
            quads.vertex(bx + ex - sx, by + ey - sy, bz + ez - sz, color, u1, -1, p1, p2);
            along = u1;
        }
    }

    /**
     * A sphere of quads, banded by latitude and longitude. Each corner carries its own longitude and
     * latitude, from which {@code fx_sphere.fsh} rebuilds the surface's normal.
     */
    private static void sphere(Quads quads, float x, float y, float z, float radius, int color, float p1, float p2) {
        int bands = 14, segments = 28;
        for (int b = 0; b < bands; b++) {
            float lat0 = (float) (-Math.PI / 2 + Math.PI * b / bands), lat1 = (float) (-Math.PI / 2 + Math.PI * (b + 1) / bands);
            for (int s = 0; s < segments; s++) {
                float lon0 = (float) (Math.PI * 2 * s / segments), lon1 = (float) (Math.PI * 2 * (s + 1) / segments);
                sphereVertex(quads, x, y, z, radius, lon0, lat0, color, p1, p2);
                sphereVertex(quads, x, y, z, radius, lon1, lat0, color, p1, p2);
                sphereVertex(quads, x, y, z, radius, lon1, lat1, color, p1, p2);
                sphereVertex(quads, x, y, z, radius, lon0, lat1, color, p1, p2);
            }
        }
    }

    private static void sphereVertex(Quads quads, float x, float y, float z, float radius, float lon, float lat, int color,
                                     float p1, float p2) {
        float c = (float) Math.cos(lat);
        quads.vertex(x + radius * c * (float) Math.cos(lon), y + radius * (float) Math.sin(lat),
                z + radius * c * (float) Math.sin(lon), color, lon, lat, p1, p2);
    }

    private static void decal(FxDecal decal, float partial) {
        float t = Mth.clamp((decal.age + partial) / decal.life, 0, 1);
        float g = decal.grow > 0 ? Mth.clamp((decal.age + partial) / decal.grow, 0, 1) : t;
        float grow = decal.easeOut ? 1 - (1 - g) * (1 - g) * (1 - g) : g;
        float radius = Mth.lerp(grow, decal.radiusStart, decal.radiusEnd);
        float alpha = decal.alpha * envelope(t, decal.fadeIn, decal.fadeOut);
        if (radius <= 0.001f || alpha <= 0.003f) return;
        double cx = Mth.lerp(partial, decal.prevCenter.x, decal.center.x);
        double cy = Mth.lerp(partial, decal.prevCenter.y, decal.center.y);
        double cz = Mth.lerp(partial, decal.prevCenter.z, decal.center.z);
        float x = (float) (cx - camera.x), y = (float) (cy - camera.y), z = (float) (cz - camera.z);
        if (!visible(x, y, z, radius)) return;
        // A sphere seen from inside cannot be drawn as a disc facing the camera; the screen glow stands in for it.
        if (decal.kind == FxDecal.Kind.SHIELD && x * x + y * y + z * z < radius * radius * 1.1f) return;
        int color = withAlpha(decal.color, alpha);
        if (decal.kind == FxDecal.Kind.SPHERE) {
            sphere(QUADS.get(FxRenderTypes.SPHERE), x, y, z, radius, color, decal.param, decal.seed * 0.37f);
            return;
        }

        float p1 = decal.param, p2 = decal.seed;
        RenderType type = null;
        switch (decal.kind) {
            case WARP_RING -> p2 = 0;
            case WARP_HEAT -> p2 = 1;
            case WARP_SWIRL -> p2 = 2;
            case WARP_LENS -> p2 = 3;
            case RING -> type = FxRenderTypes.RING;
            case RUNE -> {
                type = FxRenderTypes.RUNE;
                p1 = decal.spin * (decal.age + partial);
                float drawn = decal.reveal <= 0 ? 0.999f : Math.min(0.999f, (decal.age + partial) / decal.reveal);
                p2 = decal.seed + drawn;
            }
            case SHIELD -> {
                type = FxRenderTypes.SHIELD;
                p2 = decal.seed * 0.37f;
            }
            default -> {
                type = FxRenderTypes.GLOW;
                p2 = 0;
            }
        }
        Quads quads = decal.kind.warps() ? DISTORT : QUADS.get(type);
        if (decal.normal == null) {
            billboard(quads, x, y, z, radius, color, p1, p2);
            return;
        }
        // Two axes in the decal's plane.
        Vec3 n = decal.normal;
        Vec3 helper = Math.abs(n.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 a = n.cross(helper).normalize().scale(radius);
        Vec3 b = n.cross(a).normalize().scale(radius);
        float ax = (float) a.x, ay = (float) a.y, az = (float) a.z, bx = (float) b.x, by = (float) b.y, bz = (float) b.z;
        quads.vertex(x - ax - bx, y - ay - by, z - az - bz, color, -1, -1, p1, p2);
        quads.vertex(x - ax + bx, y - ay + by, z - az + bz, color, -1, 1, p1, p2);
        quads.vertex(x + ax + bx, y + ay + by, z + az + bz, color, 1, 1, p1, p2);
        quads.vertex(x + ax - bx, y + ay - by, z + az - bz, color, 1, -1, p1, p2);
    }
}
