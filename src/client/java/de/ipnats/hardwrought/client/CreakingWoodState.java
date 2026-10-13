package de.ipnats.hardwrought.client;

import de.ipnats.hardwrought.mobs.HeartWood;
import org.jspecify.annotations.Nullable;

/** Added to the creaking's render state: the wood it was called out of, if not pale oak. */
public interface CreakingWoodState {
    @Nullable HeartWood hardwrought$wood();

    void hardwrought$setWood(@Nullable HeartWood wood);
}
