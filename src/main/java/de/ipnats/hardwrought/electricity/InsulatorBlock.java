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
 * Section 76: a ceramic insulator — the support a line is strung from. It stands out from whatever
 * face it was set against, on a post, a wall or under a beam, and holds up to four wires. It is
 * nothing electrically: what comes in along one wire goes out along the others.
 */
public class InsulatorBlock extends ElectricNodeBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public InsulatorBlock(Properties properties) {
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

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return standing(state.getValue(FACING), 5, 9);
    }

    /** A post of this width and height standing out along a direction from the opposite face. */
    static VoxelShape standing(Direction facing, double width, double height) {
        double low = 8 - width / 2, high = 8 + width / 2;
        return switch (facing) {
            case UP -> box(low, 0, low, high, height, high);
            case DOWN -> box(low, 16 - height, low, high, 16, high);
            case NORTH -> box(low, low, 16 - height, high, high, 16);
            case SOUTH -> box(low, low, 0, high, high, height);
            case WEST -> box(16 - height, low, low, 16, high, high);
            case EAST -> box(0, low, low, height, high, high);
        };
    }

    /** A point this many sixteenths out along the direction from the face the block stands on. */
    static Vec3 out(Direction facing, double sixteenths) {
        double along = sixteenths / 16.0 - 0.5;
        return new Vec3(0.5 + facing.getStepX() * along, 0.5 + facing.getStepY() * along, 0.5 + facing.getStepZ() * along);
    }

    /** The wires are tied round its head. */
    @Override
    public Vec3 terminal(BlockState state) {
        return out(state.getValue(FACING), 8);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricBlockEntity(ElectricBlocks.INSULATOR_ENTITY, pos, state);
    }
}
