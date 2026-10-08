package de.ipnats.hardwrought.client.smithing;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.core.networking.GrindingPayloads;
import de.ipnats.hardwrought.smithing.ForgeQuality;
import de.ipnats.hardwrought.smithing.Grinding;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

/**
 * A part at the grindstone. The part is held against the stone by hand: holding the mouse button, or
 * the space bar, presses it on, and the marker climbs to the right; letting go eases it off, and the
 * marker falls back. On the stone lies the band where the edge meets it true, with the best of it in
 * the middle, and the band wanders. A pass lasts a few seconds, and it is worth as much as the marker
 * was kept in the band.
 *
 * <p>Nothing here is a matter of one instant: the band moves smoothly and can be followed, a slip can
 * be made good in the rest of the pass, and what the pass was worth is how steady the hand was. Each
 * pass the band is a little narrower and wanders a little further, so the last ones ask the most.
 */
public class GrindingScreen extends Screen {
    private static final int PANEL_WIDTH = 216, PANEL_HEIGHT = 152;
    private static final int PANEL = 0xFFC6C6C6, PANEL_LIGHT = 0xFFFFFFFF, PANEL_MID = 0xFF8B8B8B, PANEL_DARK = 0xFF373737;
    private static final int TEXT = 0xFF404040, FADED = 0xFF606060;
    private static final int STONE = 0xFF5A5A5A, STONE_GRAIN = 0xFF6B6B6B, TRUE = 0xFF3F8F3F, DEAD_ON = 0xFFF2D24A,
            MARKER = 0xFFFFFFFF, PROGRESS = 0xFFE0A040;
    private static final int BAR_X = 14, BAR_Y = 62, BAR_WIDTH = PANEL_WIDTH - 28, BAR_HEIGHT = 16;
    /** Seconds one pass lasts. */
    private static final double PASS_SECONDS = 3.0;
    /** How hard pressing on pushes the marker and letting go pulls it back, and how quickly it settles. */
    private static final double PUSH = 2.4, PULL = 2.0, DRAG = 2.2;
    /** Half the width of the band on the first pass and on the last, as shares of the stone. */
    private static final double WIDE = 0.15, NARROW = 0.10;
    /** How much of the band is its best middle. */
    private static final double DEAD_ON_SHARE = 0.4;
    /** How far the band wanders from the middle of the stone, and how fast, on the first pass and on the last. */
    private static final double DRIFT_SMALL = 0.10, DRIFT_LARGE = 0.24, DRIFT_SLOW = 1.1, DRIFT_FAST = 1.9;
    /** What a moment in the best middle, in the band, and just beside it is worth. */
    private static final double IN_MIDDLE = 1.0, IN_BAND = 0.72, BESIDE = 0.3;
    /** The share of a pass that makes it dead on, clean, or just off; under that it is a scratch. */
    private static final double FOR_PERFECT = 0.85, FOR_GOOD = 0.62, FOR_POOR = 0.35;
    private static final int CLOSE_TICKS = 50;

    private final BlockPos grindstone;
    private final Random random = new Random();
    private int left, top;
    private boolean mouseDown, keyDown;
    /** The marker: where it is on the stone, 0 to 1, and how fast it is moving. */
    private double marker = 0.2, speed;
    /** This pass: whether it is running, how long it has run, what it has earned, and where its band started. */
    private boolean running;
    private double elapsed, earned, phase;
    private long lastFrame;
    private Grinding.Outcome lastOutcome;
    private long lastPassEnded;
    /** Passes this screen has sent that the part in hand does not show yet. */
    private int sent;
    private int shownPasses = -1;
    private int closeCountdown = -1;

    public GrindingScreen(BlockPos grindstone) {
        super(Component.translatable("gui.hardwrought.grinding"));
        this.grindstone = grindstone;
        phase = random.nextDouble() * Math.PI * 2;
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
    }

    private ItemStack part() {
        return minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
    }

    /** Passes made so far, counting the ones the server has not answered yet. */
    private int used() {
        ForgeQuality quality = ForgeQuality.of(part());
        return quality == null ? Grinding.PASSES : Math.min(Grinding.PASSES, quality.passes() + sent);
    }

    /** 0 on the first pass, 1 on the last. */
    private double difficulty() {
        return Math.min(1.0, used() / (double) (Grinding.PASSES - 1));
    }

    private double halfWidth() {
        return WIDE + (NARROW - WIDE) * difficulty();
    }

    /** Where the middle of the band is this far into the pass: a slow sway with a quicker, smaller one on it. */
    private double band(double seconds) {
        double difficulty = difficulty();
        double drift = DRIFT_SMALL + (DRIFT_LARGE - DRIFT_SMALL) * difficulty;
        double pace = DRIFT_SLOW + (DRIFT_FAST - DRIFT_SLOW) * difficulty;
        return 0.5 + drift * Math.sin(phase + seconds * pace) + drift * 0.35 * Math.sin(phase * 1.7 + seconds * pace * 2.3);
    }

    private boolean pressing() {
        return mouseDown || keyDown;
    }

    @Override
    public void tick() {
        ItemStack part = part();
        if (!Grinding.isPart(part)) {
            onClose();
            return;
        }
        // The server's answer has arrived: the part in hand shows the pass, so it is no longer owed.
        int passes = ForgeQuality.of(part).passes();
        if (shownPasses >= 0 && passes > shownPasses) sent = Math.max(0, sent - (passes - shownPasses));
        shownPasses = passes;
        if (closeCountdown < 0 && passes >= Grinding.PASSES) closeCountdown = CLOSE_TICKS;
        if (closeCountdown > 0 && --closeCountdown == 0) onClose();
    }

    /** Moves the marker and, while a pass runs, counts what this moment of it is worth. */
    private void advance() {
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.min(0.05, (now - lastFrame) / 1e9);
        lastFrame = now;
        if (dt <= 0) return;
        speed += ((pressing() ? PUSH : -PULL) - speed * DRAG) * dt;
        marker += speed * dt;
        if (marker <= 0 || marker >= 1) {
            marker = Math.clamp(marker, 0, 1);
            speed = 0;
        }
        if (!running) {
            // A pass begins with the first press, so nobody is caught with their hand off the part.
            if (pressing() && used() < Grinding.PASSES && System.currentTimeMillis() - lastPassEnded > 600) {
                running = true;
                elapsed = 0;
                earned = 0;
            }
            return;
        }
        double off = Math.abs(marker - band(elapsed)), half = halfWidth();
        earned += dt * (off <= half * DEAD_ON_SHARE ? IN_MIDDLE : off <= half ? IN_BAND : off <= half * 1.5 ? BESIDE : 0);
        elapsed += dt;
        if (elapsed >= PASS_SECONDS) finishPass();
    }

    private void finishPass() {
        double share = earned / PASS_SECONDS;
        lastOutcome = share >= FOR_PERFECT ? Grinding.Outcome.PERFECT : share >= FOR_GOOD ? Grinding.Outcome.GOOD
                : share >= FOR_POOR ? Grinding.Outcome.POOR : Grinding.Outcome.MISS;
        running = false;
        lastPassEnded = System.currentTimeMillis();
        sent++;
        ClientPlayNetworking.send(new GrindingPayloads.Pass(grindstone, lastOutcome));
        phase = random.nextDouble() * Math.PI * 2;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) return true;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        mouseDown = true;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) mouseDown = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_SPACE) {
            keyDown = true;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == InputConstants.KEY_SPACE) keyDown = false;
        return super.keyReleased(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL_DARK);
        graphics.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, PANEL_LIGHT);
        graphics.fill(left + 3, top + 3, left + PANEL_WIDTH - 1, top + PANEL_HEIGHT - 1, PANEL_MID);
        graphics.fill(left + 3, top + 3, left + PANEL_WIDTH - 3, top + PANEL_HEIGHT - 3, PANEL);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        advance();
        graphics.text(font, title, left + 10, top + 8, TEXT, false);
        ItemStack part = part();
        ForgeQuality quality = ForgeQuality.of(part);
        if (quality == null) return;

        graphics.item(part, left + 12, top + 24);
        graphics.text(font, part.getHoverName(), left + 34, top + 23, TEXT, false);
        graphics.text(font, Component.translatable("gui.hardwrought.grinding.quality", Math.round(quality.total() * 100),
                Math.round(quality.polish() * 100)), left + 34, top + 34, FADED, false);

        // One pip for every pass the part can take: dark once it is spent.
        int used = used();
        for (int i = 0; i < Grinding.PASSES; i++) {
            int px = left + PANEL_WIDTH - 14 - (Grinding.PASSES - i) * 8;
            graphics.fill(px, top + 9, px + 6, top + 15, PANEL_DARK);
            graphics.fill(px + 1, top + 10, px + 5, top + 14, i < used ? PANEL_MID : DEAD_ON);
        }

        int bx = left + BAR_X, by = top + BAR_Y;
        graphics.fill(bx - 1, by - 1, bx + BAR_WIDTH + 1, by + BAR_HEIGHT + 1, PANEL_DARK);
        graphics.fill(bx, by, bx + BAR_WIDTH, by + BAR_HEIGHT, STONE);
        for (int x = 3; x < BAR_WIDTH; x += 7) graphics.fill(bx + x, by + 2 + x % 5, bx + x + 2, by + 3 + x % 5, STONE_GRAIN);
        if (used < Grinding.PASSES || running) {
            double centre = band(running ? elapsed : 0), half = halfWidth();
            band(graphics, bx, by, centre, half, TRUE);
            band(graphics, bx, by, centre, half * DEAD_ON_SHARE, DEAD_ON);
            int mx = bx + (int) Math.round(marker * (BAR_WIDTH - 1));
            graphics.fill(mx - 1, by - 3, mx + 2, by + BAR_HEIGHT + 3, PANEL_DARK);
            graphics.fill(mx, by - 2, mx + 1, by + BAR_HEIGHT + 2, MARKER);
        }
        // How far this pass has got, under the stone.
        int py = by + BAR_HEIGHT + 5;
        graphics.fill(bx - 1, py - 1, bx + BAR_WIDTH + 1, py + 4, PANEL_DARK);
        graphics.fill(bx, py, bx + BAR_WIDTH, py + 3, PANEL_MID);
        if (running) graphics.fill(bx, py, bx + (int) Math.round(BAR_WIDTH * elapsed / PASS_SECONDS), py + 3, PROGRESS);

        Component line = used >= Grinding.PASSES && !running ? Component.translatable("gui.hardwrought.grinding.done")
                : running ? Component.translatable("gui.hardwrought.grinding.steady",
                        Math.round(100 * earned / Math.max(0.2, elapsed)))
                : lastOutcome != null && System.currentTimeMillis() - lastPassEnded < 1500
                ? Component.translatable("gui.hardwrought.grinding." + lastOutcome.name().toLowerCase(java.util.Locale.ROOT))
                : Component.translatable("gui.hardwrought.grinding.hint");
        int ty = py + 10;
        // Dark ink on the light panel, without the drop shadow that smears it.
        for (var row : font.split(line, PANEL_WIDTH - 20)) {
            graphics.text(font, row, left + (PANEL_WIDTH - font.width(row)) / 2, ty, TEXT, false);
            ty += font.lineHeight + 1;
        }
        Component passes = Component.translatable("gui.hardwrought.grinding.passes", Grinding.PASSES - used);
        graphics.text(font, passes, left + (PANEL_WIDTH - font.width(passes)) / 2, top + PANEL_HEIGHT - 16, FADED, false);
    }

    /** A band on the stone around a place, this far to either side, cut off at the stone's ends. */
    private static void band(GuiGraphicsExtractor graphics, int bx, int by, double centre, double half, int colour) {
        int from = bx + (int) Math.round(Math.max(0, centre - half) * BAR_WIDTH);
        int to = bx + (int) Math.round(Math.min(1, centre + half) * BAR_WIDTH);
        graphics.fill(from, by, Math.max(from + 1, to), by + BAR_HEIGHT, colour);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
