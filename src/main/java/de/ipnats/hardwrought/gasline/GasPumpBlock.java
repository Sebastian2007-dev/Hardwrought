package de.ipnats.hardwrought.gasline;

import de.ipnats.hardwrought.machinery.KineticBlock;
import de.ipnats.hardwrought.machinery.KineticHolder;
import de.ipnats.hardwrought.machinery.Kinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;


/**
 * A gas pump on a shaft: gas in at the back, out at the front, never the other way (see
 * {@link GasCompressor}). Turned by a shaft, gear or crank on any of its other four sides, it moves
 * {@value Entity#RATED} units a second at sixteen turns a minute, in proportion to its speed; a hand crank
 * is enough. Standing still it lets nothing through either way.
 */
public class GasPumpBlock extends Block implements EntityBlock, KineticBlock {
    /** Where the gas goes out. It comes in on the opposite face. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    /** Strength taken per turn per minute: a hand crank turns it. */
    public static final float IMPACT = 6.0f;

    public GasPumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Set down, it pushes the way the player is looking: in behind, out in front. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Gas lines join it at the back and the front; shafts at the other four sides. */
    @Override
    public float port(BlockState state, Direction face) {
        return face.getAxis() == state.getValue(FACING).getAxis() ? 0.0f : 1.0f;
    }

    @Override
    public float impact(Level level, BlockPos pos, BlockState state) {
        return IMPACT;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (!level.isClientSide() && !previous.is(this)) Kinetics.update(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moving) {
        Kinetics.update(level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Entity pump)) return InteractionResult.PASS;
        if (!level.isClientSide()) player.sendOverlayMessage(pump.compressor.describe());
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != GasLines.PUMP_ENTITY) return null;
        return (tickLevel, pos, tickState, entity) -> ((Entity) entity).tick((ServerLevel) tickLevel, pos, tickState);
    }

    public static class Entity extends BlockEntity implements KineticHolder {
        /** Units a second at the rated speed. */
        public static final double RATED = 4.0;
        private static final float RATED_SPEED = 16.0f;

        private float speed;
        public final GasCompressor compressor = new GasCompressor(this::rate,
                () -> level == null ? 0 : level.getGameTime(), this::setChanged);

        public Entity(BlockPos pos, BlockState state) {
            super(GasLines.PUMP_ENTITY, pos, state);
        }

        public GasStorage storage(Direction side) {
            return compressor.storage(getBlockState().getValue(FACING), side);
        }

        /** How much it draws a tick, in units, at the speed it turns. */
        public double rate() {
            return RATED / 20.0 * Math.abs(speed) / RATED_SPEED;
        }

        void tick(ServerLevel level, BlockPos pos, BlockState state) {
            compressor.tick(level, pos, state.getValue(FACING));
        }

        @Override
        public float kineticSpeed() {
            return speed;
        }

        @Override
        public void setKineticSpeed(float speed) {
            this.speed = speed;
            setChanged();
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.putFloat("speed", speed);
            compressor.save(output);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            speed = input.getFloatOr("speed", 0.0f);
            compressor.load(input);
        }
    }
}
