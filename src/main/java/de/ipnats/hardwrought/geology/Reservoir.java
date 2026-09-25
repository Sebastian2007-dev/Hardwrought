package de.ipnats.hardwrought.geology;

import net.minecraft.core.BlockPos;

/**
 * Sections 60 and 61: a body of oil or gas deep in the rock. Not a block to be dug but a trap in
 * the strata that a well is sunk into.
 *
 * <p>Like an ore body it is derived from the world seed and its region and never stored; only how
 * much has been taken out of it is (see {@link de.ipnats.hardwrought.core.save.CoreSaveData}). It is
 * an ellipsoid far wider than it is high, the way a reservoir lies under its cap rock. An oil
 * reservoir carries a cap of gas on top of it, which is what comes up first and what makes drilling
 * into one dangerous; a gas field is gas all the way down.
 *
 * @param kind             oil with a gas cap, or gas alone
 * @param regionX          the region it belongs to, which is also its name
 * @param regionZ          the region it belongs to, which is also its name
 * @param centre           the middle of it
 * @param horizontalRadius how far it reaches sideways
 * @param verticalRadius   how far it reaches up and down
 * @param pressure         how hard it pushes when first opened, 0 to 1; a well produces in proportion
 */
public record Reservoir(Kind kind, int regionX, int regionZ, BlockPos centre, int horizontalRadius,
                        int verticalRadius, double pressure) {
    /**
     * What a block of reservoir rock holds, in millibuckets of oil or units of gas. A reservoir
     * forty blocks across is some thousands of buckets — a well runs for a long time, not for ever.
     */
    public static final double CONTENT_PER_BLOCK = 4.0;

    public enum Kind {
        OIL, GAS;

        public String serializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public Reservoir {
        if (kind == null || centre == null) throw new IllegalArgumentException("A reservoir needs a kind and a place");
        if (horizontalRadius <= 0 || verticalRadius <= 0) throw new IllegalArgumentException("A reservoir needs a size");
        if (!Double.isFinite(pressure) || pressure <= 0 || pressure > 1) {
            throw new IllegalArgumentException("Pressure is a fraction above 0");
        }
    }

    /** The name its depletion is saved under. One reservoir per region, so the region is enough. */
    public String key() {
        return regionX + "," + regionZ;
    }

    /** Whether this column of the world stands over it. */
    public boolean under(int x, int z) {
        return horizontalReach(x, z) <= 1.0;
    }

    private double horizontalReach(int x, int z) {
        double dx = (x - centre.getX()) / (double) horizontalRadius;
        double dz = (z - centre.getZ()) / (double) horizontalRadius;
        return dx * dx + dz * dz;
    }

    /**
     * The height of its top under this column: where a well sunk here breaks into it. The top is
     * domed, highest over the middle, which is why a well in the middle reaches it soonest.
     * Integer.MIN_VALUE where the column does not stand over it.
     */
    public int topAt(int x, int z) {
        double reach = horizontalReach(x, z);
        if (reach > 1.0) return Integer.MIN_VALUE;
        return centre.getY() + (int) Math.floor(verticalRadius * Math.sqrt(1.0 - reach));
    }

    /**
     * What it held before anyone drilled into it: its volume in blocks times what a block holds.
     * An ellipsoid's volume is four thirds of pi times its three radii.
     */
    public long capacity() {
        double volume = 4.0 / 3.0 * Math.PI * horizontalRadius * horizontalRadius * verticalRadius;
        return Math.round(volume * CONTENT_PER_BLOCK);
    }

    /** Blocks from a position to its middle on the surface, for prospecting. */
    public double horizontalDistance(BlockPos pos) {
        double dx = centre.getX() - pos.getX();
        double dz = centre.getZ() - pos.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
