package de.ipnats.hardwrought.water;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.RandomSource;

/**
 * Ice the shape of the water it came from. Specification sections 23.1 and 24.
 *
 * <p>Hardwrought water has a real volume, measured in millibuckets, and a cell holding an eighth of
 * a block is drawn an eighth of a block deep. Vanilla ice is always a whole block, so freezing a
 * puddle with it would hand a player seven eighths of a bucket of water they never had — and a
 * frozen lake would rise above its own banks. This block is the answer: it has the same eight steps
 * the water is drawn in, and it freezes at the height the water stood at.
 *
 * <p>What the cell actually held is not in the block state, because eight steps cannot hold a
 * figure in millibuckets. It stays in {@link FrozenWater}, which is the same kind of chunk
 * attachment the liquid water uses. Freeze and thaw are therefore exact: the water that goes into
 * the ice is the water that comes back out of it, to the millibucket, which is the property the
 * whole water system is built to keep.
 */
public class ThinIceBlock extends Block {
    /** The same eight steps {@link WaterAmounts#displayLevel} draws water in. */
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    public static final int MAX_LAYERS = 8;

    private static final VoxelShape[] SHAPES = makeShapes();

    public ThinIceBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, MAX_LAYERS));
    }

    private static VoxelShape[] makeShapes() {
        VoxelShape[] shapes = new VoxelShape[MAX_LAYERS + 1];
        for (int layers = 0; layers <= MAX_LAYERS; layers++) {
            shapes[layers] = Shapes.box(0, 0, 0, 1, layers / (double) MAX_LAYERS, 1);
        }
        return shapes;
    }

    /** How many eighths of ice a cell holding this much water freezes into; never fewer than one. */
    public static int layersFor(int milliBuckets) {
        int layers = (int) Math.ceil(milliBuckets / (double) WaterAmounts.DISPLAY_STEP);
        return Math.max(1, Math.min(MAX_LAYERS, layers));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                           BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, net.minecraft.world.level.BlockGetter level,
                                       BlockPos pos) {
        return state.getValue(LAYERS) == MAX_LAYERS ? 0.8f : 1.0f;
    }

    /**
     * Breaking ice gives the water back rather than an item. A player who cuts a hole in a frozen
     * lake to fish or to fill a skin should find water under it, which is the whole reason anyone
     * cuts a hole in a frozen lake.
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
                                               boolean movedByPiston) {
        FrozenWater.release(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    /** Ice in a warm place does not wait for a simulation pass to notice. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Freezing.thawIfWarm(level, pos);
    }
}
