package de.ipnats.hardwrought.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import de.ipnats.hardwrought.building.SharedCellBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * A door checks that its other half is still there, and drops off if it is not. With a carpet or a
 * pane in one half, that half is a shared cell; the door has to see the door inside it.
 */
@Mixin(DoorBlock.class)
public abstract class DoorBlockSharedCellMixin {
    @WrapMethod(method = "updateShape")
    private BlockState hardwrought$seeDoorInSharedCell(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                                                       BlockPos pos, Direction direction, BlockPos neighborPos,
                                                       BlockState neighborState, RandomSource random,
                                                       Operation<BlockState> original) {
        if (level.getBlockEntity(neighborPos) instanceof SharedCellBlockEntity cell && !cell.host().isAir()) {
            neighborState = cell.host();
        }
        return original.call(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }
}
