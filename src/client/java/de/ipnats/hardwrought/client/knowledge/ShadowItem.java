package de.ipnats.hardwrought.client.knowledge;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * How an undiscovered thing is drawn: the shape of the item in black, and a name of question marks.
 *
 * <p>Section 81 asks for an entry that is present but empty rather than one that is missing. A black
 * shadow does that better than a blank square, because the outline is a clue — a player who sees an
 * ingot-shaped hole in a recipe knows to go looking for an ingot.
 *
 * <p>The silhouette of a flat item is its model's particle texture tinted to black, so its outline is
 * the clue. A block is drawn as a little cube in the inventory, and its texture is a full square, so
 * a block's shadow is the cube's outline instead.
 *
 * <p>The model is resolved per draw rather than cached. That is what the inventory does for every
 * stack it renders anyway, and a cache of atlas sprites would go stale on the next resource reload.
 */
public final class ShadowItem {
    /** The name every undiscovered entry carries. */
    public static final Component UNKNOWN_NAME = Component.literal("???");
    private static final int BLACK = 0xFF000000;
    private static final ItemStackRenderState STATE = new ItemStackRenderState();
    private static final RandomSource RANDOM = RandomSource.create(0L);

    private ShadowItem() { }

    /** Draws one stack as itself when it is known, and as its own shadow when it is not. */
    public static void render(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, boolean known) {
        if (stack.isEmpty()) return;
        if (known) {
            graphics.item(stack, x, y);
            graphics.itemDecorations(Minecraft.getInstance().font, stack, x, y);
            return;
        }
        TextureAtlasSprite sprite = silhouette(stack);
        if (STATE.usesBlockLight()) {
            // A block is drawn in the inventory as a little cube, not as its flat texture. Tinting
            // the texture black would give a full square that reads as a hole in the page rather
            // than as a block; the cube's outline says "a block goes here".
            cube(graphics, x, y);
        } else if (sprite == null) {
            graphics.fill(x + 2, y + 2, x + 14, y + 14, BLACK);
        } else {
            graphics.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    sprite, x, y, 16, 16, BLACK);
        }
    }

    /**
     * The outline of an item-sized block seen from the inventory's angle: a hexagon, pointed at top
     * and bottom, drawn one pixel row at a time.
     */
    private static void cube(GuiGraphicsExtractor graphics, int x, int y) {
        for (int row = 1; row < 16; row++) {
            int half = row < 5 ? row * 7 / 4 : row > 11 ? (16 - row) * 7 / 4 : 7;
            if (half <= 0) continue;
            graphics.fill(x + 8 - half, y + row, x + 8 + half, y + row + 1, BLACK);
        }
    }

    /** The tooltip for one entry: its real name, or question marks and why. */
    public static List<Component> tooltip(ItemStack stack, boolean known) {
        if (known) return net.minecraft.client.gui.screens.Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
        return List.of(UNKNOWN_NAME,
                Component.translatable("gui.hardwrought.compendium.undiscovered")
                        .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static TextureAtlasSprite silhouette(ItemStack stack) {
        Minecraft client = Minecraft.getInstance();
        STATE.clear();
        if (client.level == null) return null;
        try {
            client.getItemModelResolver().updateForTopItem(STATE, stack, ItemDisplayContext.GUI,
                    client.level, null, 0);
            Material.Baked baked = STATE.pickParticleMaterial(RANDOM);
            return baked == null ? null : baked.sprite();
        } catch (RuntimeException failure) {
            // A broken model must not be able to crash a screen the player opened on purpose.
            return null;
        }
    }

}
