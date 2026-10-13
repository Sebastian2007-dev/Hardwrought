package de.ipnats.hardwrought.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import de.ipnats.hardwrought.mobs.TreeHearts;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Once a tree stands with its decorations, it may grow a creaking heart into its trunk. Done here
 * rather than as a decorator on each tree, so it reaches every tree of every forest. See {@link TreeHearts}.
 */
@Mixin(TreeFeature.class)
public abstract class TreeFeatureMixin {
    @Inject(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/BoundingBox;encapsulatingPositions(Ljava/lang/Iterable;)Ljava/util/Optional;"))
    private void hardwrought$growHeart(WorldGenLevel level, net.minecraft.world.level.chunk.ChunkGenerator generator,
                                       RandomSource random, BlockPos origin, CallbackInfoReturnable<Boolean> callback,
                                       @Local(ordinal = 1) Set<BlockPos> trunks,
                                       @Local(ordinal = 3) Set<BlockPos> decorations) {
        TreeHearts.growInto(level, random, origin, trunks, decorations);
    }
}
