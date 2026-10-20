package de.ipnats.hardwrought.progression;

import de.ipnats.hardwrought.storage.StorageDeskBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Tier three: a joiner's bench bound in steel. It makes what the nailed bench makes and keeps its grid
 * the same way; what it adds is the store room. Set against a storage desk, it becomes the desk's own
 * bench: the desk opens with this grid beside its list, what is made there goes where the player wants
 * it, and the grid is filled again from the chests after every craft (see
 * {@link de.ipnats.hardwrought.storage.StorageDeskMenu}).
 */
public class WorkshopBenchBlock extends CraftingTableBlock implements EntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public WorkshopBenchBlock(Properties properties) {
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
        InteractionResult opened = NailedWorkbenchBlock.openKeptGrid(level, pos, player);
        return opened != null ? opened : super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** The workshop bench standing against a desk at any of its four sides, or above or below it; null for none. */
    public static BlockPos beside(Level level, BlockPos desk) {
        for (Direction side : Direction.values()) {
            BlockPos at = desk.relative(side);
            if (level.getBlockState(at).getBlock() instanceof WorkshopBenchBlock) return at;
        }
        return null;
    }

    /** Whether a desk stands against this bench: shown on the bench's own tooltip in the book, and for tests. */
    public static boolean atDesk(Level level, BlockPos bench) {
        for (Direction side : Direction.values()) {
            if (level.getBlockState(bench.relative(side)).getBlock() instanceof StorageDeskBlock) return true;
        }
        return false;
    }
}
