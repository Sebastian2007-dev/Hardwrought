package de.ipnats.hardwrought.client.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The layers of a resolved item model, to draw them as a ghost (see {@code GhostItem}). */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
    @Accessor("activeLayerCount")
    int hardwrought$activeLayerCount();

    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] hardwrought$layers();
}
