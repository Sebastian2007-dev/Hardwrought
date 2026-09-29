package de.ipnats.hardwrought.machinery;

import de.ipnats.hardwrought.geology.DrillTier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One block of an ore drill's frame (see {@link OreDrillBlockEntity}). Five metals, five tiers: the
 * frame decides how deep the drill reaches, and a frame is only as strong as its weakest block.
 *
 * <p>Built into a complete drill, every frame block draws its piece of the drill as a whole — a
 * casing below, a derrick above — and using any of them works the drill, since its head is closed in.
 * For the same reason the frame carries the drive: a shaft or gear against any outer face of a
 * finished drill turns its head.
 */
public class DrillFrameBlock extends Block implements KineticBlock {
    /** Where this block sits in a finished drill, 1 to 17; 0 while it is a loose frame. */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, OreDrillBlockEntity.FRAME_BLOCKS);
    /** The block in front of the head, which carries the chute the ore comes out of. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty OUTPUT =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("output");

    private final DrillTier tier;

    public DrillFrameBlock(DrillTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(PART, 0).setValue(OUTPUT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, OUTPUT);
    }

    public DrillTier tier() {
        return tier;
    }

    /** Loose, a frame is a full block; built in, it holds only its piece of the drill. */
    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                                                  BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
        int part = state.getValue(PART);
        return part == 0 ? net.minecraft.world.phys.shapes.Shapes.block() : OreDrillShapes.part(part);
    }

    /** An axle meets a finished drill on any of its outer faces; a loose frame takes none. */
    @Override
    public float port(BlockState state, net.minecraft.core.Direction face) {
        int part = state.getValue(PART);
        if (part == 0) return 0.0f;
        BlockPos beyond = OreDrillBlockEntity.framePositions(BlockPos.ZERO).get(part - 1).relative(face);
        boolean outside = Math.abs(beyond.getX()) > 1 || Math.abs(beyond.getZ()) > 1 || beyond.getY() < 0 || beyond.getY() > 1;
        return outside ? 1.0f : 0.0f;
    }

    /** What turns the frame turns the head inside it. */
    @Override
    public void links(Level level, BlockPos pos, BlockState state, LinkSink sink) {
        int part = state.getValue(PART);
        if (part == 0) return;
        BlockPos head = OreDrillBlockEntity.headOf(pos, part);
        if (level.getBlockState(head).getBlock() instanceof OreDrillBlock) sink.link(head, 1.0f);
    }

    /** Any block of a finished drill opens the drill. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        int part = state.getValue(PART);
        if (part == 0) return InteractionResult.PASS;
        BlockPos head = OreDrillBlockEntity.headOf(pos, part);
        if (!(level.getBlockState(head).getBlock() instanceof OreDrillBlock)) return InteractionResult.PASS;
        return OreDrillBlock.open(level, head, player);
    }

    /** A frame block set down may be the one that finishes a drill: every head it could belong to looks. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (level.isClientSide() || previous.getBlock() instanceof DrillFrameBlock) return;
        lookOverDrillsAround(level, pos);
    }

    /** Taking a block out of a finished drill makes it loose frames again at once. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moving) {
        if (state.getValue(PART) != 0) lookOverDrillsAround(level, pos);
    }

    private static void lookOverDrillsAround(Level level, BlockPos pos) {
        for (BlockPos offset : OreDrillBlockEntity.framePositions(BlockPos.ZERO)) {
            if (level.getBlockEntity(pos.subtract(offset)) instanceof OreDrillBlockEntity drill) {
                drill.lookOverFrameNow();
            }
        }
    }
}
