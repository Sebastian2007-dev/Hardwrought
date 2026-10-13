package de.ipnats.hardwrought.machinery;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A pipe that carries items, in one of the five metals of {@link ItemPipeTier}: the better the metal,
 * the more it moves and the faster.
 *
 * <p>It joins other item pipes of any metal, and anything beside it that takes or gives items: a
 * chest, a hopper, a furnace, the chute of an ore drill. A side set to draw with a pipe wrench takes
 * items out of the block there; anything that pushes items out (a drill, a hopper) puts them in on
 * any side. Inside, each batch goes to the nearest block along the pipes that has room for it, never
 * back where it came in and never into a block the pipes draw from. See {@link ItemPipeBlockEntity}.
 */
public class ItemPipeBlock extends ConnectedPipeBlock {
    private final ItemPipeTier tier;

    public ItemPipeBlock(ItemPipeTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public ItemPipeTier tier() {
        return tier;
    }

    @Override
    protected boolean sameRun(BlockState neighbour) {
        return neighbour.getBlock() instanceof ItemPipeBlock;
    }

    @Override
    protected boolean offers(Level level, BlockPos neighbour, Direction face) {
        BlockState state = level.getBlockState(neighbour);
        // The chute of a drill hands its ore to what is outside it, so the pipe reaches in to meet it.
        if (state.getBlock() instanceof DrillFrameBlock && state.getValue(DrillFrameBlock.OUTPUT)) return true;
        return ItemStorage.SIDED.find(level, neighbour, face) != null;
    }

    /** Whether a pipe at pos joins what is on the given side of it. */
    public static boolean joins(LevelReader level, BlockPos pos, Direction side) {
        return level.getBlockState(pos).getBlock() instanceof ItemPipeBlock pipe && pipe.linkFor(level, pos, side).joined();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemPipeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.ITEM_PIPE) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>)
                (BlockEntityTicker<ItemPipeBlockEntity>) ItemPipeBlockEntity::serverTick;
        return ticker;
    }
}
