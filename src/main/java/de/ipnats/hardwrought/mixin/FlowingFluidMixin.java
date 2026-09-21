package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.water.WaterFlow;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Specification section 23.1: water is a quantity, not a pattern copied outward from a source block.
 *
 * <p>Vanilla's spread is what makes water infinite: it does not move water, it re-derives every
 * flowing block from whichever source it can still see, so one source wets an unlimited area for
 * ever. That is replaced wholesale by the conserving simulation in {@link WaterFlow}.
 *
 * <p>The scheduled tick itself is kept and put to use. Vanilla already schedules a fluid tick on
 * water whenever anything beside it changes, which is exactly the signal needed to know that a cell
 * has been disturbed — so still water costs nothing and nothing else has to watch for block changes.
 *
 * <p>Lava is untouched and keeps spreading the way it always did.
 */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void hardwrought$conserveWater(ServerLevel level, BlockPos pos, BlockState state,
                                           FluidState fluid, CallbackInfo callback) {
        if (!fluid.is(FluidTags.WATER)) return;
        callback.cancel();
        // A fluid tick is only a wake-up signal. Moving water here would bypass WaterFlow's
        // per-tick time budget, and every resulting block update schedules more fluid ticks. That
        // feedback loop can freeze the entire server for seconds or minutes around a large lake.
        WaterFlow.disturb(level, pos);
    }
}
