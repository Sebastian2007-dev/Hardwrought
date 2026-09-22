package de.ipnats.hardwrought.client.knowledge;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
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
 * <p>The silhouette is the item model particle texture tinted to black, which for a flat item model
 * is its own icon. Block faces fill the complete slot and therefore use a neutral mystery cube;
 * otherwise every undiscovered block would look like the same solid black square.
 *
 * <p>The model is resolved per draw rather than cached. That is what the inventory does for every
 * stack it renders anyway, and a cache of atlas sprites would go stale on the next resource reload.
 */
public final class ShadowItem {
    /** The name every undiscovered entry carries. */
    public static final Component UNKNOWN_NAME = Component.literal("???");
    private static final int BLACK = 0xFF000000;
    private static final Identifier UNKNOWN_BLOCK = Identifier.fromNamespaceAndPath(
            "hardwrought", "compendium/unknown_block");
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
        // A block's particle texture fills all 16 pixels and used to become an unreadable black
        // square. Keep genuine silhouettes for shaped items and use a neutral mystery cube for
        // blocks (and models which cannot provide a useful sprite).
        if (stack.getItem() instanceof BlockItem) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, UNKNOWN_BLOCK, x, y, 16, 16);
            return;
        }
        TextureAtlasSprite sprite = silhouette(stack);
        if (sprite == null) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, UNKNOWN_BLOCK, x, y, 16, 16);
        else graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16, BLACK);
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
        if (client.level == null) return null;
        try {
            STATE.clear();
            client.getItemModelResolver().updateForTopItem(STATE, stack, ItemDisplayContext.GUI,
                    client.level, null, 0);
            Material.Baked baked = STATE.pickParticleMaterial(RANDOM);
            return baked == null ? null : baked.sprite();
        } catch (RuntimeException failure) {
            // A model that will not describe itself gets the mystery cube rather than a crash in a
            // screen the player opened on purpose.
            return null;
        }
    }

}
