package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.underworld.Underworld;
import net.minecraft.client.renderer.item.properties.numeric.CompassAngleState;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ItemOwner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * There is no north in the Underworld: every compass spins there, even one bound to a lodestone down
 * there. (The clock already spins outside the overworld.)
 */
@Mixin(CompassAngleState.class)
public abstract class CompassAngleStateMixin {
    @Inject(method = "isValidCompassTargetPos", at = @At("HEAD"), cancellable = true)
    private static void hardwrought$noNorth(ItemOwner owner, GlobalPos target, CallbackInfoReturnable<Boolean> callback) {
        if (owner != null && owner.level() != null && owner.level().dimension() == Underworld.LEVEL) callback.setReturnValue(false);
    }
}
