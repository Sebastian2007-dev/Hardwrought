package de.ipnats.hardwrought.fluids;

import de.ipnats.hardwrought.machinery.ConnectedPipeBlock;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A pipe for liquids, in one of the metals of {@link FluidPipeTier}. It joins other fluid pipes of any
 * metal, pumps, and anything that holds a liquid: a tank, a well, a still. A side set to draw with a
 * pipe wrench takes from what is there — a tank, a well, or a lake of oil or lava standing beside it —
 * but only while a pump in the run is working; what is drawn goes to the nearest block along the run
 * that will take it. See {@link FluidPipeBlockEntity}.
 */
public class FluidPipeBlock extends ConnectedPipeBlock {
    private final FluidPipeTier tier;

    public FluidPipeBlock(FluidPipeTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public FluidPipeTier tier() {
        return tier;
    }

    @Override
    protected boolean sameRun(BlockState neighbour) {
        return neighbour.getBlock() instanceof FluidPipeBlock || neighbour.getBlock() instanceof FluidPumpBlock;
    }

    @Override
    protected boolean offers(Level level, BlockPos neighbour, Direction face) {
        return FluidStorage.SIDED.find(level, neighbour, face) != null || WorldFluid.source(level, neighbour) != null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidPipeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != FluidBlocks.PIPE_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>)
                (BlockEntityTicker<FluidPipeBlockEntity>) FluidPipeBlockEntity::serverTick;
        return ticker;
    }
}
