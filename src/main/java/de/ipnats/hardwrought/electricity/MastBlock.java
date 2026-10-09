package de.ipnats.hardwrought.electricity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Section 76: a line mast — a timber pole {@link #HEIGHT} blocks tall with an insulator on its
 * crossarm, set up in one piece. It carries a line over the heads of everything that walks under it:
 * a wire strung from mast to mast hangs, at its lowest, well above a standing player.
 *
 * <p>The head is the point of the network. A coil, a meter or shears used anywhere on the pole reach
 * it, since nobody's arm does. Any part broken, the whole mast comes down.
 */
public class MastBlock extends ElectricNodeBlock {
    public static final int HEIGHT = 4;
    /** Which block of the mast this is, counted from the foot. */
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, HEIGHT - 1);

    private static final VoxelShape POLE = box(6, 0, 6, 10, 16, 10);
    private static final VoxelShape HEAD = Shapes.or(box(6, 0, 6, 10, 12, 10), box(1, 9, 6, 15, 12, 10),
            box(6, 12, 6, 10, 16, 10));

    public MastBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SEGMENT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SEGMENT);
    }

    /** Where the head of the mast is that this block is part of. */
    public static BlockPos head(BlockPos pos, BlockState state) {
        return pos.above(HEIGHT - 1 - state.getValue(SEGMENT));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(SEGMENT) == HEIGHT - 1 ? HEAD : POLE;
    }

    /** Only where the whole of it has room. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos foot = context.getClickedPos();
        Level level = context.getLevel();
        for (int i = 1; i < HEIGHT; i++) {
            if (!level.isInsideBuildHeight(foot.above(i)) || !level.getBlockState(foot.above(i)).canBeReplaced(context)) return null;
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        for (int i = 1; i < HEIGHT; i++) level.setBlock(pos.above(i), state.setValue(SEGMENT, i), Block.UPDATE_ALL);
    }

    /** A mast is one thing: with the part under or over this one gone, this one goes too. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        int segment = state.getValue(SEGMENT);
        boolean belongs = neighborState.is(this) && neighborState.getValue(SEGMENT) == segment + direction.getStepY();
        if ((direction == Direction.DOWN && segment > 0 || direction == Direction.UP && segment < HEIGHT - 1) && !belongs) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    /** The wires are tied round the insulator on top of the crossarm. */
    @Override
    public Vec3 terminal(BlockState state) {
        return new Vec3(0.5, 14.0 / 16.0, 0.5);
    }

    /** Shears anywhere on the pole take the wires off its head. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!stack.is(Items.SHEARS)) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        return Electricity.cut(server, head(pos, state), player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /** Timber: a hand on the pole feels nothing of what runs over it. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(SEGMENT) == HEIGHT - 1 ? new ElectricBlockEntity(ElectricBlocks.MAST_ENTITY, pos, state) : null;
    }
}
