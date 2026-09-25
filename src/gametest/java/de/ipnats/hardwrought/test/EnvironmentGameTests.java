package de.ipnats.hardwrought.test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.networking.EnvironmentSnapshotPayload;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.core.save.CoreSaveData;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.environment.CellAtmosphere;
import de.ipnats.hardwrought.environment.Gas;
import de.ipnats.hardwrought.environment.GasMixture;
import de.ipnats.hardwrought.environment.GasSources;
import de.ipnats.hardwrought.environment.Gases;
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
    private static final ResourceKey<DamageType> CARBON_MONOXIDE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Hardwrought.id("carbon_monoxide"));
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
                        && clamped.methane() == 0 && clamped.carbonMonoxide() == GasMixture.MAX_CARBON_MONOXIDE,
                "Every operation stays inside the declared bounds");
        helper.assertTrue(GasMixture.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                        "{\"oxygen\":0.9,\"carbon_dioxide\":0,\"methane\":0,\"carbon_monoxide\":0}")).error().isPresent(),
                "A saved value outside the bounds is rejected rather than clamped silently");
        helper.succeed();
    }

    @GameTest
    public void eightUnitsLandOnTheThresholdsOfEachGas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = workspace(helper);
        try {
            level.setBlock(pos, Gases.with(Blocks.AIR.defaultBlockState(), Gas.CARBON_DIOXIDE, 8), 2);
            GasMixture full = Gases.sample(level, pos);
            helper.assertTrue(full.carbonDioxide() > GasMixture.CARBON_DIOXIDE_LETHAL - 0.001,
                    "A block full of carbon dioxide is lethal to breathe: " + full.carbonDioxide());
            level.setBlock(pos, Gases.with(Blocks.AIR.defaultBlockState(), Gas.CARBON_DIOXIDE, 1), 2);
            GasMixture one = Gases.sample(level, pos);
            helper.assertTrue(one.carbonDioxide() >= GasMixture.CARBON_DIOXIDE_NOTICEABLE
                            && one.carbonDioxide() < GasMixture.CARBON_DIOXIDE_SEVERE,
                    "One unit is just noticeable");
            helper.assertTrue(one.oxygen() < GasMixture.OUTDOOR_OXYGEN, "and pushes a little oxygen out");
            for (int units = 1; units <= Gas.CAPACITY; units++) {
                level.setBlock(pos, Gases.with(Blocks.AIR.defaultBlockState(), Gas.METHANE, units), 2);
                boolean explosive = Gases.sample(level, pos).explosive();
                helper.assertTrue(explosive == (units >= 3 && units <= 7),
                        "Methane is explosive from three to seven units, not at " + units);
            }
            level.setBlock(pos, Gases.with(Blocks.AIR.defaultBlockState(), Gas.CARBON_MONOXIDE, 8), 2);
            helper.assertTrue(Gases.sample(level, pos).carbonMonoxideStress() == 1.0,
                    "A block full of carbon monoxide is lethal");
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            helper.assertTrue(Gases.sample(level, pos).equals(GasMixture.OUTDOOR), "Plain air is outside air");
        } finally {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
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
        var stale = new CellAtmosphere(24.5, 100);
        data.setCellAtmosphere("minecraft:overworld@1", stale);
        var encoded = CoreSaveData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        var decoded = CoreSaveData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        helper.assertTrue(stale.equals(decoded.cellAtmosphere("minecraft:overworld@1")),
                "A room keeps the warmth it built up across a restart");

        for (int index = 0; index < CoreSaveData.MAX_SAVED_CELLS + 40; index++) {
            data.setCellAtmosphere("minecraft:overworld@" + index,
                    new CellAtmosphere(15, 200 + index));
        }
        helper.assertTrue(data.savedCellCount() <= CoreSaveData.MAX_SAVED_CELLS,
                "The saved room table is bounded rather than growing forever");
        helper.assertTrue(data.cellAtmosphere("minecraft:overworld@1") == null,
                "The room unvisited longest is the one that is forgotten");
        expectFailure(() -> new CellAtmosphere(Double.NaN, 0));
        expectFailure(() -> new CellAtmosphere(15, -1));
        helper.succeed();
    }

    @GameTest
    public void gasDiagnosticsAreNowSupplied(GameTestHelper helper) {
        var runtime = CoreLifecycle.require(helper.getLevel().getServer());
        var lines = runtime.snapshot(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        helper.assertTrue(lines.stream().noneMatch(line -> line.startsWith("GAS | unavailable")),
                "The gas channel has a real provider once the environment model exists");
        helper.assertTrue(lines.stream().anyMatch(line -> line.startsWith("GAS | ")
                        && line.contains("O2=") && line.contains("of " + Gas.CAPACITY)),
                "The gas channel reports the gas block at the position and the air it makes");
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
        var poison = registry.get(CARBON_MONOXIDE).orElse(null);
        helper.assertTrue(badAir != null && poison != null,
                "Section 18 hazards have their own damage types, so a death message names the cause");
        helper.assertTrue(badAir.is(DamageTypeTags.BYPASSES_ARMOR) && poison.is(DamageTypeTags.BYPASSES_ARMOR),
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
        helper.assertTrue(weapons.get(Hardwrought.id("flint_sword")) != null
                        && weapons.get(Hardwrought.id("flint_hatchet")) != null
                        && weapons.get(Hardwrought.id("stone_hatchet")) != null
                        && weapons.get(Hardwrought.id("iron_hatchet")) != null,
                "The new sword and hatchets use the combat profiles matching their shapes");
        helper.assertTrue(dagger.staminaCost() < greatsword.staminaCost(),
                "Section 30: a dagger costs very little stamina, a two-hander a lot");
        helper.assertTrue(greatsword.impact() > dagger.impact(), "A two-hander breaks guards, a dagger does not");
        helper.assertTrue(halberd.reachBonusBlocks() > greatsword.reachBonusBlocks()
                        && halberd.minimumReachBlocks() > 0,
                "A polearm reaches furthest and is weak at close quarters");
        helper.assertTrue(runtime.itemWeights().get(Hardwrought.id("iron_halberd")) == 3.6,
                "The new equipment has carried mass");
        helper.assertTrue(ModItems.SAFETY_LAMP != null && ModItems.IRON_HALBERD != null
                        && ModItems.FLINT_PICKAXE != null,
                "Existing equipment and the new primitive items are registered");
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
    public void aFireWarmsARoomByWhatKindOfFireItIs(GameTestHelper helper) {
        double campfire = EnvironmentSystem.flameWeight(Blocks.CAMPFIRE.defaultBlockState()
                .setValue(BlockStateProperties.LIT, true));
        helper.assertTrue(campfire < EnvironmentSystem.flameWeight(Blocks.FIRE.defaultBlockState()),
                "A contained fire warms a room less than an open one");
        helper.assertTrue(EnvironmentSystem.flameWeight(Blocks.TORCH.defaultBlockState()) < campfire,
                "and a torch less again");
        helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void carbonDioxideSinksAndGathersIntoFullBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = workspace(helper);
        buildShell(level, base, Blocks.STONE.defaultBlockState());
        BlockPos floor = base.below();
        level.setBlockAndUpdate(floor, Gases.with(Blocks.AIR.defaultBlockState(), Gas.CARBON_DIOXIDE, 7));
        level.setBlockAndUpdate(base.above(), Gases.with(Blocks.AIR.defaultBlockState(), Gas.CARBON_DIOXIDE, 2));
        helper.runAfterDelay(150, () -> {
            try {
                // Random ticks may break a unit or two down meanwhile, and that rises: it is counted apart.
                int decayed = decayedIn(level, base);
                helper.assertTrue(Gases.units(level.getBlockState(floor), Gas.CARBON_DIOXIDE) + decayed >= 8,
                        "Two falling onto seven make eight: " + level.getBlockState(floor));
                helper.assertTrue(countIn(level, base, Gas.CARBON_DIOXIDE, 1) == 0
                                && countIn(level, base, Gas.CARBON_DIOXIDE, 0) == 0,
                        "Nothing of it is left above the floor");
                helper.assertTrue(countIn(level, base, Gas.CARBON_DIOXIDE, -1) + decayed == 9,
                        "and the one left over lies beside it on the floor: nothing is lost or made");
                helper.assertTrue(gasBlocksIn(level, base, -1) == 2,
                        "It gathers into as few blocks as it fits in, not a film over the floor");
            } finally {
                clearShell(level, base);
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 300)
    public void methaneRisesPastCarbonDioxide(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = workspace(helper);
        buildShell(level, base, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(base.below(), Gases.with(Blocks.AIR.defaultBlockState(), Gas.METHANE, 4));
        level.setBlockAndUpdate(base, Gases.with(Blocks.AIR.defaultBlockState(), Gas.CARBON_DIOXIDE, 8));
        helper.runAfterDelay(250, () -> {
            try {
                helper.assertTrue(countIn(level, base, Gas.METHANE, 1) == 4,
                        "The light gas ends up against the roof, all four units of it");
                helper.assertTrue(countIn(level, base, Gas.CARBON_DIOXIDE, -1) + decayedIn(level, base) == 8,
                        "and the heavy one on the floor, all eight units of it, bar what has broken down");
            } finally {
                clearShell(level, base);
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 60)
    public void lightGasRisesWithoutJumpingToTheClouds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = workspace(helper);
        int ceiling = de.ipnats.hardwrought.environment.GasBlock.cloudCeiling(level);
        helper.assertTrue(ceiling > pos.getY() + 24 && ceiling <= level.getMaxY(),
                "There is enough open sky below the clouds for the rising-gas test");
        BlockPos under = new BlockPos(pos.getX(), ceiling, pos.getZ());
        level.setBlockAndUpdate(pos, Gases.with(Blocks.AIR.defaultBlockState(), Gas.METHANE, 5));
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(Gases.units(level.getBlockState(pos), Gas.METHANE) == 4
                            && Gases.units(level.getBlockState(pos.above()), Gas.METHANE) == 1,
                    "Methane releases one unit into the block above instead of moving as one jumping block");
        });
        helper.runAfterDelay(30, () -> {
            try {
                helper.assertTrue(Gases.total(level.getBlockState(pos)) == 0,
                        "With nothing above it, methane rises");
                int nearby = 0;
                for (int y = 0; y <= 24; y++) {
                    nearby += Gases.units(level.getBlockState(pos.above(y)), Gas.METHANE);
                }
                for (BlockPos near : BlockPos.betweenClosed(under.offset(-3, -3, -3), under.offset(3, 0, 3))) {
                    helper.assertTrue(Gases.units(level.getBlockState(near), Gas.METHANE) == 0,
                            "Rising methane must not teleport to the cloud layer");
                }
                helper.assertTrue(nearby == 5,
                        "All five methane units should still be in the rising plume, found " + nearby);
            } finally {
                for (int y = 0; y <= 24; y++) {
                    BlockPos rising = pos.above(y);
                    if (Gases.isGas(level.getBlockState(rising))) {
                        level.setBlock(rising, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                for (BlockPos near : BlockPos.betweenClosed(under.offset(-3, -3, -3), under.offset(3, 0, 3))) {
                    if (Gases.isGas(level.getBlockState(near))) level.setBlock(near, Blocks.AIR.defaultBlockState(), 2);
                }
            }
            helper.succeed();
        });
    }

    @GameTest
    public void gasCanBePushedABlockAlong(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = workspace(helper);
        buildShell(level, base, Blocks.STONE.defaultBlockState());
        BlockPos from = base.offset(-1, -1, 0);
        try {
            level.setBlock(from, Gases.with(Blocks.AIR.defaultBlockState(), Gas.CARBON_DIOXIDE, 5), 2);
            helper.assertTrue(de.ipnats.hardwrought.environment.GasPush.push(level, from, Direction.EAST) == 5,
                    "A push moves the whole block of gas");
            helper.assertTrue(Gases.total(level.getBlockState(from)) == 0
                            && Gases.units(level.getBlockState(from.east()), Gas.CARBON_DIOXIDE) == 5,
                    "one block the way it was pushed");
            level.setBlock(from.east(2), Gases.with(Blocks.AIR.defaultBlockState(), Gas.CARBON_DIOXIDE, 6), 2);
            helper.assertTrue(de.ipnats.hardwrought.environment.GasPush.push(level, from.east(), Direction.EAST) == 2,
                    "Into a gas block it goes only as far as there is room");
            helper.assertTrue(Gases.units(level.getBlockState(from.east()), Gas.CARBON_DIOXIDE) == 3,
                    "and the rest stays behind");
            helper.assertTrue(de.ipnats.hardwrought.environment.GasPush.push(level, from.east(2), Direction.EAST) == 0,
                    "Against a wall it goes nowhere");
        } finally {
            clearShell(level, base);
        }
        helper.succeed();
    }

    @GameTest
    public void carbonDioxideBreaksDownIntoALighterGas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = workspace(helper);
        buildShell(level, base, Blocks.STONE.defaultBlockState());
        BlockPos pos = base.below();
        try {
            level.setBlock(pos, Gases.with(Blocks.AIR.defaultBlockState(), Gas.CARBON_DIOXIDE, 8), 2);
            for (int tick = 0; tick < 400 && Gases.units(level.getBlockState(pos), Gas.DECAYED_CARBON_DIOXIDE) == 0; tick++) {
                level.getBlockState(pos).randomTick(level, pos, level.getRandom());
            }
            BlockState after = level.getBlockState(pos);
            helper.assertTrue(Gases.units(after, Gas.DECAYED_CARBON_DIOXIDE) > 0,
                    "Left lying, carbon dioxide breaks down into the light kind");
            helper.assertTrue(Gases.total(after) == 8, "unit for unit: nothing is lost or made");
            helper.assertTrue(Gas.DECAYED_CARBON_DIOXIDE.drift() == Direction.UP,
                    "and that rises where carbon dioxide sank");
            level.setBlock(pos, Gases.with(Blocks.AIR.defaultBlockState(), Gas.DECAYED_CARBON_DIOXIDE, 8), 2);
            helper.assertTrue(Math.abs(Gases.sample(level, pos).carbonDioxide() - 0.0804) < 1.0E-6,
                    "Breathed, it is carbon dioxide all the same");
        } finally {
            clearShell(level, base);
        }
        helper.succeed();
    }

    @GameTest
    public void acidRainPoisonsWhatGrows(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = workspace(helper);
        try {
            level.setBlock(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            helper.assertTrue(de.ipnats.hardwrought.environment.AcidRain.poison(level, pos)
                            && level.getBlockState(pos.below()).is(Blocks.DIRT),
                    "Grass under acid rain dies back to dirt");

            level.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState(), 2);
            level.setBlock(pos, Blocks.WHEAT.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.CropBlock.AGE, 3), 2);
            de.ipnats.hardwrought.environment.AcidRain.poison(level, pos);
            helper.assertTrue(level.getBlockState(pos).getValue(net.minecraft.world.level.block.CropBlock.AGE) == 2,
                    "A crop withers back a stage");
            level.setBlock(pos, Blocks.WHEAT.defaultBlockState(), 2);
            de.ipnats.hardwrought.environment.AcidRain.poison(level, pos);
            helper.assertTrue(level.getBlockState(pos).isAir(), "and a seedling dies");

            level.setBlock(pos.below(), Blocks.OAK_LEAVES.defaultBlockState(), 2);
            de.ipnats.hardwrought.environment.AcidRain.poison(level, pos);
            helper.assertTrue(level.getBlockState(pos.below()).isAir(), "A wild tree loses its leaves");
            level.setBlock(pos.below(), Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(BlockStateProperties.PERSISTENT, true), 2);
            helper.assertFalse(de.ipnats.hardwrought.environment.AcidRain.poison(level, pos),
                    "but leaves someone placed are left alone");
        } finally {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 2);
        }
        helper.succeed();
    }

    @GameTest
    public void leavesTakeUpCarbonDioxideButNotMethane(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = workspace(helper);
        buildShell(level, base, Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(BlockStateProperties.PERSISTENT, true));
        BlockPos pos = base.below();
        try {
            BlockState mixed = Gases.with(Gases.with(Blocks.AIR.defaultBlockState(), Gas.CARBON_DIOXIDE, 3),
                    Gas.METHANE, 2);
            level.setBlock(pos, mixed, 2);
            level.getBlockState(pos).randomTick(level, pos, level.getRandom());
            BlockState after = level.getBlockState(pos);
            helper.assertTrue(Gases.units(after, Gas.CARBON_DIOXIDE)
                            + Gases.units(after, Gas.DECAYED_CARBON_DIOXIDE) == 2,
                    "Leaves take up a unit of carbon dioxide");
            helper.assertTrue(Gases.units(after, Gas.METHANE) == 2, "but leave methane alone");
        } finally {
            clearShell(level, base);
        }
        helper.succeed();
    }

    @GameTest
    public void aFireInAirFullOfGasGoesOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = workspace(helper);
        buildShell(level, base, Blocks.STONE.defaultBlockState());
        BlockPos fire = base.below();
        try {
            level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true), 2);
            level.setBlock(fire.above(), Gases.with(Blocks.AIR.defaultBlockState(), Gas.CARBON_DIOXIDE,
                    GasSources.SMOTHERING), 2);
            GasSources.burnAt(level, fire, level.getRandom());
            helper.assertFalse(level.getBlockState(fire).getValue(BlockStateProperties.LIT),
                    "A campfire under a block that full of gas has no air to burn and goes out");
            level.setBlock(fire.above(), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true), 2);
            // Pass by pass until the first unit comes out; in the game the gas drifts off between passes.
            for (int pass = 0; pass < 60 && Gases.total(level.getBlockState(fire.above())) == 0; pass++) {
                GasSources.burnAt(level, fire, level.getRandom());
            }
            helper.assertTrue(level.getBlockState(fire).getValue(BlockStateProperties.LIT),
                    "In clean air it keeps burning");
            helper.assertTrue(Gases.total(level.getBlockState(fire.above())) > 0,
                    "and lets its fumes out above itself");
        } finally {
            clearShell(level, base);
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void firedampGoesOffAtAFlame(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = workspace(helper);
        buildShell(level, base, Blocks.OBSIDIAN.defaultBlockState());
        // Firedamp gathers against the roof, so it is a flame held up there that sets it off.
        level.setBlock(base.offset(1, 1, 0), Blocks.WALL_TORCH.defaultBlockState()
                .setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.WEST), 2);
        level.setBlockAndUpdate(base.above(), Gases.with(Blocks.AIR.defaultBlockState(), Gas.METHANE, 5));
        helper.runAfterDelay(60, () -> {
            try {
                helper.assertTrue(countIn(level, base, Gas.METHANE, -1) + countIn(level, base, Gas.METHANE, 0)
                                + countIn(level, base, Gas.METHANE, 1) == 0,
                        "Methane that reaches a flame burns, all of the pocket at once");
            } finally {
                clearShell(level, base);
            }
            helper.succeed();
        });
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

    /** Units of a gas in one layer of the 3x3x3 interior of a shell: -1 the floor, 0 the middle, 1 the roof. */
    private static int countIn(ServerLevel level, BlockPos base, Gas gas, int layer) {
        int units = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                units += Gases.units(level.getBlockState(base.offset(x, layer, z)), gas);
            }
        }
        return units;
    }

    /** Carbon dioxide that has broken down, anywhere in the interior of a shell. */
    private static int decayedIn(ServerLevel level, BlockPos base) {
        int units = 0;
        for (int layer = -1; layer <= 1; layer++) units += countIn(level, base, Gas.DECAYED_CARBON_DIOXIDE, layer);
        return units;
    }

    private static int gasBlocksIn(ServerLevel level, BlockPos base, int layer) {
        int blocks = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (Gases.isGas(level.getBlockState(base.offset(x, layer, z)))) blocks++;
            }
        }
        return blocks;
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException expected) {
            return;
        }
        throw new AssertionError("Expected operation to be rejected");
    }

    @GameTest(maxTicks = 80)
    public void leavesHoldUpSmallAnimalsButNotPeople(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(1, 4, 1));
        for (int dx = -1; dx < 4; dx++) {
            for (int dz = -1; dz < 4; dz++) {
                level.setBlockAndUpdate(floor.offset(dx, 0, dz), Blocks.OAK_LEAVES.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
                level.setBlockAndUpdate(floor.offset(dx, -4, dz), Blocks.STONE.defaultBlockState());
            }
        }
        BlockPos centre = floor.offset(1, 1, 1);
        // An armour stand is a person's size and falls like one; a mob without its mind would float.
        var zombie = net.minecraft.world.entity.EntityTypes.ARMOR_STAND.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        zombie.snapTo(centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5, 0, 0);
        level.addFreshEntity(zombie);
        var chicken = net.minecraft.world.entity.EntityTypes.CHICKEN.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        chicken.snapTo(centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5 - 1, 0, 0);
        level.addFreshEntity(chicken);
        var player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(level.getBlockState(floor).getCollisionShape(level, floor,
                        net.minecraft.world.phys.shapes.CollisionContext.of(player)).isEmpty(),
                "A leaf block has nothing for a player to stand on");
        helper.runAfterDelay(40, () -> {
            try {
                helper.assertTrue(zombie.getY() < floor.getY(), "Something the size of a person falls through: " + zombie.getY());
                helper.assertTrue(chicken.getY() >= floor.getY() + 1 - 1e-3, "A chicken perches on the leaves: " + chicken.getY());
            } finally {
                zombie.discard();
                chicken.discard();
            }
            helper.succeed();
        });
    }
}
