package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.smithing.SmithingEvents;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hot metal thrown into water is quenched there (see {@code SmithingEvents#quenchIfInWater}), and metal
 * thrown onto magma in the Nether heats up (see {@code MagmaHeating}); a wither skeleton skull in soul fire
 * charges there unburnt (see {@code SoulSkulls}).
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    /** A skull charging in soul fire is not burnt by it. */
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void hardwrought$charging(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source,
                                      float damage, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> callback) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                && de.ipnats.hardwrought.wither.SoulSkulls.shieldedFromFire((ItemEntity) (Object) this)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void hardwrought$quench(CallbackInfo info) {
        SmithingEvents.quenchIfInWater((ItemEntity) (Object) this);
        // In the Nether a magma block is a forge fire: metal lying on it heats up.
        de.ipnats.hardwrought.nether.MagmaHeating.heatOnMagma((ItemEntity) (Object) this);
        // A wither skeleton skull in soul fire drinks it, and comes out charged.
        de.ipnats.hardwrought.wither.SoulSkulls.charge((ItemEntity) (Object) this);
    }
}
