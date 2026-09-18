package de.ipnats.hardwrought.test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.networking.EnvironmentSnapshotPayload;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.core.save.CoreSaveData;
import de.ipnats.hardwrought.environment.CellAtmosphere;
import de.ipnats.hardwrought.environment.GasMixture;
import de.ipnats.hardwrought.environment.EnvironmentSystem;
import de.ipnats.hardwrought.environment.RoomScan;
import de.ipnats.hardwrought.core.simulation.SimulationScheduler;
import de.ipnats.hardwrought.core.simulation.SimulationTier;
import de.ipnats.hardwrought.survival.SurvivalSystem;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class EnvironmentGameTests {
    private static final ResourceKey<DamageType> BAD_AIR =
            ResourceKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("bad_air"));
    private static final ResourceKey<DamageType> SMOKE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("smoke"));
    /** Built clear of the test structure so the scan never meets the test blocks themselves. */
    private static final int WORKSPACE_OFFSET = 24;

    @GameTest
    public void gasMixtureStaysBoundedAndMonotone(GameTestHelper helper) {
        var outdoor = GasMixture.OUTDOOR;
        helper.assertTrue(outdoor.breathable(), "Outside air is breathable");
        helper.assertTrue(outdoor.oxygenStress() == 0 && outdoor.carbonDioxideStress() == 0,
                "Outside air puts no stress on anyone");
        var spent = new GasMixture(0.10, 0.05, 0, 0);
        helper.assertFalse(spent.breathable(), "Spent air is not breathable");
        helper.assertTrue(spent.oxygenStress() > 0 && spent.oxygenStress() < 1,
                "Stress rises between the impaired and the lethal threshold");
        helper.assertTrue(new GasMixture(0.0, 0, 0, 0).oxygenStress() == 1.0, "Stress is capped at one");

        var clamped = new GasMixture(99, -4, Double.NaN, 7);
        helper.assertTrue(clamped.oxygen() == GasMixture.MAX_OXYGEN && clamped.carbonDioxide() == 0
                        && clamped.methane() == 0 && clamped.smoke() == 1,
                "Every operation stays inside the declared bounds");
        helper.assertTrue(GasMixture.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                        "{\"oxygen\":0.9,\"carbon_dioxide\":0,\"methane\":0,\"smoke\":0}")).error().isPresent(),
                "A saved value outside the bounds is rejected rather than clamped silently");
        helper.succeed();
    }

    @GameTest
    public void ventilationConvergesOnOutsideAir(GameTestHelper helper) {
        var spent = new GasMixture(0.09, 0.06, 0.02, 0.8);
        var once = spent.ventilate(0.5);
        helper.assertTrue(once.oxygen() > spent.oxygen() && once.carbonDioxide() < spent.carbonDioxide()
                        && once.methane() < spent.methane() && once.smoke() < spent.smoke(),
                "Ventilation moves every value toward the outside air");
        var mixture = spent;
        for (int step = 0; step < 200; step++) mixture = mixture.ventilate(0.5);
        helper.assertTrue(Math.abs(mixture.oxygen() - GasMixture.OUTDOOR_OXYGEN) < 1.0E-6
                        && mixture.smoke() < 1.0E-6,
                "Repeated ventilation converges on the outside baseline");
        helper.assertTrue(spent.ventilate(0).equals(spent), "No ventilation changes nothing");
        helper.succeed();
    }

    @GameTest
    public void methaneIsOnlyExplosiveInsideItsWindow(GameTestHelper helper) {
        helper.assertFalse(new GasMixture(0.2, 0, 0.02, 0).explosive(), "Too lean to ignite");
        helper.assertTrue(new GasMixture(0.2, 0, 0.08, 0).explosive(), "Inside the flammability window");
        helper.assertFalse(new GasMixture(0.2, 0, 0.20, 0).explosive(), "Too rich to ignite");
        helper.succeed();
    }

    @GameTest
    public void roomScanSeparatesSealedSpaceFromOpenAir(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = workspace(helper);
        try {
            buildShell(level, base, Blocks.STONE.defaultBlockState());
            RoomScan sealed = RoomScan.scan(level, base);
            helper.assertTrue(sealed.sealed(), "A closed shell is a sealed space");
            helper.assertTrue(sealed.volume() == 27, "A 5x5x5 shell encloses 27 blocks");
            helper.assertTrue(sealed.insulation() > 0.4 && sealed.insulation() < 0.6,
                    "Stone walls insulate moderately");
            helper.assertTrue(sealed.combustionSources().isEmpty(), "Nothing is burning in an empty room");

            // Opening the roof must be enough to turn it back into open air.
            level.setBlockAndUpdate(base.above(2), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(base.above(3), Blocks.AIR.defaultBlockState());
            RoomScan opened = RoomScan.scan(level, base);
            helper.assertFalse(opened.sealed(), "A space open to the sky is not a sealed room");
            helper.assertTrue(opened.combustionSources().isEmpty() && opened.volume() == RoomScan.MAX_VOLUME,
                    "Open air reports the cell budget instead of a measured volume");
        } finally {
            clearShell(level, base);
        }
        helper.succeed();
    }

    @GameTest
    public void roomScanFindsFireAndCoalInTheWalls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = workspace(helper);
        try {
            buildShell(level, base, Blocks.COAL_ORE.defaultBlockState());
            level.setBlockAndUpdate(base.above(), Blocks.TORCH.defaultBlockState());
            RoomScan scan = RoomScan.scan(level, base);
            helper.assertTrue(scan.sealed(), "The shell is still closed");
            helper.assertTrue(scan.combustionSources().contains(base.above()),
                    "A burning block inside the room is found by the scan");
            helper.assertTrue(scan.coalExposure() > 0.9,
                    "Section 18.3: exposed coal in the walls is what methane is tied to for now");
            helper.assertTrue(RoomScan.isCombustionSource(Blocks.FIRE.defaultBlockState()), "Fire burns");
            helper.assertFalse(RoomScan.isCombustionSource(Blocks.STONE.defaultBlockState()), "Stone does not");
        } finally {
            clearShell(level, base);
        }
        helper.succeed();
    }

    @GameTest
    public void insulationFollowsTheSpecificationOrdering(GameTestHelper helper) {
        double wool = RoomScan.insulationOf(
                Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState());
        double planks = RoomScan.insulationOf(Blocks.OAK_PLANKS.defaultBlockState());
        double earth = RoomScan.insulationOf(Blocks.DIRT.defaultBlockState());
        double stone = RoomScan.insulationOf(Blocks.STONE.defaultBlockState());
        double glass = RoomScan.insulationOf(Blocks.GLASS.defaultBlockState());
        double metal = RoomScan.insulationOf(Blocks.IRON_BLOCK.defaultBlockState());
        helper.assertTrue(wool > planks, "Section 17: wool insulates better than wood");
        helper.assertTrue(planks >= earth && earth > stone, "Wood and earth beat stone");
        helper.assertTrue(stone > glass && glass > metal, "Stone beats glass, glass beats metal");
        helper.succeed();
    }

    @GameTest
    public void cellAtmospheresPersistAndStayBounded(GameTestHelper helper) {
        var data = CoreSaveData.TYPE.constructor().get();
        helper.assertTrue(data.cellAtmosphere("minecraft:overworld@1") == null,
                "A room with no history reports nothing rather than a guessed value");
        var stale = new CellAtmosphere(new GasMixture(0.12, 0.03, 0.01, 0.2), 24.5, 100);
        data.setCellAtmosphere("minecraft:overworld@1", stale);
        var encoded = CoreSaveData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        var decoded = CoreSaveData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        helper.assertTrue(stale.equals(decoded.cellAtmosphere("minecraft:overworld@1")),
                "A room keeps the air it built up across a restart");

        for (int index = 0; index < CoreSaveData.MAX_SAVED_CELLS + 40; index++) {
            data.setCellAtmosphere("minecraft:overworld@" + index,
                    new CellAtmosphere(GasMixture.OUTDOOR, 15, 200 + index));
        }
        helper.assertTrue(data.savedCellCount() <= CoreSaveData.MAX_SAVED_CELLS,
                "The saved room table is bounded rather than growing forever");
        helper.assertTrue(data.cellAtmosphere("minecraft:overworld@1") == null,
                "The room unvisited longest is the one that is forgotten");
        expectFailure(() -> new CellAtmosphere(null, 0, 0));
        expectFailure(() -> new CellAtmosphere(GasMixture.OUTDOOR, Double.NaN, 0));
        helper.succeed();
    }

    @GameTest
    public void gasDiagnosticsAreNowSupplied(GameTestHelper helper) {
        var runtime = CoreLifecycle.require(helper.getLevel().getServer());
        var lines = runtime.snapshot(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        helper.assertTrue(lines.stream().noneMatch(line -> line.startsWith("GAS | unavailable")),
                "The gas channel has a real provider once the environment model exists");
        helper.assertTrue(lines.stream().anyMatch(line -> line.startsWith("GAS | ")
                        && (line.contains("open air") || line.contains("O2=") || line.contains("not simulated yet"))),
                "The gas channel reports measured air, an open space, or that it has not simulated one yet");
        helper.assertTrue(lines.stream().anyMatch(line -> line.startsWith("TEMPERATURE | ")
                        && (line.contains("wind=") || line.contains("room=") || line.contains("outdoor="))),
                "The temperature channel reports the room model");
        helper.assertTrue(lines.stream().anyMatch(line -> line.startsWith("STRUCTURE | unavailable")),
                "Systems that still do not exist keep saying so");
        helper.succeed();
    }

    @GameTest
    public void gasDamageTypesAreRegisteredAndBypassArmor(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        var badAir = registry.get(BAD_AIR).orElse(null);
        var smoke = registry.get(SMOKE).orElse(null);
        helper.assertTrue(badAir != null && smoke != null,
                "Section 18 hazards have their own damage types, so a death message names the cause");
        helper.assertTrue(badAir.is(DamageTypeTags.BYPASSES_ARMOR) && smoke.is(DamageTypeTags.BYPASSES_ARMOR),
                "Armor cannot keep a gas out");
        helper.succeed();
    }

    @GameTest
    public void missingWeaponClassesNowHaveItemsAndProfiles(GameTestHelper helper) {
        var runtime = CoreLifecycle.require(helper.getLevel().getServer());
        var weapons = runtime.weaponProfiles();
        var dagger = weapons.get(Hardwrought.id("iron_dagger"));
        var greatsword = weapons.get(Hardwrought.id("iron_greatsword"));
        var halberd = weapons.get(Hardwrought.id("iron_halberd"));
        helper.assertTrue(dagger != null && greatsword != null && halberd != null,
                "The knife, two-handed and polearm classes of section 30 now have items");
        helper.assertTrue(weapons.get(Hardwrought.id("flint_dagger")) != null, "The primitive knife exists too");
        helper.assertTrue(dagger.staminaCost() < greatsword.staminaCost(),
                "Section 30: a dagger costs very little stamina, a two-hander a lot");
        helper.assertTrue(greatsword.impact() > dagger.impact(), "A two-hander breaks guards, a dagger does not");
        helper.assertTrue(halberd.reachBonusBlocks() > greatsword.reachBonusBlocks()
                        && halberd.minimumReachBlocks() > 0,
                "A polearm reaches furthest and is weak at close quarters");
        helper.assertTrue(runtime.itemWeights().get(Hardwrought.id("iron_halberd")) == 3.6,
                "The new equipment has carried mass");
        helper.assertTrue(ModItems.SAFETY_LAMP != null && ModItems.IRON_HALBERD != null, "Items are registered");
        helper.succeed();
    }

    @GameTest
    public void environmentProtocolRoundTrip(GameTestHelper helper) {
        var payload = new EnvironmentSnapshotPayload(new GasMixture(0.17, 0.02, 0.03, 0.4),
                21.5, 0.4, true, 96, true);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            EnvironmentSnapshotPayload.CODEC.encode(buffer, payload);
            helper.assertTrue(payload.equals(EnvironmentSnapshotPayload.CODEC.decode(buffer)),
                    "The air report must round trip without client-side reconstruction");
        } finally {
            buffer.release();
        }
        expectFailure(() -> new EnvironmentSnapshotPayload(GasMixture.OUTDOOR, Double.NaN, 0, false, 0, false));
        expectFailure(() -> new EnvironmentSnapshotPayload(GasMixture.OUTDOOR, 20, 5, false, 0, false));
        expectFailure(() -> new EnvironmentSnapshotPayload(null, 20, 0, false, 0, false));
        helper.succeed();
    }

    @GameTest
    public void environmentJobIsRegistered(GameTestHelper helper) {
        var scheduler = CoreLifecycle.require(helper.getLevel().getServer()).scheduler();
        helper.assertTrue(scheduler.profiles().stream()
                        .anyMatch(profile -> profile.id().equals("hardwrought:environment_cells")
                                && !profile.disabled()),
                "The environment runs as a registered simulation job and has not failed");
        helper.succeed();
    }

    @GameTest
    public void savedClockIsNotAPhaseSourceInsideASimulationPass(GameTestHelper helper) {
        // The saved clock is only written after the scheduler has finished, so inside a pass it is
        // always one tick behind. Two periodic hazards were gated on it and could never fire.
        var save = CoreSaveData.TYPE.constructor().get();
        var scheduler = new SimulationScheduler(0, System::nanoTime, (id, error) -> { });
        long[] seenInsidePass = {-1};
        scheduler.register("probe", SimulationTier.MEDIUM, () -> seenInsidePass[0] = save.ticks());
        for (int tick = 0; tick < 40; tick++) {
            scheduler.tick();
            save.setTicks(scheduler.ticks());
        }
        helper.assertTrue(seenInsidePass[0] == 39,
                "Inside a medium pass the saved clock is one tick behind the scheduler");
        helper.assertTrue(seenInsidePass[0] % 40 != 0 && seenInsidePass[0] % 200 != 0,
                "Gating a periodic hazard on the saved clock therefore never fires; count passes instead");
        helper.succeed();
    }

    @GameTest
    public void fatigueLeavesRoomForAFullDayAndNight(GameTestHelper helper) {
        double dayAndNightSeconds = 24_000 / 20.0;
        double awake = SurvivalSystem.FATIGUE_PER_SECOND_AWAKE * dayAndNightSeconds;
        helper.assertTrue(awake < 100,
                "Staying awake through one full day and night must never exhaust the bar");
        helper.assertTrue(awake <= 35,
                "and it must leave clear headroom, so sleep stays a choice rather than a timer");
        double worstAir = (SurvivalSystem.FATIGUE_PER_SECOND_AWAKE
                + SurvivalSystem.FATIGUE_PER_SECOND_CARBON_DIOXIDE) * dayAndNightSeconds;
        helper.assertTrue(worstAir < 100,
                "Even the worst possible air must not fill the bar inside a single cycle");
        helper.assertTrue(SurvivalSystem.FATIGUE_PER_SECOND_CARBON_DIOXIDE
                        <= 2 * SurvivalSystem.FATIGUE_PER_SECOND_AWAKE,
                "Bad air is tiring, but it must stay the same order of magnitude as normal waking");
        helper.succeed();
    }

    @GameTest
    public void lethalAirIsRecognisedAtTheDeclaredThresholds(GameTestHelper helper) {
        helper.assertTrue(new GasMixture(0.0008, 0.1494, 0, 0).oxygenStress() == 1.0,
                "Air with almost no oxygen left is at full stress");
        helper.assertTrue(new GasMixture(0.0008, 0.1494, 0, 0).oxygen() < GasMixture.OXYGEN_LETHAL,
                "and is below the lethal oxygen threshold");
        helper.assertTrue(new GasMixture(0.0008, 0.1494, 0, 0).carbonDioxide()
                        > GasMixture.CARBON_DIOXIDE_LETHAL,
                "and above the lethal carbon dioxide threshold");
        helper.assertFalse(new GasMixture(0.0008, 0.1494, 0, 0).breathable(), "Such air is not breathable");
        helper.succeed();
    }

    @GameTest
    public void anOpeningVentilatesTheRoomInsteadOfDissolvingIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = workspace(helper);
        BlockPos wall = base.offset(2, 0, 0);
        try {
            buildShell(level, base, Blocks.STONE.defaultBlockState());
            RoomScan closed = RoomScan.scan(level, base);
            helper.assertTrue(closed.enclosure() == RoomScan.Enclosure.ROOM && closed.apertureArea() == 0,
                    "A closed shell is a room with nothing to exchange air through");

            // A closed trapdoor still seals, even though its collision box is a thin slab.
            level.setBlockAndUpdate(wall, Blocks.OAK_TRAPDOOR.defaultBlockState()
                    .setValue(BlockStateProperties.OPEN, false));
            helper.assertTrue(RoomScan.scan(level, base).apertureArea() == 0,
                    "A closed trapdoor keeps the room shut");

            // Opening it must not dissolve the room, and it is a whole opening, not a partial one.
            level.setBlockAndUpdate(wall, Blocks.OAK_TRAPDOOR.defaultBlockState()
                    .setValue(BlockStateProperties.OPEN, true));
            RoomScan opened = RoomScan.scan(level, base);
            helper.assertTrue(opened.enclosure() == RoomScan.Enclosure.ROOM,
                    "An open trapdoor is still a wall: the space stays a room");
            helper.assertTrue(opened.apertureArea() == 1.0 && opened.volume() == closed.volume(),
                    "It counts as a whole opening and the room keeps its size");

            // A grille bounds the room too, but less of it is actually gap.
            level.setBlockAndUpdate(wall, Blocks.OAK_FENCE.defaultBlockState());
            RoomScan fenced = RoomScan.scan(level, base);
            helper.assertTrue(fenced.enclosure() == RoomScan.Enclosure.ROOM
                            && fenced.apertureArea() > 0 && fenced.apertureArea() < 1.0,
                    "A fence lets gas through, but not as freely as an open doorway");

            // A pane window is glass: it holds the air in like any other glass.
            level.setBlockAndUpdate(wall, Blocks.GLASS_PANE.defaultBlockState());
            RoomScan glazed = RoomScan.scan(level, base);
            helper.assertTrue(glazed.enclosure() == RoomScan.Enclosure.ROOM && glazed.apertureArea() == 0,
                    "A pane window keeps the room shut, because glass is glass");

            // A real hole is different again: there the space simply is outdoors.
            level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            helper.assertTrue(RoomScan.scan(level, base).enclosure() == RoomScan.Enclosure.OPEN,
                    "A missing block is a hole, and behind it is the open sky");
        } finally {
            clearShell(level, base);
        }
        helper.succeed();
    }

    @GameTest
    public void wallsAreJudgedPerFaceAndPerBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = workspace(helper);
        helper.assertTrue(RoomScan.blocksFace(level, pos, Blocks.STONE.defaultBlockState(), Direction.NORTH)
                        && RoomScan.blocksFace(level, pos, Blocks.STONE.defaultBlockState(), Direction.UP),
                "A full block closes every face");
        helper.assertTrue(RoomScan.blocksFace(level, pos, Blocks.GLASS.defaultBlockState(), Direction.NORTH),
                "Solid glass closes a face just as well");
        helper.assertTrue(RoomScan.blocksFace(level, pos, Blocks.GLASS_PANE.defaultBlockState(), Direction.NORTH),
                "and so does a pane, which never fills a block but is still glass");
        helper.assertFalse(RoomScan.blocksFace(level, pos, Blocks.AIR.defaultBlockState(), Direction.NORTH),
                "Air closes nothing");
        helper.assertFalse(RoomScan.blocksFace(level, pos, Blocks.TORCH.defaultBlockState(), Direction.NORTH),
                "A torch is part of the room, not a wall of it");

        // A bottom slab is a ceiling seen from below, but it is not a wall.
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState();
        helper.assertTrue(RoomScan.blocksFace(level, pos, slab, Direction.DOWN),
                "A bottom slab seals the face it lies against");
        helper.assertFalse(RoomScan.blocksFace(level, pos, slab, Direction.UP),
                "and leaves the space above it open");

        BlockState openTrapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.OPEN, true);
        BlockState shutTrapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.OPEN, false);
        helper.assertTrue(RoomScan.blocksFace(level, pos, shutTrapdoor, Direction.NORTH),
                "A closed trapdoor seals despite its thin collision box");
        helper.assertFalse(RoomScan.blocksFace(level, pos, openTrapdoor, Direction.NORTH),
                "and an open one does not, despite the very same box");
        helper.succeed();
    }

    @GameTest
    public void openingsAreWeightedByWhatTheyActuallyLetThrough(GameTestHelper helper) {
        double door = RoomScan.apertureWeight(Blocks.OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.OPEN, true));
        double bars = RoomScan.apertureWeight(Blocks.IRON_BARS.defaultBlockState());
        double fence = RoomScan.apertureWeight(Blocks.OAK_FENCE.defaultBlockState());
        double wall = RoomScan.apertureWeight(Blocks.COBBLESTONE_WALL.defaultBlockState());

        helper.assertTrue(door == 1.0,
                "An open door is not a partial obstruction: the leaf swings aside and a doorway is left");
        helper.assertTrue(door > bars && bars > fence && fence > wall && wall > 0,
                "A grille lets less through than a doorway, and a decorative wall less again");
        helper.assertTrue(RoomScan.apertureWeight(Blocks.OAK_DOOR.defaultBlockState()
                        .setValue(BlockStateProperties.OPEN, false)) == 0,
                "A closed door lets nothing through");
        helper.assertTrue(RoomScan.apertureWeight(Blocks.GLASS_PANE.defaultBlockState()) == 0
                        && RoomScan.apertureWeight(Blocks.STONE.defaultBlockState()) == 0,
                "and neither does a pane window or a full block");
        helper.succeed();
    }

    @GameTest
    public void apertureVentilationKeepsARoomLiveable(GameTestHelper helper) {
        double openDoor = RoomScan.apertureWeight(Blocks.OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.OPEN, true));
        helper.assertTrue(EnvironmentSystem.ventilationRate(27, openDoor)
                        > EnvironmentSystem.ventilationRate(27, 0),
                "An opening raises how fast a room exchanges air");
        helper.assertTrue(EnvironmentSystem.ventilationRate(512, openDoor)
                        < EnvironmentSystem.ventilationRate(27, openDoor),
                "while the same opening does less for a far larger space");

        helper.assertTrue(EnvironmentSystem.equilibriumOxygen(8, 1, 0, 0) < GasMixture.OXYGEN_LETHAL
                        && EnvironmentSystem.equilibriumCarbonDioxide(8, 1, 0, 0)
                        > GasMixture.CARBON_DIOXIDE_LETHAL,
                "A shut box of 8 blocks is lethal on both counts");

        // With the door open there is nothing in the way: the room breathes with the outside.
        helper.assertTrue(GasMixture.OUTDOOR_OXYGEN
                        - EnvironmentSystem.equilibriumOxygen(30, 1, openDoor, 0) < 0.005,
                "An open door leaves an ordinary room within half a percent of outside air");
        helper.assertTrue(GasMixture.OUTDOOR_OXYGEN
                        - EnvironmentSystem.equilibriumOxygen(8, 1, openDoor, 0) < 0.005,
                "and does the same for the smallest one");
        helper.assertTrue(EnvironmentSystem.equilibriumCarbonDioxide(30, 1, openDoor, 0)
                        < GasMixture.CARBON_DIOXIDE_NOTICEABLE,
                "so its carbon dioxide never even becomes noticeable");

        // Section 19: a fire indoors has to be ventilated, not merely enclosed.
        double campfire = EnvironmentSystem.flameWeight(Blocks.CAMPFIRE.defaultBlockState()
                .setValue(BlockStateProperties.LIT, true));
        helper.assertTrue(EnvironmentSystem.equilibriumOxygen(30, 1, 0, campfire)
                        < EnvironmentSystem.FIRE_MINIMUM_OXYGEN,
                "A campfire in a shut room uses the air up until it goes out");
        helper.assertTrue(EnvironmentSystem.equilibriumOxygen(30, 1, openDoor, campfire)
                        > EnvironmentSystem.FIRE_MINIMUM_OXYGEN,
                "With the door open it keeps burning");
        helper.assertTrue(campfire < EnvironmentSystem.flameWeight(Blocks.FIRE.defaultBlockState()),
                "A contained fire burns less air than an open one");
        helper.assertTrue(EnvironmentSystem.flameWeight(Blocks.TORCH.defaultBlockState()) < campfire,
                "and a torch less again");
        helper.succeed();
    }

    @GameTest
    public void gasesLayerByTheirWeight(GameTestHelper helper) {
        var mixture = new GasMixture(0.20, 0.04, 0.04, 0.4);
        var floor = mixture.at(0);
        var ceiling = mixture.at(1);
        helper.assertTrue(floor.carbonDioxide() > ceiling.carbonDioxide(),
                "Carbon dioxide is heavier than air and pools in the low places");
        helper.assertTrue(ceiling.methane() > floor.methane(),
                "Methane is lighter than air and gathers against the roof");
        helper.assertTrue(ceiling.smoke() > floor.smoke(), "Smoke rises with the heat that makes it");
        helper.assertTrue(floor.oxygen() == ceiling.oxygen(),
                "Oxygen is close enough to air to stay evenly mixed");
        helper.assertTrue(Math.abs(mixture.at(0.5).carbonDioxide() - mixture.carbonDioxide()) < 1.0E-9,
                "Half way up is the stored average");
        helper.assertTrue(Math.abs((floor.carbonDioxide() + ceiling.carbonDioxide()) / 2
                        - mixture.carbonDioxide()) < 1.0E-9,
                "Sampling a height creates no gas and destroys none");
        helper.assertTrue(mixture.at(-5).equals(floor) && mixture.at(9).equals(ceiling),
                "A height outside the space is clamped, never extrapolated");

        // Why a safety lamp is held up: the average can be below the window while the roof is not.
        var lean = new GasMixture(0.20, 0, 0.040, 0);
        helper.assertFalse(lean.explosive(), "On average this space is below the flammability window");
        helper.assertTrue(lean.at(1).explosive(), "but against the roof it is inside it");
        helper.assertFalse(lean.at(0).explosive(), "and down at the floor there is almost nothing");
        helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void aLargeClosedSpaceIsNotOpenAir(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = workspace(helper);
        int radius = 5;
        try {
            // A hollow 11x11x11 hull leaves 729 interior blocks, well past the 512 cell budget.
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        boolean hull = Math.abs(x) == radius || Math.abs(y) == radius || Math.abs(z) == radius;
                        level.setBlockAndUpdate(base.offset(x, y, z), hull
                                ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                }
            }
            RoomScan scan = RoomScan.scan(level, base);
            helper.assertTrue(scan.enclosure() == RoomScan.Enclosure.LARGE,
                    "A closed space past the budget is a large space, not outside air");
            helper.assertTrue(scan.sealed(), "and it still carries its own atmosphere");
            helper.assertTrue(scan.volume() == RoomScan.MAX_VOLUME,
                    "reported with the cell budget, because the real volume was never counted");
        } finally {
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest
    public void breathingIsCalibratedAgainstRoomSize(GameTestHelper helper) {
        double hut = EnvironmentSystem.equilibriumOxygen(30, 1);
        double chamber = EnvironmentSystem.equilibriumOxygen(15, 1);
        double coffin = EnvironmentSystem.equilibriumOxygen(8, 1);
        helper.assertTrue(hut > GasMixture.OXYGEN_IMPAIRED,
                "A sealed hut of about 30 blocks must stay breathable indefinitely");
        helper.assertTrue(chamber < GasMixture.OXYGEN_IMPAIRED && chamber > GasMixture.OXYGEN_LETHAL,
                "A small sealed chamber gets bad without becoming a death trap on its own");
        helper.assertTrue(coffin < GasMixture.OXYGEN_LETHAL,
                "A coffin-sized sealed box still kills");
        helper.assertTrue(EnvironmentSystem.equilibriumOxygen(30, 4) < hut,
                "More occupants use the same air up faster");

        helper.assertTrue(EnvironmentSystem.equilibriumCarbonDioxide(30, 1)
                        < GasMixture.CARBON_DIOXIDE_SEVERE,
                "The same hut must not sit permanently in severe carbon dioxide");
        helper.assertTrue(EnvironmentSystem.equilibriumCarbonDioxide(8, 1)
                        > GasMixture.CARBON_DIOXIDE_LETHAL,
                "while the sealed box is lethal on carbon dioxide too");

        double large = EnvironmentSystem.equilibriumOxygen(RoomScan.MAX_VOLUME, 1);
        helper.assertTrue(large > GasMixture.OXYGEN_IMPAIRED,
                "A large cave is tracked but its air is never used up by breathing alone");
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    private static BlockPos workspace(GameTestHelper helper) {
        return helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
    }

    /** A 5x5x5 shell of the given material around a 3x3x3 interior, centred on the base position. */
    private static void buildShell(ServerLevel level, BlockPos base, BlockState wall) {
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    boolean shell = Math.abs(x) == 2 || Math.abs(y) == 2 || Math.abs(z) == 2;
                    level.setBlockAndUpdate(base.offset(x, y, z),
                            shell ? wall : Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void clearShell(ServerLevel level, BlockPos base) {
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException expected) {
            return;
        }
        throw new AssertionError("Expected operation to be rejected");
    }
}
