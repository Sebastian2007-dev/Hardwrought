package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.water.WaterBowlItem;
import de.ipnats.hardwrought.water.WaterQuality;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A bowl of water goes on a campfire like anything else that is cooked there, and comes off boiled,
 * purified and hot ({@code recipe/water_bowl_from_campfire}). The recipe cannot see what is in the bowl,
 * so the campfire is told here not to take one that boiling cannot help: seawater, which it would only
 * make saltier, water already purified, and a bowl still hot from the last time.
 */
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireWaterBowlMixin {
    @Inject(method = "placeFood", at = @At("HEAD"), cancellable = true)
    private void hardwrought$onlyWaterBoilingHelps(ServerLevel level, LivingEntity cook, ItemStack stack,
                                                   CallbackInfoReturnable<Boolean> callback) {
        if (!stack.is(ModItems.WATER_BOWL)) return;
        WaterQuality quality = WaterBowlItem.quality(stack);
        if (WaterBowlItem.hot(stack) || quality.boiled() == quality) callback.setReturnValue(false);
    }
}
