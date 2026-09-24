package de.ipnats.hardwrought.machinery;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A toothed wheel on a shaft. It carries its shaft's motion through like a shaft does, and passes it
 * sideways to other gears whose teeth it meets.
 *
 * <ul>
 *   <li>Two small gears side by side, on parallel axles, turn at the same speed the other way round.</li>
 *   <li>A large gear is twice the size. A small gear meets it across the corner, and turns twice as
 *       fast as the large one, the other way round. That is the whole of gearing: up for speed, down
 *       for strength — see {@link Kinetics}.</li>
 * </ul>
 */
public class CogwheelBlock extends Block implements EntityBlock, KineticBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    private final boolean large;
    private final VoxelShape shapeX;
    private final VoxelShape shapeY;
    private final VoxelShape shapeZ;

    public CogwheelBlock(Properties properties, boolean large) {
        super(properties);
        this.large = large;
        double r = large ? 8.0 : 7.0;
        double lo = 8.0 - r;
        double hi = 8.0 + r;
        this.shapeY = box(lo, 6, lo, hi, 10, hi);
        this.shapeX = box(6, lo, lo, 10, hi, hi);
        this.shapeZ = box(lo, lo, 6, hi, hi, 10);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Y));
    }

    public static CogwheelBlock small(Properties properties) {
        return new CogwheelBlock(properties, false);
    }

    public static CogwheelBlock large(Properties properties) {
        return new CogwheelBlock(properties, true);
    }

    public boolean isLarge() {
        return large;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> shapeX;
            case Y -> shapeY;
            case Z -> shapeZ;
        };
    }

    /** On the axis of the face it was placed against, like a shaft. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    public float port(BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS) ? 1.0f : 0.0f;
    }

    /**
     * Teeth meeting teeth. Small with small straight beside it; small with large across the corner.
     * Only on parallel axles, and only in the plane the wheels turn in.
     */
    @Override
    public void links(Level level, BlockPos pos, BlockState state, LinkSink sink) {
        Direction.Axis axis = state.getValue(AXIS);
        for (Direction first : Direction.values()) {
            if (first.getAxis() == axis) continue;
            if (!large) {
                BlockPos beside = pos.relative(first);
                if (meshes(level, beside, axis, false)) sink.link(beside, -1.0f);
            }
            for (Direction second : Direction.values()) {
                if (second.getAxis() == axis || second.getAxis() == first.getAxis()) continue;
                // Each corner is reached twice, once from either side; both give the same answer.
                BlockPos corner = pos.relative(first).relative(second);
                if (meshes(level, corner, axis, !large)) sink.link(corner, large ? -2.0f : -0.5f);
            }
        }
    }

    private static boolean meshes(Level level, BlockPos pos, Direction.Axis axis, boolean large) {
        BlockState other = level.getBlockState(pos);
        return other.getBlock() instanceof CogwheelBlock cog && cog.large == large && other.getValue(AXIS) == axis;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (!level.isClientSide() && !previous.is(this)) Kinetics.update(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
                                               BlockPos pos, boolean moving) {
        Kinetics.update(level, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KineticBlockEntity(ModBlockEntities.KINETIC, pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return KineticTickers.client(level, type);
    }
}
