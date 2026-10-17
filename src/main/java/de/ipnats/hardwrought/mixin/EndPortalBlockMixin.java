package de.ipnats.hardwrought.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.ipnats.hardwrought.end.EndRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The way into the End sets its traveller down in the void, on a floor of light, not on obsidian; see {@code EndRules}. */
@Mixin(EndPortalBlock.class)
public abstract class EndPortalBlockMixin {
    @WrapOperation(method = "getPortalDestination", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/feature/EndPlatformFeature;createEndPlatform(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Z)V"))
    private void hardwrought$lightInstead(ServerLevelAccessor level, BlockPos pos, boolean dropBlocks, Operation<Void> original) {
        if (level instanceof ServerLevel end) EndRules.lightPlatform(end);
        else original.call(level, pos, dropBlocks);
    }

    @ModifyReturnValue(method = "getPortalDestination", at = @At("RETURN"))
    private TeleportTransition hardwrought$intoTheVoid(TeleportTransition transition) {
        return transition != null && transition.newLevel().dimension() == Level.END ? EndRules.arrival(transition) : transition;
    }
}
