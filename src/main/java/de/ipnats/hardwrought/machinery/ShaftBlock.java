package de.ipnats.hardwrought.machinery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A square bar that carries motion along one axis, and the first thing in this mod that moves
 * because something else is moving.
 *
 * <p>It exists to answer one question before any machinery is designed around it: can a block turn
 * on screen, and does the shape of it hold up. Everything that would make it a mechanic — torque,
 * speed, load, breaking under strain — is deliberately absent. It turns or it does not.
 *
 * <p>The block itself draws only the two bearings it sits in. The bar between them is drawn by
 * {@code ShaftRenderer}, because a bar that spins cannot be a block model: block models are baked
 * into the chunk mesh once and do not move. That split — static casing in the model, moving part in
 * the renderer — is the shape every later machine will have.
 *
 * <p>A square cross-section rather than a round one, and not only because Minecraft cannot draw a
 * round one. A square bar shows that it is turning; a cylinder does not.
 */
public class ShaftBlock extends Block implements EntityBlock, KineticBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    /** Whether a crank at either end of this line is turning. Drawing data, nothing more. */
    public static final BooleanProperty DRIVEN = BooleanProperty.create("driven");

    /** How far the bar reaches across the block, in sixteenths. Matches the model. */
    private static final double THICKNESS = 4.0;
    private static final VoxelShape SHAPE_X = box(0, 6, 6, 16, 10, 10);
    private static final VoxelShape SHAPE_Y = box(6, 0, 6, 10, 16, 10);
    private static final VoxelShape SHAPE_Z = box(6, 6, 0, 10, 10, 16);

    public ShaftBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(AXIS, Direction.Axis.Y)
                .setValue(DRIVEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, DRIVEN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> SHAPE_X;
            case Y -> SHAPE_Y;
            case Z -> SHAPE_Z;
        };
    }

    /** Laid along the face it was placed against, the way a log is. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShaftBlockEntity(pos, state);
    }

    /**
     * Client only. The angle exists for the renderer and nothing else, so a server that nobody is
     * looking at does no work for it at all.
     */
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (!level.isClientSide()
                || type != de.ipnats.hardwrought.core.registry.ModBlockEntities.SHAFT) {
            return null;
        }
        // The cast is safe because the type was just checked against the one this block registers.
        @SuppressWarnings("unchecked")
        net.minecraft.world.level.block.entity.BlockEntityTicker<T> ticker =
                (net.minecraft.world.level.block.entity.BlockEntityTicker<T>)
                        (net.minecraft.world.level.block.entity.BlockEntityTicker<ShaftBlockEntity>)
                                KineticBlockEntity::clientTick;
        return ticker;
    }

    /**
     * A shaft that appears or disappears changes what the whole line is doing, so the line is walked
     * again from here.
     *
     * <p>Only when the shaft is genuinely new, though. Marking a line as driven writes a block state
     * to every shaft on it, and every one of those writes lands back here — so refreshing on any
     * change at all made a four-block line walk itself four times, and a long one overflow the
     * stack. A change that only moved {@code DRIVEN} leaves the block the same, and that is exactly
     * the case to ignore.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous,
                           boolean moving) {
        if (level.isClientSide() || previous.is(this)) return;
        Driveline.refresh(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
                                               BlockPos pos, boolean moving) {
        Driveline.update(level, pos);
    }


    /** A shaft's two ends are the axle; nothing turns through its sides. */
    @Override
    public float port(BlockState state, net.minecraft.core.Direction face) {
        return face.getAxis() == state.getValue(AXIS) ? 1.0f : 0.0f;
    }

    /** A belt to another shaft turns that one the same way round, at the same speed. */
    @Override
    public void links(Level level, BlockPos pos, BlockState state, LinkSink sink) {
        if (level.getBlockEntity(pos) instanceof ShaftBlockEntity shaft && shaft.belt() != null
                && level.getBlockEntity(shaft.belt()) instanceof ShaftBlockEntity other && pos.equals(other.belt())) {
            sink.link(shaft.belt(), 1.0f);
        }
    }

    public static double thickness() {
        return THICKNESS;
    }
}
