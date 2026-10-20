package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.electricity.CoilBlock;
import de.ipnats.hardwrought.electricity.DynamoBlock;
import de.ipnats.hardwrought.electricity.ElectricBlocks;
import de.ipnats.hardwrought.electricity.ElectricDriveBlock;
import de.ipnats.hardwrought.electricity.ElectricDriveBlockEntity;
import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.electricity.Gauge;
import de.ipnats.hardwrought.electricity.SwitchgearBlock;
import de.ipnats.hardwrought.machinery.CrankBoxBlock;
import de.ipnats.hardwrought.machinery.StarterCrusherBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Motors that turn the driveline (docs/electricity-roadmap.md, stage E2): the electric drive, its
 * controller and its brushes.
 */
public final class MotorGameTests {
    /** Each test works at its own height above its structure, so that their wide layouts never meet. */

    /** A crank box turning a dynamo east of it at 32 turns a minute: 64 volts. Returns the dynamo. */
    private static BlockPos dynamo(ServerLevel level, List<BlockPos> used, BlockPos base) {
        place(level, used, base.east(), ElectricBlocks.DYNAMO.defaultBlockState().setValue(DynamoBlock.FACING, Direction.EAST));
        place(level, used, base, ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
        return base.east();
    }

    /** A drive with its axle east–west and this many starter crushers in a row on its east end. */
    private static BlockPos drive(ServerLevel level, List<BlockPos> used, BlockPos at, int crushers, boolean controlled) {
        place(level, used, at, ElectricBlocks.ELECTRIC_DRIVE.defaultBlockState().setValue(ElectricDriveBlock.FACING, Direction.EAST)
                .setValue(ElectricDriveBlock.CONTROLLED, controlled));
        for (int i = 1; i <= crushers; i++) place(level, used, at.east(i), ModBlocks.STARTER_CRUSHER.defaultBlockState());
        return at;
    }

    @GameTest(maxTicks = 200)
    public void aDriveTurnsItsLineAtTheSpeedItsVoltageGives(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(70);
        List<BlockPos> used = new ArrayList<>();
        BlockPos dynamo = dynamo(level, used, base);
        BlockPos drive = drive(level, used, base.south(4), 1, false);
        Electricity.connect(level, dynamo, drive, Gauge.WIRE);
        var crusher = (StarterCrusherBlockEntity) level.getBlockEntity(drive.east());
        crusher.insertFrom(new ItemStack(Items.RAW_IRON, 4), false);
        helper.runAfterDelay(120, () -> {
            try {
                var entity = (ElectricDriveBlockEntity) level.getBlockEntity(drive);
                double volts = Math.abs(entity.volts());
                helper.assertTrue(volts > 50.0, "The drive sees most of the dynamo's 64 volts: " + volts);
                helper.assertTrue(Math.abs(Math.abs(entity.kineticSpeed()) - volts / 2.0) < 1.0,
                        "It turns at a turn a minute for every two volts: " + entity.kineticSpeed() + " on " + volts);
                helper.assertTrue(Math.abs(crusher.kineticSpeed()) == Math.abs(entity.kineticSpeed()), "and its line with it");
                double amps = Math.abs(entity.amps());
                helper.assertTrue(amps > ElectricDriveBlockEntity.IDLE_AMPS + 0.5 && amps < ElectricDriveBlockEntity.RATED_AMPS,
                        "It draws what one crusher asks of it, within its rating: " + amps);
                helper.assertTrue(!crusher.getItem(StarterCrusherBlockEntity.OUTPUT).isEmpty(), "The crusher runs on a motor");
            } finally {
                clear(level, used);
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 200)
    public void aStalledDriveTripsItsBreakerAndAControllerHoldsIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(77);
        List<BlockPos> used = new ArrayList<>();
        // Two drives behind a breaker each, both with four crushers: more than a drive can turn.
        BlockPos[] breakers = new BlockPos[2];
        BlockPos[] drives = new BlockPos[2];
        for (int i = 0; i < 2; i++) {
            BlockPos dynamo = dynamo(level, used, base.south(i * 12));
            BlockPos breaker = dynamo.above(3);
            place(level, used, breaker, ElectricBlocks.BREAKER.defaultBlockState());
            BlockPos in = breaker.west(), out = breaker.east();
            place(level, used, in, ElectricBlocks.COPPER_COIL.defaultBlockState().setValue(CoilBlock.FACING, Direction.WEST));
            place(level, used, out, ElectricBlocks.COPPER_COIL.defaultBlockState().setValue(CoilBlock.FACING, Direction.EAST));
            BlockPos drive = drive(level, used, dynamo.south(5), 4, i == 1);
            Electricity.connect(level, dynamo, in, Gauge.CABLE);
            Electricity.connect(level, out, drive, Gauge.CABLE);
            breakers[i] = breaker;
            drives[i] = drive;
        }
        helper.runAfterDelay(140, () -> {
            try {
                var bare = (ElectricDriveBlockEntity) level.getBlockEntity(drives[0]);
                var held = (ElectricDriveBlockEntity) level.getBlockEntity(drives[1]);
                helper.assertTrue(!level.getBlockState(breakers[0]).getValue(SwitchgearBlock.CLOSED),
                        "Held still without a controller, a drive draws five times its rating and trips its breaker");
                helper.assertTrue(held.stalled(), "The controlled drive cannot turn four crushers either");
                helper.assertTrue(level.getBlockState(breakers[1]).getValue(SwitchgearBlock.CLOSED),
                        "but its controller holds it to its rating, and the breaker stays in");
                helper.assertTrue(Math.abs(held.amps()) <= ElectricDriveBlockEntity.RATED_AMPS + 0.2, "at about four amperes: " + held.amps());
                helper.assertTrue(bare.note() != null, "The meter says what it is");
            } finally {
                clear(level, used);
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 200)
    public void aControllerSetsTheSpeedAndWornBrushesStopIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(84);
        List<BlockPos> used = new ArrayList<>();
        BlockPos dynamo = dynamo(level, used, base);
        BlockPos drive = drive(level, used, base.south(4), 1, true);
        Electricity.connect(level, dynamo, drive, Gauge.WIRE);
        var entity = (ElectricDriveBlockEntity) level.getBlockEntity(drive);
        helper.runAfterDelay(40, () -> {
            entity.turnDown();
            entity.turnDown();
            helper.assertTrue(entity.quarters() == 2, "Turned down twice: half the voltage");
        });
        helper.runAfterDelay(100, () -> {
            double volts = Math.abs(entity.volts());
            helper.assertTrue(Math.abs(Math.abs(entity.kineticSpeed()) - volts / 4.0) < 1.0,
                    "At half the voltage it turns half as fast: " + entity.kineticSpeed() + " on " + volts);
            helper.assertTrue(!entity.newBrushes(), "New brushes are not taken while the old are good");
            entity.wearBrushes(ElectricDriveBlockEntity.BRUSH_SECONDS);
        });
        helper.runAfterDelay(140, () -> {
            try {
                helper.assertTrue(entity.drive() == 0.0f && entity.kineticSpeed() == 0.0f, "Brushes worn through, it stops");
                helper.assertTrue(((StarterCrusherBlockEntity) level.getBlockEntity(drive.east())).kineticSpeed() == 0.0f,
                        "and its line with it");
                helper.assertTrue(entity.newBrushes() && entity.brushes() == 1.0f, "A new pair goes in");
            } finally {
                clear(level, used);
            }
            helper.succeed();
        });
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
