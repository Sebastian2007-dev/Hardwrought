package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.water.WaterQualityStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FarmlandBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Seawater does not water a field. Farmland is wet where there is water within four blocks, as in
 * vanilla, but water that is seawater does not count: salt dries the soil out rather than wetting it.
 * Rain still waters a field by the sea.
 */
@Mixin(FarmlandBlock.class)
public abstract class FarmlandWaterMixin {
    @Inject(method = "isNearWater", at = @At("HEAD"), cancellable = true)
    private static void hardwrought$seawaterDoesNotWater(LevelReader level, BlockPos pos,
                                                        CallbackInfoReturnable<Boolean> callback) {
        if (!(level instanceof ServerLevel server)) return;
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 1, 4))) {
            if (server.getFluidState(near).is(FluidTags.WATER) && !WaterQualityStorage.salty(server, near)) {
                callback.setReturnValue(true);
                return;
            }
        }
        callback.setReturnValue(false);
    }
}
