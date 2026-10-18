package de.ipnats.hardwrought.electricity;

import de.ipnats.hardwrought.parts.Parts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Section 76: switchgear — a knife switch, a fuse box or a breaker, set into a line between two coils.
 * See {@link SwitchgearBlockEntity}. Its box is earthed: it is worked by hand while live.
 *
 * <ul>
 *   <li>A <b>switch</b> is opened and closed by hand, or follows a redstone signal: powered, closed.</li>
 *   <li>A <b>fuse box</b> holds a cartridge that blows above {@link Kind#FUSE}'s rating; a new cartridge
 *       is put in by hand.</li>
 *   <li>A <b>breaker</b> trips above its rating and is closed again by hand; it can be opened by hand too.</li>
 * </ul>
 */
public class SwitchgearBlock extends ElectricNodeBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty CLOSED = BooleanProperty.create("closed");
    /** Whether a redstone signal reached it when last looked at, so that a switch follows only a change. */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    /**
     * Which of the three it is.
     *
     * @param ratedAmps the current it stands for good; nothing for a switch, which never opens by itself
     * @param tripHeat  how long it stands too much: a fuse a moment, a breaker a little longer
     */
    public enum Kind {
        SWITCH(0.0, 0.0f), FUSE(4.0, 0.5f), BREAKER(16.0, 1.0f);

        private final double ratedAmps;
        private final float tripHeat;

        Kind(double ratedAmps, float tripHeat) {
            this.ratedAmps = ratedAmps;
            this.tripHeat = tripHeat;
        }

        public double ratedAmps() {
            return ratedAmps;
        }

        public float tripHeat() {
            return tripHeat;
        }
    }

    private final Kind kind;

    public SwitchgearBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CLOSED, true)
                .setValue(POWERED, false));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CLOSED, POWERED);
    }

    /** Facing whoever set it; a switch is set down open, so that nothing goes live before it is wired. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(CLOSED, kind != Kind.SWITCH);
    }

    @Override
    public Vec3 terminal(BlockState state) {
        return new Vec3(0.5, 1.0, 0.5);
    }

    /** A cartridge goes into a blown fuse box; shears, a coil and a meter go on to their own use. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (kind == Kind.FUSE && !state.getValue(CLOSED) && stack.is(Parts.FUSE_CARTRIDGE)) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!player.getAbilities().instabuild) stack.shrink(1);
            set(level, pos, state.setValue(CLOSED, true));
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6f, 1.2f);
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** A switch is thrown and a breaker closed or opened. A blown fuse says what it needs. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (kind == Kind.FUSE) {
            if (!level.isClientSide() && !state.getValue(CLOSED)) {
                player.sendOverlayMessage(Component.translatable("message.hardwrought.switchgear.needs_cartridge"));
            }
            return InteractionResult.SUCCESS;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        boolean closing = !state.getValue(CLOSED);
        set(level, pos, state.setValue(CLOSED, closing));
        level.playSound(null, pos, closing ? SoundEvents.LEVER_CLICK : SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS,
                0.6f, closing ? 0.7f : 1.2f);
        return InteractionResult.SUCCESS;
    }

    /** A switch follows a redstone signal when it comes or goes. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation,
                                   boolean moved) {
        if (level.isClientSide() || kind != Kind.SWITCH) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) return;
        set(level, pos, state.setValue(POWERED, powered).setValue(CLOSED, powered));
    }

    private static void set(Level level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (level instanceof ServerLevel server) Electricity.updateAround(server, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SwitchgearBlockEntity(pos, state);
    }
}
