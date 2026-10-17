package de.ipnats.hardwrought.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** One layer of a resolved item model: its quads and where it is placed, to draw it as a ghost. */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface ItemLayerRenderStateAccessor {
    @Accessor("quads")
    ItemQuads hardwrought$quads();

    @Invoker("applyTransform")
    void hardwrought$applyTransform(PoseStack.Pose pose);
}
