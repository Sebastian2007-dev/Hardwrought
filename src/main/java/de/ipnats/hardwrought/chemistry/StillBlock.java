package de.ipnats.hardwrought.chemistry;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Section 62: a copper pot still. Set over a fire — a furnace, a campfire, a forge — it separates
 * crude oil into its fractions, boils seawater down to salt and refines raw sulfur (see
 * {@link StillRecipes}). Opened with an empty hand.
 */
public class StillBlock extends Block implements EntityBlock {
    /** Whether a batch is on the boil. Drives the vapour, nothing else. */
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 10, 14),
            Block.box(5, 10, 5, 11, 14, 11),
            Block.box(7, 14, 7, 9, 16, 9));

    public StillBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RUNNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RUNNING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof StillBlockEntity still)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        player.openMenu(still);
        return InteractionResult.CONSUME;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RUNNING) || random.nextInt(3) != 0) return;
        level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0.0, 0.03, 0.0);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StillBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.STILL) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>)
                (BlockEntityTicker<StillBlockEntity>) StillBlockEntity::serverTick;
        return ticker;
    }
}
