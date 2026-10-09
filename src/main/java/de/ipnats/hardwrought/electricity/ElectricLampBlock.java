package de.ipnats.hardwrought.electricity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Section 22 and 76: the electric lamp — light with no fuel in it, as bright as the voltage that
 * reaches it. See {@link LampBlockEntity} for what too little and too much voltage do.
 */
public class ElectricLampBlock extends ElectricNodeBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    /** The light it gives, 0 to 15. */
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);
    /** The filament is gone. A length of wire makes a new one. */
    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");

    public ElectricLampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(LIGHT, 0).setValue(BROKEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIGHT, BROKEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return InsulatorBlock.standing(state.getValue(FACING), 8, 11);
    }

    /** The wire goes into its socket. */
    @Override
    public Vec3 terminal(BlockState state) {
        return InsulatorBlock.out(state.getValue(FACING), 2.5);
    }

    /** A coil of thin wire on a dead lamp winds it a new filament. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!state.getValue(BROKEN) || !stack.is(ElectricBlocks.COPPER_WIRE)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        level.setBlockAndUpdate(pos, state.setValue(BROKEN, false));
        level.playSound(null, pos, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 0.7f, 1.2f);
        if (level instanceof net.minecraft.server.level.ServerLevel server) Electricity.update(server, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LampBlockEntity(pos, state);
    }
}
