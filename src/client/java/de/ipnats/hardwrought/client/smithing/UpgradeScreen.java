package de.ipnats.hardwrought.client.smithing;

import com.mojang.blaze3d.platform.InputConstants;
import de.ipnats.hardwrought.core.networking.UpgradePayloads;
import de.ipnats.hardwrought.smithing.Mask;
import de.ipnats.hardwrought.smithing.Upgrading;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A piece at the smithing table, under its template. The template's pattern lies over the piece as a
 * line, and the smith traces it: the mouse button held, from the marked beginning to the end, as
 * close to the line as the hand manages. The line behind the pointer shows how it went — green where
 * it was true, yellow where it wandered, red where it left the line.
 *
 * <p>The point where the trace begins waits until the hand takes hold of it: the button pressed with
 * the pointer on it. Nothing is traced before that. The hand can be lifted and set down again on the
 * point, which then waits where the trace left off. The metal does not wait for ever,
 * though: there is a time for the whole trace, shown under the piece, and what is not traced when
 * it runs out counts as missed. Each template has its own pattern, and the further up the tiers, the
 * harder it is: a wave for netherite, a figure of eight for mithril, a spiral for adamant, each to
 * be followed more closely than the last.
 */
public class UpgradeScreen extends Screen {
    private static final int PANEL_WIDTH = 196, PANEL_HEIGHT = 232;
    private static final int PANEL = 0xFFC6C6C6, PANEL_LIGHT = 0xFFFFFFFF, PANEL_MID = 0xFF8B8B8B, PANEL_DARK = 0xFF373737;
    private static final int TEXT = 0xFF404040, FADED = 0xFF606060;
    private static final int SURFACE_X = 26, SURFACE_Y = 24, PIXEL = 9, SURFACE = Mask.SIZE * PIXEL;
    private static final int TRUE = 0xFF3FA03F, WANDERED = 0xFFE0B030, LEFT = 0xFFC03828;
    /** Each template's line before it is traced: netherite's dark, mithril's sea-green, adamant's crimson. */
    private static final int[] AHEAD = {0xFFE8E0D0, 0xFF8CE0D0, 0xFFF0A0B4};
    /** Pixels between two points of the line. */
    private static final double SPACING = 2.5;
    /** How near the waiting point the pointer has to be, with the button pressed, to take hold of the line. */
    private static final double TAKE_HOLD = 6.0;
    /** How far off the line the pointer may stray before it lets go, and how many points ahead it may run. */
    private static final double LEASH = 14.0;
    private static final int AHEAD_REACH = 10;
    /** Within this of the line a point is traced true; at the coarse distance it is not traced at all. */
    private static final double FINE = 2.5;
    private static final double[] COARSE = {9.0, 7.5, 6.0};
    /** Seconds for the whole trace. */
    private static final double[] SECONDS = {14.0, 15.0, 18.0};
    private static final int CLOSE_TICKS = 40;

    private final UpgradePayloads.Open work;
    private final int pattern;
    private final List<double[]> line = new ArrayList<>();
    private double[] worth;
    private int next;
    private int left, top;
    private boolean down;
    /** Whether the hand has hold of the line. */
    private boolean holding;
    private long started;
    private boolean done;
    private float accuracy;
    private int closeCountdown = -1;
    private int[] pixels;

    public UpgradeScreen(UpgradePayloads.Open work) {
        super(Component.translatable("gui.hardwrought.upgrade"));
        this.work = work;
        this.pattern = Math.clamp(work.pattern(), 0, COARSE.length - 1);
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        pixels = ItemPixels.of(piece());
        if (line.isEmpty()) {
            line.addAll(points(pattern));
            worth = new double[line.size()];
        }
    }

    private ItemStack piece() {
        return minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
    }

    /** The pattern as a curve over the unit square, {@code t} from 0 to 1. */
    private static double[] curve(int pattern, double t) {
        return switch (pattern) {
            case 1 -> new double[] {0.5 + 0.42 * Math.sin(2 * Math.PI * t), 0.5 + 0.36 * Math.sin(4 * Math.PI * t)};
            case 2 -> {
                double angle = 2 * Math.PI * 2.5 * t, radius = 0.46 * (1 - 0.8 * t);
                yield new double[] {0.5 + radius * Math.cos(angle), 0.5 + radius * Math.sin(angle)};
            }
            default -> new double[] {0.06 + 0.88 * t, 0.5 + 0.32 * Math.sin(3 * Math.PI * t)};
        };
    }

    /** The pattern as points on the surface, evenly spaced along it. */
    private static List<double[]> points(int pattern) {
        List<double[]> points = new ArrayList<>();
        double[] last = null;
        for (int i = 0; i <= 4000; i++) {
            double[] at = curve(pattern, i / 4000.0);
            double x = at[0] * SURFACE, y = at[1] * SURFACE;
            if (last == null || Math.hypot(x - last[0], y - last[1]) >= SPACING) {
                last = new double[] {x, y};
                points.add(last);
            }
        }
        return points;
    }

    private double secondsLeft() {
        return started == 0 ? SECONDS[pattern] : Math.max(0, SECONDS[pattern] - (System.currentTimeMillis() - started) / 1000.0);
    }

    /**
     * Follows the pointer along the line.
     *
     * <p>The hand first has to take hold: the button pressed with the pointer on the waiting point.
     * Until then nothing happens, however near the pointer comes — the point waits. Once it has hold,
     * the trace runs along with the pointer: each point of the line is done as the pointer passes it,
     * and is worth as much as the pointer was near the line just there. Letting go of the button, or
     * straying a long way off, lets go of the line; the point then waits where the trace stopped.
     */
    private void trace(double mouseX, double mouseY) {
        if (done) return;
        if (!down) {
            holding = false;
            return;
        }
        double x = mouseX - left - SURFACE_X, y = mouseY - top - SURFACE_Y;
        if (!holding) {
            if (next >= line.size() || Math.hypot(x - line.get(next)[0], y - line.get(next)[1]) > TAKE_HOLD) return;
            holding = true;
            if (started == 0) started = System.currentTimeMillis();
        }
        // The point of the line the pointer is nearest to, from the one just done to a little ahead.
        int nearest = -1;
        double distance = Double.MAX_VALUE;
        for (int i = Math.max(0, next - 1); i < Math.min(line.size(), next + AHEAD_REACH); i++) {
            double d = Math.hypot(x - line.get(i)[0], y - line.get(i)[1]);
            if (d < distance) {
                distance = d;
                nearest = i;
            }
        }
        if (nearest < 0 || distance > LEASH) {
            holding = false;
            return;
        }
        if (nearest < next) return;
        // How far off the line itself the pointer is, not how far from a point of it.
        double off = Math.min(nearest > 0 ? fromStretch(x, y, nearest - 1) : distance,
                nearest + 1 < line.size() ? fromStretch(x, y, nearest) : distance);
        double value = off <= FINE ? 1.0 : Math.max(0, 1 - (off - FINE) / (COARSE[pattern] - FINE));
        for (int i = next; i <= nearest; i++) worth[i] = value;
        next = nearest + 1;
    }

    /** The distance from a place to the stretch of line between this point and the next. */
    private double fromStretch(double x, double y, int from) {
        double[] a = line.get(from), b = line.get(from + 1);
        double dx = b[0] - a[0], dy = b[1] - a[1], length = dx * dx + dy * dy;
        double along = length == 0 ? 0 : Math.clamp(((x - a[0]) * dx + (y - a[1]) * dy) / length, 0, 1);
        return Math.hypot(x - a[0] - along * dx, y - a[1] - along * dy);
    }

    private void finish() {
        if (done) return;
        done = true;
        double sum = 0;
        for (double value : worth) sum += value;
        accuracy = (float) (sum / worth.length);
        ClientPlayNetworking.send(new UpgradePayloads.Finish(work.table(), accuracy));
        closeCountdown = CLOSE_TICKS;
    }

    @Override
    public void tick() {
        if (closeCountdown > 0 && --closeCountdown == 0) onClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) return true;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        down = true;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) down = false;
        return super.mouseReleased(event);
    }

    /** Leaving half way is a finished trace with the rest of it missed: the metal is on the piece. */
    @Override
    public void onClose() {
        if (!done && started != 0) finish();
        super.onClose();
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
        trace(mouseX, mouseY);
        if (!done && (next >= line.size() || started != 0 && secondsLeft() <= 0)) finish();

        Component becomes = Component.translatable("gui.hardwrought.upgrade.into",
                new ItemStack(BuiltInRegistries.ITEM.getValue(work.result())).getHoverName());
        graphics.text(font, becomes, left + 10, top + 8, TEXT, false);

        int sx = left + SURFACE_X, sy = top + SURFACE_Y;
        graphics.fill(sx - 1, sy - 1, sx + SURFACE + 1, sy + SURFACE + 1, PANEL_DARK);
        graphics.fill(sx, sy, sx + SURFACE, sy + SURFACE, 0xFF26221E);
        // The piece under the pattern, dimmed so that the line stands out on it.
        if (pixels != null && pixels.length == Mask.SIZE * Mask.SIZE) {
            for (int y = 0; y < Mask.SIZE; y++) {
                for (int x = 0; x < Mask.SIZE; x++) {
                    int colour = pixels[y * Mask.SIZE + x];
                    if ((colour >>> 24) <= 16) continue;
                    int r = (colour >> 16 & 0xFF) * 5 / 10, g = (colour >> 8 & 0xFF) * 5 / 10, b = (colour & 0xFF) * 5 / 10;
                    graphics.fill(sx + x * PIXEL, sy + y * PIXEL, sx + (x + 1) * PIXEL, sy + (y + 1) * PIXEL,
                            0xFF000000 | r << 16 | g << 8 | b);
                }
            }
        }
        for (int i = line.size() - 1; i >= 0; i--) {
            int px = sx + (int) Math.round(line.get(i)[0]), py = sy + (int) Math.round(line.get(i)[1]);
            int colour = i >= next ? (done ? LEFT : AHEAD[pattern])
                    : worth[i] >= 0.8 ? TRUE : worth[i] >= 0.35 ? WANDERED : LEFT;
            graphics.fill(px - 1, py - 1, px + 2, py + 2, colour);
        }
        if (!done && next < line.size()) {
            // Where the hand has to be set down: at the beginning, or where the trace left off.
            int px = sx + (int) Math.round(line.get(next)[0]), py = sy + (int) Math.round(line.get(next)[1]);
            boolean pulse = System.currentTimeMillis() / 300 % 2 == 0;
            graphics.fill(px - 5, py - 5, px + 6, py + 6, PANEL_DARK);
            graphics.fill(px - 4, py - 4, px + 5, py + 5, holding ? TRUE : pulse ? 0xFFFFFFFF : AHEAD[pattern]);
        }

        // The time the metal gives, running down.
        int by = sy + SURFACE + 6;
        graphics.fill(sx - 1, by - 1, sx + SURFACE + 1, by + 5, PANEL_DARK);
        graphics.fill(sx, by, sx + SURFACE, by + 4, PANEL_MID);
        graphics.fill(sx, by, sx + (int) Math.round(SURFACE * secondsLeft() / SECONDS[pattern]), by + 4, 0xFFE0A040);

        Component line1, line2;
        if (done) {
            int percent = Math.round(accuracy * 100);
            line1 = Component.translatable("gui.hardwrought.upgrade.traced", percent);
            line2 = Component.translatable(accuracy >= 0.9f ? "gui.hardwrought.upgrade.fine"
                    : accuracy >= Upgrading.FAIR ? "gui.hardwrought.upgrade.fair" : "gui.hardwrought.upgrade.poor");
        } else {
            line1 = Component.translatable(holding ? "gui.hardwrought.upgrade.hold"
                    : started == 0 ? "gui.hardwrought.upgrade.begin" : "gui.hardwrought.upgrade.again");
            line2 = Component.translatable("gui.hardwrought.upgrade.colours");
        }
        int ty = by + 10;
        for (Component text : List.of(line1, line2)) {
            for (var row : font.split(text, PANEL_WIDTH - 16)) {
                graphics.text(font, row, left + (PANEL_WIDTH - font.width(row)) / 2, ty, text == line1 ? TEXT : FADED, false);
                ty += font.lineHeight + 1;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
