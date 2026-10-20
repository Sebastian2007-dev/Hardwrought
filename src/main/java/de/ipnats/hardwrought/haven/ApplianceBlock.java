package de.ipnats.hardwrought.haven;

import de.ipnats.hardwrought.electricity.ElectricNodeBlock;
import de.ipnats.hardwrought.electricity.MeterItem;
import de.ipnats.hardwrought.electricity.WireItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A device of the safe haven — which one, its {@link Appliance} says. Wire is made fast to it directly;
 * its casing is earthed and can be handled while live. The part that went, used on it, mends it; an
 * empty hand turns its setting where it has one.
 */
public class ApplianceBlock extends ElectricNodeBlock implements de.ipnats.hardwrought.storage.StorageNetwork.Member {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /** Whether it is working this moment. */
    public static final BooleanProperty ON = BooleanProperty.create("on");
    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");

    private final Appliance appliance;

    public ApplianceBlock(Appliance appliance, Properties properties) {
        super(properties);
        this.appliance = appliance;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false).setValue(BROKEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON, BROKEN);
    }

    public Appliance appliance() {
        return appliance;
    }

    /** An assembler is part of a storage network: the tubes run to it, and the desk orders from it. */
    @Override
    public boolean joinsStorage(BlockState state) {
        return appliance == Appliance.ASSEMBLER;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return appliance.shape(state.getValue(FACING));
    }

    @Override
    public Vec3 terminal(BlockState state) {
        return appliance.terminal(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(BROKEN) && stack.is(appliance.mends())) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.setBlockAndUpdate(pos, state.setValue(BROKEN, false));
            if (level.getBlockEntity(pos) instanceof ApplianceBlockEntity entity) entity.mended();
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.5f, 1.5f);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.SHEARS) || stack.getItem() instanceof WireItem || stack.getItem() instanceof MeterItem) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer server)) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ApplianceBlockEntity entity)) return InteractionResult.PASS;
        if (entity instanceof net.minecraft.world.MenuProvider menu) {
            server.openMenu(menu);
            return InteractionResult.SUCCESS;
        }
        Component said = entity.use(server);
        if (said == null) return InteractionResult.PASS;
        server.sendOverlayMessage(said);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, 1.2f);
        return InteractionResult.SUCCESS;
    }

    /** A motion lamp lit for something it saw gives a signal. */
    @Override
    protected boolean isSignalSource(BlockState state) {
        return appliance == Appliance.MOTION_LAMP;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof MotionLampBlockEntity lamp ? lamp.signal() : 0;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(BROKEN) && random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 0.0, 0.03, 0.0);
        }
        if (state.getValue(ON)) appliance.animate(state, level, pos, random);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return appliance.create(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (l, p, s, entity) -> {
            if (entity instanceof ApplianceBlockEntity appliance) ApplianceBlockEntity.serverTick(l, p, s, appliance);
        };
    }
}
