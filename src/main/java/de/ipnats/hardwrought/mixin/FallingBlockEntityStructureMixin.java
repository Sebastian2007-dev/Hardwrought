package de.ipnats.hardwrought.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.ipnats.hardwrought.building.BuildingPhysics;
import de.ipnats.hardwrought.building.BuiltBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A built block that falls — collapsed, or sand a player put there — is still built where it lands.
 * Otherwise every collapse would turn rubble into anchored terrain to build on.
 */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityStructureMixin {
    private static final String BUILT_TAG = "hardwrought.built";

    @WrapMethod(method = "fall")
    private static FallingBlockEntity hardwrought$remember(Level level, BlockPos pos, BlockState state,
                                                           Operation<FallingBlockEntity> original) {
        // Asked before the block is taken away, which forgets that it was built.
        boolean built = level instanceof ServerLevel server && BuiltBlocks.isBuilt(server, pos);
        FallingBlockEntity entity = original.call(level, pos, state);
        if (built) entity.addTag(BUILT_TAG);
        return entity;
    }

    /**
     * A block of a collapse crushes a flower or a torch it lands on and takes its place, instead of
     * breaking into an item as sand does: a tree felled onto flowers would come down as loose logs.
     */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;canBeReplaced(Lnet/minecraft/world/item/context/BlockPlaceContext;)Z"))
    private boolean hardwrought$crush(BlockState state, BlockPlaceContext context, Operation<Boolean> original) {
        if (original.call(state, context)) return true;
        if (!(context.getLevel() instanceof ServerLevel server)
                || !((FallingBlockEntity) (Object) this).entityTags().contains(BuildingPhysics.COLLAPSE_TAG)) {
            return false;
        }
        BlockPos pos = context.getClickedPos();
        if (!BuildingPhysics.crushable(server, pos)) return false;
        server.destroyBlock(pos, true);
        return true;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean hardwrought$land(Level level, BlockPos pos, BlockState state, Operation<Boolean> original) {
        boolean placed = original.call(level, pos, state);
        if (placed && level instanceof ServerLevel server
                && ((FallingBlockEntity) (Object) this).entityTags().contains(BUILT_TAG)) {
            BuildingPhysics.landed(server, pos, state);
        }
        return placed;
    }
}
