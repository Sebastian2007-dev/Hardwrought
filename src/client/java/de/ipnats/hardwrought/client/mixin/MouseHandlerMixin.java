package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.client.magic.ActionCasting;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Action casting: while a rune is being drawn the mouse moves the pen, not the head. The movement
 * gathered since the last frame is handed to the pen before vanilla turns the view by it, and the
 * view is left with nothing to turn by. See {@link ActionCasting}.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void hardwrought$draw(CallbackInfo callback) {
        if (!ActionCasting.capturing()) return;
        if (accumulatedDX != 0 || accumulatedDY != 0) ActionCasting.move(accumulatedDX, accumulatedDY);
        accumulatedDX = 0;
        accumulatedDY = 0;
    }
}
