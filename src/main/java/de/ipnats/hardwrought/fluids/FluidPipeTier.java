package de.ipnats.hardwrought.fluids;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;

/**
 * The metals a fluid pipe is made in, as the item pipes are: the better the metal, the more a run of
 * them carries. A run carries no more than its narrowest pipe and no more than its pumps push.
 */
public enum FluidPipeTier {
    BRONZE("bronze", 100),
    IRON("iron", 250),
    NICKEL("nickel", 500),
    CHROMIUM("chromium", 1000),
    TITANIUM("titanium", 2000);

    private final String serializedName;
    private final int millibucketsPerSecond;

    FluidPipeTier(String serializedName, int millibucketsPerSecond) {
        this.serializedName = serializedName;
        this.millibucketsPerSecond = millibucketsPerSecond;
    }

    public String serializedName() {
        return serializedName;
    }

    public int millibucketsPerSecond() {
        return millibucketsPerSecond;
    }

    /** What one pipe lets through in a tick, in Fabric's droplets. */
    public long dropletsPerTick() {
        return millibucketsPerSecond * (FluidConstants.BUCKET / 1000) / 20;
    }
}
