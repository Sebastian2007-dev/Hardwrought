package de.ipnats.hardwrought.water;

import de.ipnats.hardwrought.core.debug.DiagnosticRegistry;
import de.ipnats.hardwrought.core.simulation.SimulationScheduler;
import de.ipnats.hardwrought.core.simulation.SimulationTier;
import de.ipnats.hardwrought.environment.EnvironmentSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.resources.Identifier;

import java.util.Locale;
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
    public static final int REGION_SIZE_BLOCKS = 64;
    private static final int TABLE_MIN_BELOW_SEA = 6;
    private static final int TABLE_RANGE = 26;
    private static final int DRY_BIOME_DROP = 24;
    private static final int WET_BIOME_RISE = 8;
    /** How far around a player seepage and evaporation look. Fixed, so the work stays bounded. */
    private static final int SAMPLE_RADIUS = 3;
    private static final int SAMPLE_HEIGHT = 2;
    /** Above this ambient temperature the sun starts taking standing water. */
    private static final double EVAPORATION_TEMPERATURE = 22.0;
    /** What a spring yields per seep. Water enters the world here and nowhere else by itself. */
    private static final int SPRING_YIELD = 250;
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
     * Section 23.2. A datapack profile for the biome decides first. Without one, ocean water is
     * salt, river water is river water, and everything else depends on whether it moves: running
     * water counts as river water, a still pool as swamp water. Nothing surface-borne is fresh —
     * fresh water comes from the ground or from boiling, which is what gives a well its point.
     */
    public WaterQuality qualityAt(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !WaterBody.isWater(level, pos)) return WaterQuality.FRESH;
        boolean moving = !level.getFluidState(pos).isSource();
        Holder<Biome> biome = level.getBiome(pos);
        Identifier id = biome.unwrapKey().map(key -> key.identifier()).orElse(null);
        WaterQualityProfile profile = id == null ? null : qualityProfiles().get(id);
        if (profile != null) return profile.quality(moving);
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)) return WaterQuality.SALT;
        if (biome.is(BiomeTags.IS_RIVER)) return WaterQuality.RIVER;
        if (moving) return WaterQuality.RIVER;
        // Water that has seeped up from below the table is groundwater, and groundwater is clean.
        if (pos.getY() <= groundwaterLevel(level, pos)) return WaterQuality.FRESH;
        return WaterBody.scan(level, pos).size() == WaterBody.Size.LARGE
                ? WaterQuality.RIVER : WaterQuality.SWAMP;
    }

    // ---------------------------------------------------------------- groundwater

    /**
     * Section 23.6: the height below which a region holds water. Derived from the world seed so it
     * is the same every time the same place is asked, without storing a table for the whole world.
     * Dry biomes push it far down, wet ones lift it, which is where regional water scarcity comes
     * from.
     */
    public int groundwaterLevel(ServerLevel level, BlockPos pos) {
        int regionX = Math.floorDiv(pos.getX(), REGION_SIZE_BLOCKS);
        int regionZ = Math.floorDiv(pos.getZ(), REGION_SIZE_BLOCKS);
        long hash = level.getSeed() * 0x9E3779B97F4A7C15L
                + regionX * 0xC2B2AE3D27D4EB4FL + regionZ * 0x165667B19E3779F9L;
        hash ^= hash >>> 29;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 32;
        int depth = TABLE_MIN_BELOW_SEA + (int) Math.floorMod(hash, TABLE_RANGE);
        int table = level.getSeaLevel() - depth;
        // Read at the centre of the region, not at the block: a region has one table, or a well
        // would work on one side of a garden and be dry on the other.
        BlockPos centre = new BlockPos(regionX * REGION_SIZE_BLOCKS + REGION_SIZE_BLOCKS / 2,
                level.getSeaLevel(), regionZ * REGION_SIZE_BLOCKS + REGION_SIZE_BLOCKS / 2);
        Holder<Biome> biome = level.getBiome(centre);
        if (biome.is(BiomeTags.IS_BADLANDS) || biome.value().getBaseTemperature() > 1.5f) {
            table -= DRY_BIOME_DROP;
        } else if (biome.is(BiomeTags.IS_JUNGLE)) {
            table += WET_BIOME_RISE;
        }
        return table;
    }

    /** True where a position sits inside the water-bearing ground of its region. */
    public boolean belowWaterTable(ServerLevel level, BlockPos pos) {
        return pos.getY() <= groundwaterLevel(level, pos);
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
            int present = WaterStorage.amount(level, seep);
            WaterStorage.setAmount(level, seep, present + SPRING_YIELD);
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
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            ServerLevel level = player.level();
            BlockPos origin = player.blockPosition();
            if (!level.hasChunkAt(origin) || level.isRaining()) continue;
            if (environment.outdoorTemperature(level, origin) < EVAPORATION_TEMPERATURE) continue;
            BlockPos surface = findExposedPuddleSurface(level, origin);
            if (surface == null) continue;
            double drying = environment.wind(level, surface)
                    * (environment.outdoorTemperature(level, surface) - EVAPORATION_TEMPERATURE) / 20.0;
            if (level.getRandom().nextDouble() > drying) continue;
            int present = WaterStorage.amount(level, surface);
            WaterStorage.setAmount(level, surface, present - EVAPORATION_YIELD);
            WaterFlow.disturb(level, surface);
            level.levelEvent(1501, surface, 0);
        }
    }

    private BlockPos findExposedPuddleSurface(ServerLevel level, BlockPos origin) {
        for (int x = -SAMPLE_RADIUS; x <= SAMPLE_RADIUS; x++) {
            for (int z = -SAMPLE_RADIUS; z <= SAMPLE_RADIUS; z++) {
                for (int y = SAMPLE_HEIGHT; y >= -SAMPLE_HEIGHT; y--) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!level.hasChunkAt(pos) || !WaterBody.isWater(level, pos)) continue;
                    if (!level.getBlockState(pos.above()).isAir()) continue;
                    if (pos.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - 1) {
                        continue;
                    }
                    if (belowWaterTable(level, pos)) continue;
                    WaterBody body = WaterBody.scan(level, pos);
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
            if (!body.exists()) {
                return String.format(Locale.ROOT, "no water here | groundwater Y=%d (%s)",
                        table, pos.getY() <= table ? "below the table" : "above the table");
            }
            return String.format(Locale.ROOT, "%s volume=%d sources=%d exposure=%.2f quality=%s | groundwater Y=%d",
                    body.size(), body.volume(), body.sources(), body.exposure(),
                    qualityAt(level, pos).serializedName(), table);
        });
    }
}
