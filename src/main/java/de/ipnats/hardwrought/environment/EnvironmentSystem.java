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
 * a position is made of, how warm it is and how much wind reaches it, and it owns the hazards that
 * belong to the world rather than to a player: methane ignition and fires that run out of oxygen.
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

    // Balancing starting values, expressed per second and divided by the room volume in blocks.
    //
    // Calibrated against the leak rate below, which is what decides where a room settles:
    //   equilibrium oxygen = 0.209 - (rate / volume) / SEALED_VENTILATION
    // A sealed 30-block hut therefore settles just at the impaired threshold and stays survivable,
    // a 15-block chamber gets dangerous, and a coffin-sized 8-block box still kills. Breathing has
    // to be the slow part; a fire is what makes air disappear quickly.
    private static final double BREATH_OXYGEN = 0.005;
    private static final double BREATH_CARBON_DIOXIDE = 0.003;
    private static final double COMBUSTION_OXYGEN = 0.030;
    private static final double COMBUSTION_CARBON_DIOXIDE = 0.025;
    private static final double COMBUSTION_SMOKE = 0.22;
    private static final double COMBUSTION_HEAT = 800.0;
    private static final double MAX_ROOM_HEAT = 45.0;
    /**
     * Section 18.3: a deep, coal-bearing pocket fills slowly. A fully coal-lined 27-block room needs
     * roughly a minute of standing still to reach the flammability window, bare rock several times
     * that. It has to be a hazard a player can walk away from, not an instant bomb.
     */
    private static final double METHANE_SEEP = 0.020;
    private static final double SEALED_VENTILATION = 0.004;
    /**
     * Section 18: ventilation is a matter of degree, not something a room either has or has not. A
     * grille, a fence or an open door bounds the room and still exchanges air, so the opening area
     * raises the exchange rate relative to the volume behind it. Calibrated so that one open door
     * turns over the air of an ordinary room in well under a minute: with the door open there is
     * nothing in the way, and the room simply breathes with the outside.
     */
    private static final double APERTURE_VENTILATION = 1.20;
    private static final double TEMPERATURE_RELAXATION = 0.10;
    private static final int MIN_EFFECTIVE_VOLUME = 8;
    /** Section 19: a flame needs oxygen. Below this the fires in the room go out. */
    public static final double FIRE_MINIMUM_OXYGEN = 0.130;
    /**
     * Not every flame eats the same amount of air. An open fire or lava is the full measure; a
     * campfire or a furnace carries its own smoke column upward and burns contained, so it takes
     * much less; a torch or a candle barely counts.
     */
    private static final double WEAK_FLAME_WEIGHT = 0.15;
    private static final double CONTAINED_FLAME_WEIGHT = 0.40;
    /** How much rock has to sit above a room before it counts as a deep cave at all. */
    private static final int METHANE_MIN_COVER = 24;
    private static final int METHANE_FULL_COVER = 84;

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
    }

    private record Tracked(String key, RoomScan scan, long scannedTick, BlockPos scannedAt) { }

    /** One enclosed space. Geometry comes from a scan, the atmosphere is carried over time. */
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
        private GasMixture gases;
        private double temperature;
        private long updatedTick;

        private EnvironmentCell(String key, CellAtmosphere saved, long tick) {
            this.key = key;
            this.gases = saved == null ? GasMixture.OUTDOOR : saved.gases();
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

        private double heightFraction(double y) {
            int height = ceilingY - floorY;
            if (height <= 0) return 0.5;
            return Math.max(0, Math.min(1, (y - floorY) / height));
        }
    }

    /**
     * Where the oxygen of a sealed space of this size settles with this many occupants breathing and
     * nothing burning. Not clamped, so a value below zero means the space simply runs out of air.
     * This is the relationship the breathing rates are calibrated against and the tests assert.
     */
    public static double ventilationRate(int volumeBlocks, double apertureArea) {
        double volume = Math.max(MIN_EFFECTIVE_VOLUME, volumeBlocks);
        return Math.min(1.0, SEALED_VENTILATION + APERTURE_VENTILATION * apertureArea / volume);
    }

    public static double equilibriumOxygen(int volumeBlocks, int occupants, double apertureArea,
                                           double combustion) {
        double volume = Math.max(MIN_EFFECTIVE_VOLUME, volumeBlocks);
        double use = (occupants * BREATH_OXYGEN + combustion * COMBUSTION_OXYGEN) / volume;
        return GasMixture.OUTDOOR_OXYGEN - use / ventilationRate(volumeBlocks, apertureArea);
    }

    public static double equilibriumOxygen(int volumeBlocks, int occupants) {
        return equilibriumOxygen(volumeBlocks, occupants, 0, 0);
    }

    /** The same relationship for carbon dioxide, which is what usually becomes dangerous first. */
    public static double equilibriumCarbonDioxide(int volumeBlocks, int occupants, double apertureArea,
                                                  double combustion) {
        double volume = Math.max(MIN_EFFECTIVE_VOLUME, volumeBlocks);
        double produced = (occupants * BREATH_CARBON_DIOXIDE + combustion * COMBUSTION_CARBON_DIOXIDE) / volume;
        return GasMixture.OUTDOOR_CARBON_DIOXIDE + produced / ventilationRate(volumeBlocks, apertureArea);
    }

    public static double equilibriumCarbonDioxide(int volumeBlocks, int occupants) {
        return equilibriumCarbonDioxide(volumeBlocks, occupants, 0, 0);
    }

    // ---------------------------------------------------------------- public reading

    /** The typed environment at a player's position. Never null, never a guessed value. */
    public EnvironmentReading reading(ServerPlayer player) {
        Tracked tracked = resolve(player);
        double wind = wind(player.level(), player.blockPosition());
        if (tracked.key == null) {
            return EnvironmentReading.outdoor(outdoorTemperature(player.level(), player.blockPosition()), wind);
        }
        EnvironmentCell cell = cells.get(tracked.key);
        if (cell == null) {
            return EnvironmentReading.outdoor(outdoorTemperature(player.level(), player.blockPosition()), wind);
        }
        // A sealed room shields its occupants from the wind outside it. The air is sampled at the
        // player's own head height, because the heavy and light gases do not sit evenly.
        GasMixture local = cell.gases.at(cell.heightFraction(player.getEyePosition().y));
        return new EnvironmentReading(local, cell.temperature, wind * 0.1, true, cell.volume, cell.insulation);
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
        temperature -= Math.max(0, pos.getY() - 64) * 0.0065;
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
        Map<String, Integer> occupants = new HashMap<>();
        Map<String, ServerPlayer> witness = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            Tracked tracked = resolve(player);
            if (tracked.key == null) continue;
            occupants.merge(tracked.key, 1, Integer::sum);
            witness.putIfAbsent(tracked.key, player);
        }
        for (var entry : witness.entrySet()) {
            EnvironmentCell cell = cells.get(entry.getKey());
            if (cell == null) continue;
            ServerLevel level = entry.getValue().level();
            updateCell(cell, level, occupants.getOrDefault(entry.getKey(), 0));
            applyHazards(cell, level);
            save.setCellAtmosphere(cell.key, new CellAtmosphere(cell.gases, cell.temperature, cell.updatedTick));
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendSnapshot(player);
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

    private void updateCell(EnvironmentCell cell, ServerLevel level, int occupants) {
        long now = scheduler.ticks();
        double seconds = Math.min(MAX_CATCH_UP_SECONDS, Math.max(0, now - cell.updatedTick) / 20.0);
        cell.updatedTick = now;
        if (seconds <= 0) return;

        double combustion = combustionWeight(level, cell);
        double volume = Math.max(MIN_EFFECTIVE_VOLUME, cell.volume);
        double oxygenUse = (occupants * BREATH_OXYGEN + combustion * COMBUSTION_OXYGEN) / volume * seconds;
        double carbonDioxide = (occupants * BREATH_CARBON_DIOXIDE + combustion * COMBUSTION_CARBON_DIOXIDE)
                / volume * seconds;
        double smoke = combustion * COMBUSTION_SMOKE / volume * seconds;
        double methane = methaneSeep(level, cell) / volume * seconds;

        GasMixture next = cell.gases.add(-oxygenUse, carbonDioxide, methane, smoke);
        double ventilation = 1.0 - Math.pow(1.0 - ventilationRate(cell.volume, cell.apertureArea), seconds);
        cell.gases = next.ventilate(ventilation);

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

    /**
     * Depth is measured as the rock actually covering the room, not as an absolute height, so the
     * model behaves the same in a normal world, a superflat one and any later vertical world.
     */
    private double methaneSeep(ServerLevel level, EnvironmentCell cell) {
        BlockPos origin = BlockPos.of(cell.origin);
        if (!level.hasChunkAt(origin)) return 0;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, origin.getX(), origin.getZ());
        int cover = surface - origin.getY();
        if (cover <= METHANE_MIN_COVER) return 0;
        double depth = clamp((cover - METHANE_MIN_COVER) / (double) (METHANE_FULL_COVER - METHANE_MIN_COVER), 0, 1);
        return METHANE_SEEP * depth * (0.25 + 0.75 * cell.coalExposure);
    }

    /** Section 18.3 and 19: the two hazards that belong to the world, not to a single player. */
    private void applyHazards(EnvironmentCell cell, ServerLevel level) {
        // Firedamp gathers against the roof, so a flame high up sets it off while one on the floor
        // may sit below it entirely. That is the whole reason a safety lamp is held up.
        BlockPos flame = null;
        for (BlockPos pos : cell.combustionSources) {
            if (!level.hasChunkAt(pos)) continue;
            if (!RoomScan.isCombustionSource(level.getBlockState(pos))) continue;
            if (cell.gases.at(cell.heightFraction(pos.getY() + 0.5)).explosive()) {
                flame = pos;
                break;
            }
        }
        if (flame != null) {
            ignite(cell, level, flame);
            return;
        }
        if (cell.gases.oxygen() < FIRE_MINIMUM_OXYGEN) extinguish(cell, level);
    }

    private void ignite(EnvironmentCell cell, ServerLevel level, BlockPos at) {
        double local = cell.gases.at(cell.heightFraction(at.getY() + 0.5)).methane();
        double excess = (local - GasMixture.METHANE_EXPLOSIVE_MIN)
                / (GasMixture.METHANE_EXPLOSIVE_MAX - GasMixture.METHANE_EXPLOSIVE_MIN);
        float power = (float) (2.0 + 4.0 * clamp(excess, 0, 1));
        level.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, power,
                Level.ExplosionInteraction.BLOCK);
        // The blast burns the gas and the oxygen it used; smoke is what stays behind.
        cell.gases = new GasMixture(cell.gases.oxygen() * 0.55, cell.gases.carbonDioxide() + 0.01,
                0, Math.min(1, cell.gases.smoke() + 0.5));
    }

    private void extinguish(EnvironmentCell cell, ServerLevel level) {
        List<BlockPos> remaining = new ArrayList<>();
        for (BlockPos pos : cell.combustionSources) {
            if (!level.hasChunkAt(pos)) {
                remaining.add(pos);
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.0f);
                continue;
            }
            if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)
                    && (state.getBlock() instanceof CampfireBlock || state.is(Blocks.FURNACE)
                    || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER))) {
                level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, false));
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.0f);
                continue;
            }
            remaining.add(pos);
        }
        cell.combustionSources = List.copyOf(remaining);
    }

    /**
     * How much air one burning block takes.
     *
     * <p>Vanilla has no unlit torch, so a torch is never taken away from a player here. It keeps
     * burning and keeps consuming air; a light source that can go out belongs to the lighting
     * progression of section 20.
     */
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
        return 1.0;
    }

    /**
     * Replaces the atmosphere of the sealed space a player is standing in. Operator tooling for
     * balancing and testing a simulation whose interesting states otherwise take minutes to reach.
     * Returns false when the player is not in a sealed space, because open air has no cell to set.
     */
    public boolean overrideAtmosphere(ServerPlayer player, GasMixture gases) {
        Tracked tracked = resolve(player);
        if (tracked.key == null) return false;
        EnvironmentCell cell = cells.get(tracked.key);
        if (cell == null) return false;
        cell.gases = gases;
        cell.updatedTick = scheduler.ticks();
        save.setCellAtmosphere(cell.key, new CellAtmosphere(cell.gases, cell.temperature, cell.updatedTick));
        sendSnapshot(player);
        return true;
    }

    // ---------------------------------------------------------------- diagnostics

    /**
     * Supplies the gas and temperature channels the debug HUD has reported as unavailable since
     * Phase 1. A probe runs one bounded scan and never invents a measurement: a space that has not
     * been simulated yet says so instead of showing the outdoor baseline as if it were measured.
     */
    public void registerDiagnostics(DiagnosticRegistry registry) {
        registry.register("hardwrought:atmosphere", DiagnosticRegistry.Channel.GAS, (level, pos) -> {
            RoomScan scan = RoomScan.scan(level, pos);
            if (!scan.sealed()) return "open air, outside baseline applies";
            EnvironmentCell cell = cells.get(cellKey(level, scan));
            if (cell == null) {
                return String.format(Locale.ROOT, "%s %d blocks, not simulated yet",
                        scan.enclosure() == RoomScan.Enclosure.LARGE ? "large space" : "enclosed",
                        scan.volume());
            }
            return String.format(Locale.ROOT,
                    "O2=%.2f%% CO2=%.3f%% CH4=%.3f%% smoke=%.2f volume=%d insulation=%.2f",
                    cell.gases.oxygen() * 100, cell.gases.carbonDioxide() * 100,
                    cell.gases.methane() * 100, cell.gases.smoke(), cell.volume, cell.insulation);
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
