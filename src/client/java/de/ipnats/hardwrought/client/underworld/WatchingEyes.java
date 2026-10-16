package de.ipnats.hardwrought.client.underworld;

import de.ipnats.hardwrought.client.fx.FxClient;
import de.ipnats.hardwrought.client.fx.FxEngine;
import de.ipnats.hardwrought.client.fx.FxParticle;
import de.ipnats.hardwrought.underworld.Underworld;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Watching eyes: a pair of faint glowing points in the dark, at the edge of the light. They never move
 * while looked at. Look away for a moment and back, and they are somewhere else — or gone. Come close, or
 * bring a light to them, and they close. Harmless, a thing of this player's client alone, and never
 * explained.
 */
public final class WatchingEyes {
    /** On average one pair every so many ticks, while there is none. */
    static final int EVERY = 1400;
    private static final double LOOKED_AT = Math.cos(Math.toRadians(28)), LOOKED_AWAY = Math.cos(Math.toRadians(65));
    private static final int AWAY_TO_MOVE = 30, MAX_AGE = 2400, CLOSING = 7;
    private static final double TOO_CLOSE = 7, SPACING = 0.22;
    private static final int COLOR = 0xFFF2F0C8;

    private static final RandomSource RANDOM = RandomSource.create();
    private static Vec3 at;
    private static int age, away, closing = -1, blink;

    private WatchingEyes() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(WatchingEyes::tick);
    }

    /** Where a pair is now, or null; for tests. */
    public static Vec3 position() {
        return at;
    }

    /** Opens a pair now, somewhere ahead in the dark, if there is such a place; for tests. */
    public static boolean open(ClientLevel level, LocalPlayer player) {
        Vec3 spot = spot(level, player, 0);
        if (spot == null) return false;
        at = spot;
        age = away = 0;
        closing = -1;
        return true;
    }

    private static void tick(Minecraft client) {
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null || level.dimension() != Underworld.LEVEL) {
            at = null;
            return;
        }
        if (client.isPaused()) return;
        if (at == null) {
            boolean grove = level.getBiome(player.blockPosition()).unwrapKey().map(k -> k.identifier().getPath().equals("mossy_grove")).orElse(false);
            if (!grove && RANDOM.nextInt(EVERY) == 0) open(level, player);
            return;
        }
        age++;
        Vec3 eye = player.getEyePosition();
        Vec3 toward = at.subtract(eye);
        double distance = toward.length();
        double facing = player.getLookAngle().dot(toward.normalize());
        if (closing < 0 && (distance < TOO_CLOSE || age > MAX_AGE || level.getBrightness(LightLayer.BLOCK, BlockPos.containing(at)) > 2)) {
            closing = 0;
        }
        if (closing >= 0) {
            if (++closing > CLOSING) at = null;
            else draw(1 - closing / (float) CLOSING);
            return;
        }
        if (facing >= LOOKED_AT) {
            away = 0;
        } else if (facing < LOOKED_AWAY) {
            // Unseen long enough: they are elsewhere by the time the player looks again, or gone.
            if (++away == AWAY_TO_MOVE) {
                Vec3 moved = RANDOM.nextInt(3) == 0 ? null : spot(level, player, 1);
                if (moved == null) {
                    at = null;
                    return;
                }
                at = moved;
                age = Math.max(age, MAX_AGE / 2);
            }
        }
        // Now and then they blink.
        if (blink > 0) blink--;
        else if (RANDOM.nextInt(90) == 0) blink = 3;
        if (blink == 0) draw(1);
    }

    /** The two points, for one tick. */
    private static void draw(float open) {
        FxEngine fx = FxClient.engine();
        if (fx == null || at == null) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Vec3 toward = player.getEyePosition().subtract(at).multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-toward.z, 0, toward.x).scale(SPACING / 2);
        for (Vec3 point : new Vec3[] {at.add(side), at.subtract(side)}) {
            fx.particle(point).shape(FxParticle.Shape.GLOW).life(2).size(0.07f * open).color(COLOR).alpha(0.85f)
                    .fade(0, 1).hot(0.9f).drag(0);
        }
    }

    /**
     * A dark place ahead of the player, a little to one side of where they look, before a wall: ten to
     * twenty-two blocks off, unlit. With {@code wider} set, anywhere round them but behind.
     */
    private static Vec3 spot(ClientLevel level, LocalPlayer player, int wider) {
        Vec3 eye = player.getEyePosition();
        double yaw = Math.toRadians(player.getYRot());
        for (int attempt = 0; attempt < 16; attempt++) {
            double turn = Math.toRadians((RANDOM.nextDouble() * 2 - 1) * (wider > 0 ? 120 : 45));
            double angle = yaw + turn;
            Vec3 direction = new Vec3(-Math.sin(angle), (RANDOM.nextDouble() - 0.5) * 0.3, Math.cos(angle)).normalize();
            double reach = 10 + RANDOM.nextDouble() * 12;
            var hit = level.clip(new ClipContext(eye, eye.add(direction.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
                    CollisionContext.empty()));
            Vec3 point = hit.getType() == HitResult.Type.MISS ? eye.add(direction.scale(reach)) : hit.getLocation().subtract(direction.scale(0.6));
            if (point.distanceTo(eye) < 9) continue;
            BlockPos pos = BlockPos.containing(point);
            if (!level.getBlockState(pos).isAir() || level.getBrightness(LightLayer.BLOCK, pos) > 0) continue;
            return point;
        }
        return null;
    }
}
