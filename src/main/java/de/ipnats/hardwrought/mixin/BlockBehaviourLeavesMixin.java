package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.environment.Leaves;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Section 40: leaves hold nothing heavy up. Leaf blocks do not override either method, so the hook
 * sits on the base behaviour and checks for leaves; every other block goes on as before. Both sides
 * run it, which matters: a player's movement is worked out by their own client.
 */
@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourLeavesMixin {
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void hardwrought$leavesHoldNothingHeavy(BlockState state, BlockGetter level, BlockPos pos,
                                                    CollisionContext context,
                                                    CallbackInfoReturnable<VoxelShape> callback) {
        if (!(context instanceof EntityCollisionContext entityContext) || !Leaves.isLeaves(state)) return;
        Entity entity = entityContext.getEntity();
        if (entity != null && Leaves.passesThrough(entity)) callback.setReturnValue(Shapes.empty());
    }

    @Inject(method = "entityInside", at = @At("HEAD"))
    private void hardwrought$pushingThroughLeaves(BlockState state, Level level, BlockPos pos, Entity entity,
                                                  InsideBlockEffectApplier effects, boolean intersects,
                                                  CallbackInfo callback) {
        if (Leaves.isLeaves(state)) Leaves.pushThrough(entity, pos);
    }
}
