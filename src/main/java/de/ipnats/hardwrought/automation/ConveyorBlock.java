package de.ipnats.hardwrought.automation;

import de.ipnats.hardwrought.machinery.KineticBlock;
import de.ipnats.hardwrought.machinery.Kinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A conveyor (stage E5): a belt over rollers, carrying what lies on it along {@link #FACING}. Its rollers
 * are turned through their axle, which runs across the belt — a shaft, a gear or an electric drive at
 * either side — and one length of belt drives the next it runs into, so a whole line turns from one
 * drive. What reaches the end goes into whatever holds things in front of it.
 */
public class ConveyorBlock extends BaseEntityBlock implements KineticBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /** How hard a length of belt is to turn, for every turn a minute. */
    public static final float IMPACT = 0.5f;
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 6, 16);

    public ConveyorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** It carries away from the player who sets it down. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** The roller axle runs across the belt. */
    @Override
    public float port(BlockState state, Direction face) {
        return face.getAxis() == state.getValue(FACING).getClockWise().getAxis() ? 1.0f : 0.0f;
    }

    /** The belt runs on into the next length before and after it that runs the same way. */
    @Override
    public void links(Level level, BlockPos pos, BlockState state, LinkSink sink) {
        Direction facing = state.getValue(FACING);
        for (BlockPos next : new BlockPos[] {pos.relative(facing), pos.relative(facing.getOpposite())}) {
            BlockState there = level.getBlockState(next);
            if (there.getBlock() instanceof ConveyorBlock && there.getValue(FACING) == facing) sink.link(next, 1.0f);
        }
    }

    @Override
    public float impact(Level level, BlockPos pos, BlockState state) {
        return IMPACT;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (!level.isClientSide() && !previous.is(this)) Kinetics.update(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moving) {
        Kinetics.update(level, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConveyorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, AutomationBlocks.CONVEYOR_ENTITY, ConveyorBlockEntity::tick);
    }
}
