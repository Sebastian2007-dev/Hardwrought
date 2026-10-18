package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.client.equipment.BundlePeek;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A bundle held open with shift, in any container screen; see {@link BundlePeek}. */
@Mixin(AbstractContainerScreen.class)
public abstract class BundlePeekMixin {
    @Shadow protected Slot hoveredSlot;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;

    /**
     * The open look is drawn where the tooltip is, last of everything, and stands in for it: only one of the
     * two is drawn. Every container screen draws its tooltip, the inventory with its recipe book included,
     * which does not pass through the screen's own render.
     */
    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void hardwrought$drawBundle(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo callback) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        BundlePeek.render(screen, graphics, hoveredSlot, mouseX, mouseY);
        if (BundlePeek.open(screen)) callback.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void hardwrought$takeFromBundle(MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> callback) {
        if (BundlePeek.click((AbstractContainerScreen<?>) (Object) this, event, leftPos, topPos)) callback.setReturnValue(true);
    }
}
