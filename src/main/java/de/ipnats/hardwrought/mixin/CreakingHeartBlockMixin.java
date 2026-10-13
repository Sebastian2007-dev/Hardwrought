package de.ipnats.hardwrought.mixin;

import de.ipnats.hardwrought.mobs.TreeHeartBlock;
import de.ipnats.hardwrought.mobs.TreeHearts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CreakingHeartBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla's heart checks for pale oak around it in static methods, which the block entity calls as
 * well; a {@link TreeHeartBlock} answers them with its own wood instead. And a vanilla heart set
 * between logs of another wood becomes a tree heart in that bark. See {@link TreeHearts}.
 */
@Mixin(CreakingHeartBlock.class)
public abstract class CreakingHeartBlockMixin {
    @Inject(method = "hasRequiredLogs", at = @At("HEAD"), cancellable = true)
    private static void hardwrought$treeHeartLogs(BlockState state, LevelReader level, BlockPos pos,
                                                  CallbackInfoReturnable<Boolean> callback) {
        if (state.getBlock() instanceof TreeHeartBlock) {
            callback.setReturnValue(TreeHearts.hasRequiredLogs(state, level, pos));
        }
    }

    @Inject(method = "isSurroundedByLogs", at = @At("HEAD"), cancellable = true)
    private static void hardwrought$treeHeartSurrounded(LevelAccessor level, BlockPos pos,
                                                        CallbackInfoReturnable<Boolean> callback) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TreeHeartBlock) {
            callback.setReturnValue(TreeHearts.surroundedByLogs(state, level, pos));
        }
    }

    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void hardwrought$takeOnBark(BlockPlaceContext context, CallbackInfoReturnable<BlockState> callback) {
        BlockState placed = callback.getReturnValue();
        if (placed != null && placed.is(Blocks.CREAKING_HEART)) {
            callback.setReturnValue(TreeHearts.forPlacement(placed, context));
        }
    }
}
