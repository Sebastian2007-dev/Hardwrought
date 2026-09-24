package de.ipnats.hardwrought.machinery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block that takes part in a line of turning parts: shafts, gears, a gearbox, a crank, a wheel, a
 * machine. See {@link Kinetics} for how a whole line is worked out.
 *
 * <p>Every part has a speed of its own, in turns per minute, and everything it connects to turns at a
 * fixed ratio of it. Two kinds of connection exist:
 * <ul>
 *   <li>an <b>axle through a face</b>, which two touching blocks share — a shaft end against a gear,
 *       say. {@link #port} says how fast the axle through that face turns for each turn of this
 *       block, measured about the positive direction of the face's axis;</li>
 *   <li>everything else — gear teeth meshing, a belt — through {@link #links}.</li>
 * </ul>
 *
 * <p>Sources ({@link #drive}, {@link #capacity}) put motion and strength into the line; machines
 * ({@link #impact}) take strength out of it, more the faster they are driven.
 */
public interface KineticBlock {
    /** How fast the axle through this face turns per turn of this block; 0 where no axle passes. */
    float port(BlockState state, Direction face);

    /**
     * Connections that are not an axle through a face. {@code ratio} is how fast the other block turns
     * for each turn of this one. Must be answered the same way from both ends.
     */
    default void links(Level level, BlockPos pos, BlockState state, LinkSink sink) { }

    /** The speed this block turns at by itself, in its own frame; 0 where it is no source. */
    default float drive(Level level, BlockPos pos, BlockState state) {
        return 0.0f;
    }

    /** How much strength this source gives the line, in stress units. */
    default float capacity(Level level, BlockPos pos, BlockState state) {
        return 0.0f;
    }

    /** How much strength this machine takes for each turn per minute it is driven at. */
    default float impact(Level level, BlockPos pos, BlockState state) {
        return 0.0f;
    }

    @FunctionalInterface
    interface LinkSink {
        void link(BlockPos other, float ratio);
    }
}
