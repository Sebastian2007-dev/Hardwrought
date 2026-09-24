package de.ipnats.hardwrought.machinery;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

/**
 * Section 53: the first machine that needs the driveline — a pair of stone jaws in a timber frame
 * that break raw ore down to powder, but only while something is turning them.
 *
 * <p>It takes its motion from any side: the end of a driven shaft pointing into it, or a hand
 * crank or crank box right against it. It has no speed, no load and no gearing yet; turning is
 * turning, and a chunk takes {@link StarterCrusherBlockEntity#CRUSH_TICKS} of it.
 *
 * <p>No screen. Ore goes in by using it on the crusher, powder comes out with an empty hand, and
 * hoppers do both from the top and the bottom.
 */
public class StarterCrusherBlock extends Block implements EntityBlock, KineticBlock {
    /** Whether the jaws are working this moment. Drives the particles, nothing else. */
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    public StarterCrusherBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RUNNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RUNNING);
    }

    /** Ore in the hand goes into the jaws. Anything else falls through to the empty-hand use. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!de.ipnats.hardwrought.metallurgy.Crushing.isCrushable(stack)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof StarterCrusherBlockEntity crusher)
                || !crusher.insertFrom(stack, !player.getAbilities().instabuild)) {
            return InteractionResult.FAIL;
        }
        level.playSound(null, pos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 0.6f, 0.9f);
        return InteractionResult.SUCCESS;
    }

    /** An empty hand takes the powder out, or the ore back when nothing has been crushed yet. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof StarterCrusherBlockEntity crusher)) {
            return InteractionResult.PASS;
        }
        ItemStack taken = crusher.takeOut();
        if (taken.isEmpty()) return InteractionResult.PASS;
        // What does not fit into the inventory is left on top of the crusher.
        if (!player.getInventory().add(taken)) popResourceFromFace(level, pos, net.minecraft.core.Direction.UP, taken);
        level.playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.5f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** Grit thrown up out of the jaws while they work. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RUNNING)) return;
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.WHITE_ASH,
                    pos.getX() + 0.25 + random.nextDouble() * 0.5, pos.getY() + 1.02,
                    pos.getZ() + 0.25 + random.nextDouble() * 0.5, 0.0, 0.05, 0.0);
        }
    }

    /** Driven from any side a shaft, gear or crank meets it on. */
    @Override
    public float port(BlockState state, net.minecraft.core.Direction face) {
        return 1.0f;
    }

    @Override
    public float impact(Level level, BlockPos pos, BlockState state) {
        return StarterCrusherBlockEntity.IMPACT;
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
        return new StarterCrusherBlockEntity(pos, state);
    }

    /** Server only: the client learns what it needs from {@link #RUNNING}. */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.STARTER_CRUSHER) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>)
                (BlockEntityTicker<StarterCrusherBlockEntity>) StarterCrusherBlockEntity::serverTick;
        return ticker;
    }
}
