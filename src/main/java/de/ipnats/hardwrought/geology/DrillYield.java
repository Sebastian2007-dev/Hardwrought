package de.ipnats.hardwrought.geology;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What a drill standing on one chunk can bring up, and how fast.
 *
 * <p>The table is not invented for the machine: it is the geology of the chunk read back. The rock
 * of the region decides which ores are in the ground at all and in what proportion, the bodies
 * actually lying under the chunk weigh their own ore up sharply, and the drill's tier decides how
 * much of that it can reach.
 *
 * <pre>
 *   bare rock over a coal region   → coal mostly, slowly, for ever
 *   the same chunk over a body     → that ore several times as often, and faster
 *   diamond in the same rock       → only once the drill can reach it at all
 * </pre>
 *
 * <p>Pure: a seed, a chunk and a tier in, a weighted table out. The machine that will use it only
 * has to roll against it.
 */
public record DrillYield(List<Entry> entries, int intervalTicks) {
    /** How much more likely an ore is when a body of it really lies under the chunk. */
    public static final int BODY_WEIGHT_FACTOR = 5;
    /** A chunk with a body under it works faster, because there is something there to work. */
    public static final double BODY_SPEED_FACTOR = 0.6;
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

    /** True where this rock holds nothing this drill can reach. A better machine might. */
    public boolean barren() {
        return entries.isEmpty();
    }

    public int totalWeight() {
        int total = 0;
        for (Entry entry : entries) total += entry.weight();
        return total;
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
     * What a drill of this tier gets out of the chunk containing this position.
     *
     * <p>Bounded by construction: the rock profile is read once and the column is sampled at a fixed
     * number of heights, so this costs the same whatever the world looks like.
     */
    public static DrillYield forChunk(long seed, Map<Identifier, RockProfile> profiles,
                                      int blockX, int blockZ, DrillTier tier) {
        if (tier == null) throw new IllegalArgumentException("A drill needs a tier");
        RockType rock = Geology.rockAt(seed, blockX, blockZ);
        RockProfile profile = profiles == null ? null : profiles.get(rock.id());
        if (profile == null || profile.deposits().isEmpty()) return NOTHING;

        boolean overBody = false;
        List<Entry> entries = new ArrayList<>(profile.deposits().size());
        for (DepositProfile deposit : profile.deposits()) {
            if (!tier.reaches(deposit.drillTier())) continue;
            int weight = deposit.weight();
            if (bodyUnder(seed, profiles, blockX, blockZ, deposit.ore())) {
                weight *= BODY_WEIGHT_FACTOR;
                overBody = true;
            }
            entries.add(new Entry(deposit.ore(), weight));
        }
        if (entries.isEmpty()) return NOTHING;
        int interval = overBody
                ? Math.max(1, (int) Math.round(tier.intervalTicks() * BODY_SPEED_FACTOR))
                : tier.intervalTicks();
        return new DrillYield(entries, interval);
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
