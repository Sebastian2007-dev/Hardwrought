package de.ipnats.hardwrought.machinery;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The head of an ore drill (see {@link OreDrillBlockEntity}). The frame closes it in on every side
 * but the bottom, so it is driven from below: a shaft coming up out of the ground, or a crank box
 * under it.
 *
 * <p>Using it opens what it has brought up and reads it: its tier and state, and what the chunk
 * under it holds for it.
 */
public class OreDrillBlock extends Block implements EntityBlock, KineticBlock {
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    /** Whether a complete frame closes the head in, so the drill is drawn as one machine. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    /** The side the ore leaves by: toward whoever set the head down. */
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction> FACING =
            net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

    public OreDrillBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RUNNING, false).setValue(FORMED, false)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RUNNING, FORMED, FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** The head on its own: a braced cap, the gear housing and the bit tapering down under it. */
    private static final net.minecraft.world.phys.shapes.VoxelShape LOOSE = net.minecraft.world.phys.shapes.Shapes.or(
            box(1, 13, 1, 15, 16, 15), box(3, 7, 3, 13, 13, 13), box(4, 5, 4, 12, 7, 12), box(6, 0, 6, 10, 5, 10));

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                                                  BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        return state.getValue(FORMED) ? OreDrillShapes.head() : LOOSE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return open(level, pos, player);
    }

    /** Shows the drill whose head is here, from whichever of its blocks was used: its state and its chunk. */
    public static InteractionResult open(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof OreDrillBlockEntity drill)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        drill.lookOverFrameNow();
        if (player instanceof ServerPlayer serverPlayer
                && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(serverPlayer,
                de.ipnats.hardwrought.core.networking.OreDrillPayloads.Info.TYPE)) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer, drill.info(serverPlayer, true));
        }
        return InteractionResult.CONSUME;
    }

    /** How far from the head a player may be and still have its screen kept current. */
    public static final double WATCH_RANGE_SQR = 12.0 * 12.0;

    /** Rock dust out of the frame while it works. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RUNNING)) return;
        level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                pos.getX() + 0.5 + random.nextGaussian() * 0.6, pos.getY() + 0.2,
                pos.getZ() + 0.5 + random.nextGaussian() * 0.6, 0.0, 0.1, 0.0);
    }

    @Override
    public float port(BlockState state, Direction face) {
        return 1.0f;
    }

    /** The frame round a finished head carries its drive in from outside; see {@link DrillFrameBlock#links}. */
    @Override
    public void links(Level level, BlockPos pos, BlockState state, LinkSink sink) {
        for (BlockPos frame : OreDrillBlockEntity.framePositions(pos)) {
            BlockState there = level.getBlockState(frame);
            if (there.getBlock() instanceof DrillFrameBlock && there.getValue(DrillFrameBlock.PART) != 0) {
                sink.link(frame, 1.0f);
            }
        }
    }

    @Override
    public float impact(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof OreDrillBlockEntity drill ? drill.impact() : OreDrillBlockEntity.BASE_IMPACT;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (!level.isClientSide() && !previous.is(this)) Kinetics.update(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
                                               BlockPos pos, boolean moving) {
        Kinetics.update(level, pos);
        // With the head gone the frame is loose blocks again.
        OreDrillBlockEntity.shape(level, pos, false);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OreDrillBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.ORE_DRILL) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>)
                (BlockEntityTicker<OreDrillBlockEntity>) OreDrillBlockEntity::serverTick;
        return ticker;
    }
}
