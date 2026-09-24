package de.ipnats.hardwrought.client.smithing;

import com.mojang.blaze3d.platform.NativeImage;
import de.ipnats.hardwrought.smithing.Mask;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * An item's own sixteen-by-sixteen pixels, read from its texture: the shape the anvil works with and
 * the colours it draws it in.
 *
 * <p>Read from the resource pack rather than from the texture atlas, because the atlas lives on the
 * graphics card. Cached per item, and dropped on a resource reload with everything else.
 */
public final class ItemPixels {
    private static final ItemStackRenderState STATE = new ItemStackRenderState();
    private static final RandomSource RANDOM = RandomSource.create(0L);
    private static final Map<Item, int[]> CACHE = new HashMap<>();

    private ItemPixels() { }

    /** Sixteen rows of sixteen ARGB colours, or null where the texture could not be read. */
    public static int[] of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        return CACHE.computeIfAbsent(stack.getItem(), item -> read(new ItemStack(item)));
    }

    public static void clear() {
        CACHE.clear();
    }

    /** The squares of this item that hold anything at all. */
    public static Mask mask(int[] pixels) {
        Mask mask = Mask.empty();
        if (pixels == null) return mask;
        for (int y = 0; y < Mask.SIZE; y++) {
            for (int x = 0; x < Mask.SIZE; x++) {
                if ((pixels[y * Mask.SIZE + x] >>> 24) > 16) mask = mask.with(x, y, true);
            }
        }
        return mask;
    }

    private static int[] read(ItemStack stack) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;
        try {
            STATE.clear();
            client.getItemModelResolver().updateForTopItem(STATE, stack, ItemDisplayContext.GUI, client.level, null, 0);
            var material = STATE.pickParticleMaterial(RANDOM);
            if (material == null) return null;
            TextureAtlasSprite sprite = material.sprite();
            Identifier name = sprite.contents().name();
            Identifier file = Identifier.fromNamespaceAndPath(name.getNamespace(), "textures/" + name.getPath() + ".png");
            var resource = client.getResourceManager().getResource(file);
            if (resource.isEmpty()) return null;
            try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                int[] pixels = new int[Mask.SIZE * Mask.SIZE];
                // Anything larger than sixteen is sampled down, so a high-resolution pack still works.
                for (int y = 0; y < Mask.SIZE; y++) {
                    for (int x = 0; x < Mask.SIZE; x++) {
                        int sx = x * image.getWidth() / Mask.SIZE;
                        int sy = y * image.getHeight() / Mask.SIZE;
                        pixels[y * Mask.SIZE + x] = image.getPixel(sx, sy);
                    }
                }
                return pixels;
            }
        } catch (Exception failure) {
            // A missing or broken texture must not be able to take the anvil screen down.
            return null;
        }
    }
}
