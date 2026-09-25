package de.ipnats.hardwrought.client.environment;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.networking.EnvironmentSnapshotPayload;
import de.ipnats.hardwrought.environment.GasMixture;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;


/**
 * Section 18.2 in the interface: bad air gives no warning of its own. A player gets symptoms — the
 * view closes in — and one carrying a safety lamp is warned in words as well. The numbers themselves
 * are read by using the lamp; the gas is in the blocks around the player, so there is no room to
 * describe on a board.
 */
public final class EnvironmentHud {
    private static final int VIGNETTE_DEPTH = 26;
    private static EnvironmentSnapshotPayload snapshot;

    private EnvironmentHud() { }

    public static EnvironmentSnapshotPayload snapshot() { return snapshot; }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(EnvironmentSnapshotPayload.TYPE,
                (payload, context) -> snapshot = payload);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> snapshot = null);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> snapshot = null);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Hardwrought.id("environment_hud"),
                (graphics, delta) -> render(graphics));
    }

    private static void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || snapshot == null) return;
        GasMixture gases = snapshot.gases();
        double suffocation = Math.max(gases.oxygenStress(), gases.carbonDioxideStress());
        double poisoning = gases.carbonMonoxideStress();

        // Carbon monoxide gives no sign of its own; what the player sees is their own head swimming.
        if (poisoning > 0.05) vignette(graphics, Math.min(1.0, poisoning), 0x3A0C14);
        if (suffocation > 0.05) vignette(graphics, suffocation, 0x0B1119);
        if (snapshot.instrumented() && Math.max(suffocation, poisoning) > 0.30) {
            warning(graphics, client, gases.explosive()
                    ? I18n.get("hud.hardwrought.air.firedamp") : I18n.get("hud.hardwrought.air.warning"));
        } else if (snapshot.instrumented() && gases.explosive()) {
            warning(graphics, client, I18n.get("hud.hardwrought.air.firedamp"));
        }
    }

    private static void warning(net.minecraft.client.gui.GuiGraphicsExtractor graphics, Minecraft client,
                                String text) {
        int x = graphics.guiWidth() / 2 - client.font.width(text) / 2;
        graphics.text(client.font, text, x, graphics.guiHeight() - 74, 0xFFE06B6B, true);
    }

    /** A cheap edge fade: the view closes in as the air gets worse. */
    private static void vignette(net.minecraft.client.gui.GuiGraphicsExtractor graphics, double strength,
                                 int rgb) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int depth = (int) Math.round(VIGNETTE_DEPTH * Math.min(1.0, strength));
        for (int step = 0; step < depth; step++) {
            int alpha = (int) Math.round(210.0 * strength * (1.0 - step / (double) Math.max(1, depth)));
            if (alpha <= 0) continue;
            int color = alpha << 24 | rgb;
            graphics.fill(0, step, width, step + 1, color);
            graphics.fill(0, height - step - 1, width, height - step, color);
            graphics.fill(step, 0, step + 1, height, color);
            graphics.fill(width - step - 1, 0, width - step, height, color);
        }
    }
}
