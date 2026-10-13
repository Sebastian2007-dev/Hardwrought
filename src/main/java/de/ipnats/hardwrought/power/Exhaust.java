package de.ipnats.hardwrought.power;

import de.ipnats.hardwrought.environment.Flues;
import de.ipnats.hardwrought.environment.Gas;
import de.ipnats.hardwrought.environment.Gases;
import de.ipnats.hardwrought.smithing.GasPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * What an engine or a burner breathes out: carbon dioxide, and from an engine carbon monoxide too.
 * A flue pipe on top of it takes the gas to its open end; without one it comes out over the block, and
 * indoors that is a room filling with gas nobody can smell.
 */
public final class Exhaust {
    private Exhaust() { }

    /** Lets so many units of each gas out of the block at pos. */
    public static void emit(ServerLevel level, BlockPos pos, int carbonDioxide, int carbonMonoxide) {
        BlockPos above = pos.above();
        BlockPos outlet = level.getBlockState(above).getBlock() instanceof GasPipeBlock ? Flues.outletFrom(level, above) : null;
        BlockPos out = outlet != null ? outlet : above;
        if (carbonDioxide > 0) Gases.emit(level, out, Gas.CARBON_DIOXIDE, carbonDioxide);
        if (carbonMonoxide > 0) Gases.emit(level, out, Gas.CARBON_MONOXIDE, carbonMonoxide);
    }
}
