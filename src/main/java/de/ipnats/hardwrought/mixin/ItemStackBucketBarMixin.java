package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.water.BucketWater;
import de.ipnats.hardwrought.water.WaterQuality;
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
 * <p>In the colour of the water rather than the green-to-red of durability, because this is not wear.
 * A bucket at a tenth is not nearly broken, it is nearly empty. A full bucket of anything but plain
 * water shows its bar too, at full width, so seawater and swamp water are told from clean at a glance.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackBucketBarMixin {
    /** Whether this bucket shows a bar: part full, or full of anything but plain water. */
    private static boolean hardwrought$shows(ItemStack stack) {
        int held = BucketWater.held(stack);
        if (held <= 0) return false;
        // A full bucket of plain water needs no bar: it is the ordinary case and a permanent bar on
        // every bucket in the game would be noise.
        return held < BucketWater.CAPACITY || hardwrought$quality(stack) != WaterQuality.FRESH;
    }

    private static WaterQuality hardwrought$quality(ItemStack stack) {
        WaterQuality quality = stack.get(ModDataComponents.WATER_QUALITY);
        return quality == null ? WaterQuality.FRESH : quality;
    }

    @Inject(method = "isBarVisible", at = @At("HEAD"), cancellable = true)
    private void hardwrought$bucketBarVisible(CallbackInfoReturnable<Boolean> callback) {
        if (hardwrought$shows((ItemStack) (Object) this)) callback.setReturnValue(true);
    }

    @Inject(method = "getBarWidth", at = @At("HEAD"), cancellable = true)
    private void hardwrought$bucketBarWidth(CallbackInfoReturnable<Integer> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        if (!hardwrought$shows(stack)) return;
        // Never zero while there is water in it: a bar that has vanished reads as an empty bucket.
        callback.setReturnValue(Math.max(1, Math.round(13.0f * BucketWater.held(stack) / BucketWater.CAPACITY)));
    }

    @Inject(method = "getBarColor", at = @At("HEAD"), cancellable = true)
    private void hardwrought$bucketBarColor(CallbackInfoReturnable<Integer> callback) {
        ItemStack stack = (ItemStack) (Object) this;
        if (hardwrought$shows(stack)) callback.setReturnValue(hardwrought$quality(stack).color());
    }
}
