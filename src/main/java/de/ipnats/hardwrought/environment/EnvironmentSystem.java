package de.ipnats.hardwrought.environment;

import de.ipnats.hardwrought.core.debug.DiagnosticRegistry;
import de.ipnats.hardwrought.core.networking.EnvironmentSnapshotPayload;
import de.ipnats.hardwrought.core.save.CoreSaveData;
import de.ipnats.hardwrought.core.simulation.SimulationScheduler;
import de.ipnats.hardwrought.core.simulation.SimulationTier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

/**
 * Milestone-3 environment: the unified model of specification section 13. It answers what the air at
 * a position is made of, how warm it is and how much wind reaches it.
 *
 * <p>The air itself is in the world, as gas blocks (see {@link GasBlock}): what a player breathes is
 * whatever is in the block their head is in. This system lets the fires, the breath and the coal
 * seams around the players put their gas out ({@link GasSources}). Rooms are still scanned, but only
 * for what is a property of a room: how warm it is and how well it keeps its warmth.
 *
 * <p>Effects on a player — stamina, fatigue, body temperature, damage — stay in the survival system,
 * which reads the typed {@link EnvironmentReading} from here. That keeps the dependency one-way.
 *
 * <p>Work is bounded exactly as section 105 requires: at most one flood fill per player per rescan
 * interval, only inside already loaded chunks, and only cells that currently hold a player are
 * simulated. A cell nobody is standing in is caught up from its saved timestamp the next time
 * someone walks back into it.
 */
public final class EnvironmentSystem {
    /** How often a player's surroundings are re-scanned while they stay in the same place. */
    public static final int RESCAN_INTERVAL_TICKS = 40;
    private static final double RESCAN_DISTANCE_SQR = 4.0;
    private static final int MAX_TRACKED_CELLS = 128;
    /** The catch-up applied when a room has been unattended for a long time. */
    private static final double MAX_CATCH_UP_SECONDS = 600.0;

    /** Degrees a room warms per unit of fire in it, divided by its volume. */
    private static final double COMBUSTION_HEAT = 800.0;
    private static final double MAX_ROOM_HEAT = 45.0;
    private static final double TEMPERATURE_RELAXATION = 0.10;
    private static final int MIN_EFFECTIVE_VOLUME = 8;
    /** Section 19: a flame needs oxygen. Air thinner than this, high up, will not keep one alight. */
    public static final double FIRE_MINIMUM_OXYGEN = 0.130;
    /**
     * Not every flame warms a room the same. An open fire or lava is the full measure; a campfire or a
     * furnace sends most of its heat up with its fumes; a torch or a candle barely counts.
     */
    private static final double WEAK_FLAME_WEIGHT = 0.15;
    private static final double CONTAINED_FLAME_WEIGHT = 0.40;
    /** One block of a forge's open bed of coal. Every block of a joined hearth burns. */
    public static final double FORGE_FLAME_WEIGHT = 0.50;

    private final MinecraftServer server;
    private final CoreSaveData save;
    private final SimulationScheduler scheduler;
    private final Map<UUID, Tracked> tracking = new HashMap<>();
    private final LinkedHashMap<String, EnvironmentCell> cells =
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, EnvironmentCell> eldest) {
                    return size() > MAX_TRACKED_CELLS;
                }
            };

    public EnvironmentSystem(MinecraftServer server, CoreSaveData save, SimulationScheduler scheduler) {
        this.server = server;
        this.save = save;
        this.scheduler = scheduler;
        scheduler.register("hardwrought:environment_cells", SimulationTier.MEDIUM, this::tickCells);
        scheduler.register("hardwrought:gas_sources", SimulationTier.SLOW, this::tickGasSources);
    }

    private record Tracked(String key, RoomScan scan, long scannedTick, BlockPos scannedAt) { }

    /** One enclosed space. Geometry comes from a scan, its warmth is carried over time. */
    private static final class EnvironmentCell {
        private final String key;
        private long origin;
        private int volume;
        private double apertureArea;
        private int floorY;
        private int ceilingY;
        private double insulation;
        private double coalExposure;
        private List<BlockPos> combustionSources;
        private double temperature;
        private long updatedTick;

        private EnvironmentCell(String key, CellAtmosphere saved, long tick) {
            this.key = key;
            this.temperature = saved == null ? 15.0 : saved.temperature();
            this.updatedTick = saved == null ? tick : saved.updatedTick();
            this.combustionSources = List.of();
            this.volume = MIN_EFFECTIVE_VOLUME;
            this.insulation = 0.5;
        }

        private void applyGeometry(RoomScan scan) {
            origin = scan.origin().asLong();
            volume = Math.max(MIN_EFFECTIVE_VOLUME, scan.volume());
            apertureArea = scan.apertureArea();
            floorY = scan.floorY();
            ceilingY = scan.ceilingY();
            insulation = scan.insulation();
            coalExposure = scan.coalExposure();
            combustionSources = scan.combustionSources();
        }
    }

    // ---------------------------------------------------------------- public reading

    /** The typed environment at a player's position. Never null, never a guessed value. */
    public EnvironmentReading reading(ServerPlayer player) {
        Tracked tracked = resolve(player);
        double wind = wind(player.level(), player.blockPosition());
        if (tracked.key == null || cells.get(tracked.key) == null) {
            return EnvironmentReading.outdoor(breathed(player),
                    outdoorTemperature(player.level(), player.blockPosition()), wind);
        }
        EnvironmentCell cell = cells.get(tracked.key);
        // A sealed room shields its occupants from the wind outside it.
        return new EnvironmentReading(breathed(player), cell.temperature, wind * 0.1, true, cell.volume,
                cell.insulation);
    }

    /** The air in the block the player's head is in: the gas there, and the air outside at that height. */
    private static GasMixture breathed(ServerPlayer player) {
        return Gases.sample(player.level(), BlockPos.containing(player.getEyePosition()));
    }

    /**
     * Outdoor temperature in degrees Celsius from biome, altitude, daylight and weather. The room
     * model adds the heat of anything burning inside; the survival system adds what is local to the
     * player, such as being in water.
     */
    public double outdoorTemperature(ServerLevel level, BlockPos pos) {
        return outdoorTemperature(level, pos, SeasonCycle.current(level));
    }

    /** Variant for systems that already sampled the shared calendar during their simulation pass. */
    public double outdoorTemperature(ServerLevel level, BlockPos pos, Season season) {
        double temperature = 14.0 + (level.getBiome(pos).value().getBaseTemperature() - 0.8) * 18.0;
        temperature += season.temperatureOffset();
        // Sections 43 and 47: the air cools going up and the rock warms going down.
        temperature -= Altitude.lapse(pos.getY());
        temperature += Altitude.geothermal(pos.getY());
        if (level.isDarkOutside()) temperature -= 4.0;
        else if (level.isBrightOutside() && RoomScan.openToSky(level, pos.above()) && !level.isRainingAt(pos)) {
            temperature += 3.0;
        }
        if (level.isRainingAt(pos)) temperature -= 3.0;
        return clamp(temperature, -35, 55);
    }

    /** Stable warm/neutral/cold grouping shared by every climate-driven subsystem. */
    public BiomeClimate biomeClimate(ServerLevel level, BlockPos pos) {
        return BiomeClimate.fromBaseTemperature(level.getBiome(pos).value().getBaseTemperature());
    }

    /**
     * Section 14 lists wind as a temperature input. Milestone 1 left it as a declared neutral value;
     * it is now derived from sky exposure, altitude and weather. 0 is still air, 1 a full gale.
     */
    public double wind(ServerLevel level, BlockPos pos) {
        if (!RoomScan.openToSky(level, pos)) return 0.05;
        double altitude = clamp((pos.getY() - 64) / 200.0, 0, 1);
        double weather = level.isThundering() ? 1.0 : level.isRaining() ? 0.6 : 0.25;
        return clamp(0.20 + 0.40 * altitude + 0.40 * weather, 0, 1);
    }

    // ---------------------------------------------------------------- simulation

    private void tickCells() {
        Map<String, ServerPlayer> witness = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            Tracked tracked = resolve(player);
            if (tracked.key == null) continue;
            witness.putIfAbsent(tracked.key, player);
        }
        for (var entry : witness.entrySet()) {
            EnvironmentCell cell = cells.get(entry.getKey());
            if (cell == null) continue;
            ServerLevel level = entry.getValue().level();
            updateCell(cell, level);
            save.setCellAtmosphere(cell.key, new CellAtmosphere(cell.temperature, cell.updatedTick));
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendSnapshot(player);
    }

    /** Section 18: the fires, the breath and the coal seams around every player let out their gas. */
    private void tickGasSources() {
        Map<ServerLevel, List<ServerPlayer>> byLevel = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            byLevel.computeIfAbsent(player.level(), level -> new ArrayList<>()).add(player);
        }
        byLevel.forEach((level, players) -> GasSources.pass(level, players, level.getRandom()));
    }

    private Tracked resolve(ServerPlayer player) {
        long now = scheduler.ticks();
        BlockPos pos = player.blockPosition();
        Tracked previous = tracking.get(player.getUUID());
        if (previous != null && now - previous.scannedTick < RESCAN_INTERVAL_TICKS
                && previous.scannedAt.distSqr(pos) <= RESCAN_DISTANCE_SQR) {
            return previous;
        }
        RoomScan scan = RoomScan.scan(player.level(), pos);
        String key = cellKey(player.level(), scan);
        if (key != null) {
            EnvironmentCell cell = cells.computeIfAbsent(key,
                    id -> new EnvironmentCell(id, save.cellAtmosphere(id), now));
            cell.applyGeometry(scan);
        }
        Tracked tracked = new Tracked(key, scan, now, pos.immutable());
        tracking.put(player.getUUID(), tracked);
        return tracked;
    }

    private void updateCell(EnvironmentCell cell, ServerLevel level) {
        long now = scheduler.ticks();
        double seconds = Math.min(MAX_CATCH_UP_SECONDS, Math.max(0, now - cell.updatedTick) / 20.0);
        cell.updatedTick = now;
        if (seconds <= 0) return;

        double combustion = combustionWeight(level, cell);
        double volume = Math.max(MIN_EFFECTIVE_VOLUME, cell.volume);
        double outdoor = outdoorTemperature(level, BlockPos.of(cell.origin));
        double heat = Math.min(MAX_ROOM_HEAT, combustion * COMBUSTION_HEAT / volume) * (0.4 + 0.6 * cell.insulation);
        double target = outdoor + heat;
        double relaxation = 1.0 - Math.pow(1.0 - TEMPERATURE_RELAXATION, seconds);
        cell.temperature = clamp(cell.temperature + (target - cell.temperature) * relaxation, -80, 1_200);
    }

    /** Re-reads only the burning positions the scan already found, never a fresh area scan. */
    private double combustionWeight(ServerLevel level, EnvironmentCell cell) {
        double weight = 0;
        for (BlockPos pos : cell.combustionSources) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!RoomScan.isCombustionSource(state)) continue;
            weight += flameWeight(state);
        }
        return weight;
    }

    /** How much one burning block warms the room it is in. */
    public static double flameWeight(BlockState state) {
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH) || state.is(Blocks.SOUL_TORCH)
                || state.is(Blocks.SOUL_WALL_TORCH) || state.is(net.minecraft.tags.BlockTags.CANDLES)
                || state.is(net.minecraft.tags.BlockTags.CANDLE_CAKES)) {
            return WEAK_FLAME_WEIGHT;
        }
        if (state.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock
                || state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)) {
            return CONTAINED_FLAME_WEIGHT;
        }
        if (RoomScan.isForgeFire(state)) return FORGE_FLAME_WEIGHT;
        return 1.0;
    }

    // ---------------------------------------------------------------- diagnostics

    /**
     * Supplies the gas and temperature channels the debug HUD has reported as unavailable since
     * Phase 1. A probe runs one bounded scan and never invents a measurement: a space that has not
     * been simulated yet says so instead of showing the outdoor baseline as if it were measured.
     */
    public void registerDiagnostics(DiagnosticRegistry registry) {
        registry.register("hardwrought:vertical_zone", DiagnosticRegistry.Channel.ENVIRONMENT, (level, pos) -> {
            int y = pos.getY();
            return String.format(Locale.ROOT,
                    "%s Y=%d (world %d..%d) | pressure=%.2f outside O2=%.2f%% | lapse=-%.1fC geothermal=+%.1fC gale=%.2f%s",
                    VerticalZone.at(y).serializedName(), y, level.getMinY(), level.getMaxY(),
                    Altitude.pressure(y), Altitude.outsideAir(y).oxygen() * 100,
                    Altitude.lapse(y), Altitude.geothermal(y), Altitude.gale(y),
                    Altitude.tooThinToBurn(y) ? " | too thin for an open fire" : "");
        });
        registry.register("hardwrought:atmosphere", DiagnosticRegistry.Channel.GAS, (level, pos) -> {
            BlockState state = level.getBlockState(pos);
            GasMixture air = Gases.sample(level, pos);
            return String.format(Locale.ROOT,
                    "gas CO2=%d CO=%d CH4=%d of %d | O2=%.2f%% CO2=%.2f%% CH4=%.2f%% CO=%.0fppm",
                    Gases.units(state, Gas.CARBON_DIOXIDE), Gases.units(state, Gas.CARBON_MONOXIDE),
                    Gases.units(state, Gas.METHANE), Gas.CAPACITY, air.oxygen() * 100, air.carbonDioxide() * 100,
                    air.methane() * 100, air.carbonMonoxide() * 1_000_000);
        });
        registry.register("hardwrought:room_temperature", DiagnosticRegistry.Channel.TEMPERATURE, (level, pos) -> {
            RoomScan scan = RoomScan.scan(level, pos);
            double outdoor = outdoorTemperature(level, pos);
            if (!scan.sealed()) {
                Season season = SeasonCycle.current(level);
                return String.format(Locale.ROOT, "outdoor=%.1fC wind=%.2f climate=%s season=%s day=%d (open air)",
                        outdoor, wind(level, pos), biomeClimate(level, pos).serializedName(),
                        season.serializedName(), SeasonCycle.dayInSeason(level.getOverworldClockTime()));
            }
            EnvironmentCell cell = cells.get(cellKey(level, scan));
            if (cell == null) {
                return String.format(Locale.ROOT, "outdoor=%.1fC room not simulated yet", outdoor);
            }
            return String.format(Locale.ROOT, "room=%.1fC outdoor=%.1fC fires=%.1f",
                    cell.temperature, outdoor, combustionWeight(level, cell));
        });
    }

    // ---------------------------------------------------------------- housekeeping

    public void disconnect(UUID id) {
        tracking.remove(id);
    }

    public int trackedCells() {
        return cells.size();
    }

    /**
     * A room is identified by where its fill closed, which is stable for the same space. A large
     * space has no such point, so it is identified by its chunk region instead — the third option
     * section 13 offers. Open air has no cell at all.
     */
    private static String cellKey(ServerLevel level, RoomScan scan) {
        return switch (scan.enclosure()) {
            case OPEN -> null;
            case ROOM -> level.dimension().identifier() + "@" + scan.origin().asLong();
            case LARGE -> level.dimension().identifier() + "#"
                    + SectionPos.blockToSectionCoord(scan.origin().getX()) + ","
                    + SectionPos.blockToSectionCoord(scan.origin().getY()) + ","
                    + SectionPos.blockToSectionCoord(scan.origin().getZ());
        };
    }

    private void sendSnapshot(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, EnvironmentSnapshotPayload.TYPE)) return;
        EnvironmentReading reading = reading(player);
        ServerPlayNetworking.send(player, new EnvironmentSnapshotPayload(reading.gases(),
                reading.temperature(), reading.wind(), reading.sealed(), reading.volume(),
                SafetyLampItem.carriedBy(player)));
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
