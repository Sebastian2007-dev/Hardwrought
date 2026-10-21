package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.chemistry.SaltPan;
import de.ipnats.hardwrought.core.registry.ModEffects;
import de.ipnats.hardwrought.electricity.Machine;
import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.gasline.GasStorage;
import de.ipnats.hardwrought.materials.ChemMachine;
import de.ipnats.hardwrought.multiblock.BigMachines;
import de.ipnats.hardwrought.multiblock.ControllerBlock;
import de.ipnats.hardwrought.nuclear.CentrifugeController;
import de.ipnats.hardwrought.nuclear.NuclearBlocks;
import de.ipnats.hardwrought.nuclear.NuclearItems;
import de.ipnats.hardwrought.nuclear.Radiation;
import de.ipnats.hardwrought.nuclear.Radioactivity;
import de.ipnats.hardwrought.nuclear.ReactorController;
import de.ipnats.hardwrought.nuclear.ReactorCore;
import de.ipnats.hardwrought.nuclear.TurbineController;
import de.ipnats.hardwrought.smithing.MetalStock;
import de.ipnats.hardwrought.water.WaterQuality;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/** Nuclear engineering: the physics of the core, the three big machines, radiation, and the fuel chain. */
public final class NuclearGameTests {
    private static final double TICK = 0.05;

    // ---------------------------------------------------------------- the core on its own

    /** What the core is given: so many fresh assemblies, so many rod drives, current, flow and water. */
    private static ReactorCore.Conditions core(int fuel, int drives, boolean powered, double flow, boolean water) {
        ReactorCore.Load[] loads = new ReactorCore.Load[9];
        Arrays.fill(loads, ReactorCore.Load.EMPTY);
        for (int i = 0; i < fuel; i++) loads[i] = ReactorCore.Load.FUEL;
        return new ReactorCore.Conditions(loads, new float[9], drives, powered, flow, water, false);
    }

    private static ReactorCore.Conditions running() {
        return core(9, 9, true, 1.0, true);
    }

    private static void run(ReactorCore core, ReactorCore.Conditions conditions, double seconds) {
        for (int i = 0; i < seconds / TICK; i++) core.step(conditions, TICK);
    }

    /** A fresh core brought up to half power by the automatic control and held there. */
    private static ReactorCore atHalfPower() {
        ReactorCore core = new ReactorCore();
        core.setSetpoint(0.5);
        run(core, running(), 1200);
        return core;
    }

    @GameTest
    public void aFreshCoreWithItsRodsInStaysShutDown(GameTestHelper helper) {
        ReactorCore core = new ReactorCore();
        run(core, running(), 60);
        helper.assertTrue(core.reactivity() < -0.1, "Nine drives with their rods in hold nine fresh assemblies down: " + core.reactivity());
        helper.assertTrue(core.power() < 1e-3 && core.shutDown(), "and the core stays shut down: " + core.power());
        helper.succeed();
    }

    @GameTest
    public void sevenAssembliesNeverGoCritical(GameTestHelper helper) {
        ReactorCore core = new ReactorCore();
        core.setRods(0.0);
        ReactorCore.Conditions seven = core(7, 0, true, 1.0, true);
        run(core, seven, 120);
        helper.assertTrue(core.k() < 1.0, "Seven fresh assemblies have a k below one: " + core.k());
        helper.assertTrue(core.power() < 1e-3, "and with no rods at all the power goes nowhere: " + core.power());
        helper.succeed();
    }

    @GameTest
    public void theAutomaticControlBringsTheCoreToItsSetpointAndHoldsIt(GameTestHelper helper) {
        ReactorCore core = atHalfPower();
        helper.assertTrue(core.trip() == ReactorCore.Trip.NONE, "Started up without a trip: " + core.trip());
        helper.assertTrue(Math.abs(core.power() - 0.5) < 0.05, "and it holds half power: " + core.power());
        helper.assertTrue(core.temperature() > 150 && core.temperature() < ReactorCore.SCRAM_TEMPERATURE,
                "at a working temperature: " + core.temperature());
        helper.assertTrue(core.reactivity() < ReactorCore.BETA, "and never near prompt critical: " + core.reactivity());
        double before = core.power();
        run(core, running(), 300);
        helper.assertTrue(Math.abs(core.power() - before) < 0.05, "Five minutes later it still holds it: " + core.power());
        helper.succeed();
    }

    @GameTest
    public void aScramDropsThePowerAndLeavesTheDecayHeat(GameTestHelper helper) {
        ReactorCore core = atHalfPower();
        core.scram();
        run(core, running(), 5);
        helper.assertTrue(core.power() < 0.1, "Five seconds after a scram the fission power is down: " + core.power());
        helper.assertTrue(core.decay() > 0.005, "but the decay heat is still there: " + core.decay());
        helper.assertTrue(core.rods() >= 0.99, "and the rods are in");
        run(core, running(), 300);
        helper.assertTrue(core.power() < 0.01 && core.shutDown(), "Five minutes on it is shut down: " + core.power());
        helper.assertTrue(core.temperature() < ReactorCore.SCRAM_TEMPERATURE, "and the pumps keep it cool: " + core.temperature());
        helper.succeed();
    }

    @GameTest
    public void losingTheFeedwaterTripsTheCore(GameTestHelper helper) {
        ReactorCore core = atHalfPower();
        run(core, core(9, 9, true, 1.0, false), 10);
        helper.assertTrue(core.trip() == ReactorCore.Trip.LOST_WATER, "No feedwater: the rods drop: " + core.trip());
        helper.assertTrue(core.power() < 0.1, "and without water to slow the neutrons the reaction stops: " + core.power());
        helper.succeed();
    }

    @GameTest
    public void theRodsDropWhenTheirDrivesLoseCurrent(GameTestHelper helper) {
        ReactorCore core = atHalfPower();
        run(core, core(9, 9, false, ReactorCore.NATURAL_FLOW, true), 3);
        helper.assertTrue(core.rods() >= 0.99, "The magnets let go: the rods are in: " + core.rods());
        helper.assertTrue(core.trip() != ReactorCore.Trip.NONE, "and the core has tripped");
        helper.succeed();
    }

    @GameTest
    public void rodsPulledOutByHandAreCaughtByTheProtection(GameTestHelper helper) {
        ReactorCore core = new ReactorCore();
        core.setRods(0.0);
        run(core, running(), 90);
        helper.assertTrue(core.trip() == ReactorCore.Trip.POWER || core.trip() == ReactorCore.Trip.TEMPERATURE,
                "Every rod pulled at once runs the power away until the protection drops them: " + core.trip());
        helper.assertTrue(core.temperature() < ReactorCore.CLADDING_FAILS, "before the cladding fails: " + core.temperature());
        helper.succeed();
    }

    @GameTest
    public void fuelLastsTwoHoursAtFullPower(GameTestHelper helper) {
        ReactorCore core = new ReactorCore();
        core.setSetpoint(1.0);
        run(core, running(), 1500);
        ReactorCore.Result result = core.step(running(), 1.0);
        double hours = 1.0 / result.burnPerAssembly() / 3600.0 * core.power();
        helper.assertTrue(Math.abs(hours - 2.0) < 0.2, "A fresh assembly lasts about two hours at full power: " + hours);
        helper.assertTrue(result.steamJoules() > 0.8 * ReactorCore.NOMINAL_WATTS * core.power(), "and its heat goes off as steam");
        helper.succeed();
    }

    // ---------------------------------------------------------------- the machines in the world

    /** The reactor's vessel round (3,3,3), its desk in the middle of the south side, its ports in the south wall. */
    private static ReactorController reactor(GameTestHelper helper, BlockPos centre, int drives) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Block block = NuclearBlocks.REACTOR_VESSEL_WALL;
                    if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1 && Math.abs(dz) <= 1) {
                        block = dy == 0 ? NuclearBlocks.FUEL_CHANNEL : BigMachines.GRAPHITE_ELECTRODE;
                    }
                    helper.setBlock(centre.offset(dx, dy, dz), block);
                }
            }
        }
        int placed = 0;
        for (int dx = -1; dx <= 1 && placed < drives; dx++) {
            for (int dz = -1; dz <= 1 && placed < drives; dz++, placed++) helper.setBlock(centre.offset(dx, 2, dz), NuclearBlocks.CONTROL_ROD_DRIVE);
        }
        helper.setBlock(centre.offset(-2, -1, 2), BigMachines.FLUID_PORT);
        helper.setBlock(centre.offset(0, 1, 2), BigMachines.GAS_PORT);
        helper.setBlock(centre.offset(-1, -1, 2), BigMachines.ITEM_PORT);
        helper.setBlock(centre.offset(2, -1, 2), BigMachines.POWER_PORT);
        helper.setBlock(centre.offset(0, 0, 2), NuclearBlocks.REACTOR_CONTROLLER.defaultBlockState()
                .setValue(ControllerBlock.FACING, Direction.SOUTH));
        return helper.getBlockEntity(centre.offset(0, 0, 2), ReactorController.class);
    }

    @GameTest(maxTicks = 60)
    public void theReactorFormsAndTakesFuelOnlyWhileShutDown(GameTestHelper helper) {
        BlockPos centre = new BlockPos(3, 3, 3);
        ReactorController reactor = reactor(helper, centre, 9);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(reactor.formed(), "The vessel stands: " + reactor.problem().getString());
            helper.assertTrue(reactor.ports().size() == 4, "with its four ports: " + reactor.ports().size());
            helper.assertTrue(reactor.canPlaceItem(0, new ItemStack(NuclearItems.FUEL_ASSEMBLY)), "Shut down, a channel takes fuel");
            helper.assertTrue(reactor.canPlaceItem(1, new ItemStack(NuclearItems.THORIUM_BREEDER_ROD)), "or a breeder rod");
            helper.assertFalse(reactor.canPlaceItem(2, new ItemStack(NuclearItems.FUEL_ROD)), "but not a loose rod");
            helper.assertFalse(reactor.canPlaceItem(3, new ItemStack(NuclearItems.SPENT_FUEL_ASSEMBLY)), "nor spent fuel");
            helper.assertTrue(net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil.simulateInsert(reactor.feedwater, SaltPan.water(WaterQuality.PURIFIED), FluidConstants.BUCKET, null) > 0,
                    "Its feedwater tank takes purified water");
            helper.assertTrue(net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil.simulateInsert(reactor.feedwater, SaltPan.water(WaterQuality.FRESH), FluidConstants.BUCKET, null) == 0,
                    "and nothing less");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 60)
    public void aCoreWithoutAWholeHeadDoesNotForm(GameTestHelper helper) {
        BlockPos centre = new BlockPos(3, 3, 3);
        ReactorController reactor = reactor(helper, centre, 9);
        helper.setBlock(centre, net.minecraft.world.level.block.Blocks.STONE);
        helper.runAfterDelay(25, () -> {
            helper.assertFalse(reactor.formed(), "Stone where a fuel channel belongs: it does not stand");
            helper.assertTrue(!reactor.problem().getString().isEmpty(), "and it says what is wrong");
            helper.succeed();
        });
    }

    /**
     * A hot core with its fuel in and its hull broken — as every reactor is for its first tick after the
     * world is loaded — keeps its rods: it shuts down rather than running away.
     */
    @GameTest(maxTicks = 120)
    public void aBrokenHullDropsTheRodsAndDoesNotMeltTheCore(GameTestHelper helper) {
        BlockPos centre = new BlockPos(3, 3, 3);
        ReactorController reactor = reactor(helper, centre, 9);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(reactor.formed(), "The vessel stands: " + reactor.problem().getString());
            for (int i = 0; i < 9; i++) reactor.setItem(i, new ItemStack(NuclearItems.FUEL_ASSEMBLY));
            reactor.core().setTemperature(300);
            try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                reactor.feedwater.insert(SaltPan.water(WaterQuality.PURIFIED), FluidConstants.BUCKET * 16, transaction);
                transaction.commit();
            }
            helper.setBlock(centre.offset(2, 2, 2), net.minecraft.world.level.block.Blocks.AIR);
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(helper.getBlockState(centre.offset(0, 0, 2)).is(NuclearBlocks.REACTOR_CONTROLLER),
                    "The desk is still there: the core did not melt");
            helper.assertTrue(reactor.core().power() < 0.01 && reactor.core().temperature() < ReactorCore.CLADDING_FAILS,
                    "and it stays shut down: " + reactor.core().power() + " at " + reactor.core().temperature());
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 80)
    public void withoutCurrentTheRodsCannotBeDrawn(GameTestHelper helper) {
        BlockPos centre = new BlockPos(3, 3, 3);
        ReactorController reactor = reactor(helper, centre, 9);
        helper.runAfterDelay(25, () -> reactor.press(de.ipnats.hardwrought.nuclear.ReactorMenu.WITHDRAW_MORE, helper.getLevel()));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(reactor.core().rods() >= 0.99, "No current to the drives: the rods stay in: " + reactor.core().rods());
            helper.assertTrue(reactor.core().trip() == ReactorCore.Trip.LOST_POWER, "and the desk says why: " + reactor.core().trip());
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 60)
    public void theTurbineTakesOnlySteamAndGivesPurifiedWater(GameTestHelper helper) {
        BlockPos centre = new BlockPos(2, 2, 2);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            helper.setBlock(centre.offset(dx, dy, dz), NuclearBlocks.TURBINE_CASING);
        }
        helper.setBlock(centre, NuclearBlocks.TURBINE_ROTOR);
        helper.setBlock(centre.below(), NuclearBlocks.CONDENSER);
        helper.setBlock(centre.offset(0, 1, -1), BigMachines.GAS_PORT);
        helper.setBlock(centre.offset(-1, -1, -1), BigMachines.FLUID_PORT);
        helper.setBlock(centre.offset(1, -1, -1), BigMachines.POWER_PORT);
        helper.setBlock(centre.south(), NuclearBlocks.TURBINE_CONTROLLER.defaultBlockState().setValue(ControllerBlock.FACING, Direction.SOUTH));
        TurbineController turbine = helper.getBlockEntity(centre.south(), TurbineController.class);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(turbine.formed(), "The turbine stands: " + turbine.problem().getString());
            GasStorage port = GasStorage.SIDED.find(helper.getLevel(), helper.absolutePos(centre.offset(0, 1, -1)), Direction.UP);
            helper.assertTrue(port != null && port.accepts(GasKind.STEAM), "Its gas port takes steam");
            helper.assertFalse(port.accepts(GasKind.LPG), "and no other gas");
            turbine.steam.insert(GasKind.STEAM, 200);
        });
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(turbine.emf() > 0.9 * TurbineController.VOLTS, "With steam in its chest it gives its voltage: " + turbine.emf());
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 60)
    public void theCascadeTakesFeedThroughItsWallAndGivesProductThroughItsRoof(GameTestHelper helper) {
        BlockPos centre = new BlockPos(2, 2, 2);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            helper.setBlock(centre.offset(dx, dy, dz), NuclearBlocks.CENTRIFUGE_CASING);
        }
        for (int[] at : new int[][] {{0, 0}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) helper.setBlock(centre.offset(at[0], 0, at[1]), NuclearBlocks.GAS_CENTRIFUGE);
        helper.setBlock(centre.offset(-1, 0, 0), BigMachines.GAS_PORT);
        helper.setBlock(centre.above(), BigMachines.GAS_PORT);
        helper.setBlock(centre.south(), NuclearBlocks.CENTRIFUGE_CONTROLLER.defaultBlockState().setValue(ControllerBlock.FACING, Direction.SOUTH));
        CentrifugeController cascade = helper.getBlockEntity(centre.south(), CentrifugeController.class);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(cascade.formed(), "The cascade stands: " + cascade.problem().getString());
            GasStorage wall = GasStorage.SIDED.find(helper.getLevel(), helper.absolutePos(centre.offset(-1, 0, 0)), Direction.WEST);
            GasStorage roof = GasStorage.SIDED.find(helper.getLevel(), helper.absolutePos(centre.above()), Direction.UP);
            helper.assertTrue(wall == cascade.feed && wall.accepts(GasKind.URANIUM_HEXAFLUORIDE), "The wall's port takes the feed");
            helper.assertFalse(wall.accepts(GasKind.CHLORINE), "and nothing else");
            helper.assertTrue(roof == cascade.product && !roof.accepts(GasKind.ENRICHED_HEXAFLUORIDE), "The roof's port only gives");
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- the grid

    @GameTest
    public void aPowerStationHasConductorsAndATransformerForItsCurrent(GameTestHelper helper) {
        var heavy = de.ipnats.hardwrought.electricity.Gauge.HEAVY_CABLE;
        var overhead = de.ipnats.hardwrought.electricity.Gauge.OVERHEAD;
        helper.assertTrue(heavy.amps() >= 80 && heavy.insulated(), "The heavy cable carries eighty amperes, sheathed");
        helper.assertTrue(overhead.span() >= 48 && !overhead.insulated(), "The overhead conductor spans far, bare");
        helper.assertTrue(heavy.item() == de.ipnats.hardwrought.electricity.ElectricBlocks.HEAVY_POWER_CABLE
                && overhead.item() == de.ipnats.hardwrought.electricity.ElectricBlocks.OVERHEAD_CONDUCTOR, "Each is its own coil");
        helper.setBlock(new BlockPos(1, 2, 1), de.ipnats.hardwrought.electricity.ElectricBlocks.POWER_TRANSFORMER);
        var transformer = helper.getBlockEntity(new BlockPos(1, 2, 1), de.ipnats.hardwrought.electricity.TransformerBlockEntity.class);
        helper.assertTrue(transformer.ratedAmps() == 60 && transformer.ohms() < 0.1, "The power transformer carries sixty amperes");
        helper.assertFalse(transformer.carried(helper.getLevel(), 50, 600), "fifty for ten minutes without burning");
        helper.succeed();
    }

    /** A battery bank round centre, full of cell stacks, its power port in the south wall. */
    private static de.ipnats.hardwrought.multiblock.BatteryBankController batteryBank(GameTestHelper helper, BlockPos centre) {
        for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
            boolean inside = Math.abs(dx) <= 1 && Math.abs(dy) <= 1 && Math.abs(dz) <= 1;
            helper.setBlock(centre.offset(dx, dy, dz), inside ? BigMachines.BATTERY_CELL_STACK : BigMachines.BATTERY_BANK_CASING);
        }
        helper.setBlock(centre.offset(2, -1, 2), BigMachines.POWER_PORT);
        helper.setBlock(centre.offset(0, 0, 2), BigMachines.BATTERY_BANK_CONTROLLER.defaultBlockState()
                .setValue(ControllerBlock.FACING, Direction.SOUTH));
        return helper.getBlockEntity(centre.offset(0, 0, 2), de.ipnats.hardwrought.multiblock.BatteryBankController.class);
    }

    @GameTest(maxTicks = 200)
    public void aTurbineChargesTheBatteryBank(GameTestHelper helper) {
        BlockPos turbineCentre = new BlockPos(3, 2, 2), bankCentre = new BlockPos(10, 3, 3);
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            helper.setBlock(turbineCentre.offset(dx, dy, dz), NuclearBlocks.TURBINE_CASING);
        }
        helper.setBlock(turbineCentre, NuclearBlocks.TURBINE_ROTOR);
        helper.setBlock(turbineCentre.below(), NuclearBlocks.CONDENSER);
        helper.setBlock(turbineCentre.offset(1, -1, -1), BigMachines.POWER_PORT);
        helper.setBlock(turbineCentre.south(), NuclearBlocks.TURBINE_CONTROLLER.defaultBlockState().setValue(ControllerBlock.FACING, Direction.SOUTH));
        TurbineController turbine = helper.getBlockEntity(turbineCentre.south(), TurbineController.class);
        var bank = batteryBank(helper, bankCentre);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(bank.formed(), "The bank stands: " + bank.problem().getString());
            helper.assertTrue(bank.stacks() == 27 && bank.capacity() == 27 * de.ipnats.hardwrought.multiblock.BatteryBankController.STACK_JOULES,
                    "with its 27 stacks");
            turbine.steam.insert(GasKind.STEAM, 400);
            de.ipnats.hardwrought.electricity.Electricity.connect(helper.getLevel(), helper.absolutePos(turbineCentre.offset(1, -1, -1)),
                    helper.absolutePos(bankCentre.offset(2, -1, 2)), de.ipnats.hardwrought.electricity.Gauge.HEAVY_CABLE);
        });
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(bank.charge() > 10_000, "At 240 volts against an empty bank's 216, it charges: " + bank.charge());
            var port = helper.getBlockEntity(bankCentre.offset(2, -1, 2), de.ipnats.hardwrought.multiblock.PortBlock.Entity.class);
            helper.assertTrue(Math.abs(port.amps()) > 1 && Math.abs(port.amps()) < de.ipnats.hardwrought.electricity.Gauge.HEAVY_CABLE.amps(),
                    "and draws no more than a heavy cable carries: " + port.amps());
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- radiation

    @GameTest
    public void leadStopsWhatAirLetsThrough(GameTestHelper helper) {
        BlockPos from = new BlockPos(1, 2, 1), to = new BlockPos(7, 2, 1);
        Vec3 a = Vec3.atCenterOf(helper.absolutePos(from)), b = Vec3.atCenterOf(helper.absolutePos(to));
        helper.assertTrue(Radiation.shielding(helper.getLevel(), a, b) > 0.99, "Air lets it all through");
        helper.setBlock(new BlockPos(4, 2, 1), de.ipnats.hardwrought.metallurgy.MetalBlocks.block("lead"));
        helper.setBlock(new BlockPos(5, 2, 1), de.ipnats.hardwrought.metallurgy.MetalBlocks.block("lead"));
        double lead = Radiation.shielding(helper.getLevel(), a, b);
        helper.assertTrue(lead < 0.001, "Two blocks of lead let almost nothing through: " + lead);
        helper.succeed();
    }

    @GameTest
    public void spentFuelIsFarHotterThanFresh(GameTestHelper helper) {
        double fresh = Radioactivity.of(new ItemStack(NuclearItems.FUEL_ASSEMBLY));
        double spent = Radioactivity.of(new ItemStack(NuclearItems.SPENT_FUEL_ASSEMBLY));
        double ore = Radioactivity.of(new ItemStack(de.ipnats.hardwrought.metallurgy.ModMetals.raw(de.ipnats.hardwrought.metallurgy.Metal.URANIUM)));
        helper.assertTrue(ore > 0 && ore < 1e-4, "Raw uranium ticks, barely: " + ore);
        helper.assertTrue(fresh < 1e-3, "Fresh fuel is safe to carry: " + fresh);
        helper.assertTrue(spent > 10 * Radiation.LETHAL / 6, "Spent fuel kills within the hour: " + spent);
        helper.succeed();
    }

    @GameTest(maxTicks = 40)
    public void aLargeDoseMakesAPlayerSick(GameTestHelper helper) {
        var player = TestPlayers.survivor(helper, 2, 2);
        Radiation.setDose(player, 4.0);
        helper.runAfterDelay(5, () -> Radiation.second(player));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(player.hasEffect(ModEffects.RADIATION_SICKNESS), "Four sieverts: radiation sickness");
            helper.assertTrue(Radiation.dose(player) < 4.0, "and the body has begun to mend: " + Radiation.dose(player));
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- the fuel chain

    @GameTest
    public void theFuelChainRunsFromOreToPellet(GameTestHelper helper) {
        var reactor = ChemMachine.CHEMICAL_REACTOR.recipes();
        for (String step : new String[] {"yellowcake", "hydrofluoric_acid", "uranium_hexafluoride", "uranium_dioxide", "monazite",
                "zirconium", "thorium_reprocessing"}) {
            helper.assertTrue(reactor.stream().anyMatch(recipe -> recipe.id().equals("chemical_reactor/" + step)),
                    "The chemical reactor has its step: " + step);
        }
        helper.assertTrue(CentrifugeController.recipes().getFirst().gasOut() == GasKind.ENRICHED_HEXAFLUORIDE, "The cascade enriches");
        helper.assertTrue(Machine.PRESS.result(null, new ItemStack(NuclearItems.URANIUM_DIOXIDE)).is(NuclearItems.GREEN_PELLET),
                "The plate press presses green pellets");
        helper.assertTrue(Machine.GLASS_KILN.result(null, new ItemStack(NuclearItems.GREEN_PELLET)).is(NuclearItems.FUEL_PELLET),
                "the kiln fires them");
        helper.assertTrue(Machine.TUBE_MILL.result(null, new ItemStack(MetalStock.plate("zircaloy"))).is(NuclearItems.CLADDING_TUBE),
                "and the tube mill draws zircaloy into cladding");
        helper.assertTrue(de.ipnats.hardwrought.multiblock.ArcFurnaceController.recipes().stream()
                        .anyMatch(recipe -> recipe.outputs().getFirst().is(de.ipnats.hardwrought.metallurgy.Alloys.ZIRCALOY_INGOT)),
                "The large arc furnace makes zircaloy");
        helper.succeed();
    }
}
