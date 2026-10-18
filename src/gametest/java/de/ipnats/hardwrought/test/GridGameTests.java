package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.electricity.CoilBlock;
import de.ipnats.hardwrought.electricity.DynamoBlock;
import de.ipnats.hardwrought.electricity.ElectricBlockEntity;
import de.ipnats.hardwrought.electricity.ElectricBlocks;
import de.ipnats.hardwrought.electricity.ElectricLampBlock;
import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.electricity.Gauge;
import de.ipnats.hardwrought.electricity.InsulatorBlock;
import de.ipnats.hardwrought.electricity.MachineBlock;
import de.ipnats.hardwrought.electricity.MachineBlockEntity;
import de.ipnats.hardwrought.electricity.SwitchgearBlock;
import de.ipnats.hardwrought.electricity.SwitchgearBlockEntity;
import de.ipnats.hardwrought.electricity.TransformerBlock;
import de.ipnats.hardwrought.electricity.TransformerBlockEntity;
import de.ipnats.hardwrought.machinery.CrankBoxBlock;
import de.ipnats.hardwrought.parts.Parts;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The grid (docs/electricity-roadmap.md, stage E1): transformers, porcelain, switchgear, insulated wire,
 * the coil winder and the 240-volt machines.
 */
public final class GridGameTests {
    private static final int WORKSPACE = 60;
    private static final BlockState LAMP = ElectricBlocks.ELECTRIC_LAMP.defaultBlockState();
    private static final BlockState POST = ElectricBlocks.INSULATOR.defaultBlockState().setValue(InsulatorBlock.FACING, Direction.UP);
    private static final BlockState PORCELAIN_POST = ElectricBlocks.PORCELAIN_INSULATOR.defaultBlockState()
            .setValue(InsulatorBlock.FACING, Direction.UP);

    /** A crank box at the base turning a dynamo east of it at 32 turns a minute: 64 volts. */
    private static BlockPos dynamoOnACrankBox(ServerLevel level, List<BlockPos> used, BlockPos base) {
        place(level, used, base.east(), ElectricBlocks.DYNAMO.defaultBlockState().setValue(DynamoBlock.FACING, Direction.EAST));
        place(level, used, base, ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
        return base.east();
    }

    /** A transformer with its high side to this face, and a bushing on that face. Returns the bushing. */
    private static BlockPos transformer(ServerLevel level, List<BlockPos> used, BlockPos at, Direction high) {
        place(level, used, at, ElectricBlocks.TRANSFORMER.defaultBlockState().setValue(TransformerBlock.FACING, high));
        place(level, used, at.relative(high), ElectricBlocks.BUSHING.defaultBlockState().setValue(CoilBlock.FACING, high));
        return at.relative(high);
    }

    private static BlockPos coil(ServerLevel level, List<BlockPos> used, BlockPos seat, Direction side) {
        place(level, used, seat.relative(side), ElectricBlocks.COPPER_COIL.defaultBlockState().setValue(CoilBlock.FACING, side));
        return seat.relative(side);
    }

    @GameTest
    public void aTransformerStepsALineUpAndAnotherStepsItDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            BlockPos dynamo = dynamoOnACrankBox(level, used, base);
            // Up at the dynamo, a line of 24 blocks over a porcelain post, and down again at the far end.
            BlockPos up = dynamo.above(3);
            BlockPos upHigh = transformer(level, used, up, Direction.NORTH);
            BlockPos post = up.north(12);
            place(level, used, post, PORCELAIN_POST);
            BlockPos down = up.north(24);
            BlockPos downHigh = transformer(level, used, down, Direction.SOUTH);
            BlockPos[] lamps = {down.east(3), down.west(3)};
            for (BlockPos lamp : lamps) place(level, used, lamp, LAMP);

            Electricity.connect(level, dynamo, up, Gauge.WIRE);
            Electricity.connect(level, upHigh, post, Gauge.WIRE);
            Electricity.connect(level, post, downHigh, Gauge.WIRE);
            for (BlockPos lamp : lamps) Electricity.connect(level, down, lamp, Gauge.WIRE);

            var network = Electricity.solve(level, dynamo);
            helper.assertTrue(network.couplings().size() == 2, "Both transformers are in the one network: " + network.couplings());
            double low = network.volts()[network.indexOf(up)], high = network.volts()[network.indexOf(upHigh)];
            helper.assertTrue(high > low * 3.9 && high <= low * 4.0 + 1e-6, "The high side stands at four times the low: " + low + " / " + high);
            helper.assertTrue(high > 200.0, "From a 64-volt dynamo, over two hundred volts on the line: " + high);
            double lampVolts = network.volts()[network.indexOf(lamps[0])];
            helper.assertTrue(lampVolts > 50.0 && lampVolts < 64.0, "Stepped down again, the lamps see what they are made for: " + lampVolts);
            helper.assertTrue(level.getBlockState(lamps[0]).getValue(ElectricLampBlock.LIGHT) > 10, "and burn bright");

            double lineAmps = 0.0, lampAmps = 0.0;
            int bushing = network.indexOf(upHigh), postIndex = network.indexOf(post);
            for (var flow : network.flows()) {
                if ((flow.from() == bushing && flow.to() == postIndex) || (flow.to() == bushing && flow.from() == postIndex)) {
                    lineAmps = Math.abs(flow.amps());
                }
            }
            for (BlockPos lamp : lamps) lampAmps += Math.abs(network.amps()[network.indexOf(lamp)]);
            helper.assertTrue(lineAmps > 0.0 && lineAmps < lampAmps / 3.5,
                    "The line carries a quarter of what the lamps take: " + lineAmps + " of " + lampAmps);
            helper.assertTrue(Math.abs(network.generated() - network.delivered() - network.lost()) < 0.05,
                    "What is generated is delivered or lost, in the windings too: " + network);
            helper.assertTrue(((TransformerBlockEntity) level.getBlockEntity(up)).load() > 0.0f, "The transformer knows its load");
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest
    public void anOverloadedTransformerBurnsOutAndCouplesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            BlockPos dynamo = dynamoOnACrankBox(level, used, base);
            BlockPos at = dynamo.above(3);
            BlockPos high = transformer(level, used, at, Direction.NORTH);
            BlockPos lamp = high.north(3);
            place(level, used, lamp, LAMP);
            Electricity.connect(level, dynamo, at, Gauge.WIRE);
            Electricity.connect(level, high, lamp, Gauge.WIRE);
            var transformer = (TransformerBlockEntity) level.getBlockEntity(at);
            helper.assertTrue(transformer.highSide(level) != null, "A working transformer has its high side");

            boolean cool = transformer.carried(level, TransformerBlockEntity.RATED_AMPS * 0.9, 600.0);
            helper.assertTrue(!cool && !transformer.burnt(), "Within its rating it never burns");

            // Half as much again as it is rated for: the windings char within a quarter of a minute.
            boolean burnt = false;
            for (int i = 0; i < 40 && !burnt; i++) burnt = transformer.carried(level, TransformerBlockEntity.RATED_AMPS * 1.5, 0.5);
            helper.assertTrue(burnt && level.getBlockState(at).getValue(TransformerBlock.BURNT), "It burns out");
            Electricity.updateAround(level, at);
            helper.assertTrue(transformer.highSide(level) == null, "A burnt transformer couples nothing");
            helper.assertTrue(Electricity.solve(level, dynamo).couplings().isEmpty(), "The network no longer reaches past it");
            helper.assertTrue(((ElectricBlockEntity) level.getBlockEntity(lamp)).volts() == 0.0f, "and the lamp beyond it is dead");
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest
    public void aFuseBlowsBeforeTheWireAndASwitchCutsALine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            BlockPos dynamo = dynamoOnACrankBox(level, used, base);
            // Dynamo → fuse box → two posts standing over water: about six amperes, half again a fuse's four.
            BlockPos fuse = dynamo.above(3);
            place(level, used, fuse, ElectricBlocks.FUSE_BOX.defaultBlockState());
            BlockPos in = coil(level, used, fuse, Direction.WEST), out = coil(level, used, fuse, Direction.EAST);
            Electricity.connect(level, dynamo, in, Gauge.WIRE);
            BlockPos[] wet = {out.east(3).north(), out.east(3).south()};
            for (BlockPos post : wet) {
                place(level, used, post, POST);
                place(level, used, post.below(), Blocks.WATER.defaultBlockState());
                Electricity.connect(level, out, post, Gauge.CABLE);
            }
            var network = Electricity.solve(level, dynamo);
            helper.assertTrue(network.indexOf(wet[0]) >= 0, "Closed, the fuse joins its coils");
            var gear = (SwitchgearBlockEntity) level.getBlockEntity(fuse);
            Electricity.pass(level, dynamo, 0.5);
            helper.assertTrue(!level.getBlockState(fuse).getValue(SwitchgearBlock.CLOSED), "Half a second at half again its rating blows it");
            helper.assertTrue(((ElectricBlockEntity) level.getBlockEntity(dynamo)).wireTo(in) != null,
                    "The wire it guards is still there");
            helper.assertTrue(((ElectricBlockEntity) level.getBlockEntity(wet[0])).volts() == 0.0f, "Everything after it is dead");
            helper.assertTrue(Electricity.solve(level, dynamo).indexOf(wet[0]) < 0, "Blown, it joins nothing");
            helper.assertTrue(gear.note() != null, "The meter says what it is");

            // A knife switch in its place, with a lamp after it.
            for (BlockPos post : wet) Electricity.disconnect(level, out, post);
            BlockPos lamp = out.east(3);
            place(level, used, lamp, LAMP);
            Electricity.connect(level, out, lamp, Gauge.WIRE);
            level.setBlockAndUpdate(fuse, ElectricBlocks.KNIFE_SWITCH.defaultBlockState().setValue(SwitchgearBlock.CLOSED, true));
            Electricity.updateAround(level, fuse);
            helper.assertTrue(level.getBlockState(lamp).getValue(ElectricLampBlock.LIGHT) > 10, "Closed, the lamp after the switch burns");
            level.setBlockAndUpdate(fuse, level.getBlockState(fuse).setValue(SwitchgearBlock.CLOSED, false));
            Electricity.updateAround(level, fuse);
            helper.assertTrue(level.getBlockState(lamp).getValue(ElectricLampBlock.LIGHT) == 0, "Open, it is dark");
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest
    public void insulatedWireGoesThroughAWallAndBitesNobody(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos base = origin.above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        var pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, BlockPos.ZERO.above(2));
        try {
            BlockPos dynamo = dynamoOnACrankBox(level, used, base);
            BlockPos a = dynamo.above(3), b = a.north(6);
            place(level, used, a, POST);
            place(level, used, b, POST);
            place(level, used, a.north(3), Blocks.STONE.defaultBlockState());
            helper.assertTrue(Electricity.refusal(level, a, b, Gauge.WIRE) == Electricity.Refusal.BLOCKED,
                    "Bare wire needs clear air between its posts");
            helper.assertTrue(Electricity.refusal(level, a, b, Gauge.INSULATED) == null, "Insulated wire goes through the wall");
            level.setBlockAndUpdate(a.north(3), Blocks.AIR.defaultBlockState());

            // A pig standing in the middle of the line, at the height of the wire.
            Vec3 middle = Electricity.along(((ElectricBlockEntity) level.getBlockEntity(a)).terminal(),
                    ((ElectricBlockEntity) level.getBlockEntity(b)).terminal(), 0.5);
            Electricity.connect(level, dynamo, a, Gauge.WIRE);
            for (Gauge gauge : new Gauge[] {Gauge.INSULATED, Gauge.WIRE}) {
                Electricity.connect(level, a, b, gauge);
                pig.setHealth(pig.getMaxHealth());
                pig.setPos(middle.x, middle.y - 0.4, middle.z);
                Electricity.pass(level, dynamo, 0.5);
                boolean hurt = pig.getHealth() < pig.getMaxHealth();
                helper.assertTrue(hurt == !gauge.insulated(), (gauge.insulated() ? "Insulated wire bites nobody" : "Bare wire bites")
                        + ": " + pig.getHealth());
                Electricity.disconnect(level, a, b);
            }
        } finally {
            pig.discard();
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest
    public void porcelainStandsHighVoltageAndTheFourthMarksWantIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            place(level, used, base, POST);
            place(level, used, base.east(), PORCELAIN_POST);
            place(level, used, base.east(2), ElectricBlocks.BUSHING.defaultBlockState());
            double plain = ((ElectricBlockEntity) level.getBlockEntity(base)).insulationVolts();
            double porcelain = ((ElectricBlockEntity) level.getBlockEntity(base.east())).insulationVolts();
            double bushing = ((ElectricBlockEntity) level.getBlockEntity(base.east(2))).insulationVolts();
            helper.assertTrue(plain < 240.0 && porcelain > 240.0 && bushing > 240.0,
                    "Glazed brick flashes over at 240 volts in the rain, porcelain does not: " + plain + ", " + porcelain);

            helper.assertTrue(MachineBlockEntity.rate(60.0, 240.0) == 0.0 && MachineBlockEntity.rate(240.0, 240.0) == 1.0,
                    "A fourth mark only hums on sixty volts and runs on 240");
            helper.assertTrue(MachineBlockEntity.rate(60.0) == 1.0, "The first marks still run on sixty");

            // Eight lengths of enamelled wire make a winding; seven are not enough.
            BlockPos winder = base.above(2);
            place(level, used, winder, ElectricBlocks.COIL_WINDER.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
            var machine = (MachineBlockEntity) level.getBlockEntity(winder);
            helper.assertTrue(machine.takes(level, new ItemStack(Parts.ENAMELLED_WIRE)), "The winder takes enamelled wire");
            machine.insertFrom(new ItemStack(Parts.ENAMELLED_WIRE, 7), true);
            helper.assertTrue(machine.conductance() == 0.0, "Seven lengths: it waits for the eighth");
            machine.insertFrom(new ItemStack(Parts.ENAMELLED_WIRE, 1), true);
            helper.assertTrue(machine.conductance() > 0.0, "Eight: it has work");
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
