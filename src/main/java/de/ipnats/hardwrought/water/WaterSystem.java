package de.ipnats.hardwrought.water;

import de.ipnats.hardwrought.core.debug.DiagnosticRegistry;
import de.ipnats.hardwrought.core.simulation.SimulationScheduler;
import de.ipnats.hardwrought.core.simulation.SimulationTier;
import de.ipnats.hardwrought.environment.BiomeClimate;
import de.ipnats.hardwrought.environment.EnvironmentSystem;
import de.ipnats.hardwrought.environment.Season;
import de.ipnats.hardwrought.environment.SeasonCycle;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Milestone-4 water: specification section 23. Vanilla infinite water is gone (see the water fluid
 * mixin), which leaves three questions this system answers — how good the water at a position is,
 * where the water table lies, and what the sun does to a puddle.
 *
 * <p>Like the environment model it is bounded by construction: one flood fill per lookup, a fixed
 * sample box per player per slow pass, and never a chunk load from a tick job.
 */
public final class WaterSystem {
    /** Section 23.6: a region has one water table, so a well works anywhere inside it. */
    public static final int REGION_SIZE_BLOCKS = Groundwater.REGION_SIZE_BLOCKS;
    /** How far around a player seepage and evaporation look. Fixed, so the work stays bounded. */
    private static final int SAMPLE_RADIUS = 3;
    private static final int SAMPLE_HEIGHT = 2;
    /** Below this ambient temperature evaporation is too weak to matter at block scale. */
    private static final double EVAPORATION_TEMPERATURE = 4.0;
    /** What the sun takes from a puddle at once. */
    private static final int EVAPORATION_YIELD = 125;

    private final MinecraftServer server;
    private final EnvironmentSystem environment;

    public WaterSystem(MinecraftServer server, EnvironmentSystem environment, SimulationScheduler scheduler) {
        this.server = server;
        this.environment = environment;
        scheduler.register("hardwrought:water_seepage", SimulationTier.SLOW, this::tickSeepage);
        scheduler.register("hardwrought:water_evaporation", SimulationTier.SLOW, this::tickEvaporation);
    }

    public Map<Identifier, WaterQualityProfile> qualityProfiles() {
        return server.getOrThrow(WaterQualityProfiles.KEY);
    }

    // ---------------------------------------------------------------- quality

    /**
     * Section 23.2: what is actually in this water, in the order the question can be answered.
     *
     * <p>A mark left by something that was poured or seeped in decides first, then a datapack
     * profile for the biome, then the surroundings: ocean water is salt, river water is river
     * water, running water counts as river water, and water standing below the table is the
     * groundwater of its region — which is what gives a well its point. Nothing else off the
     * surface is fresh; that takes a fire or, later, a filter.
     */
    public WaterQuality qualityAt(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !WaterBody.isWater(level, pos)) return WaterQuality.FRESH;
        // Water carries what has been put into it: seawater poured into a pond is still seawater,
        // and spring water lifted out of a well is still spring water.
        WaterQuality marked = WaterQualityStorage.stored(level, pos);
        if (marked != null) return marked;
        WaterQuality declared = WaterQualityProfiles.declared(level, pos);
        if (declared != null) return declared;
        WaterQuality ambient = WaterQualityProfiles.ambient(level, pos);
        if (ambient != WaterQuality.SWAMP) return ambient;
        // The one case that needs a measured body: a still pool is stagnant, but a body large
        // enough to run past the counting budget keeps itself moving and is no worse than a river.
        return WaterBody.scan(level, pos).size() == WaterBody.Size.LARGE
                ? WaterQuality.RIVER : WaterQuality.SWAMP;
    }

    // ---------------------------------------------------------------- groundwater

    /**
     * Section 23.6: the height below which a region still holds water. The model behind it lives in
     * {@link Groundwater}: the table is derived from the world seed, but it sinks as the reserve of
     * its region is drawn down, so the same place does not answer the same way for ever.
     */
    public int groundwaterLevel(ServerLevel level, BlockPos pos) {
        return Groundwater.tableAt(level, pos);
    }

    /** True where a position sits inside the water-bearing ground of its region. */
    public boolean belowWaterTable(ServerLevel level, BlockPos pos) {
        return pos.getY() <= groundwaterLevel(level, pos);
    }

    /** Section 68: only natural, porous ground lets water through. See {@link Groundwater}. */
    public static boolean isWaterBearing(BlockState state) {
        return Groundwater.isWaterBearing(state);
    }

    /**
     * Sections 23.6 and 68 seen from the same side: ground opened below the table fills with what
     * the region holds. How fast depends on how deep the opening lies — a shaft at the table seeps,
     * one cut far beneath it floods — and every drop comes out of a finite regional reserve, so a
     * mine that floods day and night drains the ground around it and eventually falls quiet.
     */
    private void tickSeepage() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            ServerLevel level = player.level();
            BlockPos origin = player.blockPosition();
            if (!level.hasChunkAt(origin)) continue;
            int table = groundwaterLevel(level, origin);
            if (origin.getY() > table + SAMPLE_HEIGHT) continue;
            BlockPos seep = findSeepage(level, origin, table);
            if (seep == null) continue;
            int yield = Groundwater.drawAt(level, seep, Groundwater.yieldAt(table - seep.getY()));
            if (yield <= 0) continue;
            int present = WaterStorage.amount(level, seep);
            WaterStorage.setAmount(level, seep, present + yield);
            WaterQualityStorage.add(level, seep, Groundwater.qualityOf(level, seep), present, yield);
            WaterFlow.disturb(level, seep);
            if (present == 0) {
                level.playSound(null, seep, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.4f, 1.2f);
            }
        }
    }

    /** A fixed, small box around the player: at most 7x5x7 block reads per player per slow pass. */
    private BlockPos findSeepage(ServerLevel level, BlockPos origin, int table) {
        for (int y = -SAMPLE_HEIGHT; y <= SAMPLE_HEIGHT; y++) {
            for (int x = -SAMPLE_RADIUS; x <= SAMPLE_RADIUS; x++) {
                for (int z = -SAMPLE_RADIUS; z <= SAMPLE_RADIUS; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (pos.getY() > table || !level.hasChunkAt(pos)) continue;
                    if (WaterStorage.amount(level, pos) >= WaterAmounts.BLOCK) continue;
                    if (!level.getBlockState(pos).isAir() && !WaterStorage.isFreeWater(level.getBlockState(pos))) {
                        continue;
                    }
                    if (!touchesWaterBearingGround(level, pos)) continue;
                    return pos;
                }
            }
        }
        return null;
    }

    private static boolean touchesWaterBearingGround(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP) continue;
            BlockPos next = pos.relative(direction);
            if (!level.hasChunkAt(next)) continue;
            if (isWaterBearing(level.getBlockState(next))) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- evaporation

    /**
     * Section 23.4: evaporation depends on sunlight, temperature, wind and the size of the body.
     * Only a puddle open to the sky in warm, moving, dry air loses anything. A lake is never touched:
     * at this scale it is fed as fast as it loses, and taking blocks out of one would be a change to
     * the world nobody asked for.
     */
    private void tickEvaporation() {
        Map<ServerLevel, LongOpenHashSet> processed = new IdentityHashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            ServerLevel level = player.level();
            BlockPos origin = player.blockPosition();
            if (!level.hasChunkAt(origin) || !level.isBrightOutside()) continue;
            BlockPos surface = findExposedPuddleSurface(level, origin);
            if (surface == null || level.isRainingAt(surface)) continue;
            if (!processed.computeIfAbsent(level, ignored -> new LongOpenHashSet()).add(surface.asLong())) continue;
            Season season = SeasonCycle.current(level);
            BiomeClimate climate = environment.biomeClimate(level, surface);
            double drying = evaporationChance(environment.outdoorTemperature(level, surface, season),
                    environment.wind(level, surface), climate, season);
            if (level.getRandom().nextDouble() > drying) continue;
            int present = WaterStorage.amount(level, surface);
            WaterStorage.setAmount(level, surface, present - EVAPORATION_YIELD);
            WaterFlow.disturb(level, surface);
            level.levelEvent(1501, surface, 0);
        }
    }

    /**
     * Chance per slow simulation pass. Temperature remains the continuous physical input while the
     * broad climate and season make two equally warm moments in different regions behave differently.
     */
    public static double evaporationChance(double temperature, double wind,
                                           BiomeClimate climate, Season season) {
        if (!Double.isFinite(temperature) || !Double.isFinite(wind) || climate == null || season == null) {
            throw new IllegalArgumentException("Invalid evaporation inputs");
        }
        if (temperature <= EVAPORATION_TEMPERATURE) return 0;
        double heat = clamp((temperature - EVAPORATION_TEMPERATURE) / 28.0, 0, 1);
        double movingAir = 0.25 + 0.75 * clamp(wind, 0, 1);
        return clamp(0.30 * heat * movingAir * climate.evaporationFactor()
                * season.evaporationFactor(), 0, 0.75);
    }

    private BlockPos findExposedPuddleSurface(ServerLevel level, BlockPos origin) {
        var measuredWater = new LongOpenHashSet();
        for (int x = -SAMPLE_RADIUS; x <= SAMPLE_RADIUS; x++) {
            for (int z = -SAMPLE_RADIUS; z <= SAMPLE_RADIUS; z++) {
                for (int y = SAMPLE_HEIGHT; y >= -SAMPLE_HEIGHT; y--) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (measuredWater.contains(pos.asLong())) continue;
                    if (!level.hasChunkAt(pos) || !WaterBody.isWater(level, pos)) continue;
                    if (!level.getBlockState(pos.above()).isAir()) continue;
                    if (pos.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - 1) {
                        continue;
                    }
                    if (belowWaterTable(level, pos)) continue;
                    WaterBody body = WaterBody.scan(level, pos, measuredWater::add);
                    if (body.puddle()) return pos;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- diagnostics

    public void registerDiagnostics(DiagnosticRegistry registry) {
        registry.register("hardwrought:water_body", DiagnosticRegistry.Channel.WATER, (level, pos) -> {
            int table = groundwaterLevel(level, pos);
            WaterBody body = WaterBody.scan(level, pos);
            Season season = SeasonCycle.current(level);
            BiomeClimate climate = environment.biomeClimate(level, pos);
            double drying = evaporationChance(environment.outdoorTemperature(level, pos, season),
                    environment.wind(level, pos), climate, season);
            if (!body.exists()) {
                return String.format(Locale.ROOT,
                        "no water here | groundwater Y=%d (%s) | climate=%s season=%s evaporation=%.1f%%",
                        table, pos.getY() <= table ? "below the table" : "above the table",
                        climate.serializedName(), season.serializedName(), drying * 100);
            }
            return String.format(Locale.ROOT,
                    "%s volume=%d sources=%d exposure=%.2f quality=%s | groundwater Y=%d climate=%s season=%s evaporation=%.1f%%",
                    body.size(), body.volume(), body.sources(), body.exposure(),
                    qualityAt(level, pos).serializedName(), table, climate.serializedName(),
                    season.serializedName(), drying * 100);
        });
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
