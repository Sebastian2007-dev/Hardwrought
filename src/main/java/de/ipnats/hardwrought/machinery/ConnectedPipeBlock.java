package de.ipnats.hardwrought.machinery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

/**
 * What every pipe has in common, whatever it carries: how it joins what is around it, and how a pipe
 * wrench sets each of its sides.
 *
 * <p>A pipe joins the pipes of its own kind beside it, and any block that holds what it carries. Towards
 * such a block a side <em>fills</em> it by default; struck there with a {@link PipeWrenchItem} it
 * <em>draws from</em> it instead, and struck again it is cut off from it. A side towards another pipe
 * can be cut off and joined again the same way. Which side is meant is the one whose arm, or face of
 * the pipe's core, the wrench strikes.
 */
public abstract class ConnectedPipeBlock extends Block implements EntityBlock {
    public static final EnumProperty<PipeLink> NORTH = EnumProperty.create("north", PipeLink.class);
    public static final EnumProperty<PipeLink> EAST = EnumProperty.create("east", PipeLink.class);
    public static final EnumProperty<PipeLink> SOUTH = EnumProperty.create("south", PipeLink.class);
    public static final EnumProperty<PipeLink> WEST = EnumProperty.create("west", PipeLink.class);
    public static final EnumProperty<PipeLink> UP = EnumProperty.create("up", PipeLink.class);
    public static final EnumProperty<PipeLink> DOWN = EnumProperty.create("down", PipeLink.class);
    public static final Map<Direction, EnumProperty<PipeLink>> LINKS = new EnumMap<>(Map.of(Direction.NORTH, NORTH,
            Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST, Direction.UP, UP, Direction.DOWN, DOWN));

    /** Half the width of the pipe, in pixels, as its model draws it. */
    private static final double HALF = 4.0;
    private static final VoxelShape CORE = box(8 - HALF, 8 - HALF, 8 - HALF, 8 + HALF, 8 + HALF, 8 + HALF);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Map.of(
            Direction.NORTH, box(8 - HALF, 8 - HALF, 0, 8 + HALF, 8 + HALF, 8 - HALF),
            Direction.SOUTH, box(8 - HALF, 8 - HALF, 8 + HALF, 8 + HALF, 8 + HALF, 16),
            Direction.WEST, box(0, 8 - HALF, 8 - HALF, 8 - HALF, 8 + HALF, 8 + HALF),
            Direction.EAST, box(8 + HALF, 8 - HALF, 8 - HALF, 16, 8 + HALF, 8 + HALF),
            Direction.DOWN, box(8 - HALF, 0, 8 - HALF, 8 + HALF, 8 - HALF, 8 + HALF),
            Direction.UP, box(8 - HALF, 8 + HALF, 8 - HALF, 8 + HALF, 16, 8 + HALF)));

    private final Function<BlockState, VoxelShape> shapes;

    protected ConnectedPipeBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (EnumProperty<PipeLink> link : LINKS.values()) state = state.setValue(link, PipeLink.NONE);
        registerDefaultState(state);
        shapes = getShapeForEachState(ConnectedPipeBlock::shapeOf);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    /** Whether the block beside is part of the same run: another pipe of this kind, or something that belongs in it. */
    protected abstract boolean sameRun(BlockState neighbour);

    /**
     * Whether a side of these pipes can be set to draw. Gas lines cannot: the pressure decides which way
     * their gas goes, so a side towards a block is only ever joined or cut off.
     */
    protected boolean draws() {
        return true;
    }

    /** Whether the block beside holds what these pipes carry, through the given face of it. */
    protected abstract boolean offers(Level level, BlockPos neighbour, Direction face);

    public static PipeLink link(BlockState state, Direction side) {
        return state.hasProperty(LINKS.get(side)) ? state.getValue(LINKS.get(side)) : PipeLink.NONE;
    }

    /** What the pipe at pos is on one side: from what is there, and what a wrench has set. */
    public PipeLink linkFor(LevelReader level, BlockPos pos, Direction side) {
        ConnectedPipeBlockEntity pipe = level.getBlockEntity(pos) instanceof ConnectedPipeBlockEntity own ? own : null;
        if (pipe != null && pipe.disabled(side)) return PipeLink.NONE;
        BlockPos next = pos.relative(side);
        BlockState neighbour = level.getBlockState(next);
        if (sameRun(neighbour)) {
            // A pipe beside that has been cut off from this one is cut off from both ends.
            boolean cut = level.getBlockEntity(next) instanceof ConnectedPipeBlockEntity other && other.disabled(side.getOpposite());
            return cut ? PipeLink.NONE : PipeLink.CONNECTED;
        }
        if (level instanceof Level world && offers(world, next, side.getOpposite())) {
            return pipe != null && pipe.extracting(side) ? PipeLink.EXTRACT : PipeLink.CONNECTED;
        }
        return PipeLink.NONE;
    }

    /** Works out every side of the pipe at pos again, after a wrench or anything else changed them. */
    public static void refresh(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ConnectedPipeBlock pipe)) return;
        BlockState updated = state;
        for (Direction side : Direction.values()) updated = updated.setValue(LINKS.get(side), pipe.linkFor(level, pos, side));
        if (updated != state) level.setBlock(pos, updated, Block.UPDATE_ALL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction side : Direction.values()) {
            state = state.setValue(LINKS.get(side), linkFor(context.getLevel(), context.getClickedPos(), side));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        return state.setValue(LINKS.get(direction), linkFor(level, pos, direction));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        // A storage beside is only found once the pipe is in the world; look again now that it is.
        if (!level.isClientSide() && !previous.is(this)) refresh(level, pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.apply(state);
    }

    private static VoxelShape shapeOf(BlockState state) {
        VoxelShape shape = CORE;
        for (Direction side : Direction.values()) {
            if (link(state, side).joined()) shape = Shapes.or(shape, ARMS.get(side));
        }
        return shape;
    }

    // ---------------------------------------------------------------- the wrench

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof PipeWrenchItem)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ConnectedPipeBlockEntity pipe)) return InteractionResult.PASS;
        Direction side = struck(state, pos, hit);
        String said = turn(level, pos, pipe, side);
        if (said == null) return InteractionResult.PASS;
        refresh(level, pos);
        player.sendOverlayMessage(Component.translatable("message.hardwrought.pipe." + said));
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.5f, 1.6f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Sets one side to the next of what it can be: towards a block, filling, drawing, cut off; towards
     * another pipe, joined or cut off. The name of what it now is, or null if there is nothing there to set.
     */
    private String turn(Level level, BlockPos pos, ConnectedPipeBlockEntity pipe, Direction side) {
        BlockPos next = pos.relative(side);
        if (pipe.disabled(side)) {
            pipe.setDisabled(side, false);
            pipe.setExtracting(side, false);
            return sameRun(level.getBlockState(next)) ? "joined" : "insert";
        }
        if (sameRun(level.getBlockState(next))) {
            pipe.setDisabled(side, true);
            return "cut";
        }
        if (!offers(level, next, side.getOpposite())) return null;
        if (draws() && !pipe.extracting(side)) {
            pipe.setExtracting(side, true);
            return "extract";
        }
        pipe.setExtracting(side, false);
        pipe.setDisabled(side, true);
        return "cut";
    }

    /** The side the wrench was put to: the arm it struck, or the face of the core. */
    static Direction struck(BlockState state, BlockPos pos, BlockHitResult hit) {
        Vec3 local = hit.getLocation().subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double ax = Math.abs(local.x), ay = Math.abs(local.y), az = Math.abs(local.z);
        double reach = Math.max(ax, Math.max(ay, az));
        if (reach <= HALF / 16 + 1e-3) return hit.getDirection();
        if (ax >= ay && ax >= az) return local.x > 0 ? Direction.EAST : Direction.WEST;
        if (ay >= az) return local.y > 0 ? Direction.UP : Direction.DOWN;
        return local.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
