package de.ipnats.hardwrought.geology;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the oil and gas are (sections 51, 60 and 61). Only sedimentary rock carries them — oil and
 * gas form from what was laid down in old seas and stay where a layer above holds them in — and not
 * every sedimentary region has any.
 *
 * <p>A pure function of the seed and a region, like everything else in {@link Geology}: the well, the
 * prospector and the diagnostics all derive the same reservoir from the same numbers, and nothing is
 * stored but how much has been taken out.
 */
public final class Reservoirs {
    /** How many sedimentary regions in a hundred hold a reservoir. */
    public static final int CHANCE_PERCENT = 65;
    /** How many reservoirs in a hundred are oil with a gas cap; the rest are gas fields. */
    public static final int OIL_PERCENT = 60;
    public static final int MIN_Y = -48;
    public static final int MAX_Y = 16;
    public static final int MIN_RADIUS = 20;
    public static final int MAX_RADIUS = 48;
    public static final int MIN_HEIGHT = 5;
    public static final int MAX_HEIGHT = 10;
    public static final double MIN_PRESSURE = 0.4;

    private Reservoirs() { }

    /** The reservoir of one region, or null where it has none. */
    public static Reservoir inRegion(long seed, int regionX, int regionZ) {
        RockType rock = RockType.byOrdinal((int) Math.floorMod(Geology.hash(seed, regionX, regionZ, 0),
                RockType.values().length));
        if (rock != RockType.SEDIMENTARY) return null;
        if (Math.floorMod(Geology.hash(seed, regionX, regionZ, 900), 100) >= CHANCE_PERCENT) return null;

        Reservoir.Kind kind = Math.floorMod(Geology.hash(seed, regionX, regionZ, 901), 100) < OIL_PERCENT
                ? Reservoir.Kind.OIL : Reservoir.Kind.GAS;
        long place = Geology.hash(seed, regionX, regionZ, 902);
        int radius = MIN_RADIUS + (int) Math.floorMod(Geology.hash(seed, regionX, regionZ, 903),
                MAX_RADIUS - MIN_RADIUS + 1);
        int height = MIN_HEIGHT + (int) Math.floorMod(Geology.hash(seed, regionX, regionZ, 904),
                MAX_HEIGHT - MIN_HEIGHT + 1);
        int y = MIN_Y + (int) Math.floorMod(Geology.hash(seed, regionX, regionZ, 905), MAX_Y - MIN_Y + 1);
        double pressure = MIN_PRESSURE + (1.0 - MIN_PRESSURE)
                * (Math.floorMod(Geology.hash(seed, regionX, regionZ, 906), 1_000) / 999.0);

        // Kept inside its own region, so a well never has to ask two regions which one it is in.
        int margin = radius + 4;
        int span = Math.max(1, Geology.REGION_SIZE_BLOCKS - 2 * margin);
        int x = regionX * Geology.REGION_SIZE_BLOCKS + margin + (int) Math.floorMod(place, span);
        int z = regionZ * Geology.REGION_SIZE_BLOCKS + margin + (int) Math.floorMod(place >>> 32, span);
        return new Reservoir(kind, regionX, regionZ, new BlockPos(x, y, z), radius, height, pressure);
    }

    /** The reservoir this column stands over, or null. */
    public static Reservoir under(long seed, int x, int z) {
        Reservoir reservoir = inRegion(seed, Geology.regionX(x), Geology.regionZ(z));
        return reservoir != null && reservoir.under(x, z) ? reservoir : null;
    }

    /** The reservoirs whose middle lies within {@code radius} of a position on the surface, nearest first. */
    public static List<Reservoir> nearby(long seed, BlockPos pos, int radius) {
        List<Reservoir> found = new ArrayList<>();
        int regionX = Geology.regionX(pos.getX());
        int regionZ = Geology.regionZ(pos.getZ());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Reservoir reservoir = inRegion(seed, regionX + dx, regionZ + dz);
                if (reservoir != null && reservoir.horizontalDistance(pos) <= radius) found.add(reservoir);
            }
        }
        found.sort((first, second) -> Double.compare(first.horizontalDistance(pos), second.horizontalDistance(pos)));
        return List.copyOf(found);
    }
}
