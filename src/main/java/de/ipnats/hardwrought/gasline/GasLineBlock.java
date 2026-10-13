package de.ipnats.hardwrought.gasline;

import de.ipnats.hardwrought.machinery.ConnectedPipeBlock;
import de.ipnats.hardwrought.machinery.ConnectedPipeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A closed gas line, in one of the metals of {@link GasLineTier}: no pump, the pressure of the stores on
 * it drives the gas from where it stands high to where it stands low (see {@link GasNetwork}). A wrench
 * joins or cuts a side, never sets it to draw. Not to be confused with the open copper flue that carries
 * fumes off a hood, which lets gas go rather than holding it.
 */
public class GasLineBlock extends ConnectedPipeBlock {
    private final GasLineTier tier;

    public GasLineBlock(GasLineTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public GasLineTier tier() {
        return tier;
    }

    @Override
    protected boolean draws() {
        return false;
    }

    @Override
    protected boolean sameRun(BlockState neighbour) {
        return neighbour.getBlock() instanceof GasLineBlock;
    }

    @Override
    protected boolean offers(Level level, BlockPos neighbour, Direction face) {
        return GasStorage.SIDED.find(level, neighbour, face) != null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != GasLines.LINE_ENTITY) return null;
        return (lvl, pos, st, entity) -> GasNetwork.tick((ServerLevel) lvl, pos);
    }

    /** What a wrench has set on the line's sides; the work is the network's. */
    public static class Entity extends ConnectedPipeBlockEntity {
        public Entity(BlockPos pos, BlockState state) {
            super(GasLines.LINE_ENTITY, pos, state);
        }
    }
}
