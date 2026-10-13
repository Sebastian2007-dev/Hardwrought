package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.mobs.Perception;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player in the dark is noticed from nearer; see {@link Perception#visibility}. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityVisibilityMixin {
    @Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
    private void hardwrought$darkness(ServerLevel level, Entity looker, CallbackInfoReturnable<Double> callback) {
        // A veiled body is not seen at all, armour and all, by anything.
        if (de.ipnats.hardwrought.magic.Veil.veiled((LivingEntity) (Object) this)) {
            callback.setReturnValue(0.0);
            return;
        }
        if (!(looker instanceof Monster)) return;
        callback.setReturnValue(callback.getReturnValueD() * Perception.visibility(level, (LivingEntity) (Object) this));
    }
}
