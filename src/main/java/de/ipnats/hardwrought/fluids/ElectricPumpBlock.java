package de.ipnats.hardwrought.fluids;

import de.ipnats.hardwrought.electricity.ElectricBlockEntity;
import de.ipnats.hardwrought.electricity.ElectricNodeBlock;
import de.ipnats.hardwrought.electricity.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A centrifugal pump on a motor: four times what the shaft pump pushes, at its rated voltage. Like
 * the other machines it is joined to a line through a coil set against it, and runs slower the lower
 * the voltage reaching it (see {@link MachineBlockEntity#rate}). Fluid pipes join it on every side.
 */
public class ElectricPumpBlock extends ElectricNodeBlock implements FluidPumpBlock {
    public ElectricPumpBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Vec3 terminal(BlockState state) {
        return new Vec3(0.5, 1.0, 0.5);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    public static class Entity extends ElectricBlockEntity implements FluidPump {
        /** Millibuckets a second at the rated voltage. */
        public static final int RATED = 1000;
        public static final double OHMS = 40.0;

        public Entity(BlockPos pos, BlockState state) {
            super(FluidBlocks.ELECTRIC_PUMP_ENTITY, pos, state);
        }

        @Override
        public double pumpRate() {
            return RATED / 20.0 * MachineBlockEntity.rate(volts());
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
    }
}
