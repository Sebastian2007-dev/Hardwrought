package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.underworld.Underworld;
import de.ipnats.hardwrought.witchking.WitchKingContent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.CompassAngleState;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * There is no north in the Underworld: every compass spins there, even one bound to a lodestone down
 * there. (The clock already spins outside the overworld.) All but one: a soul compass is drawn to the
 * souls of the Witch Kings, not to the north, and that is the one thing it is for.
 *
 * <p>The check that decides whether a needle points is not told which item it is drawing, so the
 * angle's calculation, which is, notes it for the moment it runs. Item models are drawn on the render
 * thread alone.
 */
@Mixin(CompassAngleState.class)
public abstract class CompassAngleStateMixin {
    @Unique
    private static boolean hardwrought$soul;

    @Inject(method = "calculate", at = @At("HEAD"))
    private void hardwrought$noteSoul(ItemStack stack, ClientLevel level, int seed, ItemOwner owner, CallbackInfoReturnable<Float> callback) {
        hardwrought$soul = stack.is(WitchKingContent.SOUL_COMPASS);
    }

    @Inject(method = "calculate", at = @At("RETURN"))
    private void hardwrought$forgetSoul(ItemStack stack, ClientLevel level, int seed, ItemOwner owner, CallbackInfoReturnable<Float> callback) {
        hardwrought$soul = false;
    }

    @Inject(method = "isValidCompassTargetPos", at = @At("HEAD"), cancellable = true)
    private static void hardwrought$noNorth(ItemOwner owner, GlobalPos target, CallbackInfoReturnable<Boolean> callback) {
        if (hardwrought$soul) return;
        if (owner != null && owner.level() != null && owner.level().dimension() == Underworld.LEVEL) callback.setReturnValue(false);
    }
}
