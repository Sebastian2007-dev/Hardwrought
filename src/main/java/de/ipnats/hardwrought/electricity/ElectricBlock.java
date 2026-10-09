package de.ipnats.hardwrought.electricity;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A block wires can be strung to. See {@link Electricity} for how a whole network is worked out. */
public interface ElectricBlock {
    /** Where on the block its wires are made fast, measured from the block's lower corner. */
    Vec3 terminal(BlockState state);
}
