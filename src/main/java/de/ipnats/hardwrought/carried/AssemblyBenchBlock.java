package de.ipnats.hardwrought.carried;

import de.ipnats.hardwrought.progression.KeptCraftingMenu;
import de.ipnats.hardwrought.progression.KeptGridBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The assembly bench (stage E4): a steel table with a vice, a parts tray and a drawing board, the rung
 * of the bench ladder above the nailed bench (see {@code BenchTier.ASSEMBLY}). A carried device is laid
 * out on it part by part — housing, cell, switch and the parts that do the work — and nothing lower
 * makes one. Like the nailed bench, it keeps what lies on it.
 */
public class AssemblyBenchBlock extends CraftingTableBlock implements EntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public AssemblyBenchBlock(Properties properties) {
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KeptGridBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer worker) || !(level.getBlockEntity(pos) instanceof KeptGridBlockEntity grid)) {
            return InteractionResult.SUCCESS;
        }
        if (!grid.claim(worker)) {
            worker.sendSystemMessage(Component.translatable("message.hardwrought.bench_in_use"), true);
            return InteractionResult.CONSUME;
        }
        var access = ContainerLevelAccess.create(level, pos);
        worker.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new KeptCraftingMenu(id, inventory, access, grid),
                Component.translatable("container.hardwrought.assembly_bench")));
        return InteractionResult.SUCCESS;
    }
}
