package de.ipnats.hardwrought.client.survival;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.networking.SleepRequestPayload;
import de.ipnats.hardwrought.core.networking.SurvivalSnapshotPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

import java.util.Locale;

/** Survival HUD rendered next to the matching vanilla controls. Server snapshots remain read-only. */
public final class SurvivalHud {
    private static final KeyMapping.Category CATEGORY = de.ipnats.hardwrought.client.HardwroughtKeys.CATEGORY;
    private static final KeyMapping SLEEP = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.hardwrought.sleep", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, CATEGORY));
    private static final KeyMapping TOGGLE_DETAILS = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.hardwrought.vitals_hud", InputConstants.Type.KEYBOARD, InputConstants.KEY_H, CATEGORY));

    /**
     * Where the vitals panel sits and how far down it reaches. Public because the air panel stacks
     * under it: when this grows, that has to move, and a shared number is the only way that stays
     * true without somebody remembering it.
     */
    public static final int PANEL_X = 10;
    public static final int PANEL_Y = 10;
    public static final int PANEL_WIDTH = 122;
    public static final int PANEL_HEIGHT = 136;
    /** The first free pixel under the panel, border included. */
    public static final int PANEL_BOTTOM = PANEL_Y + PANEL_HEIGHT;

    private static final int DROP_COUNT = 10;
    private static final int DROP_SPACING = 8;
    private static final int STAMINA_COLOR = 0xFF55C96B;
    private static final int WATER_COLOR = 0xFF49AEE8;
    private static SurvivalSnapshotPayload snapshot;
    private static boolean detailsVisible;

    private SurvivalHud() { }

    public static SurvivalSnapshotPayload snapshot() { return snapshot; }
    public static boolean detailsVisible() { return detailsVisible; }
    public static void setDetailsVisible(boolean visible) { detailsVisible = visible; }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(SurvivalSnapshotPayload.TYPE,
                (payload, context) -> snapshot = payload);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> snapshot = null);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> snapshot = null);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (SLEEP.consumeClick() && client.player != null
                    && ClientPlayNetworking.canSend(SleepRequestPayload.TYPE)) {
                ClientPlayNetworking.send(new SleepRequestPayload());
            }
            while (TOGGLE_DETAILS.consumeClick()) detailsVisible = !detailsVisible;
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Hardwrought.id("survival_hud"),
                (graphics, delta) -> render(graphics));
    }

    private static void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || snapshot == null) return;

        int center = graphics.guiWidth() / 2;
        if (!client.player.isCreative()) {
            int dropY = graphics.guiHeight() - 49;
            renderDrops(graphics, center - 91, dropY, snapshot.stamina(), STAMINA_COLOR);
            if (!client.player.isUnderWater()) {
                renderDrops(graphics, center + 11, dropY, snapshot.hydration(), WATER_COLOR);
            }
        }

        if (detailsVisible) renderDetails(graphics, snapshot);
        if (snapshot.sleeping()) {
            String sleep = String.format(Locale.ROOT, "%s %.0f%%",
                    I18n.get("hud.hardwrought.sleep_quality"), snapshot.sleepQuality() * 100);
            int textX = center - client.font.width(sleep) / 2;
            graphics.text(client.font, sleep, textX, graphics.guiHeight() - 72, 0xFFDCCFFF, true);
        }
    }

    private static void renderDrops(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                    int x, int y, double value, int color) {
        double scaled = clamp(value / 10.0, 0, DROP_COUNT);
        for (int index = 0; index < DROP_COUNT; index++) {
            double fill = clamp(scaled - index, 0, 1);
            renderDrop(graphics, x + index * DROP_SPACING, y, fill, color);
        }
    }

    private static void renderDrop(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                   int x, int y, double fill, int color) {
        int outline = 0xF0101417;
        int empty = 0x7031383C;
        int[] outerLeft =  {3, 2, 2, 1, 1, 0, 0, 1, 2};
        int[] outerRight = {4, 5, 5, 6, 6, 7, 7, 6, 5};
        for (int row = 0; row < outerLeft.length; row++) {
            graphics.fill(x + outerLeft[row], y + row, x + outerRight[row], y + row + 1, outline);
        }

        int filledRows = (int) Math.ceil(fill * 6.0);
        for (int row = 2; row <= 7; row++) {
            int left = row == 2 || row == 7 ? 3 : row <= 4 ? 2 : 1;
            int right = row == 2 ? 4 : row == 7 ? 5 : row <= 4 ? 5 : 6;
            int rowColor = empty;
            if (row >= 8 - filledRows) {
                rowColor = row <= 3 ? shade(color, 1.18)
                        : row >= 7 ? shade(color, 0.66)
                        : row == 6 ? shade(color, 0.80) : color;
            }
            graphics.fill(x + left, y + row, x + right, y + row + 1, rowColor);
        }
        if (fill > 0.66) {
            graphics.fill(x + 2, y + 3, x + 3, y + 5, 0xC8FFFFFF);
        }
    }

    private static void renderDetails(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                      SurvivalSnapshotPayload values) {
        Minecraft client = Minecraft.getInstance();
        int x = PANEL_X;
        int y = PANEL_Y;
        int width = PANEL_WIDTH;
        graphics.fill(x - 4, y - 4, x + width + 4, y + PANEL_HEIGHT, 0xA6080B0E);
        graphics.fill(x - 3, y - 3, x + width + 3, y - 2, 0x805D6972);

        graphics.text(client.font, I18n.get("hud.hardwrought.fatigue"), x, y, 0xFFE8EDF0, true);
        thinBar(graphics, x, y + 12, width, values.fatigue(), 100,
                fatigueColor(values.fatigue()));
        String fatigue = String.format(Locale.ROOT, "%.0f / 100", values.fatigue());
        graphics.text(client.font, fatigue, x + (width - client.font.width(fatigue)) / 2,
                y + 20, 0xFFC9D0D4, false);

        graphics.text(client.font, I18n.get("hud.hardwrought.temperature"), x, y + 32,
                0xFFE8EDF0, true);
        temperatureBar(graphics, x, y + 44, width, values.bodyTemperature());
        String temperature = String.format(Locale.ROOT, "%.1f °C", values.bodyTemperature());
        graphics.text(client.font, temperature, x + (width - client.font.width(temperature)) / 2,
                y + 52, temperatureTextColor(values.bodyTemperature()), false);

        graphics.text(client.font, I18n.get("hud.hardwrought.stress"), x, y + 64, 0xFFE8EDF0, true);
        thinBar(graphics, x, y + 76, width, values.stress(), 100, fatigueColor(values.stress()));
        String stress = String.format(Locale.ROOT, "%.0f / 100", values.stress());
        graphics.text(client.font, stress, x + (width - client.font.width(stress)) / 2,
                y + 84, 0xFFC9D0D4, false);

        // Carry weight is only a fair rule if a player can see where they stand against it.
        graphics.text(client.font, I18n.get("hud.hardwrought.carried"), x, y + 96, 0xFFE8EDF0, true);
        double capacity = Math.max(0.001, values.capacityKg());
        double ratio = values.carriedKg() / capacity;
        thinBar(graphics, x, y + 108, width, Math.min(ratio, 1.0), 1.0, loadColor(ratio));
        String carried = String.format(Locale.ROOT, "%.1f / %.0f kg", values.carriedKg(), capacity);
        graphics.text(client.font, carried, x + (width - client.font.width(carried)) / 2,
                y + 116, ratio > 1.0 ? 0xFFF3A66B : 0xFFC9D0D4, false);
    }

    /** Green while the load is free, amber once it starts to tell, red when it is telling loudly. */
    private static int loadColor(double ratio) {
        if (ratio <= 1.0) return 0xFF6FBF73;
        return ratio < 1.6 ? 0xFFD9A441 : 0xFFC4553D;
    }

    private static void thinBar(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int x, int y,
                                int width, double value, double max, int color) {
        double ratio = clamp(value / max, 0, 1);
        graphics.fill(x - 1, y - 1, x + width + 1, y + 6, 0xD812171A);
        graphics.fill(x, y, x + width, y + 5, 0xFF30383D);
        graphics.fill(x, y, x + (int) Math.round(width * ratio), y + 5, color);
        graphics.fill(x, y, x + (int) Math.round(width * ratio), y + 1, 0x70FFFFFF);
    }

    private static void temperatureBar(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                       int x, int y, int width, double temperature) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + 7, 0xD812171A);
        for (int pixel = 0; pixel < width; pixel++) {
            double position = pixel / (double) Math.max(1, width - 1);
            graphics.fill(x + pixel, y, x + pixel + 1, y + 6, temperatureGradient(position));
        }
        int marker = x + (int) Math.round(clamp((temperature - 34.0) / 6.0, 0, 1) * (width - 1));
        graphics.fill(marker - 1, y - 3, marker + 2, y + 9, 0xFF090B0D);
        graphics.fill(marker, y - 2, marker + 1, y + 8, 0xFFFFFFFF);
    }

    private static int fatigueColor(double fatigue) {
        if (fatigue < 50) return lerpColor(0xFF70C98B, 0xFFD9B45A, fatigue / 50.0);
        return lerpColor(0xFFD9B45A, 0xFFD15B62, (fatigue - 50) / 50.0);
    }

    private static int temperatureGradient(double position) {
        if (position < 0.25) return lerpColor(0xFF397DE0, 0xFF48C5D8, position / 0.25);
        if (position < 0.50) return lerpColor(0xFF48C5D8, 0xFF72C97A, (position - 0.25) / 0.25);
        if (position < 0.75) return lerpColor(0xFF72C97A, 0xFFE0C657, (position - 0.50) / 0.25);
        return lerpColor(0xFFE0C657, 0xFFE15353, (position - 0.75) / 0.25);
    }

    private static int temperatureTextColor(double temperature) {
        if (temperature < 35.0) return 0xFF75B7FF;
        if (temperature > 39.0) return 0xFFFF7777;
        return 0xFFE8EDF0;
    }

    private static int lerpColor(int from, int to, double amount) {
        double t = clamp(amount, 0, 1);
        int red = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int green = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int blue = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int shade(int color, double factor) {
        int alpha = color >>> 24;
        int red = (int) clamp(Math.round(((color >> 16) & 0xFF) * factor), 0, 255);
        int green = (int) clamp(Math.round(((color >> 8) & 0xFF) * factor), 0, 255);
        int blue = (int) clamp(Math.round((color & 0xFF) * factor), 0, 255);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
