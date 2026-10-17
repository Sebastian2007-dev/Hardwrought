package de.ipnats.hardwrought.client.ritual;

import de.ipnats.hardwrought.client.fx.FxScreen;
import de.ipnats.hardwrought.ritual.Ritual;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The end of the ritual, seen (see {@code Ritual}): whoever was held in the chamber rises from it, up
 * through the open roof into the sky, and hangs there over the temple.
 *
 * <p>For the player's own rise the camera leaves them: first it looks up after them from the chamber floor,
 * closing in under the opening as they pass through it; then, with a flash, it is outside, circling them
 * in the open sky and drawing in on them. A golden outline kindles round them ({@code AscensionOutline}),
 * light breaks out of them at the height of it ({@code RitualFx}, the server's creative mode with it), and
 * the camera comes back to their eyes. Others in the chamber are seen to rise too, outlined the same.
 *
 * <p>The client moves its own player: set where the path has them every tick, the server takes it as any
 * movement; it only keeps them from being thought to fly or fall.
 */
public final class RitualCutscene {
    /** Ticks into the rise: drifting to the middle, through the roof, the cut outside, the outline, the height of it, back, the end. */
    static final int DRIFT = 50, CLIMB_FROM = 40, CLIMB_TO = 115, CUT = 88, OUTLINE_FROM = 100, OUTLINE_FULL = 140,
            CLIMAX = Ritual.CLIMAX - Ritual.ASCEND, BACK_FROM = 205, LENGTH = Ritual.END - Ritual.ASCEND;
    /** How high over the altar the rise ends: over every roof. */
    static final double HEIGHT = 44;

    /** A player rising, as every client near sees it. */
    record Rising(Vec3 altar, long start, int color) { }

    private static final Map<UUID, Rising> RISINGS = new HashMap<>();

    /** The local player's own rise, if it is theirs. */
    private static Rising own;
    private static Vec3 from;
    private static float orbitFrom;
    private static boolean hidGui, wasHidden;

    private RitualCutscene() { }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(Ritual.Ascension.TYPE, (payload, context) -> begin(payload));
        ClientTickEvents.END_CLIENT_TICK.register(RitualCutscene::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            RISINGS.clear();
            end(client);
        });
    }

    private static void begin(Ritual.Ascension payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        BlockPos at = payload.altar();
        Rising rising = new Rising(new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5), client.level.getGameTime(), payload.color());
        for (UUID id : payload.players()) RISINGS.put(id, rising);
        if (!payload.players().contains(client.player.getUUID())) return;
        own = rising;
        from = client.player.position();
        Vec3 out = from.subtract(rising.altar());
        // The camera starts across the chamber from where they stood.
        orbitFrom = (float) Math.toDegrees(Math.atan2(out.z, out.x)) + 150;
        wasHidden = client.gui.hud.isHidden();
        hidGui = true;
        if (!wasHidden) client.gui.hud.toggle();
    }

    /** Whether the local player's own rise is being shown: the camera is the cutscene's. */
    public static boolean active() {
        return own != null;
    }

    private static float ticks(Rising rising, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return 0;
        return client.level.getGameTime() - rising.start() + partialTick;
    }

    private static void tick(Minecraft client) {
        if (client.level == null) {
            RISINGS.clear();
            end(client);
            return;
        }
        RISINGS.values().removeIf(rising -> client.level.getGameTime() - rising.start() > LENGTH + 20);
        if (own == null) return;
        LocalPlayer player = client.player;
        float t = ticks(own, 0);
        if (player == null || !player.isAlive() || t > LENGTH) {
            end(client);
            return;
        }
        // The HUD comes back with the camera, for the thanks the game says then.
        if (hidGui && t >= LENGTH - 8) {
            hud(client, wasHidden);
            hidGui = false;
        }
        if (t == CUT) FxScreen.flash(0xFFF6E0, 0.85f, 500);
        Vec3 at = path(t);
        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(at.x, at.y, at.z);
        player.fallDistance = 0;
        if (t < CLIMAX) player.setXRot(Mth.approachDegrees(player.getXRot(), -25, 1.5f));
    }

    private static void end(Minecraft client) {
        if (own == null) return;
        own = null;
        if (hidGui) hud(client, wasHidden);
        hidGui = false;
        if (client.player != null && client.player.getAbilities().mayfly) client.player.getAbilities().flying = true;
    }

    private static void hud(Minecraft client, boolean hidden) {
        if (client.gui.hud.isHidden() != hidden) client.gui.hud.toggle();
    }

    // ------------------------------------------------------------------------------------ the rise

    /** Where the local player is, so many ticks into their rise. */
    static Vec3 path(float t) {
        Vec3 axis = own.altar();
        double drift = smooth(t / DRIFT);
        double x = Mth.lerp(drift, from.x, axis.x), z = Mth.lerp(drift, from.z, axis.z);
        double first = axis.y + 4;
        double y = t < CLIMB_FROM ? Mth.lerp(smooth(t / CLIMB_FROM), from.y, first)
                : first + (axis.y + HEIGHT - first) * smooth((t - CLIMB_FROM) / (CLIMB_TO - CLIMB_FROM));
        // Hanging there, a breath up and down.
        if (t > CLIMB_TO) y += Math.sin((t - CLIMB_TO) * 0.08) * 0.15 * smooth((t - CLIMB_TO) / 20);
        return new Vec3(x, y, z);
    }

    /** Where the camera is and where it looks, so many ticks into the rise; null to leave it be. */
    public static Shot shot(float partialTick, Vec3 eye, float viewYaw, float viewPitch) {
        if (own == null) return null;
        float t = Math.min(ticks(own, partialTick), LENGTH);
        Entity player = Minecraft.getInstance().player;
        if (player == null) return null;
        Vec3 body = player.getPosition(partialTick).add(0, 0.9, 0);
        Vec3 axis = own.altar();
        Vec3 camera;
        if (t < CUT) {
            // From the chamber floor, looking up after them, closing in under the opening as they near it.
            double angle = Math.toRadians(orbitFrom + t * 0.6);
            double radius = Mth.lerp(smooth((t - 35) / (CUT - 35)), 6.5, 1.6);
            double height = axis.y + 1.2 + t * 0.03;
            camera = new Vec3(axis.x + Math.cos(angle) * radius, height, axis.z + Math.sin(angle) * radius);
        } else {
            // Outside, circling them in the open sky, drawing in; pulled back for the light breaking out of them.
            double angle = Math.toRadians(orbitFrom + 60 + t * 0.8);
            double radius = Mth.lerp(smooth((t - CUT) / 62), 10, 3.4);
            double lift = 1.0;
            if (t > CLIMAX) {
                double back = smooth((t - CLIMAX) / 14);
                radius = Mth.lerp(back, radius, 7.5);
                lift = Mth.lerp(back, 1.0, 2.5);
            }
            camera = new Vec3(body.x + Math.cos(angle) * radius, body.y + lift, body.z + Math.sin(angle) * radius);
        }
        Vec3 look = body.subtract(camera);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()));
        // In from the player's own eyes at the start, and back to them at the end.
        double blend = t < 20 ? smooth(t / 20) : t > BACK_FROM ? 1 - smooth((t - BACK_FROM) / (LENGTH - BACK_FROM - 5)) : 1;
        if (blend < 1) {
            camera = eye.lerp(camera, blend);
            yaw = viewYaw + Mth.wrapDegrees(yaw - viewYaw) * (float) blend;
            pitch = Mth.lerp((float) blend, viewPitch, pitch);
        }
        return new Shot(camera, yaw, pitch, blend > 0.02);
    }

    /** A camera: where, which way, and whether it is away from the player's eyes. */
    public record Shot(Vec3 position, float yaw, float pitch, boolean detached) { }

    /** How strong the golden outline round this player is just now, 0 for none; more than 1 at the height of it. */
    public static float outline(UUID player, float partialTick) {
        Rising rising = RISINGS.get(player);
        if (rising == null) return 0;
        float t = ticks(rising, partialTick);
        if (t < OUTLINE_FROM || t > LENGTH) return 0;
        float k = (float) smooth((t - OUTLINE_FROM) / (OUTLINE_FULL - OUTLINE_FROM));
        if (t >= CLIMAX) k += 1.6f * (float) Math.exp(-(t - CLIMAX) / 12.0);
        // A slow breath in it, and gone at the end.
        k *= 0.9f + 0.1f * (float) Math.sin(t * 0.15);
        return k * (1 - (float) smooth((t - (LENGTH - 25)) / 25.0));
    }

    private static double smooth(double x) {
        x = Math.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
