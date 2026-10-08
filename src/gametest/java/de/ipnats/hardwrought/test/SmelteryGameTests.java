package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.metallurgy.Alloys;
import de.ipnats.hardwrought.metallurgy.OrePowders;
import de.ipnats.hardwrought.smeltery.CastingTableBlockEntity;
import de.ipnats.hardwrought.smeltery.Casts;
import de.ipnats.hardwrought.smeltery.FaucetBlock;
import de.ipnats.hardwrought.smeltery.FaucetBlockEntity;
import de.ipnats.hardwrought.smeltery.MoltenMetals;
import de.ipnats.hardwrought.smeltery.SmelteryBlocks;
import de.ipnats.hardwrought.smeltery.SmelteryControllerBlock;
import de.ipnats.hardwrought.smeltery.SmelteryControllerBlockEntity;
import de.ipnats.hardwrought.smeltery.SmelteryStructure;
import de.ipnats.hardwrought.smithing.ForgeQuality;
import de.ipnats.hardwrought.smithing.Grinding;
import de.ipnats.hardwrought.smithing.ToolParts;
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
        ItemStack block = new ItemStack(SmelteryBlocks.COKE_BLOCK);
        helper.assertTrue(SmelteryControllerBlockEntity.isCoke(block)
                        && SmelteryControllerBlockEntity.fuelTicks(block) == 9 * SmelteryControllerBlockEntity.fuelTicks(new ItemStack(SmelteryBlocks.COKE))
                        && de.ipnats.hardwrought.smithing.ForgeBlock.fuelValue(block) > de.ipnats.hardwrought.smithing.ForgeBlock.fuelValue(new ItemStack(Items.COAL_BLOCK)),
                "A block of coke is coke, burns as nine pieces, and outlasts a block of coal in the forge");
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

    /**
     * A forged part pressed into a blank leaves a cast; what is poured in that cast is the part again,
     * but rough, and a grindstone makes a decent part of it and no more than that.
     */
    @GameTest(maxTicks = 400)
    public void aPartIsCastRoughAndGroundBetter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos controllerPos = build(helper);
        Direction east = helper.getTestRotation().rotate(Direction.EAST);
        BlockPos faucetPos = helper.absolutePos(new BlockPos(4, 2, 2));
        level.setBlockAndUpdate(faucetPos, SmelteryBlocks.FAUCET.defaultBlockState().setValue(FaucetBlock.FACING, east));
        BlockPos tablePos = helper.absolutePos(new BlockPos(4, 1, 2));
        level.setBlockAndUpdate(tablePos, SmelteryBlocks.CASTING_TABLE.defaultBlockState());
        CastingTableBlockEntity table = (CastingTableBlockEntity) level.getBlockEntity(tablePos);

        ItemStack forged = new ItemStack(ToolParts.part(ToolParts.SmithMetal.IRON, ToolParts.Part.PICKAXE_HEAD));
        Casts.Cast pickaxe = Casts.imprintOf(forged);
        helper.assertTrue(pickaxe != null && pickaxe.part() == ToolParts.Part.PICKAXE_HEAD
                        && pickaxe.amount() == 3 * MoltenMetals.INGOT, "A pickaxe head leaves a cast of three bars");
        helper.assertTrue(Casts.imprintOf(new ItemStack(Items.IRON_INGOT)) == Casts.of(new ItemStack(SmelteryBlocks.INGOT_CAST)),
                "and a bar leaves the cast of a bar");
        table.placeCast(new ItemStack(Casts.BLANK));
        helper.assertFalse(table.accepts("iron"), "Nothing is poured into a blank");
        helper.assertTrue(table.imprint(forged) == pickaxe && table.cast().is(pickaxe.unfired()) && forged.getCount() == 1,
                "Pressed into the blank, the part leaves its shape and is not used up");
        helper.assertFalse(table.accepts("iron"), "nor into a cast that has not been fired");
        helper.assertTrue(table.take().is(pickaxe.unfired()), "The unfired cast is taken off to the furnace");
        table.placeCast(new ItemStack(pickaxe.fired()));
        helper.assertTrue(table.accepts("iron") && !table.accepts("tin"), "Iron makes a pickaxe head; tin makes none");

        SmelteryControllerBlockEntity smeltery = (SmelteryControllerBlockEntity) level.getBlockEntity(controllerPos);
        smeltery.addFluidForTesting("iron", 3 * MoltenMetals.INGOT);
        helper.succeedWhen(() -> {
            if (table.result().isEmpty()) ((FaucetBlockEntity) level.getBlockEntity(faucetPos)).open();
            ItemStack cast = table.result();
            helper.assertTrue(cast.is(forged.getItem()), "Three bars of iron set into a pickaxe head: " + table.amount() + " mB");
            ForgeQuality rough = ForgeQuality.of(cast);
            helper.assertTrue(rough != null && rough.craftsmanship() == Casts.CAST_CRAFTSMANSHIP, "It comes out rough: " + rough);
            helper.assertTrue(smeltery.fluidTotal() == 0, "and it took all three bars: " + smeltery.fluids());
            // Eight passes and no more: dead on each time, the stone adds all it has to give.
            int passes = 0;
            while (Grinding.grind(cast, Grinding.Outcome.PERFECT)) passes++;
            ForgeQuality ground = ForgeQuality.of(cast);
            helper.assertTrue(passes == Grinding.PASSES && Grinding.passesLeft(cast) == 0
                            && Math.abs(ground.total() - (Casts.CAST_CRAFTSMANSHIP + Grinding.MAX_POLISH)) < 0.001f,
                    "A grindstone adds its share and then has no more to give: " + passes + " passes, " + ground);
            // Polish is the grindstone's own: on a perfectly forged part it goes beyond anything the anvil gives.
            ItemStack best = forged.copy(), clumsy = forged.copy();
            best.set(ModDataComponents.FORGE_QUALITY, new ForgeQuality(1f, ForgeQuality.Treatment.AIR));
            clumsy.set(ModDataComponents.FORGE_QUALITY, new ForgeQuality(1f, ForgeQuality.Treatment.AIR));
            double unground = ForgeQuality.of(best).speedFactor();
            for (int i = 0; i < Grinding.PASSES; i++) {
                Grinding.grind(best, Grinding.Outcome.PERFECT);
                Grinding.grind(clumsy, i % 2 == 0 ? Grinding.Outcome.GOOD : Grinding.Outcome.MISS);
            }
            helper.assertTrue(ForgeQuality.of(best).total() > 1.3f && ForgeQuality.of(best).speedFactor() > unground
                            && ForgeQuality.of(best).protectionFactor() > 1.15,
                    "The best part, ground dead on, stands well above 100 %: " + ForgeQuality.of(best));
            helper.assertTrue(ForgeQuality.of(clumsy).total() < 1.1f && ForgeQuality.of(clumsy).polish() >= 0,
                    "Every other pass a scratch leaves little polish, and never less than none: " + ForgeQuality.of(clumsy));
        });
    }

    /** A hopper feeding the smeltery lays one piece in each melting place, never a stack in one. */
    @GameTest(maxTicks = 200)
    public void aHopperFillsTheMeltingPlacesOnePieceEach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos, SmelteryBlocks.CONTROLLER.defaultBlockState());
        level.setBlockAndUpdate(pos.above(), net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState());
        SmelteryControllerBlockEntity smeltery = (SmelteryControllerBlockEntity) level.getBlockEntity(pos);
        var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) level.getBlockEntity(pos.above());
        hopper.setItem(0, new ItemStack(Items.RAW_COPPER, 5));
        helper.succeedWhen(() -> {
            helper.assertTrue(hopper.isEmpty(), "The hopper empties into the smeltery");
            int filled = 0;
            for (int place = 1; place < SmelteryControllerBlockEntity.CONTAINER_SIZE; place++) {
                ItemStack lying = smeltery.getItem(place);
                helper.assertTrue(lying.getCount() <= 1, "No melting place holds more than one piece: " + lying);
                if (!lying.isEmpty()) filled++;
            }
            helper.assertTrue(filled == 5 && smeltery.getItem(SmelteryControllerBlockEntity.FUEL_SLOT).isEmpty(),
                    "Five pieces lie in five places, and ore is not put with the fuel: " + filled);
        });
    }

    /** A hopper under the table takes what has set and leaves the cast, so casting can run unattended. */
    @GameTest(maxTicks = 200)
    public void aHopperUnderTheTableDrawsTheBarAndLeavesTheCast(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tablePos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(tablePos.below(), net.minecraft.world.level.block.Blocks.HOPPER.defaultBlockState());
        level.setBlockAndUpdate(tablePos, SmelteryBlocks.CASTING_TABLE.defaultBlockState());
        CastingTableBlockEntity table = (CastingTableBlockEntity) level.getBlockEntity(tablePos);
        var hopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) level.getBlockEntity(tablePos.below());
        table.placeCast(new ItemStack(SmelteryBlocks.INGOT_CAST));
        helper.runAfterDelay(20, () -> helper.assertTrue(hopper.isEmpty() && table.cast().is(SmelteryBlocks.INGOT_CAST),
                "An empty cast is not a thing a hopper takes"));
        helper.runAfterDelay(21, () -> table.fill("gold", MoltenMetals.INGOT));
        helper.succeedWhen(() -> {
            helper.assertTrue(hopper.getItem(0).is(Items.GOLD_INGOT), "The hopper draws the bar out once it has set");
            helper.assertTrue(table.result().isEmpty() && table.cast().is(SmelteryBlocks.INGOT_CAST) && table.accepts("gold"),
                    "and the cast stays, ready for the next pour");
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
