package de.ipnats.hardwrought.storage;

import de.ipnats.hardwrought.electricity.ElectricNodeBlock;
import de.ipnats.hardwrought.electricity.MeterItem;
import de.ipnats.hardwrought.electricity.WireItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The desk of a store room: one place to stand and have every chest on the tubes to hand. It holds
 * nothing of its own and remembers nothing; opening it follows the tubes to the chests that are
 * there now. A room may have as many desks as it likes. See {@link StorageNetwork}.
 *
 * <p>It runs on current, taken through a copper coil set against it as a machine takes it; without
 * current the desk opens and has nothing to show. See {@link StorageDeskBlockEntity}.
 */
public class StorageDeskBlock extends ElectricNodeBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public StorageDeskBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Wires go onto a coil set against it; where one is asked for, it is the middle of its top. */
    @Override
    public Vec3 terminal(BlockState state) {
        return new Vec3(0.5, 1.0, 0.5);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageDeskBlockEntity(pos, state);
    }

    /** Shears cut its wires; a coil or a meter goes on to its own use. Anything else opens it. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (stack.is(Items.SHEARS) || stack.getItem() instanceof WireItem || stack.getItem() instanceof MeterItem) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** Opens it. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer opener && level instanceof ServerLevel server) {
            BlockPos at = pos.immutable();
            opener.openMenu(new SimpleMenuProvider(
                    (id, inventory, who) -> new StorageDeskMenu(id, inventory, server, at), getName()));
        }
        return InteractionResult.SUCCESS;
    }
}
