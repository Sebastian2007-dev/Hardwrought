package de.ipnats.hardwrought.electricity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Section 76: a copper coil — the connection between a line and a machine. Set against any face of a
 * dynamo, a battery or a machine, it is where that block's wires are made fast: what comes down the
 * wire goes through the coil into the block it sits on, and what the block gives goes out the same
 * way. Those blocks take no wire themselves.
 *
 * <p>Several coils on one block are several ways into it, and current passes from one to the other
 * through the block's own terminals — which is how a line is carried on past a machine. Set against
 * anything else a coil is a short, stubby support and nothing more. It is bare copper: live, it bites.
 */
public class CoilBlock extends ElectricNodeBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public CoilBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Standing out from the face it was put against. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    /** The block this coil sits on, and feeds. */
    public static BlockPos seat(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING).getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return InsulatorBlock.standing(state.getValue(FACING), 6, 8);
    }

    @Override
    public Vec3 terminal(BlockState state) {
        return InsulatorBlock.out(state.getValue(FACING), 7);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricBlockEntity(ElectricBlocks.COIL_ENTITY, pos, state);
    }
}
