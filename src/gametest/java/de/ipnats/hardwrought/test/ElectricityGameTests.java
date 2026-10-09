package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.electricity.DynamoBlock;
import de.ipnats.hardwrought.electricity.DynamoBlockEntity;
import de.ipnats.hardwrought.electricity.ElectricBlockEntity;
import de.ipnats.hardwrought.electricity.ElectricBlocks;
import de.ipnats.hardwrought.electricity.ElectricLampBlock;
import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.electricity.Gauge;
import de.ipnats.hardwrought.electricity.InsulatorBlock;
import de.ipnats.hardwrought.electricity.LampBlockEntity;
import de.ipnats.hardwrought.machinery.CogwheelBlock;
import de.ipnats.hardwrought.machinery.CrankBoxBlock;
import de.ipnats.hardwrought.machinery.HandCrankBlock;
import de.ipnats.hardwrought.machinery.Kinetics;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Section 76: a dynamo on the driveline, wire strung from it, and what voltage and current do.
 */
public final class ElectricityGameTests {
    private static final int WORKSPACE = 60;
    private static final BlockState LAMP = ElectricBlocks.ELECTRIC_LAMP.defaultBlockState();
    private static final BlockState POST = ElectricBlocks.INSULATOR.defaultBlockState().setValue(InsulatorBlock.FACING, Direction.UP);

    /** A crank box at the base turning a dynamo east of it at 32 turns a minute: 64 volts. */
    private static BlockPos dynamoOnACrankBox(ServerLevel level, List<BlockPos> used, BlockPos base) {
        place(level, used, base.east(), ElectricBlocks.DYNAMO.defaultBlockState().setValue(DynamoBlock.FACING, Direction.EAST));
        place(level, used, base, ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
        return base.east();
    }

    @GameTest
    public void aDynamoOnTheDrivelineLightsALamp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            BlockPos dynamo = dynamoOnACrankBox(level, used, base);
            BlockPos lamp = dynamo.east(4);
            place(level, used, lamp, LAMP);
            helper.assertTrue(Math.abs(Kinetics.speed(level, dynamo)) == CrankBoxBlock.SPEED,
                    "The dynamo turns with the box it stands against: " + Kinetics.speed(level, dynamo));
            helper.assertTrue(level.getBlockState(lamp).getValue(ElectricLampBlock.LIGHT) == 0, "A lamp with no wire to it is dark");
            helper.assertTrue(Electricity.refusal(level, dynamo, lamp, Gauge.WIRE) == Electricity.Refusal.NEEDS_COIL,
                    "A dynamo takes no wire itself: " + Electricity.refusal(level, dynamo, lamp, Gauge.WIRE));
            Electricity.connect(level, dynamo, lamp, Gauge.WIRE);

            var network = Electricity.solve(level, lamp);
            double volts = network.volts()[network.indexOf(lamp)];
            helper.assertTrue(volts > 60.0 && volts < 64.0, "Sixty-four volts less what the dynamo and the wire keep: " + volts);
            helper.assertTrue(level.getBlockState(lamp).getValue(ElectricLampBlock.LIGHT) == 15,
                    "At its voltage the lamp gives full light: " + level.getBlockState(lamp));
            float drawn = ((DynamoBlockEntity) level.getBlockEntity(dynamo)).amps();
            helper.assertTrue(Math.abs(drawn - volts / LampBlockEntity.OHMS) < 0.01, "The dynamo gives what the lamp takes: " + drawn);
            helper.assertTrue(Math.abs(network.generated() - network.delivered() - network.lost()) < 0.01,
                    "What is generated is delivered or lost: " + network);
            helper.assertTrue(((DynamoBlock) ElectricBlocks.DYNAMO).impact(level, dynamo, level.getBlockState(dynamo))
                            > DynamoBlockEntity.IDLE_IMPACT + 2.0f, "A dynamo giving current is heavier to turn");

            // The wire gone again, the lamp goes dark.
            Electricity.disconnect(level, lamp, dynamo);
            helper.assertTrue(level.getBlockState(lamp).getValue(ElectricLampBlock.LIGHT) == 0, "Cut off, the lamp is dark");
            helper.assertTrue(((ElectricBlockEntity) level.getBlockEntity(dynamo)).wires().isEmpty(), "Both ends let go of a cut wire");
            helper.assertTrue(LampBlockEntity.light(5.0) == 0 && LampBlockEntity.light(30.0) > 0
                            && LampBlockEntity.light(30.0) < LampBlockEntity.light(60.0) && LampBlockEntity.light(200.0) == 15,
                    "A lamp is dark on a trickle, dim on half its voltage, and no brighter for too much");
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest
    public void aLongThinLineLosesVoltageAndACableLosesLess(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            BlockPos dynamo = dynamoOnACrankBox(level, used, base);
            // Straight up: two spans of fourteen blocks, and three lamps round the top of it.
            BlockPos middle = dynamo.above(14), top = dynamo.above(28);
            place(level, used, middle, POST);
            place(level, used, top, POST);
            BlockPos[] lamps = {top.east(2), top.west(2), top.north(2)};
            for (BlockPos lamp : lamps) place(level, used, lamp, LAMP);
            place(level, used, dynamo.east(2), POST);
            helper.assertTrue(Electricity.refusal(level, dynamo.east(2), top, Gauge.WIRE) == Electricity.Refusal.TOO_FAR,
                    "Twenty-eight blocks is more than a thin wire spans");
            double[] drop = new double[2];
            for (Gauge gauge : Gauge.values()) {
                Electricity.connect(level, dynamo, middle, gauge);
                Electricity.connect(level, middle, top, gauge);
                for (BlockPos lamp : lamps) Electricity.connect(level, top, lamp, gauge);
                var network = Electricity.solve(level, dynamo);
                drop[gauge.ordinal()] = network.volts()[network.indexOf(dynamo)] - network.volts()[network.indexOf(top)];
                helper.assertTrue(network.lost() > 0.0 && network.lost() < network.generated(),
                        "A line turns some of what it carries into heat: " + network.lost());
                for (BlockPos lamp : lamps) Electricity.disconnect(level, top, lamp);
                Electricity.disconnect(level, middle, top);
                Electricity.disconnect(level, dynamo, middle);
            }
            helper.assertTrue(drop[0] > 1.0, "Three lamps at the end of 28 blocks of thin wire see over a volt less: " + drop[0]);
            helper.assertTrue(drop[1] < drop[0] / 3.0, "A cable loses a quarter of that: " + drop[1]);
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest
    public void wetPointsLeakAndAnOverloadedWireBurnsThrough(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            BlockPos dynamo = dynamoOnACrankBox(level, used, base);
            BlockPos hub = dynamo.above(4);
            place(level, used, hub, POST);
            BlockPos[] wet = {hub.east(3), hub.west(3), hub.north(3)};
            for (BlockPos post : wet) {
                place(level, used, post, POST);
                place(level, used, post.below(), Blocks.WATER.defaultBlockState());
            }
            Electricity.connect(level, dynamo, hub, Gauge.WIRE);
            for (BlockPos post : wet) Electricity.connect(level, hub, post, Gauge.CABLE);

            var network = Electricity.pass(level, dynamo, 0.5);
            double amps = Math.abs(network.flows().get(0).amps());
            helper.assertTrue(network.wet()[network.indexOf(wet[0])] && network.leaked() > 100.0,
                    "Three points standing over water let hundreds of watts creep away: " + network.leaked());
            helper.assertTrue(amps > Gauge.WIRE.amps() * 1.5, "That is far more than a thin wire carries: " + amps);
            var feed = (ElectricBlockEntity) level.getBlockEntity(dynamo);
            helper.assertTrue(feed.wireTo(hub) != null && feed.wireTo(hub).hot(), "The overloaded wire glows");
            var hubEntity = (ElectricBlockEntity) level.getBlockEntity(hub);
            helper.assertTrue(!hubEntity.wireTo(wet[0]).hot(), "The cables after it, each carrying a third, stay cold");

            for (int i = 0; i < 20 && feed.wireTo(hub) != null; i++) Electricity.pass(level, dynamo, 0.5);
            helper.assertTrue(feed.wireTo(hub) == null && hubEntity.wireTo(dynamo) == null,
                    "Within ten seconds it has burnt through at both ends");
            helper.assertTrue(hubEntity.volts() == 0.0f, "And everything beyond it is dead: " + hubEntity.volts());
            helper.assertTrue(Electricity.shockDamage(20.0) == 0.0f && Electricity.shockDamage(60.0) == 3.0f
                            && Electricity.shockDamage(120.0) == 6.0f, "Twenty volts can be handled, sixty bite, 120 bite twice as hard");
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest
    public void aDynamoAHandCannotTurnLetsGoInsteadOfStallingTheLine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            BlockPos dynamo = base.east();
            place(level, used, dynamo, ElectricBlocks.DYNAMO.defaultBlockState().setValue(DynamoBlock.FACING, Direction.EAST));
            place(level, used, base, ModBlocks.HAND_CRANK.defaultBlockState()
                    .setValue(HandCrankBlock.FACING, Direction.EAST).setValue(HandCrankBlock.TURNING, true));
            helper.assertTrue(Math.abs(Kinetics.speed(level, dynamo)) == HandCrankBlock.SPEED,
                    "A hand crank turns the dynamo: " + Kinetics.speed(level, dynamo));
            BlockPos lamp = dynamo.east(3);
            place(level, used, lamp, LAMP);
            Electricity.connect(level, dynamo, lamp, Gauge.WIRE);
            int light = level.getBlockState(lamp).getValue(ElectricLampBlock.LIGHT);
            helper.assertTrue(light > 0 && light < 15, "At a hand's sixteen turns a lamp glows, dimly: " + light);
            var entity = (DynamoBlockEntity) level.getBlockEntity(dynamo);
            helper.assertTrue(!entity.tripped(), "One lamp is no load for an arm");

            // Now three points over water on the same dynamo: more current than an arm can turn against.
            BlockPos hub = dynamo.above(4);
            place(level, used, hub, POST);
            Electricity.connect(level, dynamo, hub, Gauge.CABLE);
            for (BlockPos post : new BlockPos[] {hub.east(3), hub.west(3), hub.north(3)}) {
                place(level, used, post, POST);
                place(level, used, post.below(), Blocks.WATER.defaultBlockState());
                Electricity.connect(level, hub, post, Gauge.CABLE);
            }
            helper.assertTrue(entity.tripped(), "The dynamo lets go");
            helper.assertTrue(Math.abs(Kinetics.speed(level, dynamo)) == HandCrankBlock.SPEED,
                    "and the line it is on keeps turning: " + Kinetics.speed(level, dynamo));
            Electricity.update(level, dynamo);
            helper.assertTrue(level.getBlockState(lamp).getValue(ElectricLampBlock.LIGHT) == 0, "While it has let go, the lamp is dark");
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest
    public void gearedUpADynamoGivesMoreThanALampStands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            // A large gear on the box, a small one across its corner at twice the speed, the dynamo on that.
            BlockPos large = base.east(), small = large.above().north(), dynamo = small.east();
            place(level, used, large, ModBlocks.LARGE_COGWHEEL.defaultBlockState().setValue(CogwheelBlock.AXIS, Direction.Axis.X));
            place(level, used, small, ModBlocks.COGWHEEL.defaultBlockState().setValue(CogwheelBlock.AXIS, Direction.Axis.X));
            place(level, used, dynamo, ElectricBlocks.DYNAMO.defaultBlockState().setValue(DynamoBlock.FACING, Direction.EAST));
            place(level, used, base, ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
            helper.assertTrue(Math.abs(Kinetics.speed(level, dynamo)) == 2 * CrankBoxBlock.SPEED,
                    "Geared up two to one: " + Kinetics.speed(level, dynamo));
            BlockPos lamp = dynamo.east(3);
            place(level, used, lamp, LAMP);
            Electricity.connect(level, dynamo, lamp, Gauge.WIRE);
            helper.assertTrue(((ElectricBlockEntity) level.getBlockEntity(lamp)).volts() > LampBlockEntity.POP_VOLTS,
                    "Twice the speed is twice the voltage: " + ((ElectricBlockEntity) level.getBlockEntity(lamp)).volts());
            helper.assertTrue(!level.getBlockState(lamp).getValue(ElectricLampBlock.BROKEN), "Looking at a network breaks nothing");
            Electricity.pass(level, dynamo, 0.5);
            helper.assertTrue(level.getBlockState(lamp).getValue(ElectricLampBlock.BROKEN)
                            && level.getBlockState(lamp).getValue(ElectricLampBlock.LIGHT) == 0, "The lamp's filament goes at once");
            var network = Electricity.solve(level, dynamo);
            helper.assertTrue(network.delivered() == 0.0, "A dead lamp takes nothing: " + network.delivered());
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest
    public void aBatteryFillsFromTheLineAndHoldsItUpWhenTheDynamoStops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            BlockPos dynamo = dynamoOnACrankBox(level, used, base);
            BlockPos battery = dynamo.east(3), lamp = battery.east(3);
            place(level, used, battery, ElectricBlocks.BATTERY.defaultBlockState());
            place(level, used, lamp, LAMP);
            Electricity.connect(level, dynamo, battery, Gauge.WIRE);
            Electricity.connect(level, battery, lamp, Gauge.WIRE);
            var cells = (de.ipnats.hardwrought.electricity.BatteryBlockEntity) level.getBlockEntity(battery);
            helper.assertTrue(cells.charge() == 0.0 && level.getBlockState(lamp).getValue(ElectricLampBlock.LIGHT) == 15,
                    "With the dynamo turning the lamp is lit and the battery starts empty");
            for (int i = 0; i < 20; i++) Electricity.pass(level, dynamo, 0.5);
            helper.assertTrue(cells.amps() < -1.0, "Sixty-four volts push current into an empty battery: " + cells.amps());
            helper.assertTrue(cells.charge() > 500.0, "Ten seconds of that are in it: " + cells.charge());
            helper.assertTrue(level.getBlockState(battery).getValue(de.ipnats.hardwrought.electricity.BatteryBlock.CHARGE) == 1,
                    "and its gauge shows the first sliver");

            // The box stops. The dynamo gives nothing and takes nothing; the battery holds the lamp up.
            level.setBlockAndUpdate(base, ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, false));
            Kinetics.update(level, base);
            Electricity.update(level, dynamo);
            helper.assertTrue(Kinetics.speed(level, dynamo) == 0.0f, "The dynamo stands");
            helper.assertTrue(level.getBlockState(lamp).getValue(ElectricLampBlock.LIGHT) >= 13,
                    "The lamp burns on from the battery: " + level.getBlockState(lamp));
            helper.assertTrue(cells.amps() > 0.4 && ((ElectricBlockEntity) level.getBlockEntity(dynamo)).amps() == 0.0f,
                    "The battery gives what the lamp takes, and none of it runs back through the dynamo: " + cells.amps());
            double before = cells.charge();
            Electricity.pass(level, battery, 0.5);
            helper.assertTrue(cells.charge() < before, "Giving empties it");

            cells.setCharge(0.0);
            Electricity.update(level, battery);
            helper.assertTrue(level.getBlockState(lamp).getValue(ElectricLampBlock.LIGHT) == 0, "Empty, it gives nothing");

            // Turning again, with the battery full: it takes no more.
            level.setBlockAndUpdate(base, ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
            Kinetics.update(level, base);
            cells.setCharge(de.ipnats.hardwrought.electricity.BatteryBlockEntity.CAPACITY);
            Electricity.update(level, dynamo);
            helper.assertTrue(cells.amps() >= 0.0f, "A full battery takes no more: " + cells.amps());
            helper.assertTrue(level.getBlockState(battery).getValue(de.ipnats.hardwrought.electricity.BatteryBlock.CHARGE) == 4,
                    "and its gauge is full");
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest
    public void aMastIsOneThingAndCarriesALineOverheadFromItsFoot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            BlockPos[] feet = {base, base.east(12)};
            BlockState mast = ElectricBlocks.MAST.defaultBlockState();
            for (BlockPos foot : feet) {
                for (int i = 0; i < de.ipnats.hardwrought.electricity.MastBlock.HEIGHT; i++) {
                    place(level, used, foot.above(i), mast.setValue(de.ipnats.hardwrought.electricity.MastBlock.SEGMENT, i));
                }
            }
            BlockPos head = feet[0].above(3), far = feet[1].above(3);
            helper.assertTrue(head.equals(Electricity.point(level, feet[0])) && head.equals(Electricity.point(level, feet[0].above(2))),
                    "A coil used on the foot of a mast is used on its head: " + Electricity.point(level, feet[0]));
            helper.assertTrue(level.getBlockEntity(feet[0]) == null && level.getBlockEntity(head) instanceof ElectricBlockEntity,
                    "Only the head is a point of the network");
            helper.assertTrue(Electricity.refusal(level, head, far, Gauge.WIRE) == null,
                    "Twelve blocks from mast to mast: " + Electricity.refusal(level, head, far, Gauge.WIRE));
            Electricity.connect(level, head, far, Gauge.WIRE);
            var first = (ElectricBlockEntity) level.getBlockEntity(head);
            var second = (ElectricBlockEntity) level.getBlockEntity(far);
            double lowest = Electricity.along(first.terminal(), second.terminal(), 0.5).y - feet[0].getY();
            helper.assertTrue(lowest > 2.5, "The wire hangs clear over a standing player at its lowest: " + lowest);

            // One block of the pole knocked out, the rest follows and the wire comes off.
            level.setBlock(feet[0].above(1), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(level.getBlockState(feet[0]).isAir() && level.getBlockState(head).isAir(),
                    "A mast with a piece gone is gone: " + level.getBlockState(feet[0]) + " / " + level.getBlockState(head));
            helper.assertTrue(second.wires().isEmpty(), "and the other mast lets go of the wire");
            for (var coil : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(head).inflate(3.0))) coil.discard();
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest
    public void theBasicCrusherBreaksOreOnCurrentAndTakesNoneStandingIdle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            BlockPos dynamo = dynamoOnACrankBox(level, used, base);
            BlockPos at = dynamo.east(4);
            place(level, used, at, ElectricBlocks.BASIC_CRUSHER.defaultBlockState());
            Electricity.connect(level, dynamo, at, Gauge.WIRE);
            var crusher = (de.ipnats.hardwrought.electricity.MachineBlockEntity) level.getBlockEntity(at);
            helper.assertTrue(crusher.amps() == 0.0f && crusher.volts() > 63.0f, "Live and idle, it takes nothing: " + crusher.amps());

            var rawIron = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_IRON, 2);
            helper.assertTrue(crusher.canPlaceItem(0, rawIron) && crusher.insertFrom(rawIron, true), "Raw ore goes into the jaws");
            helper.assertTrue(!crusher.canPlaceItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND)),
                    "What it does not break does not");
            for (int i = 0; i < 30; i++) {
                de.ipnats.hardwrought.electricity.MachineBlockEntity.serverTick(level, at, level.getBlockState(at), crusher);
            }
            helper.assertTrue(Math.abs(crusher.amps() - crusher.volts() / 40.0f) < 0.01f && crusher.amps() > 1.4f,
                    "Working, it takes an ampere and a half: " + crusher.amps());
            helper.assertTrue(level.getBlockState(at).getValue(de.ipnats.hardwrought.electricity.MachineBlock.RUNNING)
                    && crusher.getItem(1).isEmpty(), "After a second and a half it is running and not done");
            for (int i = 0; i < 60; i++) {
                de.ipnats.hardwrought.electricity.MachineBlockEntity.serverTick(level, at, level.getBlockState(at), crusher);
            }
            helper.assertTrue(crusher.getItem(1).getCount() == 2 && crusher.getItem(0).isEmpty(),
                    "Two pieces are powder within four and a half seconds: " + crusher.getItem(1));
            helper.assertTrue(crusher.amps() == 0.0f && !level.getBlockState(at)
                            .getValue(de.ipnats.hardwrought.electricity.MachineBlock.RUNNING),
                    "With nothing left to break it stops and takes nothing: " + crusher.amps());
            var rate = (java.util.function.DoubleUnaryOperator) de.ipnats.hardwrought.electricity.MachineBlockEntity::rate;
            helper.assertTrue(rate.applyAsDouble(60.0) == 1.0 && rate.applyAsDouble(29.0) == 0.0
                            && Math.abs(rate.applyAsDouble(30.0) - 0.25) < 1e-9 && rate.applyAsDouble(90.0) > 2.0,
                    "Half the voltage is a quarter of the work, and under that it only hums");
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest
    public void everyMachineMakesWhatItIsForAndTheFurnaceSmeltsOnCurrent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            var Machine = de.ipnats.hardwrought.electricity.Machine.class;
            var iron = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
            var log = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG);
            // What the crusher makes of raw lead is what the furnace takes: the two stand in a row.
            var ore = de.ipnats.hardwrought.electricity.Machine.CRUSHER.result(level, new net.minecraft.world.item.ItemStack(
                    de.ipnats.hardwrought.metallurgy.ModMetals.raw(de.ipnats.hardwrought.metallurgy.Metal.LEAD)));
            var plate = de.ipnats.hardwrought.electricity.Machine.PRESS.result(level, iron);
            helper.assertTrue(plate.is(de.ipnats.hardwrought.smithing.MetalStock.plate("iron")) && plate.getCount() == 1,
                    "The press makes a plate of an ingot: " + plate);
            helper.assertTrue(de.ipnats.hardwrought.electricity.Machine.PRESS.result(level, log).isEmpty(), "and nothing of a log");
            var boards = de.ipnats.hardwrought.electricity.Machine.SAWMILL.result(level, log);
            helper.assertTrue(boards.is(net.minecraft.tags.ItemTags.PLANKS) && boards.getCount() >= 3 && boards.getCount() % 3 == 0,
                    "The sawmill cuts half as many boards again out of a log as a hand does: " + boards);
            helper.assertTrue(de.ipnats.hardwrought.electricity.Machine.SAWMILL.result(level, iron).isEmpty(), "and nothing out of iron");
            var smelted = de.ipnats.hardwrought.electricity.Machine.FURNACE.result(level, ore);
            helper.assertTrue(smelted.is(de.ipnats.hardwrought.metallurgy.ModMetals.ingot(de.ipnats.hardwrought.metallurgy.Metal.LEAD)),
                    "The electric furnace smelts what a furnace smelts: " + smelted + " of " + Machine.getSimpleName());

            BlockPos dynamo = dynamoOnACrankBox(level, used, base);
            BlockPos at = dynamo.east(4);
            place(level, used, at, ElectricBlocks.ELECTRIC_FURNACE.defaultBlockState());
            Electricity.connect(level, dynamo, at, Gauge.WIRE);
            var furnace = (de.ipnats.hardwrought.electricity.MachineBlockEntity) level.getBlockEntity(at);
            helper.assertTrue(furnace.machine() == de.ipnats.hardwrought.electricity.Machine.FURNACE, "The block says which machine it is");
            helper.assertTrue(!furnace.canPlaceItem(0, log) || !de.ipnats.hardwrought.electricity.Machine.FURNACE.result(level, log).isEmpty(),
                    "A hopper can put in only what it takes");
            helper.assertTrue(furnace.canPlaceItem(0, ore), "Lead powder goes into the furnace");
            furnace.setItem(0, ore.copy());
            for (int i = 0; i < 60; i++) {
                de.ipnats.hardwrought.electricity.MachineBlockEntity.serverTick(level, at, level.getBlockState(at), furnace);
            }
            helper.assertTrue(furnace.amps() > 1.9f && furnace.getItem(1).isEmpty(),
                    "After three seconds it draws its two amperes and is not done: " + furnace.amps());
            for (int i = 0; i < 60; i++) {
                de.ipnats.hardwrought.electricity.MachineBlockEntity.serverTick(level, at, level.getBlockState(at), furnace);
            }
            helper.assertTrue(furnace.getItem(1).is(smelted.getItem()) && furnace.getItem(0).isEmpty(),
                    "Within six seconds the ingot lies in it: " + furnace.getItem(1));
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest
    public void coilsJoinALineToTheBlocksTheySitOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            BlockState coil = ElectricBlocks.COPPER_COIL.defaultBlockState();
            BlockPos dynamo = dynamoOnACrankBox(level, used, base);
            BlockPos battery = dynamo.east(4), furnace = dynamo.east(8), lamp = furnace.east(3);
            place(level, used, battery, ElectricBlocks.BATTERY.defaultBlockState());
            place(level, used, furnace, ElectricBlocks.ELECTRIC_FURNACE.defaultBlockState());
            place(level, used, lamp, LAMP);
            // A coil on top of each, and a second on the far side of the furnace to carry the line on.
            BlockPos onDynamo = dynamo.above(), onBattery = battery.above(), onFurnace = furnace.above(), past = furnace.east();
            place(level, used, onDynamo, coil);
            place(level, used, onBattery, coil);
            place(level, used, onFurnace, coil);
            place(level, used, past, coil.setValue(de.ipnats.hardwrought.electricity.CoilBlock.FACING, Direction.EAST));
            for (BlockPos[] run : new BlockPos[][] {{onDynamo, onBattery}, {onBattery, onFurnace}, {past, lamp}}) {
                helper.assertTrue(Electricity.refusal(level, run[0], run[1], Gauge.WIRE) == null,
                        "Coil to coil, and coil to lamp, wire may be strung: " + Electricity.refusal(level, run[0], run[1], Gauge.WIRE));
                Electricity.connect(level, run[0], run[1], Gauge.WIRE);
            }
            var network = Electricity.solve(level, lamp);
            helper.assertTrue(network.indexOf(dynamo) >= 0 && network.indexOf(battery) >= 0 && network.indexOf(furnace) >= 0,
                    "Dynamo, battery and furnace are all on the lamp's network through their coils: " + network.nodes().size());
            helper.assertTrue(level.getBlockState(lamp).getValue(ElectricLampBlock.LIGHT) == 15,
                    "The line is carried on through the furnace from one coil to the other, and the lamp is lit");
            var coilOnDynamo = (ElectricBlockEntity) level.getBlockEntity(onDynamo);
            float dynamoVolts = ((ElectricBlockEntity) level.getBlockEntity(dynamo)).volts();
            helper.assertTrue(Math.abs(coilOnDynamo.volts() - dynamoVolts) < 0.1f && dynamoVolts > 55.0f,
                    "A coil stands at the voltage of what it sits on: " + coilOnDynamo.volts() + " / " + dynamoVolts);
            Electricity.pass(level, dynamo, 0.5);
            helper.assertTrue(((ElectricBlockEntity) level.getBlockEntity(battery)).amps() < -0.5f,
                    "The battery charges through its coil: " + ((ElectricBlockEntity) level.getBlockEntity(battery)).amps());

            // The coil taken off the dynamo, the battery alone holds the line up.
            level.setBlock(onDynamo, Blocks.AIR.defaultBlockState(), 3 | net.minecraft.world.level.block.Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
            Electricity.update(level, lamp);
            network = Electricity.solve(level, lamp);
            helper.assertTrue(network.indexOf(dynamo) < 0, "With its coil gone the dynamo is on nothing");
            helper.assertTrue(((ElectricBlockEntity) level.getBlockEntity(battery)).amps() > 0.3f
                            && level.getBlockState(lamp).getValue(ElectricLampBlock.LIGHT) >= 13,
                    "and the lamp burns on from the battery: " + level.getBlockState(lamp));
            // A coil against plain stone is a support and joins nothing.
            place(level, used, base.north(3), Blocks.STONE.defaultBlockState());
            place(level, used, base.north(3).above(), coil);
            helper.assertTrue(Electricity.solve(level, base.north(3).above()).nodes().size() == 1, "A coil on stone is alone");
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    private static void place(ServerLevel level, List<BlockPos> used, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state);
        used.add(pos.immutable());
    }

    /** Without the side effects of removal, so that no coils are left lying in the test world. */
    private static void clear(ServerLevel level, List<BlockPos> used) {
        for (int i = used.size() - 1; i >= 0; i--) {
            level.setBlock(used.get(i), Blocks.AIR.defaultBlockState(), 2 | net.minecraft.world.level.block.Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        }
    }
}
