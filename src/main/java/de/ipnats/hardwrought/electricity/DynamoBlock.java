package de.ipnats.hardwrought.electricity;

import de.ipnats.hardwrought.machinery.KineticBlock;
import de.ipnats.hardwrought.machinery.Kinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Section 76: the dynamo — a machine on the driveline that turns its turning into voltage.
 *
 * <p>Its axle runs through it along {@link #FACING}; a shaft, a gear or a crank at either end drives
 * it. It is joined to a line by a copper coil set against it. See {@link DynamoBlockEntity} for what it gives and
 * what that costs the line.
 */
public class DynamoBlock extends ElectricNodeBlock implements KineticBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 13, 15);

    public DynamoBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Its axle lies the way the player looks, so it goes straight onto the end of a shaft. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Vec3 terminal(BlockState state) {
        return new Vec3(0.5, 15.0 / 16.0, 0.5);
    }

    @Override
    public float port(BlockState state, Direction face) {
        return face.getAxis() == state.getValue(FACING).getAxis() ? 1.0f : 0.0f;
    }

    /** Heavier to turn the more current is drawn from it. */
    @Override
    public float impact(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof DynamoBlockEntity dynamo ? dynamo.impact() : DynamoBlockEntity.IDLE_IMPACT;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (!level.isClientSide() && !previous.is(this)) Kinetics.update(level, pos);
        super.onPlace(state, level, pos, previous, moving);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moving) {
        Kinetics.update(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, moving);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DynamoBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ElectricBlocks.DYNAMO_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>)
                (BlockEntityTicker<DynamoBlockEntity>) DynamoBlockEntity::serverTick;
        return ticker;
    }
}
