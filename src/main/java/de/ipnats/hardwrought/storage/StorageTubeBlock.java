package de.ipnats.hardwrought.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The tube a store room is strung together with. It joins other tubes, ports and desks, and nothing
 * else: unlike an item pipe it moves nothing by itself and takes nothing from a chest it merely
 * runs past. It is the way a desk reaches its ports — see {@link StorageNetwork}.
 */
public class StorageTubeBlock extends PipeBlock {
    /** How wide the tube is, in pixels, as the model draws it: the hitbox has to match. */
    private static final float WIDTH = 6.0f;

    public StorageTubeBlock(Properties properties) {
        super(WIDTH, properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    private static boolean joins(LevelReader level, BlockPos pos, Direction side) {
        return StorageNetwork.isPart(level.getBlockState(pos.relative(side)));
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
}
