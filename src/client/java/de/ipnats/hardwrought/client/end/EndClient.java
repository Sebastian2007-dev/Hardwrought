package de.ipnats.hardwrought.client.end;

import de.ipnats.hardwrought.client.fx.FxDecal;
import de.ipnats.hardwrought.client.fx.FxEngine;
import de.ipnats.hardwrought.client.fx.FxParticle;
import de.ipnats.hardwrought.end.EndRules;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** The End on the client: whether building is forbidden (see {@link EndRules}), and the floor of light it is entered on. */
public final class EndClient {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int VIOLET = 0xFFB25CFF, PALE = 0xFFF0F4FF;
    private static final RandomSource RANDOM = RandomSource.create();

    private EndClient() { }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(EndRules.State.TYPE, (payload, context) -> EndRules.clientNoBuilding = payload.noBuilding());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> EndRules.clientNoBuilding = false);
    }

    /**
     * What the floor of light has about it besides its own plate ({@code VoidLight}): a great sigil turning
     * slowly beneath it, seen round its edges from above and whole from below, its light on whoever stands
     * on it, motes rising along its edges so it is plain where it ends — and in its last seconds sparks
     * falling away from it into the void.
     */
    public static void platform(FxEngine fx, Vec3 top, int color, float reach, int ticks) {
        int c = 0xFF000000 | color;
        int life = Math.max(20, ticks);
        float r = reach + 0.5f;
        fx.decal(FxDecal.Kind.RUNE, top.add(0, 0.04, 0), UP).radius(reach * 0.95f).life(life).color(PALE).alpha(0.55f).spin(0.004f)
                .fade(0.03f, 0.85f);
        fx.decal(FxDecal.Kind.HEX_SIGIL, top.add(0, -0.7, 0), UP).radius(r * 1.5f).life(life).color(VIOLET).alpha(0.7f).spin(-0.005f)
                .reveal(30).fade(0.03f, 0.85f);
        fx.light(top.add(0, 0.6, 0), c, 8, life).intensity(1.2f).fadeFrom(0.85f);
        fx.task((engine, age) -> {
            float left = 1 - age / (float) life;
            boolean failing = left < 0.2f;
            // Motes along the edge of the square it holds, so it is plain where it ends.
            int motes = failing && RANDOM.nextInt(3) == 0 ? 0 : 2;
            for (int i = 0; i < motes; i++) {
                double along = RANDOM.nextDouble() * 2 - 1;
                int side = RANDOM.nextInt(4);
                double x = side < 2 ? along * reach : (side == 2 ? -reach : reach);
                double z = side < 2 ? (side == 0 ? -reach : reach) : along * reach;
                engine.particle(top.add(x, 0.05, z)).velocity(0, 0.015 + RANDOM.nextDouble() * 0.02, 0).life(30).size(0.04f, 0)
                        .color(PALE, c).alpha(0.8f).hot(0.9f).twinkle(0.4f);
            }
            if (failing && RANDOM.nextInt(2) == 0) {
                // Breaking up: sparks falling away into the void beneath it.
                Vec3 at = top.add((RANDOM.nextDouble() * 2 - 1) * reach, -0.05, (RANDOM.nextDouble() * 2 - 1) * reach);
                engine.particle(at).shape(FxParticle.Shape.SPARK).velocity(0, -0.08, 0).life(30).size(0.06f, 0).color(PALE, VIOLET)
                        .gravity(0.01f).hot(1.0f);
            }
            return age < life;
        });
    }
}
