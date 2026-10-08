package de.ipnats.hardwrought.client.magic;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.client.HardwroughtKeys;
import de.ipnats.hardwrought.magic.CastSpellPayload;
import de.ipnats.hardwrought.magic.Rune;
import de.ipnats.hardwrought.magic.RuneKnowledge;
import de.ipnats.hardwrought.magic.Spell;
import de.ipnats.hardwrought.magic.WandItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Action casting (magic specification section 11): no parchment, no pause. While the cast key is
 * held with a wand in hand, the mouse draws instead of turning the head; letting go casts at once.
 * A click while still holding ends one rune and begins the next, for spells of more than one.
 *
 * <p>Speed is the point of it, and the drawing is judged like any other: a rune scrawled in a panic
 * comes out weak, or wrong.
 */
public final class ActionCasting {
    public static final KeyMapping CAST = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.hardwrought.action_cast", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, HardwroughtKeys.CATEGORY));

    private static final List<InkStroke> strokes = new ArrayList<>();
    private static InkStroke current;
    private static boolean drawing;
    /** Where the pen is, in window pixels from where the drawing began. */
    private static float penX, penY;

    private ActionCasting() { }

    static void initialize() {
        ClientTickEvents.START_CLIENT_TICK.register(ActionCasting::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Hardwrought.id("action_casting"),
                (graphics, delta) -> render(graphics));
    }

    /** Whether the mouse draws rather than turns the head; asked by {@code MouseHandlerMixin}. */
    public static boolean capturing() {
        return drawing;
    }

    /** The mouse moved while drawing. */
    public static void move(double dx, double dy) {
        if (current == null) return;
        Minecraft client = Minecraft.getInstance();
        float limitX = client.getWindow().getWidth() / 2f, limitY = client.getWindow().getHeight() / 2f;
        penX = (float) Math.max(-limitX, Math.min(limitX, penX + dx));
        penY = (float) Math.max(-limitY, Math.min(limitY, penY + dy));
        current.add(penX, penY);
    }

    private static void tick(Minecraft client) {
        boolean able = client.player != null && client.gui.screen() == null && WandItem.held(client.player) != null
                && !client.player.isSpectator();
        if (!drawing) {
            if (able && CAST.isDown()) {
                drawing = true;
                penX = penY = 0;
                current = new InkStroke();
                current.add(0, 0);
            }
            return;
        }
        if (!able) {
            reset();
            return;
        }
        // A click while drawing ends the rune in hand and begins the next; it swings nothing.
        while (client.options.keyAttack.consumeClick()) {
            next();
        }
        if (!CAST.isDown()) {
            next();
            if (!strokes.isEmpty()) {
                List<CastSpellPayload.Stroke> payload = new ArrayList<>(strokes.size());
                for (InkStroke stroke : strokes) payload.add(stroke.toPayload());
                float size = Math.min(client.getWindow().getWidth(), client.getWindow().getHeight());
                ClientPlayNetworking.send(new CastSpellPayload(payload, size, true));
            }
            reset();
        }
    }

    private static void next() {
        if (current == null) return;
        current.finish();
        if (current.count >= 3 && strokes.size() < Spell.MAX_STROKES) strokes.add(current);
        current = new InkStroke();
        current.add(penX, penY);
    }

    private static void reset() {
        drawing = false;
        current = null;
        strokes.clear();
    }

    /**
     * The drawing, in the middle of the view, as the cursor would have drawn it: on a sheet of faint
     * parchment, so it shows against any sky, with a word on how to go on.
     */
    private static void render(GuiGraphicsExtractor graphics) {
        if (!drawing) return;
        Minecraft client = Minecraft.getInstance();
        // The GUI scale is a whole number: divided into 1 as such it would be 0, and the drawing a dot.
        float scale = (float) (1.0 / client.getWindow().getGuiScale());
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        float cx = w / 2f, cy = h / 2f;
        graphics.fill(0, 0, w, h, 0x60000000);
        int sheet = Math.min(w, h) * 3 / 4;
        int left = (w - sheet) / 2, top = (h - sheet) / 2;
        graphics.fill(left - 2, top - 2, left + sheet + 2, top + sheet + 2, 0x806B5432);
        graphics.fill(left, top, left + sheet, top + sheet, 0x50E9DCB8);
        for (int i = 0; i < strokes.size(); i++) {
            InkStroke stroke = strokes.get(i);
            RuneInk.stroke(graphics, stroke.xs, stroke.ys, stroke.count, cx, cy, scale, 3, RuneInk.separation(i));
        }
        if (current != null) {
            RuneInk.stroke(graphics, current.xs, current.ys, current.count, cx, cy, scale, 3, 0xFF2A2018);
            int px = Math.round(cx + penX * scale), py = Math.round(cy + penY * scale);
            graphics.fill(px - 3, py - 3, px + 3, py + 3, 0xFF000000);
            graphics.fill(px - 2, py - 2, px + 2, py + 2, 0xFFFFFFFF);
        }
        graphics.centeredText(client.font, Component.translatable("gui.hardwrought.action_cast.hint"), w / 2, top - 12, 0xFFE9DCB8);
        int x = left, y = top + sheet + 6;
        for (int i = 0; i < strokes.size(); i++) {
            Rune rune = strokes.get(i).reading == null ? null : strokes.get(i).reading.rune();
            boolean known = rune != null && client.player != null && RuneKnowledge.knows(client.player, rune);
            Component label = Component.literal((i + 1) + " ")
                    .append(known ? Component.translatable(rune.translationKey()) : Component.literal("?"));
            graphics.text(client.font, label, x, y, RuneInk.separation(i), true);
            x += client.font.width(label) + 8;
        }
    }

    /** How many strokes are drawn and finished so far; for tests. */
    public static int strokes() {
        return strokes.size();
    }
}
