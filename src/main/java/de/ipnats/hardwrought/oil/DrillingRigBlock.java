package de.ipnats.hardwrought.oil;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.machinery.KineticBlock;
import de.ipnats.hardwrought.machinery.Kinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
 * Section 60: the drilling rig. Set on the ground and turned by a line — a water wheel or a
 * windmill; a hand crank is not strong enough — it bores down until it breaks into a reservoir, and
 * from then on it is a well.
 *
 * <p>No screen. Using it with an empty hand reads it: how deep it is, what it has struck, what is in
 * its tank. An empty bucket draws a bucket of crude oil off it, an empty gas canister a canister of
 * gas. A gas pipe on top of it takes its gas away; without one the gas it cannot hold escapes where
 * it stands (see {@link DrillingRigBlockEntity}).
 */
public class DrillingRigBlock extends Block implements EntityBlock, KineticBlock {
    /** Whether it is boring or pumping this moment. Drives the particles, nothing else. */
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    public DrillingRigBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RUNNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RUNNING);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        boolean bucket = stack.is(Items.BUCKET);
        boolean canister = stack.is(ModItems.GAS_CANISTER);
        if (!bucket && !canister) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(pos) instanceof DrillingRigBlockEntity rig)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack filled = bucket ? rig.drawOil() : rig.drawGas();
        if (filled.isEmpty()) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable(bucket
                    ? "message.hardwrought.rig.no_oil" : "message.hardwrought.rig.no_gas"));
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        if (!player.getInventory().add(filled) && level instanceof net.minecraft.server.level.ServerLevel server) {
            player.spawnAtLocation(server, filled);
        }
        level.playSound(null, pos, bucket ? SoundEvents.BUCKET_FILL : SoundEvents.BOTTLE_FILL,
                SoundSource.BLOCKS, 1.0f, 0.8f);
        return InteractionResult.SUCCESS;
    }

    /** An empty hand reads the well. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DrillingRigBlockEntity rig)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendOverlayMessage(rig.describe());
        return InteractionResult.SUCCESS;
    }

    /** Rock dust while it bores, a dark spatter while it pumps oil. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RUNNING)) return;
        level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5 + random.nextGaussian() * 0.1, pos.getY() + 1.05,
                pos.getZ() + 0.5 + random.nextGaussian() * 0.1, 0.0, 0.03, 0.0);
    }

    /** Driven from any side a shaft, gear or crank meets it on. */
    @Override
    public float port(BlockState state, Direction face) {
        return 1.0f;
    }

    @Override
    public float impact(Level level, BlockPos pos, BlockState state) {
        return DrillingRigBlockEntity.IMPACT;
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
        return new DrillingRigBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.DRILLING_RIG) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>)
                (BlockEntityTicker<DrillingRigBlockEntity>) DrillingRigBlockEntity::serverTick;
        return ticker;
    }
}
