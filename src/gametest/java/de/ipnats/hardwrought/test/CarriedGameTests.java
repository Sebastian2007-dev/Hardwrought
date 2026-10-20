package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.carried.CarriedItems;
import de.ipnats.hardwrought.carried.CarriedPower;
import de.ipnats.hardwrought.carried.Cell;
import de.ipnats.hardwrought.carried.ChainsawItem;
import de.ipnats.hardwrought.carried.Cells;
import de.ipnats.hardwrought.carried.DrillItem;
import de.ipnats.hardwrought.carried.OreScannerItem;
import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.electricity.DynamoBlock;
import de.ipnats.hardwrought.electricity.ElectricBlocks;
import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.electricity.Gauge;
import de.ipnats.hardwrought.haven.Appliance;
import de.ipnats.hardwrought.haven.ApplianceBlock;
import de.ipnats.hardwrought.haven.ChargerBlockEntity;
import de.ipnats.hardwrought.haven.HavenBlocks;
import de.ipnats.hardwrought.machinery.CrankBoxBlock;
import de.ipnats.hardwrought.progression.BenchTier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Carried power (docs/electricity-roadmap.md, stage E4): cells and their charging, the drill, the
 * chainsaw, the ore scanner, the worn devices, the linings and the assembly bench.
 */
public final class CarriedGameTests {
    private static ItemStack charged(Cell cell, double fraction) {
        ItemStack stack = new ItemStack(switch (cell) {
            case LEAD_ACID -> CarriedItems.LEAD_ACID_PACK;
            case NICKEL_IRON -> CarriedItems.NICKEL_IRON_CELL;
            case SILVER_ZINC -> CarriedItems.SILVER_ZINC_CELL;
        });
        stack.set(Cells.CHARGE, (float) (cell.joules() * fraction));
        return stack;
    }

    private static ItemStack device(net.minecraft.world.item.Item item, ItemStack cell, ItemStack part) {
        ItemStack stack = new ItemStack(item);
        if (!cell.isEmpty()) stack.set(Cells.CELL, cell);
        if (!part.isEmpty()) stack.set(Cells.WORKING_PART, part);
        return stack;
    }

    @GameTest(maxTicks = 200)
    public void aChargingStationFillsCellsEachAtItsOwnPace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(140);
        List<BlockPos> used = new ArrayList<>();
        place(level, used, base.east(), ElectricBlocks.DYNAMO.defaultBlockState().setValue(DynamoBlock.FACING, Direction.EAST));
        place(level, used, base, ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
        BlockPos charger = base.offset(0, 0, 4);
        place(level, used, charger, HavenBlocks.of(Appliance.CHARGING_STATION).defaultBlockState());
        Electricity.connect(level, base.east(), charger, Gauge.WIRE);
        var station = (ChargerBlockEntity) level.getBlockEntity(charger);
        station.items().setItem(0, charged(Cell.NICKEL_IRON, 0.0));
        station.items().setItem(1, device(CarriedItems.ELECTRIC_DRILL, charged(Cell.SILVER_ZINC, 0.0), ItemStack.EMPTY));
        long start = level.getGameTime();
        helper.runAfterDelay(160, () -> {
            try {
                double seconds = (level.getGameTime() - start) / 20.0;
                double slow = Cells.charge(station.items().getItem(0));
                double fast = Cells.charge(Cells.cellOf(station.items().getItem(1)));
                helper.assertTrue(slow > 0.0 && slow <= Cell.NICKEL_IRON.chargeWatts() * seconds * Cells.CHARGE_EFFICIENCY + 1.0,
                        "A nickel–iron cell charges, no faster than its 20 W: " + slow + " Ws in " + seconds + " s");
                helper.assertTrue(fast > slow * 2.0, "A silver–zinc cell in a drill charges much faster: " + fast);
                helper.assertTrue(level.getBlockState(charger).getValue(ApplianceBlock.ON), "The station works while it has something to charge");
                ItemStack zinc = Cells.cellOf(station.items().getItem(1));
                helper.assertTrue(zinc.getOrDefault(Cells.WORN, 0.0f) > 0.0f, "and the silver–zinc cell wears by what it takes");
            } finally {
                clear(level, used);
            }
            helper.succeed();
        });
    }

    @GameTest
    public void aDrillWithoutChargeIsABlockOfMetal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        ItemStack bare = device(CarriedItems.ELECTRIC_DRILL, ItemStack.EMPTY, new ItemStack(CarriedItems.STEEL_DRILL_BIT));
        ItemStack flat = device(CarriedItems.ELECTRIC_DRILL, charged(Cell.LEAD_ACID, 0.0), new ItemStack(CarriedItems.STEEL_DRILL_BIT));
        ItemStack noBit = device(CarriedItems.ELECTRIC_DRILL, charged(Cell.LEAD_ACID, 1.0), ItemStack.EMPTY);
        ItemStack steel = device(CarriedItems.ELECTRIC_DRILL, charged(Cell.LEAD_ACID, 1.0), new ItemStack(CarriedItems.STEEL_DRILL_BIT));
        ItemStack tungsten = device(CarriedItems.ELECTRIC_DRILL, charged(Cell.LEAD_ACID, 1.0), new ItemStack(CarriedItems.TUNGSTEN_STEEL_DRILL_BIT));
        for (ItemStack dead : List.of(bare, flat, noBit)) {
            helper.assertTrue(dead.getDestroySpeed(stone) <= 1.0f, "No cell, a flat cell or no bit: it does not dig " + dead);
        }
        helper.assertTrue(steel.getDestroySpeed(stone) > 10.0f, "Charged and with a steel bit it digs fast: " + steel.getDestroySpeed(stone));
        helper.assertTrue(tungsten.getDestroySpeed(stone) > steel.getDestroySpeed(stone), "and with a tungsten-steel bit faster");
        helper.assertTrue(steel.getDestroySpeed(Blocks.DIRT.defaultBlockState()) > 10.0f, "Earth as well as stone");
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        double before = Cells.charge(Cells.cellOf(steel));
        steel.getItem().mineBlock(steel, level, stone, at, helper.makeMockServerPlayerInLevel());
        helper.assertTrue(Cells.charge(Cells.cellOf(steel)) == before - DrillItem.JOULES_PER_BLOCK, "A block costs its charge");
        helper.assertTrue(Cells.part(steel).getDamageValue() == 1, "and a little of its bit");
        helper.succeed();
    }

    @GameTest
    public void aChainsawBringsDownTheWholeTree(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos foot = helper.absolutePos(new BlockPos(2, 1, 2));
        List<BlockPos> logs = new ArrayList<>();
        for (int y = 0; y < 6; y++) logs.add(foot.above(y));
        logs.add(foot.above(4).east());
        logs.add(foot.above(5).east().north());
        for (BlockPos log : logs) level.setBlockAndUpdate(log, Blocks.OAK_LOG.defaultBlockState());
        ItemStack saw = device(CarriedItems.CHAINSAW, charged(Cell.SILVER_ZINC, 1.0), new ItemStack(CarriedItems.SAW_CHAIN));
        var player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(ChainsawItem.tree(level, foot).size() == logs.size() - 1, "Every log joined above the cut is part of the tree");
        BlockState cut = level.getBlockState(foot);
        level.setBlockAndUpdate(foot, Blocks.AIR.defaultBlockState());
        double before = Cells.charge(Cells.cellOf(saw));
        saw.getItem().mineBlock(saw, level, cut, foot, player);
        for (BlockPos log : logs) helper.assertTrue(level.getBlockState(log).isAir(), "The whole tree is down: " + log);
        helper.assertTrue(Cells.charge(Cells.cellOf(saw)) < before - ChainsawItem.JOULES_PER_LOG * (logs.size() - 1),
                "every log at its cost");
        helper.assertTrue(Cells.part(saw).getDamageValue() == logs.size(), "and the chain blunted by each");
        for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(foot).inflate(6))) item.discard();
        helper.succeed();
    }

    @GameTest
    public void anOreScannerFindsTheOreAroundIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(at.east(3), Blocks.IRON_ORE.defaultBlockState());
        level.setBlockAndUpdate(at.north(2), Blocks.DEEPSLATE_COAL_ORE.defaultBlockState());
        var found = OreScannerItem.pulse(level, null, at);
        helper.assertTrue(found.getOrDefault(Blocks.IRON_ORE, 0) == 1 && found.getOrDefault(Blocks.DEEPSLATE_COAL_ORE, 0) == 1,
                "The pulse finds the iron and the coal: " + found);
        helper.assertTrue(!OreScannerItem.ore(Blocks.STONE.defaultBlockState()), "and not the stone");
        helper.succeed();
    }

    @GameTest
    public void wornDevicesWorkOnTheirCellsAndNotWithout(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        // The exoskeleton: charged and on, it takes the weight; flat, it is weight.
        player.setItemSlot(EquipmentSlot.LEGS, device(CarriedItems.EXOSKELETON, charged(Cell.SILVER_ZINC, 1.0), ItemStack.EMPTY));
        helper.assertTrue(CarriedPower.carryFactor(player) == CarriedPower.EXO_CARRY, "A working exoskeleton carries for its wearer");
        helper.assertTrue(CarriedPower.woundFactor(player) == CarriedPower.EXO_WOUND, "and softens a wound's limp");
        double before = Cells.charge(Cells.cellOf(player.getItemBySlot(EquipmentSlot.LEGS)));
        CarriedPower.second(player);
        helper.assertTrue(Cells.charge(Cells.cellOf(player.getItemBySlot(EquipmentSlot.LEGS))) < before, "at its cell's cost");
        player.setItemSlot(EquipmentSlot.LEGS, device(CarriedItems.EXOSKELETON, charged(Cell.SILVER_ZINC, 0.0), ItemStack.EMPTY));
        helper.assertTrue(CarriedPower.carryFactor(player) < 1.0, "Flat, it makes everything worse");
        // The headlamp and the goggles.
        player.setItemSlot(EquipmentSlot.HEAD, device(CarriedItems.HEADLAMP, charged(Cell.LEAD_ACID, 1.0), ItemStack.EMPTY));
        helper.assertTrue(CarriedPower.headlampLit(player), "A charged headlamp is lit");
        player.getItemBySlot(EquipmentSlot.HEAD).set(Cells.SWITCHED_ON, false);
        helper.assertTrue(!CarriedPower.headlampLit(player), "and dark switched off");
        player.setItemSlot(EquipmentSlot.HEAD, device(CarriedItems.NIGHT_VISION_GOGGLES, charged(Cell.LEAD_ACID, 1.0), ItemStack.EMPTY));
        player.teleportTo(player.getX(), helper.absolutePos(BlockPos.ZERO).getY() - 40, player.getZ());
        CarriedPower.second(player);
        helper.assertTrue(player.hasEffect(MobEffects.NIGHT_VISION) || player.hasEffect(MobEffects.BLINDNESS),
                "Goggles worn and on see in the dark or are blinded by the light");
        // A lining in a chestplate.
        ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
        chest.set(Cells.LINING, device(CarriedItems.HEATED_LINING, charged(Cell.SILVER_ZINC, 1.0), ItemStack.EMPTY));
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        double warmed = CarriedPower.lined(player, 0.0, false);
        helper.assertTrue(warmed >= CarriedPower.LINING_DEGREES - 0.01, "A heated lining makes up fifteen degrees of cold: " + warmed);
        helper.assertTrue(CarriedPower.lined(player, 30.0, false) == 30.0, "and does nothing in the warm");
        helper.assertTrue(Cells.charge(Cells.cellOf(player.getItemBySlot(EquipmentSlot.CHEST))) < Cell.SILVER_ZINC.joules(),
                "at the cost of the cell in it, reached through the chestplate");
        ItemStack cool = new ItemStack(Items.IRON_CHESTPLATE);
        cool.set(Cells.LINING, device(CarriedItems.COOLED_LINING, charged(Cell.SILVER_ZINC, 1.0), ItemStack.EMPTY));
        player.setItemSlot(EquipmentSlot.CHEST, cool);
        helper.assertTrue(CarriedPower.lined(player, 40.0, false) < 40.0, "A cooled lining cools in the heat");
        helper.succeed();
    }

    @GameTest
    public void devicesNeedTheAssemblyBench(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (var item : List.of(CarriedItems.HEADLAMP, CarriedItems.ELECTRIC_DRILL, CarriedItems.SILVER_ZINC_CELL, CarriedItems.EXOSKELETON)) {
            helper.assertTrue(BenchTier.required(new ItemStack(item)) == BenchTier.ASSEMBLY, item + " is made at the assembly bench");
            helper.assertTrue(!BenchTier.allows(BenchTier.JOINED, new ItemStack(item)), "and not at the nailed bench");
        }
        helper.assertTrue(BenchTier.required(new ItemStack(CarriedItems.STRAP)) == BenchTier.JOINED, "Its parts are made as before");
        helper.assertTrue(BenchTier.of(CarriedItems.ASSEMBLY_BENCH_BLOCK.defaultBlockState()) == BenchTier.ASSEMBLY,
                "The assembly bench is that rung");
        helper.assertTrue(BenchTier.allows(BenchTier.ASSEMBLY, new ItemStack(Items.IRON_PICKAXE)), "and makes what lower benches make");
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(at, CarriedItems.ASSEMBLY_BENCH_BLOCK.defaultBlockState());
        helper.assertTrue(level.getBlockEntity(at) != null, "and keeps what lies on it");
        level.setBlock(at, Blocks.AIR.defaultBlockState(), 2 | net.minecraft.world.level.block.Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        helper.succeed();
    }

    private static void place(ServerLevel level, List<BlockPos> used, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state);
        used.add(pos.immutable());
    }

    private static void clear(ServerLevel level, List<BlockPos> used) {
        for (int i = used.size() - 1; i >= 0; i--) {
            level.setBlock(used.get(i), Blocks.AIR.defaultBlockState(), 2 | net.minecraft.world.level.block.Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        }
    }
}
