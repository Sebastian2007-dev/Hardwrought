package de.ipnats.hardwrought.electricity;

import de.ipnats.hardwrought.machinery.KineticBlock;
import de.ipnats.hardwrought.machinery.Kinetics;
import de.ipnats.hardwrought.parts.Parts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * The electric drive: a motor in a housing on the driveline, the dynamo the other way round. Its axle
 * runs through it along {@link #FACING}; it is joined to a line by a copper coil set against it. See
 * {@link ElectricDriveBlockEntity} for what it gives and what it takes.
 *
 * <p>A motor controller is fitted to it by hand, and an empty hand then sets its speed; a pair of
 * graphite brushes renews its brushes; a coil of thin wire rewinds it burnt. Its housing is earthed.
 */
public class ElectricDriveBlock extends ElectricNodeBlock implements KineticBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    public static final BooleanProperty BROKEN = MachineBlock.BROKEN;
    /** Whether a motor controller is fitted to it. */
    public static final BooleanProperty CONTROLLED = BooleanProperty.create("controlled");
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 13, 15);

    public ElectricDriveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RUNNING, false)
                .setValue(BROKEN, false).setValue(CONTROLLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RUNNING, BROKEN, CONTROLLED);
    }

    /** Its axle lies the way the player looks, so it goes straight onto the end of a shaft. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Vec3 terminal(BlockState state) {
        return new Vec3(0.5, 15.0 / 16.0, 0.5);
    }

    @Override
    public float port(BlockState state, Direction face) {
        return face.getAxis() == state.getValue(FACING).getAxis() ? 1.0f : 0.0f;
    }

    @Override
    public float drive(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof ElectricDriveBlockEntity drive ? drive.drive() : 0.0f;
    }

    @Override
    public float capacity(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof ElectricDriveBlockEntity drive ? drive.capacity() : 0.0f;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (!level.isClientSide() && !previous.is(this)) Kinetics.update(level, pos);
        super.onPlace(state, level, pos, previous, moving);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moving) {
        Kinetics.update(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, moving);
    }

    /** A controller fitted, brushes renewed, burnt windings rewound; shears, a coil and a meter go on to their own use. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ElectricDriveBlockEntity drive)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (stack.is(Parts.MOTOR_CONTROLLER) && !state.getValue(CONTROLLED)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.setBlockAndUpdate(pos, state.setValue(CONTROLLED, true));
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.4f);
            if (level instanceof ServerLevel server) Electricity.update(server, pos);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Parts.GRAPHITE_BRUSH)) {
            if (stack.getCount() < 2 && !player.getAbilities().instabuild) {
                if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.hardwrought.drive.brushes_pair"));
                return InteractionResult.FAIL;
            }
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!drive.newBrushes()) {
                player.sendOverlayMessage(Component.translatable("message.hardwrought.drive.brushes_good",
                        Math.round(drive.brushes() * 100.0f)));
                return InteractionResult.FAIL;
            }
            if (!player.getAbilities().instabuild) stack.shrink(2);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.6f);
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(BROKEN) && stack.is(ElectricBlocks.COPPER_WIRE)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.setBlockAndUpdate(pos, state.setValue(BROKEN, false));
            drive.rewound();
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.4f, 1.6f);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(net.minecraft.world.item.Items.SHEARS) || stack.getItem() instanceof WireItem
                || stack.getItem() instanceof MeterItem) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** With a controller fitted, an empty hand turns it down a quarter, and round again. The housing is earthed. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(CONTROLLED) || !(level.getBlockEntity(pos) instanceof ElectricDriveBlockEntity drive)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        int quarters = drive.turnDown();
        player.sendOverlayMessage(Component.translatable("message.hardwrought.drive.set", quarters * 25));
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, 0.6f + quarters * 0.15f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(BROKEN) && random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 0.0, 0.03, 0.0);
        }
        // The brushes spark a little on the commutator while it turns.
        if (state.getValue(RUNNING) && random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.5,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.02, 0.0);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricDriveBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ElectricBlocks.ELECTRIC_DRIVE_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>)
                (BlockEntityTicker<ElectricDriveBlockEntity>) ElectricDriveBlockEntity::serverTick;
        return ticker;
    }
}
