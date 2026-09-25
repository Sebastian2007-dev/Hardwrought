package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.client.environment.GasPushClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A sneaking swing at gas pushes it instead of attacking; see {@link GasPushClient}. */
@Mixin(Minecraft.class)
public abstract class MinecraftGasPushMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void hardwrought$pushGas(CallbackInfoReturnable<Boolean> callback) {
        if (GasPushClient.trySwing((Minecraft) (Object) this)) callback.setReturnValue(true);
    }
}
