package de.ipnats.hardwrought.storage;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The sorting works set against a chest: what puts a thing into it and fetches it out again when
 * the desk asks. One port, one chest — the one its mouth is turned to — and that is what a large
 * store room costs. A double chest needs only one. See {@link StorageNetwork}.
 */
public class StoragePortBlock extends Block {
    /** The side its mouth is on: toward the chest. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public StoragePortBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Set against a chest, it faces the chest. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Set down, it may join a desk to a coil on the tubes: the desks work out their current again. */
    @Override
    protected void onPlace(BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, BlockState previous, boolean moving) {
        super.onPlace(state, level, pos, previous, moving);
        if (level instanceof net.minecraft.server.level.ServerLevel server && !previous.is(this)) StorageNetwork.rewire(server, pos);
    }

    /** Taken away, it may cut a desk off from its coil. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos, boolean moving) {
        super.affectNeighborsAfterRemoval(state, level, pos, moving);
        StorageNetwork.rewire(level, pos);
    }
}
