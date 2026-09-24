package de.ipnats.hardwrought.machinery;

import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a hand crank remembers: when it was last pushed round, and how fast its line lets it turn.
 *
 * <p>The last stroke is not saved. A crank that was turning when its chunk unloaded wakes up with a
 * last stroke long ago, and its pending tick stops it — nobody is holding the handle any more. The
 * handle is drawn at the line's speed, so a crank on a line too heavy for it does not go round.
 */
public class HandCrankBlockEntity extends KineticBlockEntity {
    /** Server side: the game time of the last stroke. */
    private long lastStroke = Long.MIN_VALUE / 2;

    public HandCrankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HAND_CRANK, pos, state);
    }

    void stroke(long gameTime) {
        lastStroke = gameTime;
    }

    long lastStroke() {
        return lastStroke;
    }
}
