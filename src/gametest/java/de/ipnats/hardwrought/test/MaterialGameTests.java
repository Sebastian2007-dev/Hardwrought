package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.electricity.Gauge;
import de.ipnats.hardwrought.electricity.Machine;
import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.materials.ChemMachine;
import de.ipnats.hardwrought.materials.ChemMachineBlockEntity;
import de.ipnats.hardwrought.materials.MaterialBlocks;
import de.ipnats.hardwrought.materials.Materials;
import de.ipnats.hardwrought.power.DieselGeneratorBlock;
import de.ipnats.hardwrought.power.PowerBlocks;
import de.ipnats.hardwrought.refining.RefineryProduct;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** The chemistry machines on current, and the faster marks of the old machines. */
public final class MaterialGameTests {
    /** A chemistry machine at (4,1,2), on a running diesel generator at (0,1,2) by a cable. */
    private static ChemMachineBlockEntity onCurrent(GameTestHelper helper, ChemMachine machine) {
        BlockPos generator = new BlockPos(0, 1, 2), at = new BlockPos(4, 1, 2);
        helper.setBlock(generator, PowerBlocks.DIESEL_GENERATOR);
        pour(helper.getBlockEntity(generator, DieselGeneratorBlock.Entity.class).fuel, RefineryProduct.DIESEL.source(), 4);
        helper.setBlock(at, MaterialBlocks.block(machine));
        Electricity.connect(helper.getLevel(), helper.absolutePos(generator), helper.absolutePos(at), Gauge.CABLE);
        return helper.getBlockEntity(at, ChemMachineBlockEntity.class);
    }

    private static void pour(net.fabricmc.fabric.api.transfer.v1.storage.Storage<FluidVariant> tank, Fluid fluid, long buckets) {
        try (Transaction transaction = Transaction.openOuter()) {
            tank.insert(FluidVariant.of(fluid), buckets * FluidConstants.BUCKET, transaction);
            transaction.commit();
        }
    }

    private static int count(ChemMachineBlockEntity machine, Item item) {
        int count = 0;
        for (int slot = ChemMachineBlockEntity.FIRST_OUTPUT; slot < ChemMachineBlockEntity.SIZE; slot++) {
            if (machine.getItem(slot).is(item)) count += machine.getItem(slot).getCount();
        }
        return count;
    }

    @GameTest(maxTicks = 300)
    public void theElectrolyzerSplitsSaltIntoCausticSodaAndChlorine(GameTestHelper helper) {
        ChemMachineBlockEntity machine = onCurrent(helper, ChemMachine.ELECTROLYZER);
        machine.setItem(0, new ItemStack(ModItems.SALT, 4));
        pour(machine.fluid(), Fluids.WATER, 2);
        helper.succeedWhen(() -> {
            helper.assertTrue(count(machine, Materials.CAUSTIC_SODA) >= 1, "Salt and water give caustic soda");
            helper.assertTrue(machine.gasOut.kind() == GasKind.CHLORINE && machine.gasOut.amount() >= 4, "and chlorine gas");
        });
    }

    @GameTest(maxTicks = 300)
    public void theChemicalReactorMakesPlasticFromNaphthaAndLpg(GameTestHelper helper) {
        ChemMachineBlockEntity machine = onCurrent(helper, ChemMachine.CHEMICAL_REACTOR);
        pour(machine.fluid(), RefineryProduct.NAPHTHA.source(), 1);
        machine.gasIn.insert(GasKind.LPG, 10);
        helper.succeedWhen(() -> helper.assertTrue(count(machine, Materials.PLASTIC_PELLETS) >= 4,
                "Naphtha and LPG polymerise into pellets: " + count(machine, Materials.PLASTIC_PELLETS)));
    }

    @GameTest(maxTicks = 300)
    public void theChemicalReactorVulcanisesRubber(GameTestHelper helper) {
        ChemMachineBlockEntity machine = onCurrent(helper, ChemMachine.CHEMICAL_REACTOR);
        machine.setItem(0, new ItemStack(Items.RESIN_CLUMP, 2));
        machine.setItem(1, new ItemStack(ModItems.SULFUR));
        helper.succeedWhen(() -> {
            helper.assertTrue(count(machine, Materials.RUBBER) == 2, "Resin and sulfur give rubber");
            helper.assertTrue(machine.getItem(0).isEmpty() && machine.getItem(1).isEmpty(), "and are used up");
        });
    }

    @GameTest(maxTicks = 400)
    public void theArcFurnaceReducesQuartzToSilicon(GameTestHelper helper) {
        ChemMachineBlockEntity machine = onCurrent(helper, ChemMachine.ARC_FURNACE);
        machine.setItem(0, new ItemStack(Items.QUARTZ, 2));
        machine.setItem(1, new ItemStack(de.ipnats.hardwrought.smeltery.SmelteryBlocks.COKE));
        helper.succeedWhen(() -> helper.assertTrue(count(machine, Materials.RAW_SILICON) == 1, "Quartz and coke give silicon"));
    }

    @GameTest(maxTicks = 100)
    public void aChemistryMachineDoesNothingWithoutCurrent(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, MaterialBlocks.block(ChemMachine.CHEMICAL_REACTOR));
        ChemMachineBlockEntity machine = helper.getBlockEntity(at, ChemMachineBlockEntity.class);
        machine.setItem(0, new ItemStack(Items.RESIN_CLUMP, 2));
        machine.setItem(1, new ItemStack(ModItems.SULFUR));
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(count(machine, Materials.RUBBER) == 0, "Without current nothing is made");
            helper.succeed();
        });
    }

    @GameTest
    public void theMarksWorkFasterAndTheThirdCrusherDoubles(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.assertTrue(Machine.CRUSHER_MK2.ticks() * 2 == Machine.CRUSHER.ticks()
                && Machine.CRUSHER_MK3.ticks() * 4 == Machine.CRUSHER.ticks(), "Each mark works twice as fast as the one before");
        ItemStack ore = new ItemStack(Items.RAW_IRON);
        helper.assertTrue(Machine.CRUSHER_MK3.result(level, ore).getCount() == 2 * Machine.CRUSHER.result(level, ore).getCount(),
                "The third crusher gets two powders out of an ore");
        helper.assertTrue(Machine.PRESS.result(level, new ItemStack(Materials.PLASTIC_PELLETS)).is(Materials.PLASTIC_SHEET),
                "The press makes sheet of plastic pellets");
        helper.assertTrue(Machine.SAWMILL.result(level, new ItemStack(Materials.RAW_SILICON)).getCount() == 4,
                "and the sawmill four wafers of a silicon blank");
        helper.succeed();
    }
}
