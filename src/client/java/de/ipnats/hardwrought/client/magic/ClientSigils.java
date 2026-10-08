package de.ipnats.hardwrought.client.magic;

import de.ipnats.hardwrought.client.fx.FxClient;
import de.ipnats.hardwrought.client.fx.FxDecal;
import de.ipnats.hardwrought.client.fx.FxEngine;
import de.ipnats.hardwrought.client.fx.FxLight;
import de.ipnats.hardwrought.client.fx.FxParticle;
import de.ipnats.hardwrought.magic.Rune;
import de.ipnats.hardwrought.magic.SigilBlockEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sigils as the player sees them: the drawing itself, laid on the ground in glowing ink just as it
 * was drawn, so that a sigil can be read like a diagram (sigil specification section 52); and above
 * a sigil whose element hovers, the element itself — a flame, sparks, a mote of light.
 */
final class ClientSigils {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    /** How far apart the dots of ink lie along a line, in blocks. */
    private static final double SPACING = 0.07;
    /** Ink lasts this long before it is laid again; a sigil outlives it by far, so it is renewed. */
    private static final int INK_LIFE = 20 * 60 * 10;

    private record Look(List<float[]> lines, List<FxDecal> ink, FxLight light, long laid) {
        void kill() {
            ink.forEach(FxDecal::kill);
            light.kill();
        }
    }

    private static final Map<BlockPos, Look> LOOKS = new HashMap<>();

    private ClientSigils() { }

    static void initialize() {
        SigilBlockEntity.clientTicker = ClientSigils::tick;
        SigilBlockEntity.clientRemoved = sigil -> {
            Look look = LOOKS.remove(sigil.getBlockPos());
            if (look != null) look.kill();
        };
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LOOKS.clear());
    }

    private static void tick(SigilBlockEntity sigil) {
        long ticks = sigil.getLevel() == null ? 0 : sigil.getLevel().getGameTime();
        BlockPos pos = sigil.getBlockPos().immutable();
        Look look = LOOKS.get(pos);
        // Laid anew when its drawing arrives or changes, and before the ink would fade.
        if (look == null || look.lines() != sigil.lines() || ticks - look.laid() > INK_LIFE - 200) {
            if (look != null) look.kill();
            look = lay(sigil, ticks);
            LOOKS.put(pos, look);
        }
        if (sigil.element() == null || ticks % 2 != 0) return;
        FxEngine fx = FxClient.engine();
        Vec3 hover = sigil.hoverPoint();
        if (sigil.directed() || sigil.waiting()) {
            // Ready, not burning: a faint glint where its magic gathers.
            if (ticks % 8 == 0) {
                fx.particle(hover.add(unit(fx.random()).scale(0.15))).life(14).size(0.06f, 0).color(sigil.color())
                        .glint(0.9f).twinkle(0.5f).hot(1);
            }
            return;
        }
        hovering(fx, sigil.element(), sigil.inverted(), sigil.color(), hover, sigil.shape());
    }

    /** The drawing, dot by dot along every line, on the ground, the right way round for whoever drew it. */
    private static Look lay(SigilBlockEntity sigil, long ticks) {
        FxEngine fx = FxClient.engine();
        Vec3 center = Vec3.atBottomCenterOf(sigil.getBlockPos()).add(0, 0.03, 0);
        Vec3 forward = new Vec3(sigil.forwardX(), 0, sigil.forwardZ());
        forward = forward.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        int color = sigil.color();
        float radius = SigilBlockEntity.RADIUS;
        List<FxDecal> ink = new ArrayList<>();
        for (float[] line : sigil.lines()) {
            Vec3 previous = null;
            for (int i = 0; i + 1 < line.length; i += 2) {
                Vec3 point = center.add(right.scale(line[i] * radius)).add(forward.scale(-line[i + 1] * radius));
                if (previous == null) {
                    ink.add(dot(fx, point, color));
                } else {
                    double length = previous.distanceTo(point);
                    int steps = Math.max(1, (int) Math.ceil(length / SPACING));
                    for (int s = 1; s <= steps; s++) ink.add(dot(fx, previous.lerp(point, s / (double) steps), color));
                }
                previous = point;
            }
        }
        FxLight light = fx.light(center.add(0, 0.6, 0), color, sigil.directed() || sigil.waiting() ? 3.5f : 6, INK_LIFE)
                .intensity(0.8f);
        return new Look(sigil.lines(), ink, light, ticks);
    }

    private static FxDecal dot(FxEngine fx, Vec3 at, int color) {
        return fx.decal(FxDecal.Kind.GLOW, at, UP).radius(0.05f).life(INK_LIFE).color(color).alpha(0.85f).param(0)
                .fade(0.01f, 0.995f);
    }

    /** The element itself, hovering above the sigil. */
    private static void hovering(FxEngine fx, Rune element, boolean inverted, int color, Vec3 at, de.ipnats.hardwrought.magic.SigilDesign.Shape shape) {
        RandomSource r = fx.random();
        float size = shape == de.ipnats.hardwrought.magic.SigilDesign.Shape.SPHERE ? 1.6f : 1;
        int hot = lighter(color);
        if (inverted) {
            fx.particle(at.add(unit(r).scale(0.3 * size))).velocity(0, -0.01, 0).life(18).size(0.12f * size, 0).color(hot, color)
                    .glint(0.6f).hot(0.6f);
            return;
        }
        switch (element) {
            case FIRE -> {
                for (int i = 0; i < 3; i++) {
                    Vec3 v = unit(r).scale(0.01);
                    fx.particle(at.add(unit(r).scale(0.12 * size))).velocity(v.x, 0.035 + r.nextFloat() * 0.03, v.z)
                            .life(10 + r.nextInt(8)).size(0.26f * size, 0).color(0xFFE6A0, color).hot(1).drag(0.92f);
                }
                if (r.nextFloat() < 0.3f) {
                    fx.particle(at).shape(FxParticle.Shape.SPARK).velocity(r.nextGaussian() * 0.03, 0.06, r.nextGaussian() * 0.03)
                            .life(16).size(0.03f).color(0xFFFFFF, color).gravity(0.01f).stretch(2).hot(1);
                }
            }
            case WATER -> fx.particle(at.add(unit(r).scale(0.3 * size))).velocity(0, -0.02, 0).life(20).size(0.09f * size, 0)
                    .color(hot, color).glint(0.7f).gravity(0.004f);
            case WIND -> {
                Vec3 start = at.add(unit(r).scale(0.6 * size));
                fx.particle(start).attract(at.x, at.y, at.z, 0.01f, 0.06f).life(20).size(0.07f, 0).color(hot, color).drag(0.9f);
            }
            case EARTH -> fx.particle(at.add(unit(r).scale(0.35 * size))).attract(at.x, at.y, at.z, 0.003f, 0.03f).life(26)
                    .size(0.1f * size, 0.04f).color(color, 0x4A3A28).drag(0.95f);
            case LIGHT -> fx.particle(at.add(unit(r).scale(0.25 * size))).life(18).size(0.12f * size, 0).color(0xFFFFFF, color)
                    .glint(1).twinkle(0.5f).hot(1);
            case DARK -> fx.particle(at.add(unit(r).scale(0.2 * size))).shape(FxParticle.Shape.SMOKE).velocity(0, 0.005, 0)
                    .life(24).size(0.25f * size, 0.5f * size).color(0x140A1E, 0x000000).alpha(0.7f).fade(0.2f, 0.5f);
            case LIGHTNING -> {
                if (r.nextFloat() < 0.6f) {
                    Vec3 v = unit(r).scale(0.12);
                    fx.particle(at.add(unit(r).scale(0.1 * size))).shape(FxParticle.Shape.SPARK).velocity(v.x, v.y, v.z)
                            .life(4 + r.nextInt(4)).size(0.04f).color(0xFFFFFF, color).stretch(3).hot(1.2f);
                }
            }
        }
    }

    private static int lighter(int color) {
        int red = (color >> 16) & 255, green = (color >> 8) & 255, blue = color & 255;
        return ((red + (255 - red) / 2) << 16) | ((green + (255 - green) / 2) << 8) | (blue + (255 - blue) / 2);
    }

    private static Vec3 unit(RandomSource r) {
        Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
        return v.lengthSqr() < 1e-6 ? UP : v.normalize();
    }
}
