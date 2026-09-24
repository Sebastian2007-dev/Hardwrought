package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.smithing.SmithingEvents;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hot metal thrown into water is quenched there. See {@code SmithingEvents#quenchIfInWater}. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void hardwrought$quench(CallbackInfo info) {
        SmithingEvents.quenchIfInWater((ItemEntity) (Object) this);
    }
}
