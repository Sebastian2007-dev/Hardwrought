package de.ipnats.hardwrought.machinery;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * Section 72: the first source of motion a player pays for with their own arm.
 *
 * <p>Mounted against the end of a shaft, it turns that shaft for exactly as long as the use button
 * is held on it. Holding the button repeats the use every four ticks, and every repetition is one
 * stroke: it costs a little stamina and keeps the crank turning for {@link #HOLD_TICKS} more. Let go
 * and the strokes stop coming, the window runs out, and the line winds down with it.
 *
 * <p>Unlike the crank box it turns out of one face only — the one it was mounted on. A crank is a
 * handle on an axle, and the axle goes one way.
 *
 * <p>The handle is drawn by {@code HandCrankRenderer}, for the same reason the shaft's bar is: the
 * part that moves cannot be part of the block model.
 */
public class HandCrankBlock extends Block implements EntityBlock, KineticBlock {
    /** The face the axle leaves by, and so the one face this crank drives. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty TURNING = BooleanProperty.create("turning");

    /**
     * How long one stroke keeps the crank turning. Held, the use repeats every four ticks; two ticks
     * of slack keep a lagging connection from making the line stutter.
     */
    public static final int HOLD_TICKS = 6;
    /**
     * What one stroke costs. Five strokes a second makes cranking twice as hard as sprinting, which
     * is about right for turning a load by hand and short enough that a player looks for a better
     * source before long.
     */
    public static final double STAMINA_PER_STROKE = 0.08;
    /** How fast an arm turns a crank, in turns per minute. */
    public static final float SPEED = 16.0f;
    /** How much an arm can drive: one crusher, or a bellows and a little more — not two crushers. */
    public static final float CAPACITY = 160.0f;

    /** Bearing on the north face, handle swept in front of it. Rotated for every other face. */
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateAll(Shapes.or(
            box(4, 4, 0, 12, 12, 3),
            box(1, 1, 3, 15, 15, 16)));

    public HandCrankBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(TURNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TURNING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Mounted against the face it was placed on, the way a button is. */
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

    /** The axle leaves by the one face the crank is mounted on. */
    @Override
    public float port(BlockState state, Direction face) {
        return face == state.getValue(FACING) ? 1.0f : 0.0f;
    }

    @Override
    public float drive(Level level, BlockPos pos, BlockState state) {
        return state.getValue(TURNING) ? SPEED : 0.0f;
    }

    @Override
    public float capacity(Level level, BlockPos pos, BlockState state) {
        return state.getValue(TURNING) ? CAPACITY : 0.0f;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        // Success on the client as well, so the arm swings with every stroke.
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer worker) {
            var runtime = CoreLifecycle.find(server.getServer());
            if (runtime != null && !runtime.survival().spendStamina(worker, STAMINA_PER_STROKE)) {
                worker.sendSystemMessage(Component.translatable("message.hardwrought.too_tired_to_crank"), true);
                return InteractionResult.FAIL;
            }
        }
        crank(server, pos);
        return InteractionResult.SUCCESS;
    }

    /**
     * One stroke on the crank at this position: starts it turning if it was not, and keeps it turning
     * for another {@link #HOLD_TICKS}. Public so tests can crank without a player's stamina in the way.
     */
    public static void crank(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HandCrankBlock block)) return;
        if (level.getBlockEntity(pos) instanceof HandCrankBlockEntity crank) {
            crank.stroke(level.getGameTime());
        }
        if (!state.getValue(TURNING)) {
            level.setBlock(pos, state.setValue(TURNING, true), Block.UPDATE_ALL);
        }
        // Ignored while one is already pending, which is fine: that one reschedules itself.
        level.scheduleTick(pos, block, HOLD_TICKS);
        level.playSound(null, pos, SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 0.25f,
                0.55f + level.getRandom().nextFloat() * 0.1f);
    }

    /** The end of a stroke's window: stop, unless another stroke has landed since. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(TURNING)) return;
        if (level.getBlockEntity(pos) instanceof HandCrankBlockEntity crank) {
            long idle = level.getGameTime() - crank.lastStroke();
            if (idle < HOLD_TICKS) {
                level.scheduleTick(pos, this, (int) (HOLD_TICKS - idle));
                return;
            }
        }
        level.setBlock(pos, state.setValue(TURNING, false), Block.UPDATE_ALL);
    }

    /**
     * The driveline is walked again whenever this crank appears, turns, stops or is turned to face
     * somewhere else — and not for a change that left all of that alone.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous,
                           boolean moving) {
        if (level.isClientSide()) return;
        if (previous.is(this) && previous.getValue(TURNING) == state.getValue(TURNING)
                && previous.getValue(FACING) == state.getValue(FACING)) {
            return;
        }
        Driveline.update(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
                                               boolean moving) {
        Driveline.update(level, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HandCrankBlockEntity(pos, state);
    }

    /** Client only, like the shaft's: the angle is for the renderer and nothing else. */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (!level.isClientSide() || type != ModBlockEntities.HAND_CRANK) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>)
                (BlockEntityTicker<HandCrankBlockEntity>) KineticBlockEntity::clientTick;
        return ticker;
    }
}
