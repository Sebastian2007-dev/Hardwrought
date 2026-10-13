package de.ipnats.hardwrought.machinery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What a pipe wrench has set on a pipe: which of its sides draw from the block there, and which are
 * cut off. Everything else about the sides follows from what is around the pipe (see
 * {@link ConnectedPipeBlock#linkFor}).
 */
public abstract class ConnectedPipeBlockEntity extends BlockEntity {
    private int disabled;
    private int extracting;

    protected ConnectedPipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean disabled(Direction side) {
        return (disabled & bit(side)) != 0;
    }

    public boolean extracting(Direction side) {
        return (extracting & bit(side)) != 0;
    }

    public void setDisabled(Direction side, boolean value) {
        disabled = value ? disabled | bit(side) : disabled & ~bit(side);
        setChanged();
    }

    public void setExtracting(Direction side, boolean value) {
        extracting = value ? extracting | bit(side) : extracting & ~bit(side);
        setChanged();
    }

    private static int bit(Direction side) {
        return 1 << side.get3DDataValue();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (disabled != 0) output.putInt("pipe_disabled", disabled);
        if (extracting != 0) output.putInt("pipe_extract", extracting);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        disabled = input.getIntOr("pipe_disabled", 0);
        extracting = input.getIntOr("pipe_extract", 0);
    }
}
