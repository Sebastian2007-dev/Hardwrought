package de.ipnats.hardwrought.client.survival;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.client.HardwroughtKeys;
import de.ipnats.hardwrought.core.networking.FoodNutrientsPayload;
import de.ipnats.hardwrought.core.networking.SurvivalSnapshotPayload;
import de.ipnats.hardwrought.core.registry.FoodNutritionDefinition;
import de.ipnats.hardwrought.survival.Nutrient;
import de.ipnats.hardwrought.survival.Nutrition;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;

/**
 * How a player's diet stands: each of the five nutrients against its healthy band, what lacking or
 * having too much of it is doing to them right now, and whether the whole of it is balanced.
 *
 * <p>Nothing here is worked out on the client. The levels come with the survival snapshot, and what
 * each food brings comes from the server once on joining; the compendium reads that too.
 */
public class NutritionScreen extends Screen {
    public static final KeyMapping OPEN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.hardwrought.nutrition", InputConstants.Type.KEYBOARD, InputConstants.KEY_N,
            HardwroughtKeys.CATEGORY));

    private static final int WIDTH = 300;
    private static final int HEIGHT = 250;
    private static final int BAR_WIDTH = 150;
    private static final int ROW = 34;
    private static final int TEXT = 0xFF404040;
    private static final int FADED = 0xFF606060;
    private static final int GOOD = 0xFF278427;
    private static final int LACK = 0xFF9A6A00;
    private static final int EXCESS = 0xFFAA0000;

    private static Map<Identifier, FoodNutritionDefinition> foods = Map.of();

    public NutritionScreen() {
        super(Component.translatable("gui.hardwrought.nutrition"));
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(FoodNutrientsPayload.TYPE, (payload, context) -> foods = payload.foods());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> foods = Map.of());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) client.gui.setScreen(new NutritionScreen());
            }
        });
    }

    /**
     * What a food brings, as the server said, or the same guess the server makes for a food nobody
     * wrote down. Null for anything that is not food.
     */
    public static FoodNutritionDefinition nutrientsOf(Identifier id) {
        FoodNutritionDefinition known = foods.get(id);
        if (known != null) return known;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null) return null;
        var food = new ItemStack(item).get(DataComponents.FOOD);
        return food == null ? null : FoodNutritionDefinition.guess(id, food.nutrition());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        drawVanillaPanel(graphics, left, top, WIDTH, HEIGHT);
        graphics.text(font, title, left + 10, top + 8, TEXT, false);

        SurvivalSnapshotPayload snapshot = SurvivalHud.snapshot();
        Minecraft client = Minecraft.getInstance();
        if (snapshot == null || client.player == null) {
            graphics.text(font, Component.translatable("gui.hardwrought.nutrition.waiting"), left + 10, top + 30, FADED, false);
            return;
        }
        var food = client.player.getFoodData();
        String hunger = I18n("gui.hardwrought.nutrition.hunger", food.getFoodLevel(),
                String.format(Locale.ROOT, "%.1f", food.getSaturationLevel()));
        graphics.text(font, hunger, left + WIDTH - 10 - font.width(hunger), top + 8, FADED, false);

        Nutrition diet = snapshot.nutrition();
        int y = top + 26;
        for (Nutrient nutrient : Nutrient.values()) {
            drawNutrient(graphics, nutrient, diet.get(nutrient), left + 10, y);
            y += ROW;
        }

        y += 2;
        graphics.fill(left + 10, y, left + WIDTH - 10, y + 1, 0xFF555555);
        graphics.fill(left + 10, y + 1, left + WIDTH - 10, y + 2, 0xFFFFFFFF);
        y += 5;
        Component verdict = diet.balanced()
                ? Component.translatable("gui.hardwrought.nutrition.balanced")
                : Component.translatable("gui.hardwrought.nutrition.unbalanced");
        for (FormattedCharSequence line : font.split(verdict, WIDTH - 20)) {
            graphics.text(font, line, left + 10, y, diet.balanced() ? GOOD : FADED, false);
            y += 10;
        }
        graphics.text(font, Component.translatable("gui.hardwrought.nutrition.compendium"),
                left + 10, top + HEIGHT - 14, FADED, false);
    }

    private void drawNutrient(GuiGraphicsExtractor graphics, Nutrient nutrient, double level, int x, int y) {
        Nutrient.Status status = Nutrient.status(level);
        int statusColor = status == Nutrient.Status.HEALTHY ? GOOD : status == Nutrient.Status.LOW ? LACK : EXCESS;
        graphics.text(font, Component.translatable(nutrient.translationKey()), x, y, TEXT, false);

        // The bar, with the healthy band drawn lighter behind it so the player can see where to aim.
        int barX = x + 100;
        int barY = y + 1;
        int low = barX + (int) Math.round(BAR_WIDTH * Nutrient.LOW / Nutrient.MAX);
        int high = barX + (int) Math.round(BAR_WIDTH * Nutrient.HIGH / Nutrient.MAX);
        graphics.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + 8, 0xFF373737);
        graphics.fill(barX, barY, barX + BAR_WIDTH, barY + 7, 0xFF8B8B8B);
        graphics.fill(barX + 1, barY + 1, barX + BAR_WIDTH, barY + 7, 0xFF555555);
        graphics.fill(low, barY + 1, high, barY + 7, 0xFF78917D);
        int filled = barX + (int) Math.round(BAR_WIDTH * Math.max(0, Math.min(1, level / Nutrient.MAX)));
        graphics.fill(barX + 1, barY + 2, Math.max(barX + 1, filled), barY + 6, nutrient.color());
        graphics.fill(low, barY - 2, low + 1, barY + 9, 0xFF373737);
        graphics.fill(high, barY - 2, high + 1, barY + 9, 0xFF373737);

        String value = String.format(Locale.ROOT, "%.0f", level);
        graphics.text(font, value, x + WIDTH - 20 - font.width(value), y, statusColor, false);

        // Under it: the state, and what it is doing to the body — or, while it is fine, where it comes from.
        String effect = nutrient.effectKey(status);
        Component state = Component.translatable(status.translationKey()).append(": ");
        graphics.text(font, state, x, y + 13, statusColor, false);
        Component line = effect == null ? Component.translatable(nutrient.translationKey() + ".source")
                : Component.translatable(effect);
        int after = x + font.width(state);
        graphics.text(font, font.split(line, WIDTH - 20 - (after - x)).getFirst(), after, y + 13,
                effect == null ? FADED : statusColor, false);
    }

    /** Classic vanilla container surface: stone grey with raised light and dark edges. */
    private static void drawVanillaPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFFC6C6C6);
        graphics.fill(x, y, x + width, y + 2, 0xFFFFFFFF);
        graphics.fill(x, y, x + 2, y + height, 0xFFFFFFFF);
        graphics.fill(x, y + height - 2, x + width, y + height, 0xFF555555);
        graphics.fill(x + width - 2, y, x + width, y + height, 0xFF555555);
        graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, 0xFF8B8B8B);
        graphics.fill(x + width - 3, y + 2, x + width - 2, y + height - 2, 0xFF8B8B8B);
    }

    private static String I18n(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (OPEN.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
