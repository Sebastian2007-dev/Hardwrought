package de.ipnats.hardwrought.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla does not get to make ice any more.
 *
 * <p>{@code shouldFreeze} is what tells the precipitation tick to drop a full block of
 * {@code minecraft:ice} on a water surface. A full block is precisely what Hardwrought water cannot
 * be turned into without inventing seven eighths of a bucket out of nothing, so the answer here is
 * always no and {@link de.ipnats.hardwrought.water.Freezing} does the freezing instead — at the
 * height the water actually stood, and keeping the exact amount so the thaw gives it back.
 *
 * <p>This also takes frost walker out of the ice business, which is the right outcome for the same
 * reason: a boot enchantment should not be able to conjure water either.
 */
@Mixin(Biome.class)
public abstract class BiomeFreezeMixin {
    @Inject(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void hardwrought$iceIsOurs(LevelReader level, BlockPos pos, boolean checkNeighbors,
                                       CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(false);
    }
}
