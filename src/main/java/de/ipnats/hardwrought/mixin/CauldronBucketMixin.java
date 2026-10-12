package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.water.BucketWater;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A cauldron holds a block of water and nothing finer, so only a full bucket may fill one.
 *
 * <p>Vanilla's exchange is honest in both directions as long as a water bucket really is a
 * bucketful: a full cauldron is exactly a block and a block is exactly a bucket. A measured bucket
 * breaks the first half of that, and a cauldron that took fifty millibuckets and gave back a block
 * would be a way of making water out of nothing — the thing section 23.1 exists to stop.
 *
 * <p>Emptying a cauldron into a bucket is untouched, because that half was never in doubt.
 */
@Mixin(CauldronInteraction.Dispatcher.class)
public abstract class CauldronBucketMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void hardwrought$onlyAFullBucketFillsACauldron(ItemStack stack,
                                                           CallbackInfoReturnable<CauldronInteraction> callback) {
        if (stack.is(Items.WATER_BUCKET) && !BucketWater.isFull(stack)) {
            callback.setReturnValue(CauldronInteraction.DEFAULT);
        }
    }
}
