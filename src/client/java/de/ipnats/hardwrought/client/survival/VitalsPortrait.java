package de.ipnats.hardwrought.client.survival;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Locale;

/**
 * The player, head and shoulders, in a framed window at the left of the vitals panel — and the frame
 * is the load gauge.
 *
 * <p>Carry weight used to be a bar with a number under it, which is a thing a player has to stop and
 * read. The frame says the same thing without being read: bone white while the pack is inside the
 * allowance, and redder the further past it the player goes. It is a slide and not a switch on
 * purpose — the penalty itself ramps, so a frame that only flipped at the limit would lie about
 * what is happening to the legs.
 */
public final class VitalsPortrait {
    /** The outer size of the window, border included; the panel is laid out around these. */
    public static final int WIDTH = 36;
    public static final int HEIGHT = 30;

    private static final int BORDER = 2;
    private static final int SCALE = 2;
    /** How many texel rows of the torso the window shows: the shoulders, not the chest. */
    private static final int SHOULDER_ROWS = 5;
    private static final int SKIN_TEXTURE_SIZE = 64;

    private static final int FRAME_CLEAR = 0xFFE4EAEE;
    private static final int FRAME_OVER = 0xFFD2402F;
    /** How far past the allowance the frame takes to go the whole way to red. */
    private static final double FULL_RED_AT = 0.80;
    /** From here on the window also says the load in figures: the point where it starts to cost. */
    private static final double READOUT_FROM = 0.90;

    private static final int BACKDROP_TOP = 0xFF1C262D;
    private static final int BACKDROP_BOTTOM = 0xFF0C1014;
    private static final int READOUT_BAND = 0xC8080B0E;

    private VitalsPortrait() { }

    public static void render(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font,
                              AbstractClientPlayer player, int x, int y, double ratio) {
        int frame = frameColor(ratio);
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, frame);
        // A thin dark line inside the frame so a white frame still reads as a frame against the sky.
        graphics.fill(x + BORDER - 1, y + BORDER - 1, x + WIDTH - BORDER + 1, y + HEIGHT - BORDER + 1, 0xFF070A0C);
        int left = x + BORDER;
        int top = y + BORDER;
        int inner = WIDTH - 2 * BORDER;
        int innerHeight = HEIGHT - 2 * BORDER;
        graphics.fillGradient(left, top, left + inner, top + innerHeight, BACKDROP_TOP, BACKDROP_BOTTOM);
        bust(graphics, player, left, top, inner, innerHeight);

        if (ratio >= READOUT_FROM) {
            String load = String.format(Locale.ROOT, "%.0f%%", ratio * 100);
            int bandTop = top + innerHeight - 8;
            graphics.fill(left, bandTop, left + inner, top + innerHeight, READOUT_BAND);
            graphics.text(font, load, left + (inner - font.width(load)) / 2, bandTop + 1, frame, true);
        }
    }

    /** White inside the allowance, then along to red as the load passes it. */
    public static int frameColor(double ratio) {
        double over = Math.max(0, ratio - 1.0);
        return lerpColor(FRAME_CLEAR, FRAME_OVER, Math.min(1.0, over / FULL_RED_AT));
    }

    /**
     * Head, shoulders and the tops of both arms, cut from the player's own skin. Both layers are
     * drawn, so a player wearing a hat or a jacket sees it here as well.
     */
    private static void bust(GuiGraphicsExtractor graphics, AbstractClientPlayer player,
                             int x, int y, int width, int height) {
        PlayerSkin skin = player.getSkin();
        Identifier texture = skin.body().texturePath();
        int arm = skin.model() == PlayerModelType.SLIM ? 3 : 4;
        int left = x + (width - (8 + arm * 2) * SCALE) / 2;
        int top = y + (height - (8 + SHOULDER_ROWS) * SCALE) / 2;
        int body = left + arm * SCALE;
        int shoulders = top + 8 * SCALE;
        int otherArm = body + 8 * SCALE;

        part(graphics, texture, body, top, 8, 8, 8, 8);
        part(graphics, texture, body, shoulders, 20, 20, 8, SHOULDER_ROWS);
        // The player's right arm stands on our left, because the portrait is facing us.
        part(graphics, texture, left, shoulders, 44, 20, arm, SHOULDER_ROWS);
        part(graphics, texture, otherArm, shoulders, 36, 52, arm, SHOULDER_ROWS);

        part(graphics, texture, body, top, 40, 8, 8, 8);
        part(graphics, texture, body, shoulders, 20, 36, 8, SHOULDER_ROWS);
        part(graphics, texture, left, shoulders, 44, 36, arm, SHOULDER_ROWS);
        part(graphics, texture, otherArm, shoulders, 52, 52, arm, SHOULDER_ROWS);
    }

    private static void part(GuiGraphicsExtractor graphics, Identifier texture, int x, int y,
                             int u, int v, int width, int height) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v,
                width * SCALE, height * SCALE, width, height, SKIN_TEXTURE_SIZE, SKIN_TEXTURE_SIZE);
    }

    private static int lerpColor(int from, int to, double amount) {
        double t = Math.max(0, Math.min(1, amount));
        int red = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int green = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int blue = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
