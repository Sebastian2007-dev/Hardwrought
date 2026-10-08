package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.metallurgy.Alloys;
import de.ipnats.hardwrought.metallurgy.OrePowders;
import de.ipnats.hardwrought.smeltery.CastingTableBlockEntity;
import de.ipnats.hardwrought.smeltery.FaucetBlock;
import de.ipnats.hardwrought.smeltery.FaucetBlockEntity;
import de.ipnats.hardwrought.smeltery.MoltenMetals;
import de.ipnats.hardwrought.smeltery.SmelteryBlocks;
import de.ipnats.hardwrought.smeltery.SmelteryControllerBlock;
import de.ipnats.hardwrought.smeltery.SmelteryControllerBlockEntity;
import de.ipnats.hardwrought.smeltery.SmelteryStructure;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The smeltery: its shape, the heat it has to reach, and the whole way from iron and coal to a cast
 * steel bar.
 */
public final class SmelteryGameTests {
    @GameTest
    public void theSmelteryHasToBeHotterThanAForge(GameTestHelper helper) {
        var materials = CoreLifecycle.require(helper.getLevel().getServer()).materials();
        double iron = MoltenMetals.meltingPoint("iron", materials);
        double copper = MoltenMetals.meltingPoint("copper", materials);
        double tungsten = MoltenMetals.meltingPoint("tungsten", materials);
        helper.assertTrue(copper < SmelteryControllerBlockEntity.COAL_C && iron > SmelteryControllerBlockEntity.COAL_C,
                "Coal alone melts copper but never iron");
        helper.assertTrue(iron < SmelteryControllerBlockEntity.COAL_BLOWN_C && iron < SmelteryControllerBlockEntity.COKE_C,
                "iron wants a bellows or coke");
        helper.assertTrue(tungsten > SmelteryControllerBlockEntity.COKE_C && tungsten < SmelteryControllerBlockEntity.COKE_BLOWN_C,
                "and tungsten both");
        helper.succeed();
    }

    @GameTest
    public void aTankLayerCanBeSelectedForTheNextCast(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, SmelteryBlocks.CONTROLLER.defaultBlockState());
        SmelteryControllerBlockEntity smeltery =
                (SmelteryControllerBlockEntity) helper.getLevel().getBlockEntity(pos);
        smeltery.addFluidForTesting("iron", MoltenMetals.INGOT);
        smeltery.addFluidForTesting("gold", MoltenMetals.INGOT);
        helper.assertTrue("iron".equals(smeltery.bottomCastable()), "The oldest layer starts at the bottom");
        helper.assertTrue(smeltery.selectForCasting("gold") && "gold".equals(smeltery.bottomCastable()),
                "Clicking a visible layer makes it the next metal poured");
        helper.assertFalse(smeltery.selectForCasting("carbon"), "A missing or uncastable bath cannot be selected");
        helper.succeed();
    }

    /** Builds a smeltery with a tank of one by one by two, a drain east of it, a faucet and a table. */
    private static BlockPos build(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var bricks = SmelteryBlocks.SMELTERY_BRICKS.defaultBlockState();
        level.setBlockAndUpdate(helper.absolutePos(new BlockPos(2, 1, 2)), bricks);
        for (int y = 2; y <= 3; y++) {
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(2, y, 2)), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(2, y, 1)), bricks);
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(2, y, 3)), bricks);
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(1, y, 2)), y == 2
                    ? SmelteryBlocks.CONTROLLER.defaultBlockState().setValue(SmelteryControllerBlock.FACING, helper.getTestRotation().rotate(Direction.WEST))
                    : bricks);
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(3, y, 2)), y == 2 ? SmelteryBlocks.DRAIN.defaultBlockState() : bricks);
        }
        return helper.absolutePos(new BlockPos(1, 2, 2));
    }

    @GameTest
    public void aSmelteryIsFoundFromItsController(GameTestHelper helper) {
        BlockPos controller = build(helper);
        var found = SmelteryStructure.scan(helper.getLevel(), controller,
                helper.getLevel().getBlockState(controller).getValue(SmelteryControllerBlock.FACING));
        helper.assertTrue(found != null && found.volume() == 2 && found.drains().size() == 1,
                "A tank one block wide and two high, with its drain: " + found);
        helper.getLevel().setBlockAndUpdate(helper.absolutePos(new BlockPos(2, 1, 2)), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        helper.assertTrue(SmelteryStructure.scan(helper.getLevel(), controller,
                        helper.getLevel().getBlockState(controller).getValue(SmelteryControllerBlock.FACING)) == null,
                "Without a floor of smeltery bricks it is not a smeltery");
        helper.succeed();
    }

    @GameTest(maxTicks = 600)
    public void ironAndCoalBecomeSteelAndACastBar(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controllerPos = build(helper);
        Direction east = helper.getTestRotation().rotate(Direction.EAST);
        BlockPos faucetPos = helper.absolutePos(new BlockPos(4, 2, 2));
        level.setBlockAndUpdate(faucetPos, SmelteryBlocks.FAUCET.defaultBlockState().setValue(FaucetBlock.FACING, east));
        BlockPos tablePos = helper.absolutePos(new BlockPos(4, 1, 2));
        level.setBlockAndUpdate(tablePos, SmelteryBlocks.CASTING_TABLE.defaultBlockState());

        SmelteryControllerBlockEntity smeltery = (SmelteryControllerBlockEntity) level.getBlockEntity(controllerPos);
        smeltery.setItem(SmelteryControllerBlockEntity.FUEL_SLOT, new ItemStack(SmelteryBlocks.COKE, 4));
        for (int i = 0; i < 4; i++) smeltery.setItem(1 + i, new ItemStack(Items.IRON_INGOT));
        smeltery.setItem(5, new ItemStack(OrePowders.powder(OrePowders.VanillaOre.COAL)));
        smeltery.setItem(6, new ItemStack(OrePowders.powder(OrePowders.VanillaOre.COAL)));
        smeltery.setTemperatureForTesting(1700);
        CastingTableBlockEntity table = (CastingTableBlockEntity) level.getBlockEntity(tablePos);
        table.placeCast(new ItemStack(SmelteryBlocks.INGOT_CAST));

        boolean[] opened = {false};
        helper.succeedWhen(() -> {
            int steel = smeltery.fluids().getOrDefault("steel", 0);
            if (!opened[0]) {
                // A faucet pours the lowest layer: wait until all the iron has become steel.
                helper.assertTrue(steel >= 4 * MoltenMetals.INGOT && !smeltery.fluids().containsKey("iron"),
                        "Iron and carbon alloy into steel in the tank: " + smeltery.fluids());
                ((FaucetBlockEntity) level.getBlockEntity(faucetPos)).open();
                opened[0] = true;
            }
            helper.assertTrue(table.result().is(Alloys.STEEL_INGOT),
                    "The faucet pours it into the cast, and it sets into a steel bar: " + table.amount() + " mB of " + table.metal());
        });
    }

    @GameTest(maxTicks = 200)
    public void aTankOfLavaInTheWallFiresTheSmeltery(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controllerPos = build(helper);
        // The south wall of the lower layer becomes a tank, filled with one bucket.
        BlockPos tankPos = helper.absolutePos(new BlockPos(2, 2, 3));
        level.setBlockAndUpdate(tankPos, SmelteryBlocks.TANK.defaultBlockState());
        var tank = (de.ipnats.hardwrought.smeltery.SmelteryTankBlockEntity) level.getBlockEntity(tankPos);
        tank.fill(de.ipnats.hardwrought.smeltery.SmelteryTankBlockEntity.BUCKET);
        SmelteryControllerBlockEntity smeltery = (SmelteryControllerBlockEntity) level.getBlockEntity(controllerPos);
        smeltery.setItem(1, new ItemStack(Items.IRON_INGOT));
        helper.succeedWhen(() -> {
            helper.assertTrue(tank.lava() < de.ipnats.hardwrought.smeltery.SmelteryTankBlockEntity.BUCKET,
                    "The smeltery burns lava from its tank when it has no coal: " + tank.lava());
            helper.assertTrue(smeltery.data().get(1) == (int) SmelteryControllerBlockEntity.LAVA_C,
                    "and heads for the heat of lava: " + smeltery.data().get(1));
        });
    }

    @GameTest
    public void stackedTanksJoinAndFillFromTheBottom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lower = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos upper = lower.above();
        level.setBlockAndUpdate(lower, SmelteryBlocks.TANK.defaultBlockState());
        level.setBlockAndUpdate(upper, SmelteryBlocks.TANK.getStateForPlacement(
                new net.minecraft.world.item.context.DirectionalPlaceContext(level, upper, Direction.DOWN,
                        ItemStack.EMPTY, Direction.UP)));
        helper.assertTrue(level.getBlockState(lower).getValue(net.minecraft.world.level.block.PipeBlock.UP)
                        && level.getBlockState(upper).getValue(net.minecraft.world.level.block.PipeBlock.DOWN),
                "Two tanks one on the other join");
        var bottom = (de.ipnats.hardwrought.smeltery.SmelteryTankBlockEntity) level.getBlockEntity(lower);
        var top = (de.ipnats.hardwrought.smeltery.SmelteryTankBlockEntity) level.getBlockEntity(upper);
        int poured = de.ipnats.hardwrought.smeltery.SmelteryTankBlockEntity.fillGroup(level, upper, 5000);
        helper.assertTrue(poured == 5000 && bottom.lava() == 4000 && top.lava() == 1000,
                "Poured in at the top, the lava fills the bottom tank first: " + bottom.lava() + " / " + top.lava());
        de.ipnats.hardwrought.smeltery.SmelteryTankBlockEntity.drainGroup(level, lower, 1500);
        helper.assertTrue(top.lava() == 0 && bottom.lava() == 3500,
                "and is taken from the top first: " + bottom.lava() + " / " + top.lava());
        helper.succeed();
    }

    @GameTest
    public void theJournalThinksOfTheSmelteryAndSteelBeforeOil(GameTestHelper helper) {
        var ids = de.ipnats.hardwrought.knowledge.Journal.chain().stream()
                .map(entry -> entry.id().getPath()).toList();
        helper.assertTrue(ids.indexOf("a_bath_of_metal") < ids.indexOf("harder_than_iron")
                        && ids.indexOf("harder_than_iron") < ids.indexOf("black_gold")
                        && ids.indexOf("a_bath_of_metal") >= 0,
                "The smeltery, then steel, then oil: " + ids);
        helper.assertTrue(de.ipnats.hardwrought.knowledge.Multiblocks.containing(
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(SmelteryBlocks.CONTROLLER)).size() == 1,
                "The controller's page shows how a smeltery is built");
        helper.succeed();
    }
}
