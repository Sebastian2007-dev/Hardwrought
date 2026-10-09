package de.ipnats.hardwrought.electricity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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

/**
 * Sections 53, 76 and 77: a machine that runs on current instead of on a shaft — which one, its
 * {@link Machine} says. It stands wherever a wire reaches, is opened like a furnace, and takes from
 * hoppers and gives to them as one does. See {@link MachineBlockEntity}.
 *
 * <p>Its casing is earthed: unlike a lamp or a bare insulator it can be worked by hand while live.
 */
public class MachineBlock extends ElectricNodeBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /** Whether it is working this moment. */
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    /** The motor's windings are burnt. A coil of thin wire rewinds them. */
    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");

    private final Machine machine;

    public MachineBlock(Machine machine, Properties properties) {
        super(properties);
        this.machine = machine;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(RUNNING, false).setValue(BROKEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RUNNING, BROKEN);
    }

    public Machine machine() {
        return machine;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** The wire goes onto the post at the back of its top. */
    @Override
    public Vec3 terminal(BlockState state) {
        Direction back = state.getValue(FACING).getOpposite();
        return new Vec3(0.5 + back.getStepX() * 0.3125, 1.0, 0.5 + back.getStepZ() * 0.3125);
    }

    /** Thin wire rewinds a burnt one; shears cut its wires; a coil or a meter goes on to its own use. Anything else opens it. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(BROKEN) && stack.is(ElectricBlocks.COPPER_WIRE)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.setBlockAndUpdate(pos, state.setValue(BROKEN, false));
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.4f, 1.6f);
            if (level instanceof ServerLevel server) Electricity.update(server, pos);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(net.minecraft.world.item.Items.SHEARS) || stack.getItem() instanceof WireItem
                || stack.getItem() instanceof MeterItem) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** Opens it. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity entity) player.openMenu(entity);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(BROKEN) && random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.02, pos.getZ() + 0.5, 0.0, 0.03, 0.0);
        }
        if (!state.getValue(RUNNING)) return;
        for (int i = 0; i < 3; i++) {
            level.addParticle(machine.particle(), pos.getX() + 0.25 + random.nextDouble() * 0.5, pos.getY() + 1.02,
                    pos.getZ() + 0.25 + random.nextDouble() * 0.5, 0.0, 0.05, 0.0);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ElectricBlocks.MACHINE_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>)
                (BlockEntityTicker<MachineBlockEntity>) MachineBlockEntity::serverTick;
        return ticker;
    }
}
