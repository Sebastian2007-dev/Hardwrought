package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.magic.Veil;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A veiled body is not drawn at all — not its armour, not what it holds, not its name or its shadow —
 * to anyone, its own player in third person included. See {@link Veil}.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherVeilMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void hardwrought$veiled(E entity, Frustum culler, double camX, double camY, double camZ,
                                                      float partialTicks, CallbackInfoReturnable<Boolean> callback) {
        if (Veil.veiled(entity)) callback.setReturnValue(false);
    }
}
