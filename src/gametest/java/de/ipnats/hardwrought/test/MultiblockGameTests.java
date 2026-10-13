package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.electricity.Gauge;
import de.ipnats.hardwrought.knowledge.Multiblocks;
import de.ipnats.hardwrought.materials.Materials;
import de.ipnats.hardwrought.multiblock.ArcFurnaceController;
import de.ipnats.hardwrought.multiblock.BigMachines;
import de.ipnats.hardwrought.multiblock.ControllerBlock;
import de.ipnats.hardwrought.multiblock.LargeDieselController;
import de.ipnats.hardwrought.multiblock.PortBlock;
import de.ipnats.hardwrought.refining.RefineryProduct;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** The big machines: a hull checked whole, its ports serving the controller, the machines at work. */
public final class MultiblockGameTests {
    /**
     * Builds a 3×3×3 hull of casing centred at centre, its controller in the middle of the south side, and
     * the given ports at the north-west bottom corner and the north-east bottom corner.
     */
    private static void hull(GameTestHelper helper, BlockPos centre, Block casing, Block core, Block roof, Block controller,
                             Block firstPort, Block secondPort) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) helper.setBlock(centre.offset(dx, dy, dz), casing);
            }
        }
        helper.setBlock(centre, core);
        helper.setBlock(centre.above(), roof);
        helper.setBlock(centre.offset(-1, -1, -1), firstPort);
        helper.setBlock(centre.offset(1, -1, -1), secondPort);
        helper.setBlock(centre.south(), controller.defaultBlockState().setValue(ControllerBlock.FACING, Direction.SOUTH));
    }

    /** A large diesel generator centred at (2,2,2), fuelled with four buckets of diesel. */
    private static LargeDieselController generator(GameTestHelper helper, BlockPos centre) {
        hull(helper, centre, BigMachines.ENGINE_CASING, BigMachines.ENGINE_CORE, BigMachines.ENGINE_CASING,
                BigMachines.LARGE_DIESEL_CONTROLLER, BigMachines.FLUID_PORT, BigMachines.POWER_PORT);
        LargeDieselController controller = helper.getBlockEntity(centre.south(), LargeDieselController.class);
        try (Transaction transaction = Transaction.openOuter()) {
            controller.fuel.insert(FluidVariant.of(RefineryProduct.DIESEL.source()), FluidConstants.BUCKET * 4, transaction);
            transaction.commit();
        }
        return controller;
    }

    @GameTest(maxTicks = 100)
    public void theLargeDieselGeneratorFormsAndRuns(GameTestHelper helper) {
        BlockPos centre = new BlockPos(2, 2, 2);
        LargeDieselController controller = generator(helper, centre);
        helper.succeedWhen(() -> {
            helper.assertTrue(controller.formed(), "The hull stands: " + controller.problem().getString());
            helper.assertTrue(controller.ports().size() == 2, "with its two ports");
            helper.assertTrue(controller.running(), "and with diesel in it, it runs");
            var port = helper.getBlockEntity(centre.offset(1, -1, -1), PortBlock.Entity.class);
            helper.assertTrue(port.emf() == LargeDieselController.VOLTS, "giving its voltage at the power port");
        });
    }

    @GameTest(maxTicks = 60)
    public void aHullWithAGapDoesNotForm(GameTestHelper helper) {
        BlockPos centre = new BlockPos(2, 2, 2);
        LargeDieselController controller = generator(helper, centre);
        helper.setBlock(centre.offset(-1, 1, 1), Blocks.STONE);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(!controller.formed() && !controller.running(), "Stone in the casing: it does not stand");
            helper.assertTrue(!controller.problem().getString().isEmpty(), "and it says what is wrong");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 400)
    public void theLargeArcFurnaceMakesSiliconTwiceOver(GameTestHelper helper) {
        BlockPos generatorCentre = new BlockPos(2, 2, 2), furnaceCentre = new BlockPos(7, 2, 2);
        generator(helper, generatorCentre);
        hull(helper, furnaceCentre, BigMachines.REFRACTORY_CASING, Blocks.AIR, BigMachines.GRAPHITE_ELECTRODE,
                BigMachines.ARC_FURNACE_CONTROLLER, BigMachines.ITEM_PORT, BigMachines.POWER_PORT);
        ArcFurnaceController furnace = helper.getBlockEntity(furnaceCentre.south(), ArcFurnaceController.class);
        furnace.setItem(0, new ItemStack(Items.QUARTZ, 4));
        furnace.setItem(1, new ItemStack(de.ipnats.hardwrought.smeltery.SmelteryBlocks.COKE, 2));
        helper.runAfterDelay(25, () -> Electricity.connect(helper.getLevel(), helper.absolutePos(generatorCentre.offset(1, -1, -1)),
                helper.absolutePos(furnaceCentre.offset(1, -1, -1)), Gauge.CABLE));
        helper.succeedWhen(() -> {
            helper.assertTrue(furnace.formed(), "The furnace stands: " + furnace.problem().getString());
            int silicon = 0;
            for (int slot = 2; slot < 4; slot++) if (furnace.getItem(slot).is(Materials.RAW_SILICON)) silicon += furnace.getItem(slot).getCount();
            helper.assertTrue(silicon >= 4, "Two charges give four silicon: " + silicon);
        });
    }

    @GameTest
    public void theCompendiumShowsBothStructures(GameTestHelper helper) {
        helper.assertTrue(Multiblocks.ALL.contains(Multiblocks.LARGE_ARC_FURNACE) && Multiblocks.ALL.contains(Multiblocks.LARGE_DIESEL),
                "Both big machines are in the book");
        helper.assertTrue(!Multiblocks.containing(de.ipnats.hardwrought.Hardwrought.id("power_port")).isEmpty(),
                "and the power port's page leads to them");
        helper.succeed();
    }
}
