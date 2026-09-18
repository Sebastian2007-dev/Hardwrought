package de.ipnats.hardwrought.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Specification section 23.1: vanilla infinite water is removed. Two adjacent sources no longer
 * create a third, so water can be spent and a lake can be drained.
 *
 * <p>This is unconditional rather than left to the {@code waterSourceConversion} game rule. A total
 * conversion whose whole early game is built on water being scarce cannot have that scarcity
 * switched off by a rule; the rule keeps its value for lava, where nothing changes.
 *
 * <p>What stays vanilla is how water flows. A source still spreads downhill without emptying itself,
 * because flowing water cannot be picked up: it wets things, it does not become new water. The
 * economy is finite even though the flow is not simulated by volume.
 */
@Mixin(WaterFluid.class)
public abstract class WaterFluidMixin {
    @ModifyReturnValue(method = "canConvertToSource", at = @At("RETURN"))
    private boolean hardwrought$noInfiniteWater(boolean vanillaAllowsIt) {
        return false;
    }
}
