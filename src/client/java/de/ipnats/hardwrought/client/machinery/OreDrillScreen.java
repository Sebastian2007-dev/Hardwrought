package de.ipnats.hardwrought.client.machinery;

import de.ipnats.hardwrought.client.knowledge.ShadowItem;
import de.ipnats.hardwrought.core.networking.OreDrillPayloads;
import de.ipnats.hardwrought.geology.DrillTier;
import de.ipnats.hardwrought.machinery.OreDrillBlockEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * What the ore drill shows when it is used: how it stands, where its ore goes, and everything the
 * chunk under it holds — what this frame brings up and how much of each, and what would need a better
 * frame. The drill keeps nothing, so there is nothing here to take out: this is a reading, kept
 * current once a second for as long as it is open.
 *
 * <p>An ore the player has never come across stays a black shape with no name, as in the compendium.
 */
public class OreDrillScreen extends Screen {
    private static final int WIDTH = 248;
    private static final int HEIGHT = 226;
    private static final int ROW = 19;
    private static final int LIST_TOP = 92;
    private static final int ROWS = (HEIGHT - LIST_TOP - 8) / ROW;
    private static final int TEXT = 0xFF404040;
    private static final int FADED = 0xFF7A7A7A;
    private static final int GOOD = 0xFF2F6B2A;
    private static final int BAD = 0xFF8E2A1E;
    private static final int WAITING = 0xFF8A6A12;

    private OreDrillPayloads.Info info;
    private int left;
    private int top;
    private int scroll;
    private int ticks;

    public OreDrillScreen(OreDrillPayloads.Info info) {
        super(Component.translatable(info.scan() ? "item.hardwrought.ore_scanner" : "block.hardwrought.ore_drill"));
        this.info = info;
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(OreDrillPayloads.Info.TYPE, (payload, context) ->
                context.client().execute(() -> receive(context.client(), payload)));
    }

    private static void receive(Minecraft client, OreDrillPayloads.Info payload) {
        if (client.gui.screen() instanceof OreDrillScreen open && !payload.scan() && open.info.pos().equals(payload.pos())) {
            open.info = payload;
        } else if (payload.open()) {
            client.gui.setScreen(new OreDrillScreen(payload));
        }
        // An update for a screen that has since been closed is simply dropped.
    }

    public OreDrillPayloads.Info info() {
        return info;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
    }

    @Override
    public void tick() {
        // A scan is a reading of one moment; only a drill's screen is kept current.
        if (!info.scan() && ++ticks % 20 == 0 && ClientPlayNetworking.canSend(OreDrillPayloads.Watch.TYPE)) {
            ClientPlayNetworking.send(new OreDrillPayloads.Watch(info.pos()));
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics, mouseX, mouseY, delta);
        panel(graphics, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int x = left + 10;
        int y = top + 8;
        graphics.text(font, getTitle(), x, y, TEXT, false);
        if (info.scan()) {
            graphics.textWithWordWrap(font, Component.translatable("gui.hardwrought.ore_scanner.hint"),
                    x, y + 16, WIDTH - 20, FADED, false);
            y = top + 70;
            graphics.fill(x, y, left + WIDTH - 10, y + 1, 0xFF555555);
            graphics.fill(x, y + 1, left + WIDTH - 10, y + 2, 0xFFFFFFFF);
            graphics.text(font, Component.translatable("gui.hardwrought.ore_drill.chunk", info.chunkX(), info.chunkZ()),
                    x, y + 5, TEXT, false);
            drawOres(graphics, mouseX, mouseY);
            return;
        }
        Component tier = info.tier() == 0 ? Component.translatable("gui.hardwrought.ore_drill.no_tier")
                : tierName(info.tier());
        graphics.text(font, tier, left + WIDTH - 10 - font.width(tier), y, FADED, false);

        y += 14;
        OreDrillBlockEntity.Status status = OreDrillBlockEntity.Status.values()[
                Math.clamp(info.status(), 0, OreDrillBlockEntity.Status.values().length - 1)];
        graphics.text(font, statusLine(status), x, y, statusColour(status), false);

        y += 12;
        int barWidth = WIDTH - 20;
        graphics.fill(x - 1, y - 1, x + barWidth + 1, y + 6, 0xFF373737);
        graphics.fill(x, y, x + barWidth, y + 5, 0xFF8B8B8B);
        int filled = Math.round(barWidth * Math.clamp(info.progress(), 0.0f, 1.0f));
        graphics.fill(x, y, x + filled, y + 5, status == OreDrillBlockEntity.Status.WORKING ? 0xFF6F8F4A : 0xFF6B6B6B);

        y += 11;
        graphics.text(font, Component.translatable("gui.hardwrought.ore_drill.speed",
                String.format(Locale.ROOT, "%.0f", Math.abs(info.speed()))), x, y, TEXT, false);
        y += 11;
        Component side = Component.translatable("gui.hardwrought.direction."
                + Direction.from3DDataValue(info.output()).getSerializedName());
        Component output = info.into() == null
                ? Component.translatable("gui.hardwrought.ore_drill.output.ground", side)
                : Component.translatable("gui.hardwrought.ore_drill.output.into", side,
                        BuiltInRegistries.BLOCK.getValue(info.into()).getName());
        graphics.text(font, output, x, y, TEXT, false);

        y += 14;
        graphics.fill(x, y, left + WIDTH - 10, y + 1, 0xFF555555);
        graphics.fill(x, y + 1, left + WIDTH - 10, y + 2, 0xFFFFFFFF);
        y += 5;
        graphics.text(font, Component.translatable("gui.hardwrought.ore_drill.chunk", info.chunkX(), info.chunkZ()),
                x, y, TEXT, false);
        drawOres(graphics, mouseX, mouseY);
    }

    private void drawOres(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<OreDrillPayloads.Ore> ores = info.ores();
        int x = left + 10;
        int y = top + LIST_TOP;
        if (ores.isEmpty()) {
            graphics.text(font, Component.translatable("gui.hardwrought.ore_drill.nothing"), x, y + 4, FADED, false);
            return;
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, ores.size() - ROWS));
        int right = left + WIDTH - 14;
        for (int row = 0; row < ROWS && scroll + row < ores.size(); row++) {
            OreDrillPayloads.Ore ore = ores.get(scroll + row);
            int rowY = y + row * ROW;
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(ore.ore()));
            boolean known = ore.known() > 0;
            boolean reached = ore.share() >= 0;
            ShadowItem.render(graphics, stack, x, rowY, known);
            Component name = known ? stack.getHoverName() : ShadowItem.UNKNOWN_NAME;
            graphics.text(font, name, x + 20, rowY + 1, reached || info.scan() ? TEXT : FADED, false);
            // How much of the chunk it is, drawn under its name.
            int barX = x + 20;
            int barWidth = 96;
            graphics.fill(barX, rowY + 12, barX + barWidth, rowY + 14, 0xFF8B8B8B);
            graphics.fill(barX, rowY + 12, barX + Math.max(1, Math.round(barWidth * ore.presence())), rowY + 14,
                    reached ? 0xFF6F8F4A : 0xFF6B6B6B);
            if (info.scan()) {
                // The scanner: how much of the chunk it is, and the drill it takes.
                Component presence = Component.literal(String.format(Locale.ROOT, "%.1f %%", ore.presence() * 100));
                Component needs = Component.translatable("gui.hardwrought.ore_drill.needs", ore.needs());
                graphics.text(font, presence, right - font.width(presence), rowY + 1, TEXT, false);
                graphics.text(font, needs, right - font.width(needs), rowY + 10, FADED, false);
            } else {
                Component amount = reached
                        ? Component.literal(String.format(Locale.ROOT, "%.1f %%", ore.share() * 100))
                        : Component.translatable("gui.hardwrought.ore_drill.needs", ore.needs());
                graphics.text(font, amount, right - font.width(amount), rowY + 5, reached ? GOOD : FADED, false);
            }
            if (mouseX >= x && mouseX < right && mouseY >= rowY && mouseY < rowY + ROW - 1) {
                graphics.setComponentTooltipForNextFrame(font, List.of(name,
                        Component.translatable("gui.hardwrought.ore_drill.presence",
                                String.format(Locale.ROOT, "%.1f", ore.presence() * 100))), mouseX, mouseY);
            }
        }
        if (ores.size() > ROWS) {
            // Where in the list the reader is, down the right edge.
            int trackTop = y;
            int trackHeight = ROWS * ROW - 2;
            int thumb = Math.max(8, trackHeight * ROWS / ores.size());
            int thumbTop = trackTop + (trackHeight - thumb) * scroll / Math.max(1, ores.size() - ROWS);
            graphics.fill(left + WIDTH - 9, trackTop, left + WIDTH - 7, trackTop + trackHeight, 0xFF8B8B8B);
            graphics.fill(left + WIDTH - 9, thumbTop, left + WIDTH - 7, thumbTop + thumb, 0xFF555555);
        }
    }

    private Component statusLine(OreDrillBlockEntity.Status status) {
        return switch (status) {
            case INCOMPLETE -> Component.translatable("gui.hardwrought.ore_drill.status.incomplete", info.missing());
            case BARREN -> Component.translatable("gui.hardwrought.ore_drill.status.barren");
            case STILL -> Component.translatable("gui.hardwrought.ore_drill.status.still");
            case BLOCKED -> Component.translatable("gui.hardwrought.ore_drill.status.blocked");
            case WORKING -> Component.translatable("gui.hardwrought.ore_drill.status.working",
                    String.format(Locale.ROOT, "%.0f", info.seconds()));
        };
    }

    private static int statusColour(OreDrillBlockEntity.Status status) {
        return switch (status) {
            case WORKING -> GOOD;
            case STILL, BLOCKED -> WAITING;
            case INCOMPLETE, BARREN -> BAD;
        };
    }

    private static Component tierName(int level) {
        return Component.translatable("drill_tier.hardwrought." + DrillTier.byLevel(level).serializedName());
    }

    /** A plain raised panel in the colours of the inventory. */
    private static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFFC6C6C6);
        graphics.fill(x, y, x + width, y + 2, 0xFFFFFFFF);
        graphics.fill(x, y, x + 2, y + height, 0xFFFFFFFF);
        graphics.fill(x, y + height - 2, x + width, y + height, 0xFF555555);
        graphics.fill(x + width - 2, y, x + width, y + height, 0xFF555555);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        scroll = Math.clamp(scroll + (scrollY > 0 ? -1 : 1), 0, Math.max(0, info.ores().size() - ROWS));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
