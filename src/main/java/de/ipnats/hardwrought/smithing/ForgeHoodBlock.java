package de.ipnats.hardwrought.smithing;

import de.ipnats.hardwrought.environment.Flues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/**
 * The hood hung over a forge, which takes the fumes of the fire up and away.
 *
 * <p>A coal hearth burning indoors fills the room with carbon dioxide and carbon monoxide (see
 * {@code GasSources}). A hood one to three blocks above the bed catches what rises from it, over its
 * own block and the ring of eight around it, so one hood covers a three-by-three hearth. It also
 * catches the light gas that drifts up into it from the room.
 *
 * <p>Hoods side by side join into one canopy: the skirt runs only round the outside of it. What a
 * canopy catches leaves through its flue (see {@link Flues}): a gas pipe run off it lets it out at the
 * open end, and without one it comes out above the highest hood of the canopy. A hood draws its own
 * air through the fire, so a hooded forge is never smothered by the gas in the room.
 */
public class ForgeHoodBlock extends Block {
    /** How far above the bed of coals a hood still catches what rises from it. */
    public static final int REACH = 3;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    /** Something the flue goes on into above: another hood or a gas pipe. */
    public static final BooleanProperty UP = BlockStateProperties.UP;

    private static final VoxelShape PLATE = Block.box(0, 6, 0, 16, 8, 16);
    private static final VoxelShape COLLAR = Block.box(4, 8, 4, 12, 16, 12);
    private static final VoxelShape STUB = Block.box(5, 8, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> SKIRTS = Map.of(
            Direction.NORTH, Block.box(0, 0, 0, 16, 6, 2),
            Direction.SOUTH, Block.box(0, 0, 14, 16, 6, 16),
            Direction.WEST, Block.box(0, 0, 0, 2, 6, 16),
            Direction.EAST, Block.box(14, 0, 0, 16, 6, 16));
    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public ForgeHoodBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false).setValue(UP, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP);
    }

    public static BooleanProperty side(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> null;
        };
    }

    /** Whether this hood joins what is on that side: another hood beside it, a hood or a pipe above it. */
    private static boolean joins(Direction direction, BlockState neighbour) {
        if (direction == Direction.DOWN) return false;
        if (neighbour.getBlock() instanceof ForgeHoodBlock) return true;
        return direction == Direction.UP && neighbour.getBlock() instanceof GasPipeBlock;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) continue;
            BlockState neighbour = context.getLevel().getBlockState(context.getClickedPos().relative(direction));
            state = state.setValue(side(direction), joins(direction, neighbour));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbour,
                                     RandomSource random) {
        if (direction == Direction.DOWN) return state;
        return state.setValue(side(direction), joins(direction, neighbour));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.computeIfAbsent(state, ForgeHoodBlock::shapeOf);
    }

    private static VoxelShape shapeOf(BlockState state) {
        VoxelShape shape = PLATE;
        boolean alone = true;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (state.getValue(side(direction))) alone = false;
            else shape = Shapes.or(shape, SKIRTS.get(direction));
        }
        if (state.getValue(UP)) shape = Shapes.or(shape, COLLAR);
        else if (alone) shape = Shapes.or(shape, STUB);
        return shape;
    }

    /** Whether a hood hangs close enough above this forge block to take its fumes. */
    public static boolean vents(BlockGetter level, BlockPos forge) {
        return hoodOver(level, forge) != null;
    }

    /** The hood over this forge block, within reach, or null where there is none. */
    public static BlockPos hoodOver(BlockGetter level, BlockPos forge) {
        for (int dy = 1; dy <= REACH; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos hood = forge.offset(dx, dy, dz);
                    if (level.getBlockState(hood).getBlock() instanceof ForgeHoodBlock) return hood;
                }
            }
        }
        return null;
    }

    /** Whether a burning forge lies below this hood, within its reach. */
    private static boolean overFire(Level level, BlockPos hood) {
        for (int dy = 1; dy <= REACH; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockState below = level.getBlockState(hood.offset(dx, -dy, dz));
                    if (below.getBlock() instanceof ForgeBlock && below.getValue(ForgeBlock.LIT)) return true;
                }
            }
        }
        return false;
    }

    /** Smoke out of the outlet of the flue, so it can be seen that the hood is drawing. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Every hood of a canopy over a fire would find the same outlet; a few of them are enough.
        if (random.nextInt(4) != 0 || !overFire(level, pos)) return;
        BlockPos outlet = Flues.outlet(level, pos);
        if (outlet == null) return;
        level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, outlet.getX() + 0.5 + random.nextDouble() * 0.2 - 0.1,
                outlet.getY() + 0.2, outlet.getZ() + 0.5 + random.nextDouble() * 0.2 - 0.1, 0.0, 0.05, 0.0);
    }
}
