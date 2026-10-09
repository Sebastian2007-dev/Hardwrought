package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.worldgen.DeepWorld;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Moves the lava that fills the bottom of the world down to the bottom of this world; see
 * {@link DeepWorld#LAVA_LEVEL}. The level is a number in the generator's code, in the two places
 * where it makes the lava and where it decides what is below it.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorLavaMixin {
    @ModifyConstant(method = {"createFluidPicker", "lambda$createFluidPicker$0"}, constant = @Constant(intValue = -54), require = 2)
    private static int hardwrought$lavaLevel(int vanilla) {
        return DeepWorld.LAVA_LEVEL;
    }
}
