package de.ipnats.hardwrought.geology;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Milestone-6 geology: specification sections 51 to 54. What the ground is made of, where the rock
 * is worth working, and what a prospector can read off it.
 *
 * <p>Ore <em>blocks</em> are placed by ordinary world generation and this model does not touch them.
 * What it adds is the layer underneath the blocks: a region is made of one rock, some patches of
 * that rock are far richer than the rest, and both of those are things a player can find out and
 * act on. A rich patch pays more per block broken in it and is what a drill wants to stand on.
 *
 * <p>Everything here is a <em>pure function of the world seed and a coordinate</em>. Nothing is
 * stored and nothing is cached, for three reasons that all matter:
 *
 * <ul>
 *   <li>the same body has to come out identical for the worldgen that places the blocks, for the
 *       prospector reading the rock, and for the diagnostics — computing it twice must agree;
 *   <li>chunk generation runs on many threads at once, and a shared cache there is a data race;
 *   <li>a world-sized table of ore bodies is exactly the unbounded state section 105 rules out.
 * </ul>
 *
 * <p>A region is 192 blocks square and has one rock type. Geology is deliberately independent of
 * biomes: what grows on the ground and what the ground is made of are two different questions, and a
 * granite massif does not stop being granite because a forest grows on it.
 */
public final class Geology {
    /** Section 51: one rock type per region, big enough that crossing one is a journey. */
    public static final int REGION_SIZE_BLOCKS = 192;
    /**
     * Section 52: at two to five large patches per region, roughly a third to a half of the ground
     * has rich rock under it somewhere. Enough that prospecting pays off regularly, little enough
     * that finding a good patch still means something.
     */
    public static final int MIN_DEPOSITS_PER_REGION = 2;
    public static final int MAX_DEPOSITS_PER_REGION = 5;
    /** Kept clear of the region border so a body never has to be assembled from two regions. */
    public static final int BORDER_MARGIN = 24;

    private Geology() { }

    // ---------------------------------------------------------------- regions

    public static int regionX(int blockX) {
        return Math.floorDiv(blockX, REGION_SIZE_BLOCKS);
    }

    public static int regionZ(int blockZ) {
        return Math.floorDiv(blockZ, REGION_SIZE_BLOCKS);
    }

    /** Section 51: the rock of the region this position belongs to. */
    public static RockType rockAt(long seed, int x, int z) {
        long hash = hash(seed, regionX(x), regionZ(z), 0);
        return RockType.byOrdinal((int) Math.floorMod(hash, RockType.values().length));
    }

    // ---------------------------------------------------------------- deposits

    /**
     * Every rich patch of one region. Two to five, each of them an ore the region's rock actually
     * carries, clear of the region border and inside the depth band its profile states. They are not
     * where the ore is — ore is everywhere the world generator put it — they are where it is worth
     * the effort.
     */
    public static List<OreDeposit> deposits(long seed, Map<Identifier, RockProfile> profiles,
                                            int regionX, int regionZ) {
        RockType rock = RockType.byOrdinal((int) Math.floorMod(hash(seed, regionX, regionZ, 0),
                RockType.values().length));
        RockProfile profile = profiles == null ? null : profiles.get(rock.id());
        if (profile == null || profile.deposits().isEmpty() || profile.totalWeight() <= 0) return List.of();

        int count = MIN_DEPOSITS_PER_REGION + (int) Math.floorMod(hash(seed, regionX, regionZ, 1),
                MAX_DEPOSITS_PER_REGION - MIN_DEPOSITS_PER_REGION + 1);
        List<OreDeposit> found = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long which = hash(seed, regionX, regionZ, 100 + index * 8);
            DepositProfile chosen = profile.pick((int) Math.floorMod(which, profile.totalWeight()));
            if (chosen == null) continue;

            long place = hash(seed, regionX, regionZ, 101 + index * 8);
            long depth = hash(seed, regionX, regionZ, 102 + index * 8);
            long size = hash(seed, regionX, regionZ, 103 + index * 8);
            long assay = hash(seed, regionX, regionZ, 104 + index * 8);

            int span = REGION_SIZE_BLOCKS - 2 * BORDER_MARGIN;
            int centreX = regionX * REGION_SIZE_BLOCKS + BORDER_MARGIN + (int) Math.floorMod(place, span);
            int centreZ = regionZ * REGION_SIZE_BLOCKS + BORDER_MARGIN
                    + (int) Math.floorMod(place >>> 32, span);
            int centreY = chosen.minY() + (int) Math.floorMod(depth, chosen.bandHeight() + 1);
            int radius = chosen.minRadius()
                    + (int) Math.floorMod(size, chosen.maxRadius() - chosen.minRadius() + 1);
            double grade = chosen.minGrade() + (chosen.maxGrade() - chosen.minGrade())
                    * (Math.floorMod(assay, 1_000) / 999.0);
            found.add(new OreDeposit(chosen.ore(), new BlockPos(centreX, centreY, centreZ),
                    radius, Math.max(4, radius / 3), grade));
        }
        return List.copyOf(found);
    }

    /**
     * The ore body a position sits in, or null. Neighbouring regions are checked as well: a body is
     * kept clear of its own region border, but a large one still reaches into the region next door.
     */
    public static OreDeposit depositAt(long seed, Map<Identifier, RockProfile> profiles,
                                       int x, int y, int z) {
        int regionX = regionX(x);
        int regionZ = regionZ(z);
        OreDeposit best = null;
        double bestReach = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (OreDeposit deposit : deposits(seed, profiles, regionX + dx, regionZ + dz)) {
                    double reach = deposit.reach(x, y, z);
                    // Where two bodies overlap, the one whose middle is closer is the one being dug.
                    if (reach <= 1.0 && reach < bestReach) {
                        best = deposit;
                        bestReach = reach;
                    }
                }
            }
        }
        return best;
    }

    /** The rich patch of one particular ore under this column, or null. */
    public static OreDeposit depositOver(long seed, Map<Identifier, RockProfile> profiles,
                                         int x, int z, Identifier ore) {
        return bodyOver(seed, profiles, x, z, ore);
    }

    /** Any body under this column, whatever ore it is. This is what a drill stands on. */
    public static OreDeposit bodyOver(long seed, Map<Identifier, RockProfile> profiles, int x, int z) {
        return bodyOver(seed, profiles, x, z, null);
    }

    private static OreDeposit bodyOver(long seed, Map<Identifier, RockProfile> profiles,
                                       int x, int z, Identifier ore) {
        int regionX = regionX(x);
        int regionZ = regionZ(z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (OreDeposit deposit : deposits(seed, profiles, regionX + dx, regionZ + dz)) {
                    if (ore != null && !deposit.ore().equals(ore)) continue;
                    double rx = (x - deposit.centre().getX()) / (double) deposit.horizontalRadius();
                    double rz = (z - deposit.centre().getZ()) / (double) deposit.horizontalRadius();
                    if (rx * rx + rz * rz <= 1.0) return deposit;
                }
            }
        }
        return null;
    }

    /** Section 53: what the rock assays at this position, 0 where there is no ore body at all. */
    public static double gradeAt(long seed, Map<Identifier, RockProfile> profiles, int x, int y, int z) {
        OreDeposit deposit = depositAt(seed, profiles, x, y, z);
        return deposit == null ? 0 : deposit.gradeAt(x, y, z);
    }

    /**
     * The bodies whose middle lies within {@code radius} of a position, nearest first. This is what
     * prospecting reads; it never looks further than the regions around the one it stands in.
     */
    public static List<OreDeposit> nearby(long seed, Map<Identifier, RockProfile> profiles,
                                          BlockPos pos, int radius) {
        List<OreDeposit> found = new ArrayList<>();
        int regionX = regionX(pos.getX());
        int regionZ = regionZ(pos.getZ());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (OreDeposit deposit : deposits(seed, profiles, regionX + dx, regionZ + dz)) {
                    if (horizontalDistance(deposit, pos) <= radius) found.add(deposit);
                }
            }
        }
        found.sort((first, second) -> Double.compare(horizontalDistance(first, pos),
                horizontalDistance(second, pos)));
        return List.copyOf(found);
    }

    /**
     * How far away a body is on the surface. Prospecting from above is a question of where to dig,
     * not of how deep — the depth is what the profile already says.
     */
    public static double horizontalDistance(OreDeposit deposit, BlockPos pos) {
        double dx = deposit.centre().getX() - pos.getX();
        double dz = deposit.centre().getZ() - pos.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---------------------------------------------------------------- level access

    /** The datapack rock table of a running server, or an empty table before one exists. */
    public static Map<Identifier, RockProfile> profiles(MinecraftServer server) {
        return server == null ? Map.of() : server.getOrThrow(RockProfiles.KEY);
    }

    public static RockType rockAt(WorldGenLevel level, BlockPos pos) {
        return rockAt(level.getSeed(), pos.getX(), pos.getZ());
    }

    /**
     * Worldgen access: a placement filter asks this for every position it is offered, so it must not
     * read a block, load a chunk or look at a biome — the seed and the coordinate are enough.
     */
    public static OreDeposit depositAt(WorldGenLevel level, BlockPos pos) {
        return depositAt(level.getSeed(), profiles(level.getLevel().getServer()),
                pos.getX(), pos.getY(), pos.getZ());
    }

    public static long seedOf(LevelReader level) {
        return level instanceof WorldGenLevel worldGen ? worldGen.getSeed()
                : level instanceof net.minecraft.server.level.ServerLevel server ? server.getSeed() : 0L;
    }

    // ---------------------------------------------------------------- diagnostics

    /**
     * The ore channel reported "not implemented" from Phase 1 on. It now reports the rock of the
     * region, the body being stood in and what it assays there.
     */
    public static void registerDiagnostics(de.ipnats.hardwrought.core.debug.DiagnosticRegistry registry) {
        registry.register("hardwrought:geology",
                de.ipnats.hardwrought.core.debug.DiagnosticRegistry.Channel.ORE, (level, pos) -> {
            long seed = level.getSeed();
            Map<Identifier, RockProfile> profiles = profiles(level.getServer());
            RockType rock = rockAt(seed, pos.getX(), pos.getZ());
            OreDeposit here = depositAt(seed, profiles, pos.getX(), pos.getY(), pos.getZ());
            if (here != null) {
                return String.format(java.util.Locale.ROOT,
                        "%s region | rich in %s, grade %.1f%% here (core %.1f%%, %d blocks across)",
                        rock.serializedName(), here.ore(), here.gradeAt(pos) * 100,
                        here.coreGrade() * 100, here.horizontalRadius() * 2);
            }
            List<OreDeposit> near = nearby(seed, profiles, pos, REGION_SIZE_BLOCKS);
            if (near.isEmpty()) {
                return String.format(java.util.Locale.ROOT, "%s region | no rich ground within reach",
                        rock.serializedName());
            }
            OreDeposit closest = near.get(0);
            return String.format(java.util.Locale.ROOT,
                    "%s region | nearest rich %s ground %d blocks away at Y %d (core %.1f%%)",
                    rock.serializedName(), closest.ore(),
                    Math.round(horizontalDistance(closest, pos)), closest.centre().getY(),
                    closest.coreGrade() * 100);
        });
    }

    // ---------------------------------------------------------------- internals

    /**
     * One stable value per region and purpose. The same mix as the water table uses, so a region
     * answers the same way for the life of a world without anything being written down.
     */
    static long hash(long seed, int regionX, int regionZ, int salt) {
        long hash = seed * 0x9E3779B97F4A7C15L
                + regionX * 0xC2B2AE3D27D4EB4FL
                + regionZ * 0x165667B19E3779F9L
                + salt * 0x27D4EB2F165667C5L;
        hash ^= hash >>> 29;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 32;
        hash *= 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        return hash;
    }
}
