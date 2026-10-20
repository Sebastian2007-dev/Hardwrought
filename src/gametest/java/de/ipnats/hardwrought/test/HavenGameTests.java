package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.electricity.CoilBlock;
import de.ipnats.hardwrought.electricity.DynamoBlock;
import de.ipnats.hardwrought.electricity.ElectricBlocks;
import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.electricity.Gauge;
import de.ipnats.hardwrought.electricity.MachineBlock;
import de.ipnats.hardwrought.electricity.MachineBlockEntity;
import de.ipnats.hardwrought.electricity.TransformerBlock;
import de.ipnats.hardwrought.environment.Gas;
import de.ipnats.hardwrought.environment.Gases;
import de.ipnats.hardwrought.food.Spoilage;
import de.ipnats.hardwrought.haven.AlarmBellBlockEntity;
import de.ipnats.hardwrought.haven.Appliance;
import de.ipnats.hardwrought.haven.ApplianceBlock;
import de.ipnats.hardwrought.haven.BulbBlockEntity;
import de.ipnats.hardwrought.haven.ClimateBlockEntity;
import de.ipnats.hardwrought.haven.ColdStoreBlockEntity;
import de.ipnats.hardwrought.haven.FanBlockEntity;
import de.ipnats.hardwrought.haven.Floodlights;
import de.ipnats.hardwrought.haven.HavenBlocks;
import de.ipnats.hardwrought.haven.IceBoxBlockEntity;
import de.ipnats.hardwrought.haven.MonitorBlockEntity;
import de.ipnats.hardwrought.haven.MotionLampBlockEntity;
import de.ipnats.hardwrought.machinery.CrankBoxBlock;
import de.ipnats.hardwrought.parts.Parts;
import de.ipnats.hardwrought.smeltery.MoltenMetals;
import de.ipnats.hardwrought.smithing.MetalStock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The safe haven (docs/electricity-roadmap.md, stage E3): heaters, coolers, fans, cold stores, lights,
 * the floodlight, the motion lamp and bell, the sickbed, the new stations and food that spoils.
 */
public final class HavenGameTests {
    /** Each test works at its own height above its structure, so that their wide layouts never meet. */

    /** A crank box turning a dynamo east of it: 64 volts. Returns the dynamo. */
    private static BlockPos dynamo(ServerLevel level, List<BlockPos> used, BlockPos base) {
        place(level, used, base.east(), ElectricBlocks.DYNAMO.defaultBlockState().setValue(DynamoBlock.FACING, Direction.EAST));
        place(level, used, base, ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
        return base.east();
    }

    private static BlockPos appliance(ServerLevel level, List<BlockPos> used, BlockPos at, Appliance appliance, Direction facing) {
        place(level, used, at, HavenBlocks.of(appliance).defaultBlockState().setValue(ApplianceBlock.FACING, facing));
        return at;
    }

    /** A stone room with this inside, from its lower north-west corner; returns the inside's corner. */
    private static BlockPos room(ServerLevel level, List<BlockPos> used, BlockPos corner, int width, int height, int depth) {
        for (int x = -1; x <= width; x++) for (int y = -1; y <= height; y++) for (int z = -1; z <= depth; z++) {
            boolean wall = x < 0 || y < 0 || z < 0 || x == width || y == height || z == depth;
            if (wall) place(level, used, corner.offset(x, y, z), Blocks.STONE.defaultBlockState());
        }
        return corner;
    }

    @GameTest(maxTicks = 700)
    public void aHeatedRoomWarmsAndOneWithItsDoorOpenDoesNot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(90);
        List<BlockPos> used = new ArrayList<>();
        BlockPos[] heaters = new BlockPos[2];
        for (int i = 0; i < 2; i++) {
            BlockPos inside = room(level, used, base.offset(i * 10, 0, 6), 3, 2, 3);
            heaters[i] = appliance(level, used, inside.offset(1, 0, 2), Appliance.ELECTRIC_HEATER, Direction.NORTH);
            BlockPos dynamo = dynamo(level, used, base.offset(i * 10, 0, 0));
            Electricity.connect(level, dynamo, heaters[i], Gauge.INSULATED);
            // Set far above any outside air, so that the thermostat never switches it off whatever the season.
            ((ClimateBlockEntity) level.getBlockEntity(heaters[i])).setSetting(80);
            if (i == 1) {
                // An open door in the second room's north wall.
                BlockPos door = inside.offset(1, 0, -1);
                place(level, used, door, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.OPEN, true));
                place(level, used, door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.OPEN, true)
                        .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            }
        }
        helper.runAfterDelay(600, () -> {
            try {
                var closed = (ClimateBlockEntity) level.getBlockEntity(heaters[0]);
                var open = (ClimateBlockEntity) level.getBlockEntity(heaters[1]);
                double outdoor = de.ipnats.hardwrought.core.events.CoreLifecycle.require(level.getServer()).environment()
                        .outdoorTemperature(level, heaters[0]);
                helper.assertTrue(!Double.isNaN(closed.roomTemperature()), "The heater knows its room");
                helper.assertTrue(closed.roomTemperature() > outdoor + 8.0,
                        "A closed room is warmed well above the outside: " + closed.roomTemperature() + " against " + outdoor);
                helper.assertTrue(closed.roomTemperature() > open.roomTemperature() + 4.0,
                        "and a room with its door open much less: " + open.roomTemperature());
            } finally {
                clear(level, used);
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 200)
    public void aFanDrawsTheGasOutOfARoom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(97);
        List<BlockPos> used = new ArrayList<>();
        BlockPos inside = room(level, used, base.offset(0, 0, 6), 3, 2, 3);
        // The fan in the north wall, facing into the room; behind it the open air.
        BlockPos fan = inside.offset(1, 1, -1);
        appliance(level, used, fan, Appliance.VENTILATION_FAN, Direction.SOUTH);
        BlockPos dynamo = dynamo(level, used, base);
        Electricity.connect(level, dynamo, fan, Gauge.INSULATED);
        List<BlockPos> gassy = List.of(inside.offset(0, 0, 2), inside.offset(2, 1, 1), inside.offset(1, 0, 1));
        for (BlockPos at : gassy) {
            Gases.add(level, at, Gas.CARBON_MONOXIDE, 4);
            used.add(at);
        }
        helper.runAfterDelay(140, () -> {
            try {
                int left = 0;
                for (int x = 0; x < 3; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 3; z++) {
                    left += Gases.units(level.getBlockState(inside.offset(x, y, z)), Gas.CARBON_MONOXIDE);
                }
                helper.assertTrue(left == 0, "The fan has drawn the carbon monoxide out of the room: " + left + " left");
                helper.assertTrue(level.getBlockState(fan).getValue(ApplianceBlock.ON), "and runs");
            } finally {
                clear(level, used);
                for (int dx = -3; dx <= 3; dx++) for (int dy = -3; dy <= 3; dy++) for (int dz = -4; dz <= 0; dz++) {
                    BlockPos at = fan.offset(dx, dy, dz);
                    if (Gases.isGas(level.getBlockState(at))) level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                }
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 200)
    public void aRefrigeratorSlowsSpoilageAndAFreezerStopsIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(104);
        List<BlockPos> used = new ArrayList<>();
        BlockPos dynamo = dynamo(level, used, base);
        BlockPos fridge = appliance(level, used, base.offset(0, 0, 4), Appliance.REFRIGERATOR, Direction.NORTH);
        BlockPos freezer = appliance(level, used, base.offset(3, 0, 4), Appliance.FREEZER, Direction.NORTH);
        Electricity.connect(level, dynamo, fridge, Gauge.WIRE);
        Electricity.connect(level, dynamo, freezer, Gauge.WIRE);
        long start = level.getGameTime();
        ItemStack[] beef = new ItemStack[2];
        for (int i = 0; i < 2; i++) {
            beef[i] = new ItemStack(Items.BEEF, 4);
            Spoilage.mark(beef[i], start);
        }
        long marked = beef[0].get(Spoilage.ROTS_AT);
        helper.assertTrue(marked % Spoilage.ROUNDING == 0 && marked >= start + Spoilage.life(beef[0]),
                "Food is marked with when it rots, rounded up to a quarter day");
        ((ColdStoreBlockEntity) level.getBlockEntity(fridge)).larder().items().setItem(0, beef[0]);
        ((ColdStoreBlockEntity) level.getBlockEntity(freezer)).larder().items().setItem(0, beef[1]);
        helper.runAfterDelay(160, () -> {
            try {
                long elapsed = level.getGameTime() - start;
                long cooled = ((ColdStoreBlockEntity) level.getBlockEntity(fridge)).larder().items().getItem(0).get(Spoilage.ROTS_AT) - marked;
                long frozen = ((ColdStoreBlockEntity) level.getBlockEntity(freezer)).larder().items().getItem(0).get(Spoilage.ROTS_AT) - marked;
                helper.assertTrue(cooled > elapsed * 0.5 && cooled < elapsed * 0.9,
                        "In a refrigerator food goes off at a quarter of the speed: put back " + cooled + " of " + elapsed);
                helper.assertTrue(frozen > elapsed * 0.75 && frozen > cooled, "and in a freezer not at all: " + frozen + " of " + elapsed);
                helper.assertTrue(level.getBlockState(fridge).getValue(ApplianceBlock.ON), "The refrigerator runs while it holds food");
                // Rotten food turns, meat to rotten flesh, bread to spoiled food.
                ItemStack old = new ItemStack(Items.BREAD, 3);
                old.set(Spoilage.ROTS_AT, level.getGameTime() - 1);
                helper.assertTrue(Spoilage.rotten(old, level.getGameTime()) && Spoilage.rot(old).is(Spoilage.SPOILED_FOOD)
                        && Spoilage.rot(old).getCount() == 3, "Rotten bread is spoiled food");
                helper.assertTrue(Spoilage.rot(new ItemStack(Items.BEEF)).is(Items.ROTTEN_FLESH), "rotten meat rotten flesh");
                helper.assertTrue(!Spoilage.perishable(new ItemStack(Items.HONEY_BOTTLE)), "and honey keeps");
            } finally {
                clear(level, used);
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 100)
    public void anIceBoxKeepsFoodWhileItHasIce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(at, HavenBlocks.ICE_BOX.defaultBlockState());
        var box = (IceBoxBlockEntity) level.getBlockEntity(at);
        long start = level.getGameTime();
        ItemStack fish = new ItemStack(Items.COD);
        Spoilage.mark(fish, start);
        long marked = fish.get(Spoilage.ROTS_AT);
        box.larder().items().setItem(0, fish);
        box.larder().items().setItem(1, new ItemStack(Items.ICE));
        helper.runAfterDelay(80, () -> {
            long elapsed = level.getGameTime() - start;
            long kept = box.larder().items().getItem(0).get(Spoilage.ROTS_AT) - marked;
            helper.assertTrue(box.cold(), "With ice in it it is cold");
            helper.assertTrue(kept > elapsed * 0.25 && kept < elapsed * 0.75, "Food in it goes off at half the speed: " + kept + " of " + elapsed);
            helper.assertTrue(box.larder().items().getItem(1).isEmpty(), "The block of ice is melting");
            level.setBlock(at, Blocks.AIR.defaultBlockState(), 2 | net.minecraft.world.level.block.Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 200)
    public void aFloodlitYardKeepsMonstersFromSpawningAndAway(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(111);
        List<BlockPos> used = new ArrayList<>();
        // 64 V stepped up to 256 for the arc lamp.
        BlockPos dynamo = dynamo(level, used, base);
        BlockPos up = dynamo.above(3);
        place(level, used, up, ElectricBlocks.TRANSFORMER.defaultBlockState().setValue(TransformerBlock.FACING, Direction.NORTH));
        place(level, used, up.north(), ElectricBlocks.BUSHING.defaultBlockState().setValue(CoilBlock.FACING, Direction.NORTH));
        BlockPos light = appliance(level, used, up.north(5), Appliance.ARC_FLOODLIGHT, Direction.NORTH);
        Electricity.connect(level, dynamo, up, Gauge.CABLE);
        Electricity.connect(level, up.north(), light, Gauge.WIRE);
        helper.runAfterDelay(100, () -> {
            try {
                helper.assertTrue(level.getBlockState(light).getValue(ApplianceBlock.ON), "The arc lamp burns on the 240-volt line");
                helper.assertTrue(level.getLightEmission(light) == 15, "as bright as anything");
                BlockPos near = light.east(20), far = light.east(60);
                helper.assertTrue(Floodlights.spawnBarred(level, near) && !Floodlights.spawnBarred(level, far),
                        "Nothing spawns within its reach, and beyond it the world is as it was");
                helper.assertTrue(!Monster.isDarkEnoughToSpawn(level, near, level.getRandom()), "not even in the dark at its edge");
                helper.assertTrue(Floodlights.near(level, Vec3.atCenterOf(light.east(10)), Floodlights.AVOID_REACH) != null,
                        "and a monster within its reach knows where it is to keep away from");
            } finally {
                clear(level, used);
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void aMotionLampRingsItsBellAndABulbIsChanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(118);
        List<BlockPos> used = new ArrayList<>();
        BlockPos dynamo = dynamo(level, used, base);
        BlockPos lamp = appliance(level, used, base.offset(0, 0, 4), Appliance.MOTION_LAMP, Direction.NORTH);
        BlockPos bell = appliance(level, used, lamp.east(), Appliance.ALARM_BELL, Direction.NORTH);
        BlockPos light = appliance(level, used, base.offset(4, 0, 4), Appliance.ELECTRIC_LIGHT, Direction.NORTH);
        for (BlockPos device : List.of(lamp, bell, light)) Electricity.connect(level, dynamo, device, Gauge.WIRE);
        helper.runAfterDelay(30, () -> ((MotionLampBlockEntity) level.getBlockEntity(lamp)).sense(level));
        helper.runAfterDelay(90, () -> {
            try {
                var sensor = (MotionLampBlockEntity) level.getBlockEntity(lamp);
                helper.assertTrue(sensor.triggered() && level.getBlockState(lamp).getValue(ApplianceBlock.ON), "Something moved: the lamp is lit");
                helper.assertTrue(level.getSignal(lamp.east(), Direction.EAST) == 15 || level.hasNeighborSignal(bell),
                        "and gives a redstone signal");
                helper.assertTrue(((AlarmBellBlockEntity) level.getBlockEntity(bell)).rang(), "which rings the bell beside it");
                helper.assertTrue(level.getBlockState(light).getValue(ApplianceBlock.ON) && level.getLightEmission(light) == 15,
                        "The electric light burns");
                var bulb = (BulbBlockEntity) level.getBlockEntity(light);
                bulb.wear(level, BulbBlockEntity.BULB_SECONDS);
                helper.assertTrue(level.getBlockState(light).getValue(ApplianceBlock.BROKEN) && level.getLightEmission(light) == 0,
                        "Its bulb burnt out, it is dark");
                helper.assertTrue(Appliance.ELECTRIC_LIGHT.mends() == Parts.LIGHT_BULB, "and a new bulb mends it");
            } finally {
                clear(level, used);
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 120)
    public void aSickbedHealsWhoeverSleepsInIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(125);
        List<BlockPos> used = new ArrayList<>();
        BlockPos dynamo = dynamo(level, used, base);
        BlockPos floor = base.offset(0, 0, 4);
        for (int x = -1; x <= 3; x++) for (int z = -1; z <= 2; z++) place(level, used, floor.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        BlockPos head = floor;
        place(level, used, head, Blocks.BED.white().defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.NORTH));
        place(level, used, head.south(), Blocks.BED.white().defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.NORTH));
        BlockPos monitor = appliance(level, used, head.east(), Appliance.BEDSIDE_MONITOR, Direction.WEST);
        Electricity.connect(level, dynamo, monitor, Gauge.WIRE);
        var player = helper.makeMockServerPlayerInLevel();
        player.setHealth(6.0f);
        helper.onEachTick(() -> {
            if (!player.isSleeping()) player.startSleeping(head);
        });
        helper.runAfterDelay(100, () -> {
            try {
                helper.assertTrue(MonitorBlockEntity.tended(player), "Asleep beside the monitor, the bed is a sickbed");
                helper.assertTrue(player.getHealth() > 7.0f, "and heals: " + player.getHealth());
            } finally {
                player.stopSleeping();
                clear(level, used);
            }
            helper.succeed();
        });
    }

    @GameTest
    public void theNewStationsMakeTheirPartsAndNichromeIsAnAlloy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        record Work(net.minecraft.world.level.block.Block station, ItemStack input, net.minecraft.world.item.Item made) { }
        for (Work work : List.of(new Work(ElectricBlocks.WIRE_DRAWER, new ItemStack(MetalStock.rod("tungsten")), Parts.TUNGSTEN_FILAMENT),
                new Work(ElectricBlocks.WIRE_DRAWER, new ItemStack(MetalStock.rod("nichrome")), Parts.NICHROME_WIRE),
                new Work(ElectricBlocks.GLASS_KILN, new ItemStack(Items.GLASS), Parts.GLASS_BULB),
                new Work(ElectricBlocks.AUTOCLAVE, new ItemStack(ModItems.BANDAGE), ModItems.STERILE_DRESSING))) {
            level.setBlockAndUpdate(at, work.station().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
            var machine = (MachineBlockEntity) level.getBlockEntity(at);
            helper.assertTrue(machine.takes(level, work.input()), work.station() + " takes " + work.input());
            helper.assertTrue(machine.machine().result(level, work.input()).is(work.made()), "and makes " + work.made());
        }
        level.setBlock(at, Blocks.AIR.defaultBlockState(), 2 | net.minecraft.world.level.block.Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        helper.assertTrue(MoltenMetals.ALLOYS.stream().anyMatch(alloy -> alloy.output().equals("nichrome")
                && alloy.inputs().containsKey("nickel") && alloy.inputs().containsKey("chromium")), "Nichrome is nickel and chromium alloyed");
        helper.succeed();
    }

    private static void place(ServerLevel level, List<BlockPos> used, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state);
        used.add(pos.immutable());
    }

    /** Without the side effects of removal, so that no coils or food are left lying in the test world. */
    private static void clear(ServerLevel level, List<BlockPos> used) {
        for (int i = used.size() - 1; i >= 0; i--) {
            level.setBlock(used.get(i), Blocks.AIR.defaultBlockState(), 2 | net.minecraft.world.level.block.Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        }
    }
}
