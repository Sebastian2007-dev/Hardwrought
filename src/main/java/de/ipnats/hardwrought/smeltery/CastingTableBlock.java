package de.ipnats.hardwrought.smeltery;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A casting table: put a cast on it, pour metal in from a faucet above, take what has set. */
public class CastingTableBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 15, 16);

    public CastingTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CastingTableBlockEntity table)) return InteractionResult.PASS;
        if (Casts.liesOnTable(stack)) {
            if (!level.isClientSide()) table.placeCast(stack);
            return InteractionResult.SUCCESS;
        }
        // A bar or a part pressed into a blank leaves its shape in the clay.
        if (!table.cast().is(Casts.BLANK) || Casts.imprintOf(stack) == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) {
            Casts.Cast shape = table.imprint(stack);
            if (shape != null && player instanceof net.minecraft.server.level.ServerPlayer presser) {
                presser.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.hardwrought.cast.imprinted",
                        new ItemStack(shape.fired()).getHoverName()));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CastingTableBlockEntity table)) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            ItemStack taken = table.take();
            if (!taken.isEmpty() && !player.getInventory().add(taken)) Block.popResource(level, pos.above(), taken);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CastingTableBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != SmelteryBlocks.CASTING_TABLE_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>)
                (BlockEntityTicker<CastingTableBlockEntity>) CastingTableBlockEntity::serverTick;
        return ticker;
    }
}
