package de.ipnats.hardwrought.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The render state a screen is drawn into.
 *
 * <p>Vanilla hands out its pictures in a picture — a book, a banner, an entity — through methods of
 * its own and keeps the state they are added to to itself. The compendium's structure view is one
 * more such picture, and needs the state to add itself to.
 */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {
    @Accessor("guiRenderState")
    GuiRenderState hardwrought$renderState();
}
