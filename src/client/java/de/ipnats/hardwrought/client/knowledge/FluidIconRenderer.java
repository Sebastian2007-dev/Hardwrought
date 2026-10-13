package de.ipnats.hardwrought.client.knowledge;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.knowledge.FluidIcons;
import de.ipnats.hardwrought.refining.RefineryMenu;
import de.ipnats.hardwrought.refining.RefineryProduct;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Draws a liquid or a gas of a recipe as itself in a 16×16 slot: a liquid as its still texture, tinted
 * as it is in the world; a gas as a cloud of its colour, since a gas has no texture of its own.
 */
public final class FluidIconRenderer {
    private FluidIconRenderer() { }

    public static void render(GuiGraphicsExtractor graphics, FluidIcons.Ingredient ingredient, int x, int y) {
        if (ingredient.gas()) {
            GasKind kind = ingredient.gasKind();
            int colour = kind == null ? 0xFFAAAAAA : RefineryMenu.colour(RefineryMenu.substance(kind));
            int body = 0xC0000000 | (colour & 0xFFFFFF);
            int light = 0xE0000000 | (brighter(colour) & 0xFFFFFF);
            // A loose cloud: three overlapping puffs.
            puff(graphics, x + 2, y + 6, 7, body);
            puff(graphics, x + 7, y + 3, 7, body);
            puff(graphics, x + 6, y + 8, 8, body);
            graphics.fill(x + 8, y + 4, x + 11, y + 5, light);
            graphics.fill(x + 3, y + 7, x + 6, y + 8, light);
            return;
        }
        Fluid fluid = ingredient.fluid();
        if (fluid == null) return;
        Identifier texture = texture(fluid);
        int tint = fluid == Fluids.WATER ? 0xFF3F76E4 : 0xFFFFFFFF;
        var sprite = graphics.getSprite(new SpriteId(TextureAtlas.LOCATION_BLOCKS, texture));
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16, tint);
    }

    private static void puff(GuiGraphicsExtractor graphics, int x, int y, int size, int colour) {
        graphics.fill(x + 1, y, x + size - 1, y + size, colour);
        graphics.fill(x, y + 1, x + size, y + size - 1, colour);
    }

    private static int brighter(int colour) {
        int r = Math.min(255, ((colour >> 16) & 0xFF) + 60);
        int g = Math.min(255, ((colour >> 8) & 0xFF) + 60);
        int b = Math.min(255, (colour & 0xFF) + 60);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The still texture of a liquid in the block atlas. */
    private static Identifier texture(Fluid fluid) {
        if (fluid == Fluids.WATER) return Identifier.withDefaultNamespace("block/water_still");
        if (fluid == Fluids.LAVA) return Identifier.withDefaultNamespace("block/lava_still");
        if (fluid == de.ipnats.hardwrought.oil.ModFluids.CRUDE_OIL) return Hardwrought.id("block/crude_oil_still");
        for (RefineryProduct product : RefineryProduct.values()) {
            if (product.source() == fluid) return Hardwrought.id("block/fluid/" + product.getSerializedName() + "_still");
        }
        return Identifier.withDefaultNamespace("block/water_still");
    }
}
