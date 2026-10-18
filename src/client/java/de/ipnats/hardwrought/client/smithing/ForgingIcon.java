package de.ipnats.hardwrought.client.smithing;

import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.smithing.ForgingState;
import de.ipnats.hardwrought.smithing.Heat;
import de.ipnats.hardwrought.smithing.Mask;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * A piece on the anvil, as it lies in a slot: not the bars it was begun from, but the shape it has been
 * beaten into so far — the metal already where the finished part has it in that part's colours, what still
 * stands proud in shadow — glowing as hot as it is, with a bar along its foot for how far the work has got.
 * The same picture the anvil's own screen draws, one pixel to a square.
 */
public final class ForgingIcon {
    private ForgingIcon() { }

    /** Draws the piece in place of its item. Returns false where it is no piece in work, to be drawn as usual. */
    public static boolean draw(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        ForgingState state = stack.get(ModDataComponents.FORGING_STATE);
        if (state == null) return false;
        var result = BuiltInRegistries.ITEM.getValue(state.result());
        int[] source = ItemPixels.of(stack), target = ItemPixels.of(new ItemStack(result));
        if (source == null || target == null) return false;
        Mask current = state.currentMask(), wanted = state.targetMask();
        var level = Minecraft.getInstance().level;
        double temperature = level == null ? Heat.AMBIENT : Heat.of(stack, level.getGameTime());
        float glow = (float) Math.clamp((temperature - 450.0) / 1200.0, 0.0, 0.45);
        for (int py = 0; py < Mask.SIZE; py++) {
            for (int px = 0; px < Mask.SIZE; px++) {
                if (!current.get(px, py)) continue;
                boolean shaped = wanted.get(px, py);
                int colour = (shaped ? target : source)[py * Mask.SIZE + px];
                if ((colour >>> 24) < 16) colour = 0xFF8A8A8A;
                colour = tint(colour | 0xFF000000, 0xFFFF7020, glow);
                // Metal where the finished part has none stands proud: in shadow, as on the anvil.
                if (!shaped) colour = tint(colour, 0xFF000000, 0.4f);
                graphics.fill(x + px, y + py, x + px + 1, y + py + 1, colour);
            }
        }
        int done = (int) Math.round(13 * Math.clamp(state.progress(), 0.0, 1.0));
        graphics.fill(x + 2, y + 13, x + 15, y + 15, 0xFF000000);
        graphics.fill(x + 2, y + 13, x + 2 + done, y + 14, 0xFFE08A30);
        return true;
    }

    private static int tint(int from, int to, float share) {
        int r = (int) (((from >> 16) & 0xFF) * (1 - share) + ((to >> 16) & 0xFF) * share);
        int g = (int) (((from >> 8) & 0xFF) * (1 - share) + ((to >> 8) & 0xFF) * share);
        int b = (int) ((from & 0xFF) * (1 - share) + (to & 0xFF) * share);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
