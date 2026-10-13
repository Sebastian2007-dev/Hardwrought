package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.water.BucketWater;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A part-full bucket is not a bucket of water, and a recipe may not pretend otherwise.
 *
 * <p>Every recipe that asks for {@code minecraft:water_bucket} — a cake, and whatever a pack adds —
 * matches on the item and nothing else. With a measured bucket that is a way of turning fifty
 * millibuckets into a whole one, which is the same hole in the same wall the mod already closed
 * when it took vanilla's infinite water out. So an ingredient test sees a bucket as a bucket of
 * water only when it actually holds one.
 *
 * <p>This sits on a hot path, so it answers for everything that is not a part-full water bucket
 * before it looks at anything: one item comparison, then out.
 */
@Mixin(Ingredient.class)
public abstract class IngredientBucketMixin {
    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void hardwrought$partBucketIsNotABucketful(ItemStack input,
                                                       CallbackInfoReturnable<Boolean> callback) {
        if (!input.is(net.minecraft.world.item.Items.WATER_BUCKET)) return;
        if (!BucketWater.isFull(input)) callback.setReturnValue(false);
    }
}
