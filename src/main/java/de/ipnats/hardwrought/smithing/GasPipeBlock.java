package de.ipnats.hardwrought.smithing;

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
 * A copper pipe that carries gas away from a hood (see {@link de.ipnats.hardwrought.environment.Flues}).
 *
 * <p>It joins other pipes, hoods and well heads on every side. Its only opening is at an end: a pipe with a single
 * neighbour is open on the far side, and that is where the gas comes out. So a pipe run from a
 * canopy through the wall or the roof takes the fumes outside, however it bends on the way.
 */
public class GasPipeBlock extends PipeBlock {
    /** How wide the pipe is, in pixels, as the model draws it: the hitbox has to match. */
    private static final float WIDTH = 6.0f;

    public GasPipeBlock(Properties properties) {
        super(WIDTH, properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    public static boolean joins(BlockState neighbour) {
        return neighbour.getBlock() instanceof GasPipeBlock || neighbour.getBlock() instanceof ForgeHoodBlock
                || neighbour.getBlock() instanceof de.ipnats.hardwrought.oil.DrillingRigBlock;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            BlockState neighbour = context.getLevel().getBlockState(context.getClickedPos().relative(direction));
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), joins(neighbour));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbour,
                                     RandomSource random) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), joins(neighbour));
    }
}
