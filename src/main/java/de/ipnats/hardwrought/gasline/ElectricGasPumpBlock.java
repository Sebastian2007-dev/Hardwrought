package de.ipnats.hardwrought.gasline;

import de.ipnats.hardwrought.electricity.ElectricBlockEntity;
import de.ipnats.hardwrought.electricity.ElectricNodeBlock;
import de.ipnats.hardwrought.electricity.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A gas pump on a motor: gas in at the back, out at the front, never the other way (see
 * {@link GasCompressor}). Joined to a line through a coil set against it, like the other machines, it
 * moves {@value Entity#RATED} units a second at its rated voltage, less as the voltage drops
 * ({@link MachineBlockEntity#rate}). Without current it lets nothing through either way.
 */
public class ElectricGasPumpBlock extends ElectricNodeBlock {
    public ElectricGasPumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(GasPumpBlock.FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GasPumpBlock.FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(GasPumpBlock.FACING, context.getNearestLookingDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(GasPumpBlock.FACING, rotation.rotate(state.getValue(GasPumpBlock.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(GasPumpBlock.FACING)));
    }

    @Override
    public Vec3 terminal(BlockState state) {
        return new Vec3(0.5, 1.0, 0.5);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != GasLines.ELECTRIC_PUMP_ENTITY) return null;
        return (tickLevel, pos, tickState, entity) ->
                ((Entity) entity).compressor.tick((ServerLevel) tickLevel, pos, tickState.getValue(GasPumpBlock.FACING));
    }

    public static class Entity extends ElectricBlockEntity {
        /** Units a second at the rated voltage. */
        public static final double RATED = 16.0;
        public static final double OHMS = 40.0;

        public final GasCompressor compressor = new GasCompressor(this::rate,
                () -> level == null ? 0 : level.getGameTime(), this::setChanged);

        public Entity(BlockPos pos, BlockState state) {
            super(GasLines.ELECTRIC_PUMP_ENTITY, pos, state);
        }

        public GasStorage storage(Direction side) {
            return compressor.storage(getBlockState().getValue(GasPumpBlock.FACING), side);
        }

        /** How much it draws a tick, in units, at the voltage reaching it. */
        public double rate() {
            return RATED / 20.0 * MachineBlockEntity.rate(volts());
        }

        @Override
        public Component note() {
            return compressor.describe();
        }

        @Override
        public double conductance() {
            return 1.0 / OHMS;
        }

        @Override
        public boolean takesWires() {
            return false;
        }

        @Override
        public boolean mindsRain() {
            return true;
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            compressor.save(output);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            compressor.load(input);
        }
    }
}
