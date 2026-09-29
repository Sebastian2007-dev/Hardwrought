package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.mobs.SharedAggro;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mob specification section 2: a mob that finds a target tells its own kind; see {@link SharedAggro}. */
@Mixin(Mob.class)
public abstract class MobSetTargetMixin {
    @Unique private boolean hardwrought$hadTarget;

    @Inject(method = "setTarget", at = @At("HEAD"))
    private void hardwrought$before(LivingEntity target, CallbackInfo callback) {
        hardwrought$hadTarget = ((Mob) (Object) this).getTarget() != null;
    }

    @Inject(method = "setTarget", at = @At("TAIL"))
    private void hardwrought$alert(LivingEntity target, CallbackInfo callback) {
        // Told only once, when the mob first sets on someone; the allies it alerts already have a target.
        if (!hardwrought$hadTarget && target != null) SharedAggro.alert((Mob) (Object) this, target);
    }
}
