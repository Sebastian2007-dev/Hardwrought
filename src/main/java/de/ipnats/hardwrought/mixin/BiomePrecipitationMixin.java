package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.environment.BiomeClimate;
import de.ipnats.hardwrought.environment.Season;
import de.ipnats.hardwrought.environment.SeasonState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Specification section 24: a season changes what falls out of the sky, not only how warm it is.
 *
 * <p>Vanilla decides rain or snow from the biome's own temperature and nothing else, which gives a
 * world where it snows in the same places every day of the year and never rains in a desert. Three
 * rules change that, and each of them is a thing a player can notice happening:
 *
 * <ul>
 *   <li>In winter a temperate place gets snow. The plains turn white.</li>
 *   <li>In winter a hot place gets rain — the one quarter of the year a desert sees water fall.</li>
 *   <li>In summer a cold place gets rain rather than snow, because in summer it is not cold.</li>
 * </ul>
 *
 * <p>The winter desert rain overrides {@code hasPrecipitation} outright, which is the point: a
 * biome declaring it never rains is describing its ordinary year, and winter is not that.
 *
 * <p>This runs on both sides — the client needs it to draw the weather and the server to place
 * snow — so it reads the shared {@link SeasonState} rather than a world it has not been handed.
 */
@Mixin(Biome.class)
public abstract class BiomePrecipitationMixin {
    @org.spongepowered.asm.mixin.Shadow
    public abstract float getBaseTemperature();

    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true)
    private void hardwrought$seasonalPrecipitation(BlockPos pos, int seaLevel,
                                                   CallbackInfoReturnable<Biome.Precipitation> callback) {
        Season season = SeasonState.season();
        if (season != Season.WINTER && season != Season.SUMMER) return;
        BiomeClimate climate = BiomeClimate.fromBaseTemperature(getBaseTemperature());
        if (season == Season.WINTER) {
            callback.setReturnValue(climate == BiomeClimate.WARM
                    ? Biome.Precipitation.RAIN : Biome.Precipitation.SNOW);
        } else if (climate == BiomeClimate.COLD && ((Biome) (Object) this).hasPrecipitation()) {
            callback.setReturnValue(Biome.Precipitation.RAIN);
        }
    }
}
