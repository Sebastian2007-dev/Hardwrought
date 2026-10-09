package de.ipnats.hardwrought.electricity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;

/**
 * Section 76: a lead battery — a box of plates in acid that takes current while the line gives more
 * than it holds, and gives it back when the line gives less. See {@link BatteryBlockEntity}.
 */
public class BatteryBlock extends ElectricNodeBlock {
    /** How full it is, in quarters, for the gauge on its side. */
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);

    public BatteryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE);
    }

    @Override
    public Vec3 terminal(BlockState state) {
        return new Vec3(0.5, 15.5 / 16.0, 0.5);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BatteryBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ElectricBlocks.BATTERY_ENTITY) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>)
                (BlockEntityTicker<BatteryBlockEntity>) BatteryBlockEntity::serverTick;
        return ticker;
    }
}
