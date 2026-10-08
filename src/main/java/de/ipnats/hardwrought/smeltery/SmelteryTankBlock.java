package de.ipnats.hardwrought.smeltery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A tank of lava set into a smeltery's wall: brick with a window to see how much is left. The
 * smeltery burns what it holds when there is no coal or coke in its fuel slot.
 *
 * <p>Tanks set against each other join into one: the frame between them goes, the window runs
 * through, and they share their lava as one vessel, filling from the bottom (see
 * {@link SmelteryTankBlockEntity#fillGroup}). Filled and emptied with a bucket, four buckets a block.
 */
public class SmelteryTankBlock extends TransparentBlock implements EntityBlock {
    public SmelteryTankBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (var property : PipeBlock.PROPERTY_BY_DIRECTION.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            BlockState neighbour = context.getLevel().getBlockState(context.getClickedPos().relative(direction));
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), neighbour.is(this));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbour,
                                     RandomSource random) {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), neighbour.is(this));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        boolean filling = stack.is(Items.LAVA_BUCKET);
        if (!filling && !stack.is(Items.BUCKET)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level.getBlockEntity(pos) instanceof SmelteryTankBlockEntity)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        int bucket = SmelteryTankBlockEntity.BUCKET;
        if (filling) {
            if (SmelteryTankBlockEntity.room(level, pos) < bucket) return InteractionResult.FAIL;
            SmelteryTankBlockEntity.fillGroup(level, pos, bucket);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1f, 1f);
        } else {
            if (SmelteryTankBlockEntity.lava(level, pos) < bucket) return InteractionResult.FAIL;
            SmelteryTankBlockEntity.drainGroup(level, pos, bucket);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.LAVA_BUCKET)));
            level.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmelteryTankBlockEntity(pos, state);
    }
}
