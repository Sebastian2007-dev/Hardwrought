package de.ipnats.hardwrought.client.witchking;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.client.HardwroughtKeys;
import de.ipnats.hardwrought.client.fx.FxClient;
import de.ipnats.hardwrought.client.fx.FxEngine;
import de.ipnats.hardwrought.client.fx.FxParticle;
import de.ipnats.hardwrought.witchking.MantleFlight;
import de.ipnats.hardwrought.witchking.WitchKingContent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The mantle's hover on the client (see {@link MantleFlight}): the key, the movement while hovering —
 * slow, every way, drifting gently up and down as if held on a breath — the souls round its hem, and the
 * charge shown under the crosshair while it is not full.
 */
public final class MantleClient {
    public static final KeyMapping HOVER = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.hardwrought.hover", InputConstants.Type.KEYBOARD, InputConstants.KEY_X, HardwroughtKeys.CATEGORY));
    /** How fast the hovering wearer moves, sideways, up and down, in blocks a tick: letting itself sink is quicker than climbing. */
    static final double DRIFT = 0.2, CLIMB = 0.16, SINK = 0.34;
    /** The breath it hangs on: how far it carries up and down, and how slowly. */
    static final double BOB = 0.025, BOB_RATE = 0.1;
    private static final RandomSource RANDOM = RandomSource.create();

    private static boolean hovering;
    private static float charge = 1;

    private MantleClient() { }

    public static void initialize() {
        MantleFlight.clientHovering = player -> hovering && player == Minecraft.getInstance().player;
        ClientPlayNetworking.registerGlobalReceiver(MantleFlight.State.TYPE, (payload, context) -> {
            hovering = payload.hovering();
            charge = payload.charge();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            hovering = false;
            charge = 1;
        });
        ClientTickEvents.END_CLIENT_TICK.register(MantleClient::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Hardwrought.id("mantle_charge"), (graphics, delta) -> render(graphics));
    }

    public static boolean hovering() {
        return hovering;
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) return;
        while (HOVER.consumeClick()) {
            if (hovering) ClientPlayNetworking.send(new MantleFlight.Hover(false));
            else if (wearing(player) && !player.onGround()) ClientPlayNetworking.send(new MantleFlight.Hover(true));
        }
        if (hovering && !client.isPaused()) hem(player);
    }

    private static boolean wearing(Player player) {
        ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
        return worn.is(WitchKingContent.MANTLE) && LivingEntity.canGlideUsing(worn, EquipmentSlot.CHEST);
    }

    /** Souls round the hem of the mantle, falling away beneath. */
    private static void hem(LocalPlayer player) {
        FxEngine fx = FxClient.engine();
        if (fx == null) return;
        for (int i = 0; i < 2; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            Vec3 at = player.position().add(Math.cos(angle) * 0.4, 0.15 + RANDOM.nextDouble() * 0.3, Math.sin(angle) * 0.4);
            fx.particle(at).velocity(Math.cos(angle) * 0.01, -0.03 - RANDOM.nextDouble() * 0.02, Math.sin(angle) * 0.01).life(24)
                    .size(0.09f, 0).color(WitchKingFx.PALE, WitchKingFx.SOUL).alpha(0.8f).hot(0.8f).drag(0.96f);
        }
        if (RANDOM.nextInt(3) == 0) {
            fx.particle(player.position().add(RANDOM.nextGaussian() * 0.2, 0.05, RANDOM.nextGaussian() * 0.2)).shape(FxParticle.Shape.SPARK)
                    .velocity(0, -0.06, 0).life(14).size(0.05f, 0).color(WitchKingFx.VIOLET).hot(0.9f);
        }
    }

    /**
     * One tick of hovering movement, in place of the player's own: drifting towards where the keys point,
     * on a slow breath up and down. Returns false where the player is not hovering and moves as ever.
     */
    public static boolean travel(Player player, Vec3 input) {
        if (!(player instanceof LocalPlayer local) || !hovering) return false;
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yaw), cos = Mth.cos(yaw);
        // From the keys themselves: sinking is sneaking, and sneaking would slow the drift to a crawl.
        var keys = local.input.keyPresses;
        Vec3 flat = new Vec3((keys.left() ? 1 : 0) - (keys.right() ? 1 : 0), 0, (keys.forward() ? 1 : 0) - (keys.backward() ? 1 : 0));
        if (flat.lengthSqr() > 1) flat = flat.normalize();
        double speed = DRIFT * (player.isSprinting() ? 1.5 : 1);
        double x = (flat.x * cos - flat.z * sin) * speed, z = (flat.z * cos + flat.x * sin) * speed;
        double y = (keys.jump() ? CLIMB : 0) - (keys.shift() ? SINK : 0) + BOB * Math.cos(player.tickCount * BOB_RATE);
        Vec3 velocity = player.getDeltaMovement().lerp(new Vec3(x, y, z), 0.18);
        player.setDeltaMovement(velocity);
        player.move(MoverType.SELF, velocity);
        player.resetFallDistance();
        return true;
    }

    /** A thin band under the crosshair, soul-blue, while the charge is not full. */
    private static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || charge >= 1 && !hovering || !wearing(player)) return;
        int width = 42, x = graphics.guiWidth() / 2 - width / 2, y = graphics.guiHeight() / 2 + 14;
        graphics.fill(x - 1, y - 1, x + width + 1, y + 3, 0x90000000);
        int filled = Math.round(width * charge);
        int color = charge < 0.25f ? 0xFFFF6A8A : hovering ? 0xFF8FF0FF : 0xFF5CB8D0;
        graphics.fill(x, y, x + filled, y + 2, color);
    }
}
