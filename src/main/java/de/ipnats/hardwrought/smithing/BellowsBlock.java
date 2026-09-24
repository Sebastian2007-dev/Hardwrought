package de.ipnats.hardwrought.smithing;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Leather and boards that push air into a forge beside it — but only while something works them.
 *
 * <p>The bellows does nothing by itself. It is the second machine on the driveline after the
 * crusher: a hand crank on it, or a shaft turning into it, and the forge next to it roars. A player
 * who wants iron hot enough for the harder metals has to either stand and crank or build a line.
 */
public class BellowsBlock extends Block implements net.minecraft.world.level.block.EntityBlock,
        de.ipnats.hardwrought.machinery.KineticBlock {
    /** Strength taken per turn per minute: a hand crank carries a bellows with room to spare. */
    public static final float IMPACT = 4.0f;

    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public BellowsBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Worked from any side a shaft, gear or crank meets it on. */
    @Override
    public float port(BlockState state, Direction face) {
        return 1.0f;
    }

    @Override
    public float impact(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, BlockState state) {
        return IMPACT;
    }

    @Override
    protected void onPlace(BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                           BlockState previous, boolean moving) {
        if (!level.isClientSide() && !previous.is(this)) de.ipnats.hardwrought.machinery.Kinetics.update(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
                                               net.minecraft.core.BlockPos pos, boolean moving) {
        de.ipnats.hardwrought.machinery.Kinetics.update(level, pos);
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(net.minecraft.core.BlockPos pos, BlockState state) {
        return new de.ipnats.hardwrought.machinery.KineticBlockEntity(
                de.ipnats.hardwrought.core.registry.ModBlockEntities.KINETIC, pos, state);
    }
}
