package de.ipnats.hardwrought.gasline;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.core.Direction;

/**
 * Something that holds gas under pressure: a gas tank, a well, the top of a refinery column. Its
 * pressure is how full it is times the pressure it is built for, so a full tank stands at its rating
 * and a half-full one at half of it. Gas lines move gas from the stores that stand higher to those that
 * stand lower (see {@link GasNetwork}); nothing needs to pump it.
 */
public interface GasStorage {
    /** Found on blocks the way Fabric's fluid and item stores are, per side. */
    BlockApiLookup<GasStorage, Direction> SIDED = BlockApiLookup.get(Hardwrought.id("gas_storage"), GasStorage.class, Direction.class);

    /** The gas in it, or null while it is empty. */
    GasKind kind();

    /** How much it holds, in units. */
    double amount();

    /** How much it holds at most. */
    double capacity();

    /** The pressure it stands at when full, in bar. */
    double rating();

    default double pressure() {
        return capacity() <= 0 ? 0 : amount() / capacity() * rating();
    }

    /** Whether it takes this gas in at all: one of its own kind, into a store that is not only a source. */
    default boolean accepts(GasKind gas) {
        return kind() == null || kind() == gas;
    }

    /** Puts up to so much in; how much went. */
    double insert(GasKind gas, double amount);

    /** Takes up to so much out; how much came. */
    double extract(double amount);
}
