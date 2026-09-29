package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.mobs.CreeperLeap;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mob specification § 13.1: the leap before the blast; see {@link CreeperLeap}. */
@Mixin(Creeper.class)
public abstract class CreeperLeapMixin implements CreeperLeap.Leaper {
    @Unique private boolean hardwrought$leapt;

    @Override
    public boolean hardwrought$leapt() { return hardwrought$leapt; }

    @Override
    public void hardwrought$setLeapt(boolean leapt) { hardwrought$leapt = leapt; }

    @Inject(method = "tick", at = @At("TAIL"))
    private void hardwrought$leap(CallbackInfo callback) {
        CreeperLeap.tick((Creeper) (Object) this);
    }
}
