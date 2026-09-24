package de.ipnats.hardwrought.progression;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Tier two: the hewn bench rebuilt from split boards without losing the timber it came from.
 *
 * <p>The first bench that keeps what is left on it. Whatever lies in the grid when the menu is
 * closed is still there next time — see {@link KeptGridBlockEntity}.
 */
public class NailedWorkbenchBlock extends CraftingTableBlock implements net.minecraft.world.level.block.EntityBlock {
    public static final EnumProperty<HewnWood> WOOD = HewnWorkbenchBlock.WOOD;
    public static final EnumProperty<net.minecraft.core.Direction> FACING = HorizontalDirectionalBlock.FACING;

    public NailedWorkbenchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WOOD, HewnWood.OAK)
                .setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WOOD, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return (state == null ? defaultBlockState() : state)
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KeptGridBlockEntity(pos, state);
    }

    /**
     * Opens the grid that belongs to this bench, unless somebody else is working at it. A bench from
     * before benches kept their grid has no storage yet and opens the ordinary one.
     */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level,
                                                                   BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                                   net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof net.minecraft.server.level.ServerPlayer worker)
                || !(level.getBlockEntity(pos) instanceof KeptGridBlockEntity grid)) {
            return super.useWithoutItem(state, level, pos, player, hit);
        }
        if (!grid.claim(worker)) {
            worker.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.hardwrought.bench_in_use"), true);
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        var access = net.minecraft.world.inventory.ContainerLevelAccess.create(level, pos);
        worker.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inventory, opener) -> new KeptCraftingMenu(id, inventory, access, grid),
                net.minecraft.network.chat.Component.translatable("container.crafting")));
        worker.awardStat(net.minecraft.stats.Stats.INTERACT_WITH_CRAFTING_TABLE);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    public ItemStack itemStack(HewnWood wood) {
        ItemStack stack = new ItemStack(this);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(WOOD, wood));
        return stack;
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state,
                                          boolean includeData) {
        return itemStack(state.getValue(WOOD));
    }
}
