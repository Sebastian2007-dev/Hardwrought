package de.ipnats.hardwrought.client.mixin;

import de.ipnats.hardwrought.client.environment.SeasonColors;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The year, in the colour of the world. Specification section 24.
 *
 * <p>These four methods are where every biome-tinted block in the game gets its colour — grass,
 * leaves, vines, tall grass, lily pads, sugar cane, water. Taking the answer vanilla worked out and
 * washing it with the season leaves the biome differences intact underneath: a winter jungle is
 * still greener than a winter savanna, both are simply much greyer than they were.
 *
 * <p>The colour is baked into the chunk meshes, so a season change asks for a re-render; that is
 * {@link de.ipnats.hardwrought.client.environment.SeasonClient}'s job and happens four times a
 * game year.
 */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {
    @Inject(method = "getAverageGrassColor", at = @At("RETURN"), cancellable = true)
    private static void hardwrought$seasonalGrass(BlockAndTintGetter level, BlockPos pos,
                                                  CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(SeasonColors.grass(callback.getReturnValue()));
    }

    @Inject(method = "getAverageFoliageColor", at = @At("RETURN"), cancellable = true)
    private static void hardwrought$seasonalFoliage(BlockAndTintGetter level, BlockPos pos,
                                                    CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(SeasonColors.foliage(callback.getReturnValue()));
    }

    @Inject(method = "getAverageDryFoliageColor", at = @At("RETURN"), cancellable = true)
    private static void hardwrought$seasonalDryFoliage(BlockAndTintGetter level, BlockPos pos,
                                                       CallbackInfoReturnable<Integer> callback) {
        callback.setReturnValue(SeasonColors.dryFoliage(callback.getReturnValue()));
    }

    @Inject(method = "getAverageWaterColor", at = @At("RETURN"), cancellable = true)
    private static void hardwrought$seasonalWater(BlockAndTintGetter level, BlockPos pos,
                                                  CallbackInfoReturnable<Integer> callback) {
        // The season first, then what is in the water: a frozen marsh is still a marsh.
        callback.setReturnValue(de.ipnats.hardwrought.client.environment.WaterTint.tint(
                SeasonColors.water(callback.getReturnValue()), pos));
    }
}
