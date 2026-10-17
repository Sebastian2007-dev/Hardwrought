package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.end.dragon.VoidDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Whatever vanilla would set the dragon to do — circle, strafe, land, charge — it hunts in Hardwrought's
 * way instead (see {@code DragonHunt}); only dying is left to vanilla. The client is told the phase the
 * server set, so it is left alone.
 */
@Mixin(EnderDragonPhaseManager.class)
public abstract class EnderDragonPhaseManagerMixin {
    @Shadow @Final private EnderDragon dragon;

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "setPhase", at = @At("HEAD"), argsOnly = true)
    private EnderDragonPhase<?> hardwrought$hunt(EnderDragonPhase<?> phase) {
        if (dragon.level().isClientSide() || phase == EnderDragonPhase.DYING || phase == VoidDragon.HUNT) return phase;
        return VoidDragon.HUNT;
    }
}
