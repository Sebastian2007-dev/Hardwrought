package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.building.BuildingPhysics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A block placed from an item is built, not terrain; see {@link BuildingPhysics}. */
@Mixin(BlockItem.class)
public abstract class BlockItemStructureMixin {
    @Inject(method = "placeBlock", at = @At("RETURN"))
    private void hardwrought$markBuilt(BlockPlaceContext context, BlockState state,
                                       CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ() || !(context.getLevel() instanceof ServerLevel level)) return;
        BuildingPhysics.placed(level, context.getClickedPos(), level.getBlockState(context.getClickedPos()));
    }
}
