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
 */
public class DrillFrameBlock extends Block {
    /** Where this block sits in a finished drill, 1 to 17; 0 while it is a loose frame. */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, OreDrillBlockEntity.FRAME_BLOCKS);

    private final DrillTier tier;

    public DrillFrameBlock(DrillTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    public DrillTier tier() {
        return tier;
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
