package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.mobs.MobWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mob specification section 38: a wind burst blows out flames and drives gas; see {@link MobWorld#wind}. */
@Mixin(AbstractWindCharge.class)
public abstract class AbstractWindChargeMixin {
    @Inject(method = "onHit", at = @At("TAIL"))
    private void hardwrought$wind(HitResult hit, CallbackInfo callback) {
        AbstractWindCharge self = (AbstractWindCharge) (Object) this;
        if (self.level() instanceof ServerLevel level) MobWorld.wind(level, hit.getLocation());
    }
}
