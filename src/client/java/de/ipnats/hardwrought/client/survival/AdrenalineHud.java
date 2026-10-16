package de.ipnats.hardwrought.client.survival;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModEffects;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * What adrenaline looks and sounds like (see {@code Adrenaline}): in a fight the edges of the sight
 * pulse red and the heart is heard, faster the higher it runs; in the crash after, the edges darken
 * and the heart slows.
 */
public final class AdrenalineHud {
    /** How wide the pulsing edge is, as a share of the screen's shorter side, and in how many steps it fades. */
    private static final double EDGE = 0.18;
    private static final int STEPS = 10;

    private static int untilBeat;

    private AdrenalineHud() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(AdrenalineHud::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Hardwrought.id("adrenaline"),
                (graphics, delta) -> render(graphics, delta));
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.isPaused()) return;
        MobEffectInstance rush = player.getEffect(ModEffects.ADRENALINE);
        boolean crash = player.hasEffect(ModEffects.ADRENALINE_CRASH);
        if (rush == null && !crash) {
            untilBeat = 0;
            return;
        }
        if (--untilBeat > 0) return;
        // A racing heart in the fight, a heavy slow one after.
        untilBeat = rush != null ? 16 - 3 * rush.getAmplifier() : 26;
        float volume = rush != null ? 0.5f + 0.15f * rush.getAmplifier() : 0.6f;
        player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT,
                SoundSource.PLAYERS, volume, rush != null ? 1.3f : 0.8f, false);
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;
        MobEffectInstance rush = player.getEffect(ModEffects.ADRENALINE);
        boolean crash = player.hasEffect(ModEffects.ADRENALINE_CRASH);
        if (rush == null && !crash) return;
        float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
        double strength;
        int rgb;
        if (rush != null) {
            double speed = 0.35 + 0.12 * rush.getAmplifier();
            strength = (0.18 + 0.10 * rush.getAmplifier()) * (0.55 + 0.45 * Math.sin(time * speed));
            rgb = 0xB01018;
        } else {
            strength = 0.30 + 0.08 * Math.sin(time * 0.2);
            rgb = 0x100808;
        }
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        int edge = (int) (Math.min(width, height) * EDGE);
        for (int step = 0; step < STEPS; step++) {
            int alpha = (int) (255 * strength * (1.0 - step / (double) STEPS));
            if (alpha <= 0) continue;
            int color = alpha << 24 | rgb;
            int from = edge * step / STEPS, to = edge * (step + 1) / STEPS;
            graphics.fill(0, from, width, to, color);
            graphics.fill(0, height - to, width, height - from, color);
            graphics.fill(from, to, to, height - to, color);
            graphics.fill(width - to, to, width - from, height - to, color);
        }
    }
}
