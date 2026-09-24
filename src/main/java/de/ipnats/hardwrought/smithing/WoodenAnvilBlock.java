package de.ipnats.hardwrought.smithing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The first anvil: a hardwood stump with its end grain up.
 *
 * <p>Wood, because stone is brittle — a block of rock under a hammer splits, and a blacksmith who
 * cannot yet cast iron works on end grain, which takes the blows and springs back. It does not last.
 * Every piece finished on it wears it down, and after {@link #MAX_USES} of them it splits. It also
 * gives: the blows are softer than on iron, so nothing forged on it is ever quite as good as it could
 * be — see {@link Anvils#craftsmanshipCap}.
 *
 * <p>Its wear shows: after a third of its life the end grain cracks, after two thirds it is split
 * through. An iron anvil lasts some hundred pieces; this one about ten.
 */
public class WoodenAnvilBlock extends Block {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /** Wear it can take before it splits: two for every finished piece, and now and then one for a blow. */
    public static final int MAX_USES = 24;
    /** Wear at which it looks cracked, and at which it looks split. */
    public static final int CRACKED = 8;
    public static final int SPLIT = 16;
    public static final IntegerProperty USES = IntegerProperty.create("uses", 0, MAX_USES - 1);

    private static final VoxelShape SHAPE = Shapes.or(box(2, 0, 2, 14, 11, 14), box(1, 11, 1, 15, 14, 15));

    public WoodenAnvilBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(USES, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, USES);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
