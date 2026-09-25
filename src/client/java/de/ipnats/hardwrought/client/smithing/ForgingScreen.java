package de.ipnats.hardwrought.client.smithing;

import de.ipnats.hardwrought.client.survival.MeltingPointTooltip;
import de.ipnats.hardwrought.core.networking.ForgingPayloads;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.smithing.ForgingState;
import de.ipnats.hardwrought.smithing.Heat;
import de.ipnats.hardwrought.smithing.Mask;
import de.ipnats.hardwrought.smithing.Smithing;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * The anvil, from the player's side: the piece drawn large, one square per pixel of the item it is,
 * with the shape it is meant to become showing faintly behind it.
 *
 * <p>A click is a blow. Where metal stands proud of the outline it is driven in; where the outline
 * has a gap the metal around is drawn into it; and a blow on a spot that is already right does
 * nothing but count against the work. The piece glows with its heat and dims as it cools, and below
 * working heat every blow strains it — which is the moment to take it back to the fire.
 *
 * <p>Nothing here decides anything. The screen reads the piece in the off hand every frame, and the
 * piece is what the server says it is.
 */
public class ForgingScreen extends Screen {
    private static final int CELL = 8;
    private static final int GRID = CELL * Mask.SIZE;
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 170;
    /** Milliseconds between two blows; the arm needs a moment to come back up. */
    private static final long BLOW_INTERVAL = 150;
    // Vanilla container colours and bevels, kept as fills so the wide screen is never stretched.
    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_LIGHT = 0xFFFFFFFF;
    private static final int PANEL_MID = 0xFF8B8B8B;
    private static final int PANEL_DARK = 0xFF373737;
    private static final int GRID_BACK = 0xFF252525;
    private static final int TEXT = 0xFF404040;
    private static final int FADED = 0xFF606060;
    private static final int WARN = 0xFFB03020;

    private final BlockPos anvil;
    private final List<Identifier> results;
    private int left;
    private int top;
    private long lastBlow;
    private int closeCountdown = -1;
    private Item finishedAs;
    /** What the piece is being turned into, once the work has begun from this screen. */
    private Item working;
    private net.minecraft.client.gui.components.Button cancel;

    public ForgingScreen(BlockPos anvil, List<Identifier> results) {
        super(Component.translatable("gui.hardwrought.forging"));
        this.anvil = anvil;
        this.results = List.copyOf(results);
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        // Gives up on the piece: what went into it comes back, the work on it does not.
        cancel = addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.translatable("gui.hardwrought.forging.cancel"), button -> {
                            ClientPlayNetworking.send(new ForgingPayloads.Cancel());
                            working = null;
                            onClose();
                        })
                .bounds(left + PANEL_WIDTH - 90, top + PANEL_HEIGHT - 26, 80, 18).build());
        cancel.visible = piece().has(ModDataComponents.FORGING_STATE);
    }

    private ItemStack piece() {
        Minecraft client = Minecraft.getInstance();
        return client.player == null ? ItemStack.EMPTY : client.player.getOffhandItem();
    }

    private long now() {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? 0 : client.level.getGameTime();
    }

    @Override
    public void tick() {
        ItemStack piece = piece();
        ForgingState state = piece.get(ModDataComponents.FORGING_STATE);
        if (cancel != null) cancel.visible = state != null && finishedAs == null;
        if (state != null) working = BuiltInRegistries.ITEM.getValue(state.result());
        // The piece in hand has become what it was being made into: the work is done.
        if (closeCountdown < 0 && finishedAs == null && working != null && state == null && piece.is(working)) {
            finishedAs = working;
            closeCountdown = 30;
        }
        if (closeCountdown > 0 && --closeCountdown == 0) onClose();
    }

    /** The board behind everything, drawn first so the give-up button sits on it rather than under it. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        drawRaisedPanel(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(font, title, left + 10, top + 8, TEXT, false);

        ItemStack piece = piece();
        ForgingState state = piece.get(ModDataComponents.FORGING_STATE);
        if (finishedAs != null) {
            graphics.text(font, Component.translatable("gui.hardwrought.forging.done"), left + 10, top + 30, TEXT, false);
            graphics.item(new ItemStack(finishedAs), left + 10, top + 44);
            graphics.text(font, new ItemStack(finishedAs).getHoverName(), left + 30, top + 48, TEXT, false);
            return;
        }
        if (state == null && results.size() > 1) {
            drawChoice(graphics, mouseX, mouseY);
            return;
        }
        Identifier result = state != null ? state.result() : results.getFirst();
        drawWork(graphics, piece, state, result, mouseX, mouseY);
    }

    // ---------------------------------------------------------------- choosing what to make

    private void drawChoice(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, Component.translatable("gui.hardwrought.forging.choose"), left + 10, top + 24, FADED, false);
        for (int index = 0; index < results.size(); index++) {
            int x = left + 10 + (index % 2) * 140;
            int y = top + 40 + (index / 2) * 22;
            boolean hovered = mouseX >= x && mouseX < x + 136 && mouseY >= y && mouseY < y + 20;
            drawRecess(graphics, x, y, 136, 20, hovered ? 0xFFE0E0E0 : PANEL);
            Item item = BuiltInRegistries.ITEM.getValue(results.get(index));
            graphics.item(new ItemStack(item), x + 2, y + 2);
            int cost = costOf(item);
            graphics.text(font, Component.translatable("gui.hardwrought.forging.cost", cost), x + 20, y + 6, FADED, false);
            graphics.text(font, new ItemStack(item).getHoverName(), x + 40, y + 6, TEXT, false);
        }
    }

    private int costOf(Item result) {
        Smithing.Recipe recipe = Smithing.recipeFor(piece().getItem(), BuiltInRegistries.ITEM.getKey(result));
        return recipe == null ? 1 : recipe.count();
    }

    // ---------------------------------------------------------------- the work itself

    private int gridX() {
        return left + 10;
    }

    private int gridY() {
        return top + 24;
    }

    private void drawWork(GuiGraphicsExtractor graphics, ItemStack piece, ForgingState state, Identifier result,
                          int mouseX, int mouseY) {
        Item resultItem = BuiltInRegistries.ITEM.getValue(result);
        int[] sourcePixels = ItemPixels.of(piece);
        int[] targetPixels = ItemPixels.of(new ItemStack(resultItem));
        Mask target = state != null ? state.targetMask() : ItemPixels.mask(targetPixels);
        Mask current = state != null ? state.currentMask() : ItemPixels.mask(sourcePixels);
        double temperature = Heat.of(piece, now());
        Smithing.Recipe recipe = Smithing.recipeFor(piece.getItem(), result);
        double[] range = workingRange(piece);
        boolean ingotWork = recipe != null && Smithing.isIngot(recipe.input());
        // Tinted, not painted over: the piece's own shading has to stay readable while it glows.
        float glow = (float) Math.clamp((temperature - 450.0) / 1200.0, 0.0, 0.45);

        int gx = gridX();
        int gy = gridY();
        drawRecess(graphics, gx - 3, gy - 3, GRID + 6, GRID + 6, GRID_BACK);
        for (int y = 0; y < Mask.SIZE; y++) {
            for (int x = 0; x < Mask.SIZE; x++) {
                boolean has = current.get(x, y);
                boolean wanted = target.get(x, y);
                int cx = gx + x * CELL;
                int cy = gy + y * CELL;
                if (has) {
                    int colour = wanted ? pixel(targetPixels, x, y) : pixel(sourcePixels, x, y);
                    if ((colour >>> 24) < 16) colour = 0xFF8A8A8A;
                    int shown = heated(colour | 0xFF000000, glow);
                    // Metal where the outline has none stands proud and is to be driven in: drawn in
                    // shadow, so the shape still being aimed for reads at a glance.
                    if (!wanted) shown = darker(shown, 0.45f);
                    graphics.fill(cx, cy, cx + CELL, cy + CELL, shown);
                } else if (wanted) {
                    // A gap the metal still has to be drawn into: the finished item's own colour,
                    // faint, with a fine edge so it reads as a place rather than a stain.
                    int ghost = pixel(targetPixels, x, y);
                    graphics.fill(cx, cy, cx + CELL, cy + CELL, (ghost & 0x00FFFFFF) | 0x50000000);
                    graphics.outline(cx, cy, CELL, CELL, 0x40FFFFFF);
                }
            }
        }
        int hx = (mouseX - gx) / CELL;
        int hy = (mouseY - gy) / CELL;
        if (mouseX >= gx && mouseY >= gy && hx < Mask.SIZE && hy < Mask.SIZE) {
            // The square the blow will cover: as wide as the hammer in hand reaches.
            var player = net.minecraft.client.Minecraft.getInstance().player;
            int reach = Math.max(1, player == null ? 3
                    : de.ipnats.hardwrought.smithing.Hammers.reach(player.getMainHandItem()));
            int from = de.ipnats.hardwrought.smithing.Hammers.from(reach);
            graphics.outline(gx + (hx + from) * CELL, gy + (hy + from) * CELL, CELL * reach, CELL * reach, 0xC0FFFFFF);
        }

        int ix = gx + GRID + 14;
        int iy = gy;
        graphics.item(new ItemStack(resultItem), ix, iy);
        graphics.text(font, new ItemStack(resultItem).getHoverName(), ix + 20, iy + 4, TEXT, false);
        iy += 24;
        boolean cold = !ingotWork && range != null && temperature < range[0];
        boolean tooHot = recipe != null && recipe.part() && range != null && temperature > range[1];
        graphics.text(font, Component.translatable("gui.hardwrought.forging.temperature",
                String.format(Locale.ROOT, "%.0f", temperature)), ix, iy, cold || tooHot ? WARN : heatColour(glow), false);
        iy += 11;
        if (range != null) {
            Component heatRange = ingotWork && recipe.part()
                    ? Component.translatable("gui.hardwrought.forging.optimal_max",
                            String.format(Locale.ROOT, "%.0f", range[1]))
                    : Component.translatable("gui.hardwrought.forging.workable",
                            String.format(Locale.ROOT, "%.0f", range[0]), String.format(Locale.ROOT, "%.0f", range[1]));
            graphics.text(font, heatRange, ix, iy, FADED, false);
            iy += 11;
            drawHeatBar(graphics, ix, iy, temperature,
                    ingotWork && recipe.part() ? new double[] { 0, range[1] } : range);
            iy += 10;
        }
        if (cold) {
            graphics.text(font, Component.translatable("gui.hardwrought.forging.too_cold"), ix, iy, WARN, false);
            iy += 11;
        } else if (tooHot) {
            graphics.text(font, Component.translatable("gui.hardwrought.forging.too_hot"), ix, iy, WARN, false);
            iy += 11;
        }
        iy += 4;
        if (state != null) {
            graphics.text(font, Component.translatable("gui.hardwrought.forging.progress",
                    Math.round(state.progress() * 100)), ix, iy, TEXT, false);
            iy += 11;
            graphics.text(font, Component.translatable("gui.hardwrought.forging.strikes", state.strikes(),
                    state.strikes() - state.good()), ix, iy, FADED, false);
            iy += 11;
            // Only once there is work to judge: before the first blow there is nothing to say.
            if (recipe != null && recipe.part() && state.strikes() > 0) {
                graphics.text(font, Component.translatable("gui.hardwrought.forging.craftsmanship",
                        Math.round(state.craftsmanship() * 100)), ix, iy, FADED, false);
                iy += 11;
                if (state.overheated() > 0) {
                    graphics.text(font, Component.translatable("gui.hardwrought.forging.overheated",
                            state.overheated()), ix, iy, WARN, false);
                }
            }
        } else {
            graphics.textWithWordWrap(font, Component.translatable("gui.hardwrought.forging.hint"), ix, iy,
                    PANEL_WIDTH - (ix - left) - 8, FADED, false);
        }
    }

    private void drawHeatBar(GuiGraphicsExtractor graphics, int x, int y, double temperature, double[] range) {
        int width = PANEL_WIDTH - (x - left) - 10;
        double top = range[1] * 1.1;
        drawRecess(graphics, x, y, width, 7, 0xFF202020);
        int from = x + 1 + (int) ((width - 2) * range[0] / top);
        int to = x + 1 + (int) ((width - 2) * range[1] / top);
        graphics.fill(from, y + 1, to, y + 6, 0xFFD07A24);
        int mark = x + 1 + (int) ((width - 2) * Math.clamp(temperature / top, 0.0, 1.0));
        graphics.fill(mark - 1, y, mark + 1, y + 7, 0xFFFFFFFF);
    }

    private static void drawRaisedPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL_DARK);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL_LIGHT);
        graphics.fill(x + 3, y + 3, x + width - 1, y + height - 1, PANEL_MID);
        graphics.fill(x + 3, y + 3, x + width - 3, y + height - 3, PANEL);
    }

    /** Vanilla slot-style inset: dark top/left edge and a light bottom/right edge. */
    private static void drawRecess(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill) {
        graphics.fill(x, y, x + width, y + height, PANEL_LIGHT);
        graphics.fill(x, y, x + width - 1, y + height - 1, PANEL_DARK);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
    }

    private double[] workingRange(ItemStack piece) {
        Double melting = MeltingPointTooltip.meltingPoint(BuiltInRegistries.ITEM.getKey(piece.getItem()));
        if (melting == null) return null;
        return new double[] { melting * Smithing.WORKING_MIN, melting * Smithing.WORKING_MAX };
    }

    private static int pixel(int[] pixels, int x, int y) {
        return pixels == null ? 0xFF8A8A8A : pixels[y * Mask.SIZE + x];
    }

    /** Blends a colour towards the orange of hot iron, as far as the heat says. */
    private static int heated(int colour, float glow) {
        int r = (colour >> 16) & 0xFF;
        int g = (colour >> 8) & 0xFF;
        int b = colour & 0xFF;
        r = (int) (r + (255 - r) * glow);
        g = (int) (g + (140 - g) * glow);
        b = (int) (b + (30 - b) * glow);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int darker(int colour, float factor) {
        int r = (int) (((colour >> 16) & 0xFF) * factor);
        int g = (int) (((colour >> 8) & 0xFF) * factor);
        int b = (int) ((colour & 0xFF) * factor);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int heatColour(float glow) {
        return heated(TEXT, glow);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (super.mouseClicked(event, doubled)) return true;
        if (finishedAs != null) return false;
        ItemStack piece = piece();
        ForgingState state = piece.get(ModDataComponents.FORGING_STATE);
        if (state == null && results.size() > 1) {
            for (int index = 0; index < results.size(); index++) {
                int x = left + 10 + (index % 2) * 140;
                int y = top + 40 + (index / 2) * 22;
                if (event.x() >= x && event.x() < x + 136 && event.y() >= y && event.y() < y + 20) {
                    begin(results.get(index));
                    return true;
                }
            }
            return false;
        }
        int cx = (int) Math.floor((event.x() - gridX()) / CELL);
        int cy = (int) Math.floor((event.y() - gridY()) / CELL);
        if (cx < 0 || cy < 0 || cx >= Mask.SIZE || cy >= Mask.SIZE) return false;
        long time = System.currentTimeMillis();
        if (time - lastBlow < BLOW_INTERVAL) return true;
        if (state == null) begin(results.getFirst());
        lastBlow = time;
        ClientPlayNetworking.send(new ForgingPayloads.Strike(anvil, cx, cy));
        return true;
    }

    /** Starts the work, sending the two shapes as this client's textures draw them. */
    private void begin(Identifier result) {
        Item resultItem = BuiltInRegistries.ITEM.getValue(result);
        working = resultItem;
        Mask source = ItemPixels.mask(ItemPixels.of(piece()));
        Mask target = ItemPixels.mask(ItemPixels.of(new ItemStack(resultItem)));
        ClientPlayNetworking.send(new ForgingPayloads.Begin(anvil, result, source.toArray(), target.toArray()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
