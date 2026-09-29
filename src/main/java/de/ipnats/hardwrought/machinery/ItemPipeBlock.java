package de.ipnats.hardwrought.machinery;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A pipe that carries items, in one of the five metals of {@link ItemPipeTier}: the better the metal,
 * the more it moves and the faster.
 *
 * <p>It joins other item pipes of any metal, and anything beside it that takes or gives items: a
 * chest, a hopper, a furnace, the chute of an ore drill. Items are put into it from outside, by
 * whatever pushes items out (a drill, a hopper under a chest); inside, each batch goes to the
 * nearest block along the pipes that has room for it, never back where it came in.
 * See {@link ItemPipeBlockEntity}.
 */
public class ItemPipeBlock extends PipeBlock implements EntityBlock {
    /** How wide the pipe is, in pixels, as the model draws it: the hitbox has to match. */
    private static final float WIDTH = 8.0f;

    private final ItemPipeTier tier;

    public ItemPipeBlock(ItemPipeTier tier, Properties properties) {
        super(WIDTH, properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
    }

    public ItemPipeTier tier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    /** Whether a pipe at pos joins what is on the given side of it. */
    public static boolean joins(LevelReader level, BlockPos pos, Direction side) {
        BlockPos next = pos.relative(side);
        BlockState neighbour = level.getBlockState(next);
        if (neighbour.getBlock() instanceof ItemPipeBlock) return true;
        // The chute of a drill hands its ore to what is outside it, so the pipe reaches in to meet it.
        if (neighbour.getBlock() instanceof DrillFrameBlock && neighbour.getValue(DrillFrameBlock.OUTPUT)) return true;
        return level instanceof Level world && ItemStorage.SIDED.find(world, next, side.getOpposite()) != null;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction),
                    joins(context.getLevel(), context.getClickedPos(), direction));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbour,
                                     RandomSource random) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), joins(level, pos, direction));
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
