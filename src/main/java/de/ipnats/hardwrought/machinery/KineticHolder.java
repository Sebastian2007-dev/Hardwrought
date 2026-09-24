package de.ipnats.hardwrought.machinery;

/**
 * A block entity that remembers how fast its block is turning, so that the renderer can draw it and
 * the machine can work at that speed. Written by {@link Kinetics} whenever the line is worked out.
 */
public interface KineticHolder {
    /** Turns per minute, signed: the sign is the direction about the block's own axis. */
    float kineticSpeed();

    void setKineticSpeed(float speed);
}
