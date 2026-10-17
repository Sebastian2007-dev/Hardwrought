package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.end.EndRules;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nor is a bucket emptied there: water caught under a fall would be a floor too. */
@Mixin(BucketItem.class)
public abstract class EndBucketMixin {
    @Inject(method = "emptyContents", at = @At("HEAD"), cancellable = true)
    private void hardwrought$noPouringInTheEnd(LivingEntity user, Level level, BlockPos pos, BlockHitResult hit, CallbackInfoReturnable<Boolean> info) {
        if (!(user instanceof Player player) || !EndRules.buildingForbidden(player)) return;
        EndRules.refuse(player);
        info.setReturnValue(false);
    }
}
