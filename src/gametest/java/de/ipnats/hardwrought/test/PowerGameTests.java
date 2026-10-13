package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.chemistry.StillBlockEntity;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.machinery.KineticHolder;
import de.ipnats.hardwrought.machinery.ShaftBlock;
import de.ipnats.hardwrought.power.DieselGeneratorBlock;
import de.ipnats.hardwrought.power.GasBurnerBlock;
import de.ipnats.hardwrought.power.GasolineEngineBlock;
import de.ipnats.hardwrought.power.PetroleumLampBlock;
import de.ipnats.hardwrought.power.PowerBlocks;
import de.ipnats.hardwrought.refining.ProcessTank;
import de.ipnats.hardwrought.refining.RefineryProduct;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;

/** Engines, the generator, the burner and the lamp: what oil and gas are burnt in. */
public final class PowerGameTests {
    private static void pour(ProcessTank tank, Fluid fluid, long buckets) {
        try (Transaction transaction = Transaction.openOuter()) {
            tank.insert(FluidVariant.of(fluid), buckets * FluidConstants.BUCKET, transaction);
            transaction.commit();
        }
    }

    @GameTest(maxTicks = 100)
    public void aDieselGeneratorRunsOnDieselAndStopsOnRedstone(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, PowerBlocks.DIESEL_GENERATOR);
        DieselGeneratorBlock.Entity generator = helper.getBlockEntity(pos, DieselGeneratorBlock.Entity.class);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(!generator.running() && generator.emf() == 0, "Without fuel it stands");
            pour(generator.fuel, RefineryProduct.DIESEL.source(), 2);
        });
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(generator.running() && generator.emf() == DieselGeneratorBlock.Entity.VOLTS,
                    "With diesel it runs and gives its voltage");
            helper.assertTrue(helper.getBlockState(pos).getValue(DieselGeneratorBlock.RUNNING), "and shows it");
            helper.setBlock(pos.east(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(!generator.running(), "A redstone signal stops it");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 100)
    public void aDieselGeneratorTakesTheStillsFuelButNotGasoline(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, PowerBlocks.DIESEL_GENERATOR);
        DieselGeneratorBlock.Entity generator = helper.getBlockEntity(pos, DieselGeneratorBlock.Entity.class);
        pour(generator.fuel, RefineryProduct.GASOLINE.source(), 1);
        helper.assertTrue(generator.fuel.millibuckets() == 0, "Gasoline does not go into a diesel");
        pour(generator.fuel, RefineryProduct.FUEL_FRACTION.source(), 1);
        helper.assertTrue(generator.fuel.millibuckets() == 1000, "The still's fuel does");
        helper.succeed();
    }

    @GameTest(maxTicks = 100)
    public void aGasolineEngineNeedsASparkPlugAndDrivesItsShaft(GameTestHelper helper) {
        BlockPos engine = new BlockPos(2, 1, 2), shaft = engine.south();
        // Facing north: the axle leaves by the south face, into the shaft.
        helper.setBlock(engine, PowerBlocks.GASOLINE_ENGINE.defaultBlockState().setValue(GasolineEngineBlock.FACING, Direction.NORTH));
        helper.setBlock(shaft, ModBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.Z));
        GasolineEngineBlock.Entity entity = helper.getBlockEntity(engine, GasolineEngineBlock.Entity.class);
        pour(entity.fuel, RefineryProduct.GASOLINE.source(), 1);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!entity.running(), "With fuel but no spark plug it does not start");
            entity.setPlugForTesting(GasolineEngineBlock.Entity.PLUG_LIFE / 2);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(entity.running(), "With a spark plug it runs");
            float speed = Math.abs(((KineticHolder) helper.getBlockEntity(shaft, net.minecraft.world.level.block.entity.BlockEntity.class)).kineticSpeed());
            helper.assertTrue(speed == GasolineEngineBlock.Entity.RPM, "and turns the shaft at its speed: " + speed);
            helper.assertTrue(entity.plugLeft() == 50, "wearing its plug as it goes: " + entity.plugLeft());
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 100)
    public void aGasBurnerHeatsWhatStandsOnIt(GameTestHelper helper) {
        BlockPos burner = new BlockPos(2, 1, 2), still = burner.above();
        helper.setBlock(burner, PowerBlocks.GAS_BURNER);
        helper.setBlock(still, ModBlocks.STILL);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(!StillBlockEntity.heated(helper.getLevel(), helper.absolutePos(still)), "Without gas it is cold");
            helper.getBlockEntity(burner, GasBurnerBlock.Entity.class).intake.insert(GasKind.LPG, 16);
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(helper.getBlockState(burner).getValue(GasBurnerBlock.LIT), "With gas it lights");
            helper.assertTrue(StillBlockEntity.heated(helper.getLevel(), helper.absolutePos(still)), "and heats the still on it");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void aPetroleumLampIsFilledWithKeroseneAndLights(GameTestHelper helper) {
        BlockPos lamp = new BlockPos(2, 1, 2);
        helper.setBlock(lamp, PowerBlocks.PETROLEUM_LAMP);
        @SuppressWarnings("removal") ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RefineryProduct.KEROSENE.bucket()));
        helper.useBlock(lamp, player);
        helper.assertTrue(helper.getBlockState(lamp).getValue(PetroleumLampBlock.LIT), "Filled, it lights");
        helper.assertTrue(helper.getBlockEntity(lamp, PetroleumLampBlock.Entity.class).fuel() == 1000, "holding a bucket");
        helper.assertTrue(helper.getBlockState(lamp).getLightEmission() == 15, "and lights the place");
        helper.succeed();
    }

    @GameTest(maxTicks = 200)
    public void aBurningStackOnCurrentLightsItself(GameTestHelper helper) {
        BlockPos generator = new BlockPos(0, 1, 2), tank = new BlockPos(4, 1, 2), stack = tank.above();
        helper.setBlock(generator, PowerBlocks.DIESEL_GENERATOR);
        pour(helper.getBlockEntity(generator, DieselGeneratorBlock.Entity.class).fuel, RefineryProduct.DIESEL.source(), 2);
        helper.setBlock(tank, de.ipnats.hardwrought.gasline.GasLines.TANK);
        helper.getBlockEntity(tank, de.ipnats.hardwrought.gasline.GasTankBlock.Entity.class).store.insert(GasKind.LPG, 100);
        helper.setBlock(stack, de.ipnats.hardwrought.refining.RefineryBlocks.FLARE);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(!helper.getBlockState(stack).getValue(de.ipnats.hardwrought.refining.FlareStackBlock.LIT),
                    "Without current the stack stays out");
            de.ipnats.hardwrought.electricity.Electricity.connect(helper.getLevel(), helper.absolutePos(generator),
                    helper.absolutePos(stack), de.ipnats.hardwrought.electricity.Gauge.WIRE);
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(helper.getBlockState(stack).getValue(de.ipnats.hardwrought.refining.FlareStackBlock.LIT),
                    "On current its igniter lights it");
            helper.succeed();
        });
    }
}
