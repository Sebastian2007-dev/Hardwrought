package de.ipnats.hardwrought.fluids;

/**
 * A pump in a run of fluid pipes. Fluid pipes move nothing by themselves: what a run draws out of
 * tanks, wells and lakes in a tick is at most what its pumps push. Implemented by the block entity;
 * the block itself is a {@link FluidPumpBlock}, so pipes join it.
 */
public interface FluidPump {
    /** How much it pushes this tick, in millibuckets; 0 while it stands. */
    double pumpRate();
}
