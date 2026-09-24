package de.ipnats.hardwrought.client.environment;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.client.survival.SurvivalHud;
import de.ipnats.hardwrought.core.networking.EnvironmentSnapshotPayload;
import de.ipnats.hardwrought.environment.GasMixture;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

import java.util.Locale;

/**
 * Section 18.2 in the interface: bad air gives no warning of its own. Without the safety lamp a
 * player only gets symptoms — the view closes in — and only an instrument turns the numbers on.
 */
public final class EnvironmentHud {
    private static final int VIGNETTE_DEPTH = 26;
    private static final int PANEL_X = de.ipnats.hardwrought.client.survival.SurvivalHud.PANEL_X;
    /** Stacked under the vitals panel, so growing that one pushes this down instead of hiding it. */
    private static final int PANEL_Y = de.ipnats.hardwrought.client.survival.SurvivalHud.PANEL_BOTTOM + 9;
    private static final int PANEL_WIDTH = de.ipnats.hardwrought.client.survival.SurvivalHud.PANEL_WIDTH;
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
        double smoke = gases.smokeStress();

        if (smoke > 0.05) vignette(graphics, Math.min(1.0, smoke), 0x201C18);
        if (suffocation > 0.05) vignette(graphics, suffocation, 0x0B1119);
        if (snapshot.instrumented() && suffocation > 0.30) {
            warning(graphics, client, gases.explosive()
                    ? I18n.get("hud.hardwrought.air.firedamp") : I18n.get("hud.hardwrought.air.warning"));
        } else if (snapshot.instrumented() && gases.explosive()) {
            warning(graphics, client, I18n.get("hud.hardwrought.air.firedamp"));
        }
        if (SurvivalHud.detailsVisible()) panel(graphics, client, gases);
    }

    private static void panel(net.minecraft.client.gui.GuiGraphicsExtractor graphics, Minecraft client,
                              GasMixture gases) {
        String space;
        if (!snapshot.sealed()) {
            space = I18n.get("hud.hardwrought.air.open");
        } else if (snapshot.volume() >= de.ipnats.hardwrought.environment.RoomScan.MAX_VOLUME) {
            space = I18n.get("hud.hardwrought.air.large");
        } else {
            space = I18n.get("hud.hardwrought.air.enclosed", snapshot.volume());
        }
        String noInstrument = I18n.get("hud.hardwrought.air.no_instrument");
        String[][] readings = {
                {"hud.hardwrought.air.oxygen", value(gases.oxygen())},
                {"hud.hardwrought.air.carbon_dioxide", value(gases.carbonDioxide())},
                {"hud.hardwrought.air.methane", value(gases.methane())}};

        // As wide as its longest line, never narrower than the vitals panel it hangs under: a room
        // size or a translation that runs long widens the board instead of spilling off it.
        int width = Math.max(PANEL_WIDTH, client.font.width(space));
        if (snapshot.instrumented()) {
            for (String[] reading : readings) {
                width = Math.max(width, client.font.width(I18n.get(reading[0])) + 8
                        + client.font.width(reading[1]));
            }
        } else {
            width = Math.max(width, client.font.width(noInstrument));
        }

        int height = snapshot.instrumented() ? 54 : 34;
        graphics.fill(PANEL_X - 4, PANEL_Y - 4, PANEL_X + width + 4, PANEL_Y + height, 0xA6080B0E);
        graphics.fill(PANEL_X - 3, PANEL_Y - 3, PANEL_X + width + 3, PANEL_Y - 2, 0x805D6972);
        graphics.text(client.font, I18n.get("hud.hardwrought.air"), PANEL_X, PANEL_Y, 0xFFE8EDF0, true);
        graphics.text(client.font, space, PANEL_X, PANEL_Y + 12, 0xFFB7C0C6, false);

        if (!snapshot.instrumented()) {
            graphics.text(client.font, noInstrument, PANEL_X, PANEL_Y + 22, 0xFF8C959B, false);
            return;
        }
        reading(graphics, client, width, PANEL_Y + 24, readings[0],
                gases.oxygen() < GasMixture.OXYGEN_DANGEROUS ? 0xFFE06B6B
                        : gases.oxygen() < GasMixture.OXYGEN_IMPAIRED ? 0xFFE9C45C : 0xFF8FD08F);
        reading(graphics, client, width, PANEL_Y + 34, readings[1],
                gases.carbonDioxide() > GasMixture.CARBON_DIOXIDE_SEVERE ? 0xFFE06B6B
                        : gases.carbonDioxide() > GasMixture.CARBON_DIOXIDE_NOTICEABLE ? 0xFFE9C45C : 0xFF8FD08F);
        reading(graphics, client, width, PANEL_Y + 44, readings[2],
                gases.explosive() ? 0xFFE06B6B
                        : gases.methane() > 0.005 ? 0xFFE9C45C : 0xFF8FD08F);
    }

    private static String value(double fraction) {
        return String.format(Locale.ROOT, "%.2f %%", fraction * 100);
    }

    private static void reading(net.minecraft.client.gui.GuiGraphicsExtractor graphics, Minecraft client,
                                int width, int y, String[] reading, int color) {
        graphics.text(client.font, I18n.get(reading[0]), PANEL_X, y, 0xFFB7C0C6, false);
        graphics.text(client.font, reading[1], PANEL_X + width - client.font.width(reading[1]), y, color, false);
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
