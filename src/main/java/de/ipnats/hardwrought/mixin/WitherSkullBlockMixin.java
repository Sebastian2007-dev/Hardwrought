package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.wither.SoulSkulls;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WitherSkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only soul-charged skulls raise the Wither ({@link SoulSkulls}). */
@Mixin(WitherSkullBlock.class)
public abstract class WitherSkullBlockMixin {
    @Shadow
    private static BlockPattern getOrCreateWitherFull() {
        throw new AssertionError();
    }

    @Inject(method = "checkSpawn(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/SkullBlockEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private static void hardwrought$onlyCharged(Level level, BlockPos pos, SkullBlockEntity placed, CallbackInfo callback) {
        if (!level.isClientSide() && !SoulSkulls.mayRise(level, pos, getOrCreateWitherFull())) callback.cancel();
    }
}
