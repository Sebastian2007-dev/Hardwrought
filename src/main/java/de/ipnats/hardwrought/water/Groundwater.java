package de.ipnats.hardwrought.water;

import de.ipnats.hardwrought.core.debug.DiagnosticRegistry;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.save.CoreSaveData;
import de.ipnats.hardwrought.core.simulation.SimulationScheduler;
import de.ipnats.hardwrought.core.simulation.SimulationTier;
import de.ipnats.hardwrought.environment.Season;
import de.ipnats.hardwrought.environment.SeasonCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Specification section 23.6 and section 68: the water in the ground, treated as a quantity rather
 * than as a line on a map.
 *
 * <p>A region has a water table and it has a reserve. The table says where water stands; the reserve
 * says how much is left, and the reserve is what makes the table move. Every spring, every well and
 * every flooded mine draws on the same regional reserve, and as it empties the table falls with it:
 * the well that worked last summer is dry, and the shaft that flooded stops filling. Rain, melt and
 * the season put it back. That is what section 23.6 means by regional water scarcity — and without a
 * finite reserve a well would be exactly the infinite water source section 23.1 removed.
 *
 * <pre>
 *   natural table   seed and biome; the same answer every time the same region is asked
 *   drawdown        up to twelve blocks lower as the reserve empties
 *   reserve         finite millibuckets, taken by springs, put back by weather
 * </pre>
 *
 * <p>Bounded like everything else here: one region per player per slow pass, arithmetic only, and
 * never a chunk load. A region nobody has drawn on is not stored at all, and one that has been left
 * alone catches up from its saved timestamp the next time it is asked about.
 */
public final class Groundwater {
    /** Section 23.6: a region has one water table, so a well works anywhere inside it. */
    public static final int REGION_SIZE_BLOCKS = 64;
    private static final int TABLE_MIN_BELOW_SEA = 6;
    private static final int TABLE_RANGE = 26;
    private static final int DRY_BIOME_DROP = 24;
    private static final int WET_BIOME_RISE = 8;

    /**
     * What ordinary ground holds per region, in millibuckets. Five hundred buckets: far more than a
     * household well ever asks for, and little enough that a shaft flooding day and night empties it
     * in a few days and then stops.
     */
    public static final int REGION_CAPACITY = 512_000;
    /** How far the table sinks between a full reserve and an empty one. */
    public static final int MAX_DRAWDOWN = 12;
    /** What soaks back in per slow pass in dry weather. A drained region needs weeks of it. */
    public static final int RECHARGE_PER_PASS = 215;
    /** Rain is what actually fills an aquifer; section 23.3 runs the water cycle through the ground. */
    public static final double RAIN_RECHARGE = 4.0;
    /** Past this an unattended region stops catching up, so a year away is not a year simulated. */
    public static final long MAX_CATCH_UP_TICKS = 3L * SeasonCycle.TICKS_PER_DAY;

    /** What a spring at the water table yields per pass. */
    public static final int SEEP_YIELD = 250;
    /** What a shaft cut deep into a water-bearing layer takes per pass. Section 68: a flood. */
    public static final int BREACH_YIELD = 1_000;
    /** How far below the table the ground presses hard enough for the full breach yield. */
    public static final int PRESSURE_DEPTH = 24;

    private final MinecraftServer server;
    private final CoreSaveData save;
    /**
     * The last region asked about. Reading a profile means sampling the biome at the centre of a
     * region, and the callers walk a box of positions that all belong to the same one. The datapack
     * table is replaced wholesale on reload, so comparing it by identity is enough to notice.
     */
    private Map<Identifier, AquiferProfile> memoProfiles;
    private String memoKey;
    private AquiferProfile memoProfile;

    public Groundwater(MinecraftServer server, CoreSaveData save, SimulationScheduler scheduler) {
        this.server = server;
        this.save = save;
        scheduler.register("hardwrought:groundwater_recharge", SimulationTier.SLOW, this::tickRecharge);
    }

    /** The groundwater model of the running server, or null before the runtime exists. */
    public static Groundwater of(ServerLevel level) {
        var runtime = level == null ? null : CoreLifecycle.find(level.getServer());
        return runtime == null ? null : runtime.groundwater();
    }

    // ---------------------------------------------------------------- the table

    /**
     * The height below which this region still holds water, after what has been taken out of it.
     * This is the number a well is dug against.
     */
    public int table(ServerLevel level, BlockPos pos) {
        AquiferProfile profile = profileFor(level, pos);
        int capacity = capacity(profile);
        if (capacity <= 0) return level.getMinY() - 1;
        return naturalTable(level, pos, profile) - drawdown(state(level, pos, capacity).fill(capacity));
    }

    /** Where the table stands with the reserve untouched. */
    public static int naturalTable(ServerLevel level, BlockPos pos) {
        return naturalTable(level, pos, profile(level, regionCentre(level,
                Math.floorDiv(pos.getX(), REGION_SIZE_BLOCKS),
                Math.floorDiv(pos.getZ(), REGION_SIZE_BLOCKS))));
    }

    private static int naturalTable(ServerLevel level, BlockPos pos, AquiferProfile profile) {
        int regionX = Math.floorDiv(pos.getX(), REGION_SIZE_BLOCKS);
        int regionZ = Math.floorDiv(pos.getZ(), REGION_SIZE_BLOCKS);
        long hash = level.getSeed() * 0x9E3779B97F4A7C15L
                + regionX * 0xC2B2AE3D27D4EB4FL + regionZ * 0x165667B19E3779F9L;
        hash ^= hash >>> 29;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 32;
        int depth = TABLE_MIN_BELOW_SEA + (int) Math.floorMod(hash, TABLE_RANGE);
        int table = level.getSeaLevel() - depth;
        if (profile != null) return table + profile.tableOffset();
        // Biome and profile are both read at the centre of the region, not at the block: a region
        // has one table, or a well would work on one side of a garden and be dry on the other.
        Holder<Biome> biome = level.getBiome(regionCentre(level, regionX, regionZ));
        if (biome.is(BiomeTags.IS_BADLANDS) || biome.value().getBaseTemperature() > 1.5f) {
            table -= DRY_BIOME_DROP;
        } else if (biome.is(BiomeTags.IS_JUNGLE)) {
            table += WET_BIOME_RISE;
        }
        return table;
    }

    /** How far the table has sunk at this fill level. Pure, so the drought curve is checkable. */
    public static int drawdown(double fill) {
        if (!Double.isFinite(fill)) throw new IllegalArgumentException("Non-finite aquifer fill");
        return (int) Math.round(MAX_DRAWDOWN * (1.0 - Math.clamp(fill, 0.0, 1.0)));
    }

    /** The current table, falling back to the seed-derived one before the runtime exists. */
    public static int tableAt(ServerLevel level, BlockPos pos) {
        Groundwater groundwater = of(level);
        return groundwater == null ? naturalTable(level, pos) : groundwater.table(level, pos);
    }

    public static boolean belowTable(ServerLevel level, BlockPos pos) {
        return pos.getY() <= tableAt(level, pos);
    }

    /**
     * Section 68: opening a water-bearing layer floods what was opened. Only natural, porous ground
     * lets water through, so a shaft cut through stone and gravel seeps while a cellar lined with
     * planks or bricks stays dry. That is the first countermeasure, before drainage and pumps.
     */
    public static boolean isWaterBearing(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)
                || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.TERRACOTTA);
    }

    /**
     * What an opening this far below the table takes per pass. At the table itself it is a seep a
     * bucket can keep up with; twenty-four blocks under it the ground presses hard enough to fill a
     * mine faster than it can be bailed out.
     */
    public static int yieldAt(int depthBelowTable) {
        if (depthBelowTable < 0) return 0;
        double pressure = Math.min(1.0, depthBelowTable / (double) PRESSURE_DEPTH);
        return SEEP_YIELD + (int) Math.round((BREACH_YIELD - SEEP_YIELD) * pressure);
    }

    // ---------------------------------------------------------------- the reserve

    /** What ordinary ground of this biome holds. Dry rock holds nothing and never yields. */
    public int capacity(ServerLevel level, BlockPos pos) {
        return capacity(profileFor(level, pos));
    }

    private static int capacity(AquiferProfile profile) {
        return (int) Math.round(REGION_CAPACITY * (profile == null ? 1.0 : profile.richness()));
    }

    /** Millibuckets left in this region, after catching up on the weather it has missed. */
    public int reserve(ServerLevel level, BlockPos pos) {
        return state(level, pos, capacity(level, pos)).stored();
    }

    /** How much of the region's water is left, 0 to 1. */
    public double fill(ServerLevel level, BlockPos pos) {
        int capacity = capacity(level, pos);
        return capacity <= 0 ? 0 : state(level, pos, capacity).fill(capacity);
    }

    /**
     * Takes water out of the ground and reports what was really there. A spring that asks for more
     * than the region still holds gets what is left, and after that nothing: that is a well running
     * dry, and a flooded shaft finally standing still.
     */
    public int draw(ServerLevel level, BlockPos pos, int millibuckets) {
        if (millibuckets <= 0) return 0;
        int capacity = capacity(level, pos);
        if (capacity <= 0) return 0;
        AquiferState current = state(level, pos, capacity);
        int granted = Math.min(millibuckets, current.stored());
        if (granted <= 0) {
            store(level, pos, current, capacity);
            return 0;
        }
        store(level, pos, current.drawn(granted, save.ticks()), capacity);
        return granted;
    }

    /**
     * Puts a region back to full, which is what an aquifer nobody has drawn on is. For operators
     * undoing a drought and for tests that create one on purpose.
     */
    public void refill(ServerLevel level, BlockPos pos) {
        save.clearAquifer(regionKey(level, pos));
    }

    /** Convenience for callers that only have a level, used by the seepage pass. */
    public static int drawAt(ServerLevel level, BlockPos pos, int millibuckets) {
        Groundwater groundwater = of(level);
        return groundwater == null ? millibuckets : groundwater.draw(level, pos, millibuckets);
    }

    // ---------------------------------------------------------------- quality

    /**
     * What this region's groundwater is. It has been filtered by the ground it stands in, so it is
     * clean by default — that is what gives a well its point. Coastal and salt-marsh ground is the
     * exception a datapack profile states, and a well dug there yields brine however deep it goes.
     */
    public static WaterQuality qualityOf(ServerLevel level, BlockPos pos) {
        AquiferProfile profile = profile(level, regionCentre(level,
                Math.floorDiv(pos.getX(), REGION_SIZE_BLOCKS),
                Math.floorDiv(pos.getZ(), REGION_SIZE_BLOCKS)));
        return profile == null ? WaterQuality.FRESH : profile.quality();
    }

    // ---------------------------------------------------------------- upkeep

    /**
     * Rain, melt and the season put water back into the ground the players are standing on. One
     * region per player per slow pass; everywhere else catches up from its timestamp when it is next
     * asked about.
     */
    private void tickRecharge() {
        Set<String> done = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            ServerLevel level = player.level();
            BlockPos pos = player.blockPosition();
            if (!level.hasChunkAt(pos)) continue;
            if (!done.add(regionKey(level, pos))) continue;
            int capacity = capacity(level, pos);
            if (capacity <= 0) continue;
            AquiferState current = state(level, pos, capacity);
            if (current.stored() >= capacity) {
                store(level, pos, current, capacity);
                continue;
            }
            double weather = level.isRaining() ? RAIN_RECHARGE : 1.0;
            double gain = RECHARGE_PER_PASS * richness(level, pos) * weather
                    * infiltration(SeasonCycle.current(level));
            int stored = (int) Math.min(capacity, current.stored() + Math.round(gain));
            store(level, pos, new AquiferState(stored, save.ticks()), capacity);
        }
    }

    /**
     * How readily water soaks in at this time of year. Frozen ground in winter takes almost nothing,
     * the spring melt is what really fills an aquifer, and a summer downpour mostly runs off warm,
     * hard ground.
     */
    public static double infiltration(Season season) {
        return switch (season) {
            case SPRING -> 1.40;
            case SUMMER -> 0.70;
            case AUTUMN -> 1.20;
            case WINTER -> 0.30;
        };
    }

    // ---------------------------------------------------------------- diagnostics

    public void registerDiagnostics(DiagnosticRegistry registry) {
        registry.register("hardwrought:groundwater", DiagnosticRegistry.Channel.WATER, (level, pos) -> {
            int capacity = capacity(level, pos);
            int natural = naturalTable(level, pos);
            if (capacity <= 0) {
                return String.format(Locale.ROOT, "aquifer %s: dry rock | natural table Y=%d",
                        regionKey(level, pos), natural);
            }
            AquiferState current = state(level, pos, capacity);
            return String.format(Locale.ROOT,
                    "aquifer %s | table Y=%d (natural %d) reserve=%d/%dL quality=%s",
                    regionKey(level, pos), natural - drawdown(current.fill(capacity)), natural,
                    current.stored() / 1000, capacity / 1000, qualityOf(level, pos).serializedName());
        });
    }

    // ---------------------------------------------------------------- internals

    private AquiferState state(ServerLevel level, BlockPos pos, int capacity) {
        AquiferState saved = save.aquifer(regionKey(level, pos));
        long now = save.ticks();
        if (saved == null) return new AquiferState(capacity, now);
        return saved.rechargedTo(now, capacity, rechargePerTick(level, pos), MAX_CATCH_UP_TICKS);
    }

    /** A region that is full again is forgotten rather than saved forever as "full". */
    private void store(ServerLevel level, BlockPos pos, AquiferState state, int capacity) {
        String key = regionKey(level, pos);
        if (state.stored() >= capacity) save.clearAquifer(key);
        else save.setAquifer(key, state);
    }

    private double rechargePerTick(ServerLevel level, BlockPos pos) {
        return RECHARGE_PER_PASS * richness(level, pos) * infiltration(SeasonCycle.current(level))
                / SimulationTier.SLOW.interval();
    }

    private double richness(ServerLevel level, BlockPos pos) {
        AquiferProfile profile = profileFor(level, pos);
        return profile == null ? 1.0 : profile.richness();
    }

    /**
     * The profile of the region a position belongs to. Remembers the last region: every caller here
     * walks a box of positions inside one region, and reading a profile means sampling the biome at
     * the centre of it. The remembered answer is dropped when the datapack table is replaced.
     */
    private AquiferProfile profileFor(ServerLevel level, BlockPos pos) {
        Map<Identifier, AquiferProfile> profiles = server.getOrThrow(AquiferProfiles.KEY);
        String key = regionKey(level, pos);
        if (profiles == memoProfiles && key.equals(memoKey)) return memoProfile;
        int regionX = Math.floorDiv(pos.getX(), REGION_SIZE_BLOCKS);
        int regionZ = Math.floorDiv(pos.getZ(), REGION_SIZE_BLOCKS);
        Identifier biome = biomeAt(level, regionCentre(level, regionX, regionZ));
        AquiferProfile profile = biome == null ? null : profiles.get(biome);
        memoProfiles = profiles;
        memoKey = key;
        memoProfile = profile;
        return profile;
    }

    private static AquiferProfile profile(ServerLevel level, BlockPos centre) {
        MinecraftServer server = level.getServer();
        if (server == null) return null;
        Identifier id = biomeAt(level, centre);
        if (id == null) return null;
        Map<Identifier, AquiferProfile> profiles = server.getOrThrow(AquiferProfiles.KEY);
        return profiles.get(id);
    }

    private static Identifier biomeAt(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey().map(key -> key.identifier()).orElse(null);
    }

    private static BlockPos regionCentre(ServerLevel level, int regionX, int regionZ) {
        return new BlockPos(regionX * REGION_SIZE_BLOCKS + REGION_SIZE_BLOCKS / 2,
                level.getSeaLevel(), regionZ * REGION_SIZE_BLOCKS + REGION_SIZE_BLOCKS / 2);
    }

    /** One key per dimension and region: the reserve belongs to the region, not to the block. */
    public static String regionKey(ServerLevel level, BlockPos pos) {
        return level.dimension().identifier() + "@"
                + Math.floorDiv(pos.getX(), REGION_SIZE_BLOCKS) + ","
                + Math.floorDiv(pos.getZ(), REGION_SIZE_BLOCKS);
    }
}
