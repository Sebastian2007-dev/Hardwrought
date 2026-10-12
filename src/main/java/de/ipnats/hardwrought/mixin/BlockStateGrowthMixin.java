package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.environment.SeasonalGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where the calendar reaches the crops. Specification section 24.
 *
 * <p>A plant in Minecraft grows because it is handed random ticks, so a season changes growth by
 * changing how many of them land: winter swallows half, spring and summer sometimes hand out a
 * second. The hook is on the base state rather than on any particular crop, so one rule covers
 * everything in the {@code hardwrought:seasonal_growth} tag and nothing outside it — see
 * {@link SeasonalGrowth}, which owns the decision and the guard against a bonus tick earning
 * itself another.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateGrowthMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void hardwrought$winterTakesItBack(ServerLevel level, BlockPos pos, RandomSource random,
                                               CallbackInfo callback) {
        if (SeasonalGrowth.swallow((BlockState) (Object) this, random)) callback.cancel();
    }

    @Inject(method = "randomTick", at = @At("RETURN"))
    private void hardwrought$springHurriesItAlong(ServerLevel level, BlockPos pos, RandomSource random,
                                                  CallbackInfo callback) {
        SeasonalGrowth.grantBonus((BlockState) (Object) this, level, pos, random);
    }
}
