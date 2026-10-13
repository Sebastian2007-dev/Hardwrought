package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.water.BucketWater;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * How full a bucket is, shown on the bar under the item.
 *
 * <p>The item model has three visible steps, because three rows inside the bucket's mouth is all
 * the sprite can show. That is enough to tell a nearly empty bucket from a nearly full one at a
 * glance and nowhere near enough to tell four hundred millibuckets from five hundred. The bar is
 * the rest: it has thirteen pixels and reads continuously, so the two together say both the rough
 * thing instantly and the exact thing on inspection.
 *
 * <p>Blue rather than the green-to-red of durability, because this is not wear. A bucket at a tenth
 * is not nearly broken, it is nearly empty, and the colour should not suggest otherwise.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackBucketBarMixin {
    /** The blue of still water, bright enough to read against a dark inventory slot. */
    private static final int HARDWROUGHT$WATER_BAR = 0x3A7FD5;

    @Inject(method = "isBarVisible", at = @At("HEAD"), cancellable = true)
    private void hardwrought$bucketBarVisible(CallbackInfoReturnable<Boolean> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        // A full bucket needs no bar: it is the ordinary case and a permanent bar on every bucket
        // in the game would be noise. Anything less than full says so.
        int held = BucketWater.held(stack);
        if (held > 0 && held < BucketWater.CAPACITY) callback.setReturnValue(true);
    }

    @Inject(method = "getBarWidth", at = @At("HEAD"), cancellable = true)
    private void hardwrought$bucketBarWidth(CallbackInfoReturnable<Integer> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        int held = BucketWater.held(stack);
        if (held <= 0 || held >= BucketWater.CAPACITY) return;
        // Never zero while there is water in it: a bar that has vanished reads as an empty bucket.
        callback.setReturnValue(Math.max(1, Math.round(13.0f * held / BucketWater.CAPACITY)));
    }

    @Inject(method = "getBarColor", at = @At("HEAD"), cancellable = true)
    private void hardwrought$bucketBarColor(CallbackInfoReturnable<Integer> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        int held = BucketWater.held(stack);
        if (held > 0 && held < BucketWater.CAPACITY) callback.setReturnValue(HARDWROUGHT$WATER_BAR);
    }
}
