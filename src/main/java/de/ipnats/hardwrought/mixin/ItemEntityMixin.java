package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.smithing.SmithingEvents;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hot metal thrown into water is quenched there (see {@code SmithingEvents#quenchIfInWater}), and metal
 * thrown onto magma in the Nether heats up (see {@code MagmaHeating}).
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void hardwrought$quench(CallbackInfo info) {
        SmithingEvents.quenchIfInWater((ItemEntity) (Object) this);
        // In the Nether a magma block is a forge fire: metal lying on it heats up.
        de.ipnats.hardwrought.nether.MagmaHeating.heatOnMagma((ItemEntity) (Object) this);
    }
}
