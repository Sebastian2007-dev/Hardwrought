package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.environment.Gas;
import de.ipnats.hardwrought.environment.Gases;
import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.gasline.GasLineTier;
import de.ipnats.hardwrought.gasline.GasLines;
import de.ipnats.hardwrought.gasline.GasTankBlock;
import de.ipnats.hardwrought.oil.ModFluids;
import de.ipnats.hardwrought.refining.ProcessTank;
import de.ipnats.hardwrought.refining.ProcessorBlock;
import de.ipnats.hardwrought.refining.RefineryBaseBlock;
import de.ipnats.hardwrought.refining.RefineryBlocks;
import de.ipnats.hardwrought.refining.RefineryProduct;
import de.ipnats.hardwrought.refining.RefineryStageBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;

/** Gas lines by pressure, the refinery column with its trays and flare, the cracker and reformer, and firedamp. */
public final class RefineryGameTests {
    private static void pour(ProcessTank tank, Fluid fluid, long millibuckets) {
        try (Transaction transaction = Transaction.openOuter()) {
            tank.insert(FluidVariant.of(fluid), millibuckets * ProcessTank.MILLIBUCKET, transaction);
            transaction.commit();
        }
    }

    @GameTest(maxTicks = 200)
    public void gasEvensOutBetweenTwoTanks(GameTestHelper helper) {
        BlockPos left = new BlockPos(1, 1, 1), right = new BlockPos(5, 1, 1);
        helper.setBlock(left, GasLines.TANK);
        helper.setBlock(right, GasLines.TANK);
        for (int x = 2; x <= 4; x++) helper.setBlock(new BlockPos(x, 1, 1), GasLines.LINES.get(GasLineTier.TITANIUM));
        helper.getBlockEntity(left, GasTankBlock.Entity.class).store.insert(GasKind.NATURAL_GAS, 200);
        helper.succeedWhen(() -> {
            double a = helper.getBlockEntity(left, GasTankBlock.Entity.class).store.amount();
            double b = helper.getBlockEntity(right, GasTankBlock.Entity.class).store.amount();
            helper.assertTrue(Math.abs(a - b) <= 4, "The two tanks even out: " + a + " and " + b);
            helper.assertTrue(Math.abs(a + b - 200) < 0.01, "and no gas is lost on the way: " + (a + b));
        });
    }

    @GameTest(maxTicks = 400)
    public void anOverpressuredLineBursts(GameTestHelper helper) {
        BlockPos tank = new BlockPos(1, 1, 1), empty = new BlockPos(5, 1, 1);
        helper.setBlock(tank, GasLines.TANK);
        helper.setBlock(empty, GasLines.TANK);
        for (int x = 2; x <= 4; x++) helper.setBlock(new BlockPos(x, 1, 1), GasLines.LINES.get(x == 3 ? GasLineTier.BRONZE : GasLineTier.TITANIUM));
        // A full tank stands at 20 bar; bronze holds 6.
        helper.getBlockEntity(tank, GasTankBlock.Entity.class).store.insert(GasKind.NATURAL_GAS, GasTankBlock.Entity.CAPACITY);
        helper.succeedWhen(() -> {
            helper.assertBlockNotPresent(GasLines.LINES.get(GasLineTier.BRONZE), new BlockPos(3, 1, 1));
            helper.assertBlockPresent(GasLines.LINES.get(GasLineTier.TITANIUM), new BlockPos(2, 1, 1));
        });
    }

    /** A base over magma with so many trays on it, and a bucket's worth of crude oil in it. */
    private static RefineryBaseBlock.Entity column(GameTestHelper helper, int trays) {
        BlockPos base = new BlockPos(2, 2, 2);
        helper.setBlock(base.below(), Blocks.MAGMA_BLOCK);
        helper.setBlock(base, RefineryBlocks.BASE);
        for (int tray = 1; tray <= trays; tray++) helper.setBlock(base.above(tray), RefineryBlocks.STAGE);
        RefineryBaseBlock.Entity entity = helper.getBlockEntity(base, RefineryBaseBlock.Entity.class);
        pour(entity.crude, ModFluids.CRUDE_OIL, 1000);
        return entity;
    }

    private static long held(GameTestHelper helper, int tray) {
        RefineryStageBlock.Entity stage = helper.getBlockEntity(new BlockPos(2, 2 + tray, 2), RefineryStageBlock.Entity.class);
        long total = 0;
        for (var view : stage.outlet()) total += view.getAmount() / ProcessTank.MILLIBUCKET;
        return total;
    }

    @GameTest(maxTicks = 300)
    public void aColumnOfTwoTraysCatchesHeavyOilAndDieselAndFlaresTheRest(GameTestHelper helper) {
        RefineryBaseBlock.Entity base = column(helper, 2);
        helper.succeedWhen(() -> {
            helper.assertTrue(base.crude.millibuckets() <= 500, "The base boils its crude oil: " + base.crude.millibuckets());
            long batches = (1000 - base.crude.millibuckets()) / RefineryBaseBlock.BATCH;
            helper.assertTrue(base.bitumen.millibuckets() == batches * RefineryBaseBlock.RESIDUE, "Bitumen stays in the foot");
            helper.assertTrue(held(helper, 1) == batches * 25, "The first tray holds heavy oil: " + held(helper, 1));
            helper.assertTrue(held(helper, 2) == batches * 25, "The second tray holds diesel: " + held(helper, 2));
            helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 2)).getValue(RefineryBaseBlock.RUNNING), "It is running");
        });
    }

    @GameTest(maxTicks = 300)
    public void aFullColumnSendsItsGasOutOfTheTop(GameTestHelper helper) {
        column(helper, 5);
        helper.succeedWhen(() -> {
            RefineryStageBlock.Entity top = helper.getBlockEntity(new BlockPos(2, 7, 2), RefineryStageBlock.Entity.class);
            helper.assertTrue(top.gas.kind() == GasKind.LPG && top.gas.amount() >= 4, "The top tray holds LPG: " + top.gas.amount());
            helper.assertTrue(held(helper, 4) > 0, "and the fourth naphtha");
        });
    }

    @GameTest(maxTicks = 200)
    public void aColumnWithoutFireStandsCold(GameTestHelper helper) {
        RefineryBaseBlock.Entity base = column(helper, 3);
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(base.crude.millibuckets() == 1000, "Without a fire nothing boils");
            helper.succeed();
        });
    }

    private static ProcessorBlock.Entity processor(GameTestHelper helper, ProcessorBlock block, Fluid input) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.MAGMA_BLOCK);
        helper.setBlock(pos, block);
        ProcessorBlock.Entity entity = helper.getBlockEntity(pos, ProcessorBlock.Entity.class);
        pour(entity.input, input, 1000);
        return entity;
    }

    @GameTest(maxTicks = 300)
    public void theCrackerBreaksHeavyOilIntoDieselAndGasoline(GameTestHelper helper) {
        ProcessorBlock.Entity cracker = processor(helper, RefineryBlocks.CRACKER, RefineryProduct.HEAVY_OIL.source());
        helper.succeedWhen(() -> {
            helper.assertTrue(cracker.input.millibuckets() <= 800, "It takes heavy oil in: " + cracker.input.millibuckets());
            helper.assertTrue(cracker.outputs.get(0).getResource().isOf(RefineryProduct.DIESEL.source())
                    && cracker.outputs.get(0).millibuckets() > 0, "and gives diesel");
            helper.assertTrue(cracker.outputs.get(1).getResource().isOf(RefineryProduct.GASOLINE.source())
                    && cracker.outputs.get(1).millibuckets() > 0, "and gasoline");
            helper.assertTrue(cracker.gas.kind() == GasKind.LPG, "and a little gas");
        });
    }

    @GameTest(maxTicks = 300)
    public void theReformerTurnsNaphthaIntoGasoline(GameTestHelper helper) {
        ProcessorBlock.Entity reformer = processor(helper, RefineryBlocks.REFORMER, RefineryProduct.NAPHTHA.source());
        helper.succeedWhen(() -> {
            long done = (1000 - reformer.input.millibuckets()) / 100;
            helper.assertTrue(done >= 2, "It takes naphtha in");
            helper.assertTrue(reformer.outputs.get(0).millibuckets() == done * 85, "and gives gasoline: " + reformer.outputs.get(0).millibuckets());
        });
    }

    @GameTest(maxTicks = 100)
    public void aTorchInHandSetsOffRichFiredamp(GameTestHelper helper) {
        BlockPos feet = new BlockPos(2, 1, 2);
        for (int y = 1; y <= 2; y++) helper.setBlock(feet.atY(y), Gases.with(Blocks.AIR.defaultBlockState(), Gas.METHANE, Gas.CAPACITY));
        @SuppressWarnings("removal") ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos absolute = helper.absolutePos(feet);
        player.snapTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH));
        helper.succeedWhen(() -> {
            helper.assertTrue(Gases.units(helper.getBlockState(feet), Gas.METHANE) == 0
                    && Gases.units(helper.getBlockState(feet.above()), Gas.METHANE) == 0, "The methane has gone off");
        });
    }

    @GameTest(maxTicks = 200)
    public void gasPassesThroughWhatOneWalksThrough(GameTestHelper helper) {
        // A sealed tunnel: carbon dioxide at one end, a pressure plate in the way, a pit beyond it.
        for (int x = 0; x <= 4; x++) {
            for (int y = 0; y <= 3; y++) {
                for (int z = 0; z <= 2; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        BlockPos gas = new BlockPos(1, 2, 1), plate = new BlockPos(2, 2, 1), beyond = new BlockPos(3, 2, 1), pit = new BlockPos(3, 1, 1);
        helper.setBlock(plate, Blocks.STONE_PRESSURE_PLATE);
        helper.setBlock(beyond, Blocks.AIR);
        helper.setBlock(pit, Blocks.AIR);
        helper.setBlock(gas, Gases.with(Blocks.AIR.defaultBlockState(), Gas.CARBON_DIOXIDE, 4));
        helper.succeedWhen(() -> {
            helper.assertTrue(Gases.total(helper.getBlockState(pit)) == 4,
                    "The gas went past the pressure plate into the pit");
            helper.assertBlockPresent(Blocks.STONE_PRESSURE_PLATE, plate);
        });
    }

    @GameTest
    public void theStillsBottlesAreLiquidsATankTakesAndGives(GameTestHelper helper) {
        var bottle = net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext
                .withConstant(new ItemStack(de.ipnats.hardwrought.core.registry.ModItems.HEAVY_OIL))
                .find(net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.ITEM);
        helper.assertTrue(bottle != null, "A bottle of heavy oil is a liquid container");
        try (Transaction transaction = Transaction.openOuter()) {
            long out = bottle.extract(FluidVariant.of(RefineryProduct.HEAVY_OIL.source()),
                    net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET, transaction);
            helper.assertTrue(out == net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BOTTLE, "holding a third of a bucket: " + out);
        }
        var empty = net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext
                .withConstant(new ItemStack(Items.GLASS_BOTTLE))
                .find(net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.ITEM);
        for (RefineryProduct product : new RefineryProduct[] {RefineryProduct.LIGHT_FRACTION, RefineryProduct.FUEL_FRACTION}) {
            try (Transaction transaction = Transaction.openOuter()) {
                long in = empty.insert(FluidVariant.of(product.source()),
                        net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET, transaction);
                helper.assertTrue(in == net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BOTTLE, "A glass bottle fills with " + product);
            }
        }
        for (RefineryProduct product : RefineryProduct.values()) {
            helper.assertTrue(de.ipnats.hardwrought.fluids.FluidDisplayBlock.Shown.of(product.source()) != null,
                    "A tank shows " + product);
            helper.assertTrue(product.bucket() != null, product + " has a bucket");
        }
        helper.succeed();
    }

    @GameTest
    public void theCompendiumKnowsHowOilIsRefined(GameTestHelper helper) {
        var recipes = de.ipnats.hardwrought.knowledge.WorldRecipes.all();
        for (String id : new String[] {"drilling/crude_oil", "still/light_fraction", "refinery/heavy_oil", "refinery/diesel",
                "refinery/kerosene", "refinery/naphtha", "refinery/bitumen", "refinery/lpg", "cracker/diesel",
                "cracker/gasoline", "reformer/gasoline", "gas_tank/lpg_canister", "bottling/heavy_oil"}) {
            helper.assertTrue(recipes.stream().anyMatch(r -> r.id().equals(de.ipnats.hardwrought.Hardwrought.id(id))),
                    "The compendium has " + id);
        }
        helper.assertTrue(recipes.stream().anyMatch(r -> r.makes(RefineryProduct.GASOLINE.bucket())
                && r.uses(RefineryBlocks.REFORMER.asItem())), "Gasoline is shown as made in the reformer");
        helper.succeed();
    }

    /** A lit burning stack on a liquid tank holding so many buckets of a liquid. */
    private static void stackOnTank(GameTestHelper helper, Fluid liquid, int buckets) {
        BlockPos tank = new BlockPos(2, 1, 2);
        helper.setBlock(tank, de.ipnats.hardwrought.fluids.FluidBlocks.TANK);
        try (Transaction transaction = Transaction.openOuter()) {
            de.ipnats.hardwrought.fluids.FluidTankBlock.storage(helper.getLevel(), helper.absolutePos(tank))
                    .insert(FluidVariant.of(liquid), net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET * buckets, transaction);
            transaction.commit();
        }
        helper.setBlock(tank.above(), RefineryBlocks.FLARE.defaultBlockState().setValue(de.ipnats.hardwrought.refining.FlareStackBlock.LIT, true));
    }

    private static long tankMillibuckets(GameTestHelper helper) {
        long total = 0;
        for (var view : de.ipnats.hardwrought.fluids.FluidTankBlock.storage(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)))) {
            total += view.getAmount() / ProcessTank.MILLIBUCKET;
        }
        return total;
    }

    private static int carbonDioxideAround(GameTestHelper helper) {
        int units = 0;
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 0, 0), new BlockPos(5, 6, 5))) {
            units += Gases.units(helper.getBlockState(pos), Gas.CARBON_DIOXIDE);
        }
        return units;
    }

    @GameTest(maxTicks = 200)
    public void aBurningStackBurnsOffDieselAndGivesOffCarbonDioxide(GameTestHelper helper) {
        stackOnTank(helper, RefineryProduct.DIESEL.source(), 4);
        helper.succeedWhen(() -> {
            helper.assertTrue(tankMillibuckets(helper) <= 4000 - 3 * de.ipnats.hardwrought.refining.FlareStackBlock.LIQUID_PER_SECOND,
                    "The diesel burns away: " + tankMillibuckets(helper));
            helper.assertTrue(carbonDioxideAround(helper) >= 6, "and goes up as carbon dioxide: " + carbonDioxideAround(helper));
        });
    }

    @GameTest(maxTicks = 100)
    public void aBurningStackLeavesWaterAloneAndGoesOut(GameTestHelper helper) {
        stackOnTank(helper, net.minecraft.world.level.material.Fluids.WATER, 2);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(tankMillibuckets(helper) == 2000, "Water does not burn");
            helper.assertTrue(!helper.getBlockState(new BlockPos(2, 2, 2)).getValue(de.ipnats.hardwrought.refining.FlareStackBlock.LIT),
                    "and with nothing to burn the stack goes out");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 200)
    public void aBurningStackBurnsFiredampSafely(GameTestHelper helper) {
        BlockPos tank = new BlockPos(2, 1, 2), stack = tank.above(), gas = stack.east();
        helper.setBlock(tank, GasLines.TANK);
        helper.setBlock(stack, RefineryBlocks.FLARE.defaultBlockState().setValue(de.ipnats.hardwrought.refining.FlareStackBlock.LIT, true));
        // A wall around the gas so that it stays at the flame.
        for (BlockPos wall : new BlockPos[] {gas.east(), gas.north(), gas.south(), gas.above(), gas.below()}) helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(gas, Gases.with(Blocks.AIR.defaultBlockState(), Gas.METHANE, 5));
        helper.succeedWhen(() -> {
            helper.assertTrue(Gases.units(helper.getBlockState(gas), Gas.METHANE) == 0, "The methane at the stack is burnt");
            helper.assertBlockPresent(RefineryBlocks.FLARE, stack);
            helper.assertBlockPresent(Blocks.STONE, gas.above());
            helper.assertTrue(carbonDioxideAround(helper) > 0, "into carbon dioxide, and nothing blew up");
        });
    }

    @GameTest(maxTicks = 300)
    public void aGasLineFeedsABurningStack(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 2, 2), stand = new BlockPos(3, 1, 2), stack = stand.above();
        helper.setBlock(source, GasLines.TANK);
        helper.setBlock(source.east(), GasLines.LINES.get(GasLineTier.IRON));
        helper.setBlock(source.east(2), GasLines.LINES.get(GasLineTier.IRON));
        helper.setBlock(stand, GasLines.TANK);
        helper.setBlock(stack, RefineryBlocks.FLARE.defaultBlockState().setValue(de.ipnats.hardwrought.refining.FlareStackBlock.LIT, true));
        helper.getBlockEntity(source, GasTankBlock.Entity.class).store.insert(GasKind.LPG, 100);
        helper.succeedWhen(() -> {
            double left = helper.getBlockEntity(source, GasTankBlock.Entity.class).store.amount();
            helper.assertTrue(left <= 60, "The LPG runs down the line into the flame: " + left);
            helper.assertTrue(helper.getBlockState(stack).getValue(de.ipnats.hardwrought.refining.FlareStackBlock.LIT), "which stays lit");
        });
    }
}
