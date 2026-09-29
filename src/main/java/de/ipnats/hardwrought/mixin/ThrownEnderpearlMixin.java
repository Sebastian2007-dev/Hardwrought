package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.mobs.MobWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mob specification section 19: teleportation draws endermites; see {@link MobWorld#pearlLanded}. */
@Mixin(ThrownEnderpearl.class)
public abstract class ThrownEnderpearlMixin {
    @Inject(method = "onHit", at = @At("HEAD"))
    private void hardwrought$drawEndermites(HitResult hit, CallbackInfo callback) {
        ThrownEnderpearl self = (ThrownEnderpearl) (Object) this;
        if (self.level() instanceof ServerLevel level) MobWorld.pearlLanded(level, hit.getLocation(), self.getOwner());
    }
}
