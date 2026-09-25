package de.ipnats.hardwrought.geology;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a drill standing on one chunk can bring up, and how fast.
 *
 * <p>Every chunk has its own mix of ores. The rock of the region sets the tone — the ores it carries
 * natively are there in quantity, every other ore any rock carries only as a trace — and the chunk
 * itself decides how much of each it actually holds: some ores are missing from it entirely, others
 * are there three times over. A body lying under the chunk weighs its own ore up fivefold on top of
 * that. The drill's tier then decides how much of the mix it can reach.
 *
 * <pre>
 *   granite, one chunk      iron 62 %, emerald 3 %, a trace of coal
 *   granite, the next       iron 41 %, no emerald at all, more coal
 *   over an iron body       iron nearly all of it, and faster
 * </pre>
 *
 * <p>Pure: a seed, a chunk and a tier in, a weighted table out. Nothing is stored; the same chunk
 * answers the same way for the life of a world.
 */
public record DrillYield(List<Entry> entries, int intervalTicks) {
    /** How much more likely an ore is when a body of it really lies under the chunk. */
    public static final int BODY_WEIGHT_FACTOR = 5;
    /** A chunk with a body under it works faster, because there is something there to work. */
    public static final double BODY_SPEED_FACTOR = 0.6;
    /** An ore the region's rock carries counts this many times its profile weight... */
    public static final int NATIVE_FACTOR = 4;
    /** ...one it does not is there as a trace at this weight. */
    public static final int TRACE_WEIGHT = 1;
    /** How many ores in a hundred a chunk simply does not have. */
    public static final int ABSENT_PERCENT = 25;
    /** The most a chunk has of an ore, against its region's usual amount. */
    public static final double MOST_OF_ONE = 3.0;
    /** Depth of rock a drill works through, from the surface band down. */
    public static final int SAMPLE_TOP_Y = 120;
    public static final int SAMPLE_BOTTOM_Y = -240;
    public static final int SAMPLE_STEP = 20;

    public record Entry(Identifier ore, int weight) {
        public Entry {
            if (ore == null || weight <= 0) throw new IllegalArgumentException("Invalid drill entry");
        }
    }

    public DrillYield {
        if (entries == null || intervalTicks <= 0) throw new IllegalArgumentException("Invalid drill yield");
        entries = List.copyOf(entries);
    }

    public static final DrillYield NOTHING = new DrillYield(List.of(), Integer.MAX_VALUE);

    /** True where this chunk holds nothing this drill can reach. A better machine might. */
    public boolean barren() {
        return entries.isEmpty();
    }

    public int totalWeight() {
        int total = 0;
        for (Entry entry : entries) total += entry.weight();
        return total;
    }

    /** The share of one ore in this table, 0 to 1. */
    public double share(Identifier ore) {
        int total = totalWeight();
        if (total <= 0) return 0;
        for (Entry entry : entries) if (entry.ore().equals(ore)) return entry.weight() / (double) total;
        return 0;
    }

    /** One piece of ore, or null out of rock that holds nothing for this drill. */
    public Identifier roll(RandomSource random) {
        int total = totalWeight();
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (Entry entry : entries) {
            roll -= entry.weight();
            if (roll < 0) return entry.ore();
        }
        return entries.get(entries.size() - 1).ore();
    }

    /**
     * Every ore any rock carries, with the drill tier it needs. An ore listed by two rocks needs the
     * better of the two drills, so no rock table can make an ore easier than another says it is.
     */
    public static Map<Identifier, Integer> oreTiers(Map<Identifier, RockProfile> profiles) {
        Map<Identifier, Integer> tiers = new TreeMap<>();
        if (profiles == null) return tiers;
        for (RockProfile profile : profiles.values()) {
            for (DepositProfile deposit : profile.deposits()) tiers.merge(deposit.ore(), deposit.drillTier(), Math::max);
        }
        return tiers;
    }

    /**
     * What a drill of this tier gets out of the chunk containing this position.
     *
     * <p>Bounded by construction: the rock tables are read once and the column is sampled at a fixed
     * number of heights, so this costs the same whatever the world looks like.
     */
    public static DrillYield forChunk(long seed, Map<Identifier, RockProfile> profiles,
                                      int blockX, int blockZ, DrillTier tier) {
        if (tier == null) throw new IllegalArgumentException("A drill needs a tier");
        RockType rock = Geology.rockAt(seed, blockX, blockZ);
        RockProfile profile = profiles == null ? null : profiles.get(rock.id());
        Map<Identifier, Integer> tiers = oreTiers(profiles);
        if (tiers.isEmpty()) return NOTHING;
        int chunkX = SectionPos.blockToSectionCoord(blockX);
        int chunkZ = SectionPos.blockToSectionCoord(blockZ);

        boolean overBody = false;
        List<Entry> entries = new ArrayList<>(tiers.size());
        for (var ore : tiers.entrySet()) {
            if (!tier.reaches(ore.getValue())) continue;
            boolean body = bodyUnder(seed, profiles, blockX, blockZ, ore.getKey());
            double amount = amountIn(seed, chunkX, chunkZ, ore.getKey());
            // A body lying under the chunk is that ore, whatever the chunk would otherwise hold.
            if (body) amount = Math.max(amount, 1.0);
            if (amount <= 0) continue;
            int base = TRACE_WEIGHT * 10;
            if (profile != null) {
                for (DepositProfile deposit : profile.deposits()) {
                    if (deposit.ore().equals(ore.getKey())) base = deposit.weight() * NATIVE_FACTOR * 10;
                }
            }
            int weight = Math.max(1, (int) Math.round(base * amount));
            if (body) {
                weight *= BODY_WEIGHT_FACTOR;
                overBody = true;
            }
            entries.add(new Entry(ore.getKey(), weight));
        }
        if (entries.isEmpty()) return NOTHING;
        int interval = overBody
                ? Math.max(1, (int) Math.round(tier.intervalTicks() * BODY_SPEED_FACTOR))
                : tier.intervalTicks();
        return new DrillYield(entries, interval);
    }

    /**
     * How much of an ore this chunk holds against its region's usual amount: nothing in a quarter of
     * the chunks, otherwise anywhere from a quarter to three times as much.
     */
    public static double amountIn(long seed, int chunkX, int chunkZ, Identifier ore) {
        long hash = Geology.hash(seed, chunkX, chunkZ, 7_000 + ore.hashCode());
        if (Math.floorMod(hash, 100) < ABSENT_PERCENT) return 0;
        double roll = Math.floorMod(hash >>> 16, 1_000) / 999.0;
        return 0.25 + (MOST_OF_ONE - 0.25) * roll * roll;
    }

    /** Whether a body of this ore lies anywhere in the column the drill is working. */
    private static boolean bodyUnder(long seed, Map<Identifier, RockProfile> profiles,
                                     int blockX, int blockZ, Identifier ore) {
        OreDeposit deposit = Geology.depositOver(seed, profiles, blockX, blockZ, ore);
        if (deposit == null) return false;
        for (int y = SAMPLE_BOTTOM_Y; y <= SAMPLE_TOP_Y; y += SAMPLE_STEP) {
            if (deposit.contains(blockX, y, blockZ)) return true;
        }
        // A thin body can slip between two samples; its middle never does.
        return deposit.contains(new BlockPos(blockX, deposit.centre().getY(), blockZ));
    }
}
