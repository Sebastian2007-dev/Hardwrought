package de.ipnats.hardwrought.gasline;

/**
 * The metals a gas line is made in. A line bursts at the weakest pipe in it when a store on it stands
 * above that pipe's pressure, and it carries the more the better its metal: per tick, so many units
 * for every bar between the store that gives and the one that takes.
 */
public enum GasLineTier {
    BRONZE("bronze", 6, 0.5),
    IRON("iron", 12, 1),
    NICKEL("nickel", 20, 2),
    CHROMIUM("chromium", 32, 4),
    TITANIUM("titanium", 50, 8);

    private final String serializedName;
    private final double burstBar;
    private final double flow;

    GasLineTier(String serializedName, double burstBar, double flow) {
        this.serializedName = serializedName;
        this.burstBar = burstBar;
        this.flow = flow;
    }

    public String serializedName() {
        return serializedName;
    }

    /** The pressure past which a pipe of this metal bursts, in bar. */
    public double burstBar() {
        return burstBar;
    }

    /** Units a tick it carries for each bar of difference across it. */
    public double flow() {
        return flow;
    }
}
