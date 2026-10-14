package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.survival.Overeating;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A full hunger bar no longer stops a player eating: there is room past it (see {@link Overeating}). */
@Mixin(Player.class)
public abstract class PlayerOvereatMixin {
    @Inject(method = "canEat", at = @At("RETURN"), cancellable = true)
    private void hardwrought$eatPastFull(boolean ignoreHunger, CallbackInfoReturnable<Boolean> callback) {
        Player player = (Player) (Object) this;
        if (!callback.getReturnValue() && !player.getAbilities().invulnerable && Overeating.roomFor(player)) {
            callback.setReturnValue(true);
        }
    }
}
