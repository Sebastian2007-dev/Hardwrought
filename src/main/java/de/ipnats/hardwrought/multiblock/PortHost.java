package de.ipnats.hardwrought.multiblock;

import de.ipnats.hardwrought.gasline.GasStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * What a big machine offers through the ports in its hull. A port asks the controller it belongs to,
 * and passes on what it is told: the machine's tank to a fluid pipe, its slots to an item pipe, its gas
 * store to a gas line, and its voltage or its load to the wires on a power port.
 *
 * <p>Everything defaults to nothing, so a machine says only what it has.
 */
public interface PortHost {
    /** The tank a fluid port at this place leads to, or null. */
    default Storage<FluidVariant> fluid(BlockPos port) {
        return null;
    }

    /** The slots an item port at this place leads to, from this side, or null. */
    default Storage<ItemVariant> items(BlockPos port, Direction side) {
        return null;
    }

    /** The gas store a gas port at this place leads to, or null. */
    default GasStorage gas(BlockPos port) {
        return null;
    }

    /** The voltage the machine drives its power port with; zero where it is no source. */
    default double emf() {
        return 0.0;
    }

    /** Whether it gives current now: a source with something to give. */
    default boolean canGive() {
        return emf() > 0.0;
    }

    /** Whether current may run into it against its voltage: a battery that is not full. */
    default boolean canTake() {
        return false;
    }

    /** Its resistance as a source. */
    default double sourceOhms() {
        return 1.0;
    }

    /** What it draws as a load, in siemens; zero while it draws nothing. */
    default double conductance() {
        return 0.0;
    }

    /** Told by its power port what the line took of it, or gave it, over so many seconds. */
    default void solved(double volts, double amps, double seconds) { }

    /** Whether its power port pulses the line, as every source must. */
    default boolean pulses() {
        return false;
    }
}
