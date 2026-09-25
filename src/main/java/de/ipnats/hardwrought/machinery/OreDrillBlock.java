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

    public OreDrillBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RUNNING, false).setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RUNNING, FORMED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return open(level, pos, player);
    }

    /** Opens the drill whose head is here, from whichever of its blocks was used. */
    public static InteractionResult open(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof OreDrillBlockEntity drill)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        drill.lookOverFrameNow();
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(drill.describe());
            for (var line : drill.composition()) serverPlayer.sendSystemMessage(line);
        }
        player.openMenu(drill);
        return InteractionResult.CONSUME;
    }

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
