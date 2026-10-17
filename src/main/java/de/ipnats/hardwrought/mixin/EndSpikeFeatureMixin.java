package de.ipnats.hardwrought.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import de.ipnats.hardwrought.end.BrokenIsland;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The End's obsidian pillars no longer reach down to the bottom of the world: with the island gone
 * they stand alone in the void, {@link BrokenIsland#PILLAR_DEPTH} deep, and {@code BrokenIsland} gives
 * each a broken point beneath.
 */
@Mixin(EndSpikeFeature.class)
public abstract class EndSpikeFeatureMixin {
    @WrapOperation(method = "placeSpike", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerLevelAccessor;getMinY()I"))
    private int hardwrought$standInTheVoid(ServerLevelAccessor level, Operation<Integer> original, @Local(argsOnly = true) EndSpikeFeature.EndSpike spike) {
        return Math.max(original.call(level), spike.getHeight() - BrokenIsland.PILLAR_DEPTH);
    }
}
