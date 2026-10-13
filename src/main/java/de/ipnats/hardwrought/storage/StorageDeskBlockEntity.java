package de.ipnats.hardwrought.storage;

import de.ipnats.hardwrought.electricity.ElectricBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The desk as a point of an electric network: the motors that fetch and carry run on current.
 *
 * <p>It is a resistance of {@link #OHMS} between the wire and the ground, taken the whole time it
 * stands on a line — the works have to be turning before anybody asks them for anything. Below
 * {@link #MIN_VOLTS} they do not turn, and the desk shows nothing and fetches nothing. Like a
 * machine it takes no wire itself: a copper coil set against it joins it to a line.
 */
public class StorageDeskBlockEntity extends ElectricBlockEntity {
    /** One ampere at sixty volts: twice a lamp. */
    public static final double OHMS = 60.0;
    /** What the works need to turn at all; the same as a machine's motor. */
    public static final double MIN_VOLTS = 30.0;

    public StorageDeskBlockEntity(BlockPos pos, BlockState state) {
        super(StorageBlocks.DESK_ENTITY, pos, state);
    }

    /** Whether there is current enough on it to work the store room. */
    public boolean powered() {
        return Math.abs(volts()) >= MIN_VOLTS;
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
