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
    public static final int PANEL_WIDTH = 148;
    public static final int PANEL_HEIGHT = 33;
    /** The first free pixel under the panel, border included. */
    public static final int PANEL_BOTTOM = PANEL_Y + PANEL_HEIGHT;
    /** Where the two bars begin: clear of the portrait, with a gutter between. */
    private static final int BAR_X = PANEL_X + VitalsPortrait.WIDTH + 5;
    /** What is left for the bars once the figures at the right edge have their room. */
    private static final int BAR_WIDTH = 52;

    private static final int DROP_COUNT = 10;
    private static final int DROP_SPACING = 8;
    private static final int STAMINA_COLOR = 0xFF55C96B;
    private static final int WATER_COLOR = 0xFF49AEE8;
    private static final int STAMINA_REGENERATION_COLOR = 0xFFFFD43B;
    private static final int STAMINA_WINDED_COLOR = 0xFFE5572E;
    private static final int WATER_THIRST_COLOR = 0xFF7C8A36;
    private static final int WATER_REGENERATION_COLOR = 0xFFB4F2FF;
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
        // With armor on, the stamina drops stand where vanilla writes the name of the item just
        // selected; the name moves up out of their way.
        HudElementRegistry.replaceElement(VanillaHudElements.HELD_ITEM_TOOLTIP, vanilla -> (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            int lift = client.player == null || snapshot == null
                    || de.ipnats.hardwrought.survival.SurvivalSystem.exempt(client.player) ? 0
                    : graphics.guiHeight() - 49 - staminaY(client.player, graphics.guiHeight());
            if (lift <= 0) {
                vanilla.extractRenderState(graphics, delta);
                return;
            }
            graphics.pose().pushMatrix();
            graphics.pose().translate(0, -lift);
            vanilla.extractRenderState(graphics, delta);
            graphics.pose().popMatrix();
        });
    }

    private static void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || snapshot == null) return;

        int center = graphics.guiWidth() / 2;
        if (!de.ipnats.hardwrought.survival.SurvivalSystem.exempt(client.player)) {
            int dropY = graphics.guiHeight() - 49;
            renderDrops(graphics, center - 91, staminaY(client.player, graphics.guiHeight()), snapshot.stamina(),
                    staminaColor(client.player));
            if (!client.player.isUnderWater()) {
                renderDrops(graphics, center + 11, dropY, snapshot.hydration(), waterColor(client.player));
            }
            renderStuffed(graphics, center, graphics.guiHeight() - 39, client.player);
        }

        if (detailsVisible) renderDetails(graphics, snapshot);
        if (snapshot.sleeping()) {
            String sleep = String.format(Locale.ROOT, "%s %.0f%%",
                    I18n.get("hud.hardwrought.sleep_quality"), snapshot.sleepQuality() * 100);
            int textX = center - client.font.width(sleep) / 2;
            graphics.text(client.font, sleep, textX, graphics.guiHeight() - 72, 0xFFDCCFFF, true);
        }
    }

    private static final net.minecraft.resources.Identifier FOOD_FULL = net.minecraft.resources.Identifier.withDefaultNamespace("hud/food_full");
    private static final net.minecraft.resources.Identifier FOOD_HALF = net.minecraft.resources.Identifier.withDefaultNamespace("hud/food_half");
    /** The gold of a belly fuller than full. */
    private static final int STUFFED_COLOR = 0xFFF0B838;

    /**
     * What has been eaten past full, as a second bar of food drawn in gold over the first: each gold
     * shank is two points more than the bar holds, and the bar does not fall until they are gone.
     */
    private static void renderStuffed(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int center, int y,
                                      net.minecraft.world.entity.player.Player player) {
        double stuffed = de.ipnats.hardwrought.survival.Overeating.stuffed(player);
        if (stuffed < 1) return;
        for (int icon = 0; icon < 10; icon++) {
            double here = stuffed - icon * 2;
            if (here < 1) break;
            int x = center + 91 - icon * 8 - 9;
            graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, here >= 2 ? FOOD_FULL : FOOD_HALF,
                    x, y, 9, 9, STUFFED_COLOR);
        }
    }

    /**
     * Where the stamina drops go: the row above the hearts, which is where vanilla draws armor. With
     * armor on they move up a row, above the armor; and like the armor they stand clear of however
     * many rows of hearts there are.
     */
    public static int staminaY(net.minecraft.world.entity.player.Player player, int screenHeight) {
        double hearts = Math.max(player.getMaxHealth(), player.getHealth()) + player.getAbsorptionAmount();
        int rows = Math.max(1, (int) Math.ceil(hearts / 2.0 / 10.0));
        int rowHeight = Math.max(10 - (rows - 2), 3);
        int aboveHearts = screenHeight - 39 - (rows - 1) * rowHeight - 10;
        return player.getArmorValue() > 0 ? aboveHearts - 10 : aboveHearts;
    }

    /**
     * The stamina drops show what is acting on the reserve, as vanilla's hearts show poison: orange-red
     * while the player is winded, gold under stamina regeneration. A bad effect shows over a good one.
     */
    public static int staminaColor(net.minecraft.world.entity.player.Player player) {
        if (player.hasEffect(de.ipnats.hardwrought.core.registry.ModEffects.WINDED)) return STAMINA_WINDED_COLOR;
        if (player.hasEffect(de.ipnats.hardwrought.core.registry.ModEffects.STAMINA_REGENERATION)) return STAMINA_REGENERATION_COLOR;
        return STAMINA_COLOR;
    }

    /** The water drops: a dirty green with thirst, a clear bright blue under hydration regeneration. */
    public static int waterColor(net.minecraft.world.entity.player.Player player) {
        if (player.hasEffect(de.ipnats.hardwrought.core.registry.ModEffects.THIRST)) return WATER_THIRST_COLOR;
        if (player.hasEffect(de.ipnats.hardwrought.core.registry.ModEffects.HYDRATION_REGENERATION)) return WATER_REGENERATION_COLOR;
        return WATER_COLOR;
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

    /**
     * The vitals panel: the player in a framed window, and beside it the two readings the body
     * itself would not give — how warm it is inside and how far from rested it is.
     *
     * <p>Everything else that used to stand here is gone. Strain, altitude habit and the load in
     * kilogrammes were a column of numbers a player has no way of knowing, which is a HUD telling
     * them what the game should be making them feel. What is left is what a body does know about
     * itself. The load is still shown, but as the colour of the frame round the portrait rather
     * than as a figure (see {@link VitalsPortrait}).
     *
     * <p>The thermometer carries no written label: a scale that runs blue to red and ends in °C is one
     * whatever is printed over it. The other row says what it is — "Tired 25%" — because a bare
     * "25 / 100" left players guessing what was being counted.
     */
    private static void renderDetails(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                      SurvivalSnapshotPayload values) {
        Minecraft client = Minecraft.getInstance();
        int x = PANEL_X;
        int y = PANEL_Y;
        int right = x + PANEL_WIDTH;
        java.util.List<net.minecraft.network.chat.Component> ills = diseaseLines(client.player);
        graphics.fill(x - 3, y - 3, right + 3, y + PANEL_HEIGHT + ills.size() * DISEASE_LINE, 0xA6080B0E);
        graphics.fill(x - 2, y - 2, right + 2, y - 1, 0x805D6972);

        double capacity = Math.max(0.001, values.capacityKg());
        VitalsPortrait.render(graphics, client.font, client.player, x, y, values.carriedKg() / capacity);

        temperatureBar(graphics, BAR_X, y + 5, BAR_WIDTH, values.bodyTemperature());
        String temperature = String.format(Locale.ROOT, "%.1f °C", values.bodyTemperature());
        graphics.text(client.font, temperature, right - client.font.width(temperature), y + 4,
                temperatureTextColor(values.bodyTemperature()), true);

        thinBar(graphics, BAR_X, y + 20, BAR_WIDTH, values.fatigue(), 100, fatigueColor(values.fatigue()));
        String fatigue = I18n.get("hud.hardwrought.fatigue", String.format(Locale.ROOT, "%.0f", values.fatigue()));
        graphics.text(client.font, fatigue, right - client.font.width(fatigue), y + 18, 0xFFC9D0D4, true);
        for (int line = 0; line < ills.size(); line++) {
            graphics.text(client.font, ills.get(line), x, y + PANEL_HEIGHT + line * DISEASE_LINE - 1, DISEASE_COLOR, true);
        }
    }

    private static final int DISEASE_LINE = 10;
    private static final int DISEASE_COLOR = 0xFFB4D07A;

    /**
     * One line for each disease the player feels: its name once they have been through it before, and
     * otherwise only what it does to them; and how it stands.
     */
    private static java.util.List<net.minecraft.network.chat.Component> diseaseLines(
            net.minecraft.client.player.LocalPlayer player) {
        java.util.List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        if (player == null) return lines;
        var health = de.ipnats.hardwrought.health.Diseases.health(player);
        for (var infection : health.infections()) {
            if (!infection.felt()) continue;
            var disease = infection.disease();
            var stage = infection.stage();
            net.minecraft.network.chat.Component name = health.known().contains(disease)
                    ? net.minecraft.network.chat.Component.translatable(disease.translationKey(stage))
                    : net.minecraft.network.chat.Component.translatable("disease.hardwrought.symptoms." + disease.serializedName());
            lines.add(net.minecraft.network.chat.Component.translatable("gui.hardwrought.survival.disease", name,
                    net.minecraft.network.chat.Component.translatable(stage.translationKey())));
        }
        return lines;
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
