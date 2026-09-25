package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.machinery.CogwheelBlock;
import de.ipnats.hardwrought.machinery.CrankBoxBlock;
import de.ipnats.hardwrought.machinery.HandCrankBlock;
import de.ipnats.hardwrought.machinery.Kinetics;
import de.ipnats.hardwrought.machinery.ShaftBlock;
import de.ipnats.hardwrought.machinery.ShaftBlockEntity;
import de.ipnats.hardwrought.machinery.StarterCrusherBlockEntity;
import de.ipnats.hardwrought.machinery.WaterWheelBlock;
import de.ipnats.hardwrought.machinery.WaterWheelBlockEntity;
import de.ipnats.hardwrought.machinery.WindmillBlock;
import de.ipnats.hardwrought.machinery.WindmillBlockEntity;
import de.ipnats.hardwrought.water.WaterCurrent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Milestone 10, sections 73, 74 and 92: speeds, gearing, strength, and the wheels that give it.
 */
public final class KineticsGameTests {
    private static final int WORKSPACE = 40;

    @GameTest
    public void gearsTurnTheOtherWayAndALargeOneAtHalfTheSpeed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            BlockPos a = base.above();
            BlockPos b = a.east();
            BlockPos large = a.north().west();
            place(level, used, a, ModBlocks.COGWHEEL.defaultBlockState().setValue(CogwheelBlock.AXIS, Direction.Axis.Y));
            place(level, used, b, ModBlocks.COGWHEEL.defaultBlockState().setValue(CogwheelBlock.AXIS, Direction.Axis.Y));
            place(level, used, large, ModBlocks.LARGE_COGWHEEL.defaultBlockState().setValue(CogwheelBlock.AXIS, Direction.Axis.Y));
            place(level, used, base, ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));

            float speed = Kinetics.speed(level, a);
            helper.assertTrue(Math.abs(speed) == CrankBoxBlock.SPEED, "The gear on the crank box turns at its speed: " + speed);
            helper.assertTrue(Kinetics.speed(level, b) == -speed, "The small gear beside it turns the other way as fast");
            helper.assertTrue(Kinetics.speed(level, large) == -speed / 2,
                    "The large gear across the corner turns the other way at half the speed: " + Kinetics.speed(level, large));
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest
    public void aGearboxTurnsALineRoundACorner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            place(level, used, base.east(), ModBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
            place(level, used, base.east(2), ModBlocks.GEARBOX.defaultBlockState());
            place(level, used, base.east(2).south(), ModBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.Z));
            place(level, used, base, ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
            helper.assertTrue(Math.abs(Kinetics.speed(level, base.east(2).south())) == CrankBoxBlock.SPEED,
                    "The shaft leaving the gearbox at a right angle turns as fast as the one going in");
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest
    public void aBeltCarriesTheTurnToAParallelShaft(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            BlockPos lower = base.east();
            BlockPos upper = lower.above(3).south(2);
            place(level, used, lower, ModBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
            place(level, used, upper, ModBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
            helper.assertTrue(ShaftBlockEntity.canBelt(level, lower, upper), "Two parallel shafts side by side take a belt");
            helper.assertFalse(ShaftBlockEntity.canBelt(level, lower, lower.east(3)),
                    "Two on one axis do not: they are one line already");
            ((ShaftBlockEntity) level.getBlockEntity(lower)).setBelt(upper);
            ((ShaftBlockEntity) level.getBlockEntity(upper)).setBelt(lower);
            place(level, used, base, ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
            helper.assertTrue(Kinetics.speed(level, upper) == Kinetics.speed(level, lower)
                            && Kinetics.speed(level, upper) != 0,
                    "The belted shaft turns with the driven one, the same way round");

            level.destroyBlock(lower, false);
            used.remove(lower);
            helper.assertTrue(((ShaftBlockEntity) level.getBlockEntity(upper)).belt() == null,
                    "Breaking one end throws the belt off the other");
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest
    public void aHandCrankCarriesOneCrusherButNotTwo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            place(level, used, base, ModBlocks.HAND_CRANK.defaultBlockState().setValue(HandCrankBlock.FACING, Direction.EAST));
            place(level, used, base.east(), ModBlocks.STARTER_CRUSHER.defaultBlockState());
            HandCrankBlock.crank(level, base);
            helper.assertTrue(Kinetics.speed(level, base.east()) != 0,
                    "One crusher (" + StarterCrusherBlockEntity.IMPACT * HandCrankBlock.SPEED + " of "
                            + HandCrankBlock.CAPACITY + ") turns by hand");
            place(level, used, base.east(2), ModBlocks.STARTER_CRUSHER.defaultBlockState());
            var network = Kinetics.solve(level, base.east());
            helper.assertTrue(network.status() == Kinetics.Status.OVERSTRESSED,
                    "Two are too heavy for an arm: " + network.status());
            helper.assertTrue(Kinetics.speed(level, base.east()) == 0 && Kinetics.speed(level, base.east(2)) == 0,
                    "and the whole line stops");
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest
    public void aWaterWheelTurnsInRunningWaterAndNotInStill(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hub = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        List<BlockPos> used = new ArrayList<>();
        try {
            place(level, used, hub, ModBlocks.WATER_WHEEL.defaultBlockState().setValue(WaterWheelBlock.AXIS, Direction.Axis.X));
            place(level, used, hub.east(), ModBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.X));
            // The three lower paddles stand in water, in a stone trough so it stays put.
            for (int dz = -2; dz <= 2; dz++) {
                place(level, used, hub.offset(0, -2, dz), Blocks.STONE.defaultBlockState());
                place(level, used, hub.offset(-1, -1, dz), Blocks.STONE.defaultBlockState());
                place(level, used, hub.offset(1, -1, dz), Blocks.STONE.defaultBlockState());
            }
            place(level, used, hub.offset(0, -1, -2), Blocks.STONE.defaultBlockState());
            place(level, used, hub.offset(0, -1, 2), Blocks.STONE.defaultBlockState());
            for (int dz = -1; dz <= 1; dz++) place(level, used, hub.offset(0, -1, dz), Blocks.WATER.defaultBlockState());
            var wheel = (WaterWheelBlockEntity) level.getBlockEntity(hub);
            wheel.remeasure(level, hub, level.getBlockState(hub));
            helper.assertTrue(wheel.output() == 0, "Still water turns nothing");

            // Water running under it, across the axle.
            for (int dz = -1; dz <= 1; dz++) {
                WaterCurrent.record(level, hub.offset(0, -1, dz + 1), hub.offset(0, -1, dz), 250);
            }
            wheel.remeasure(level, hub, level.getBlockState(hub));
            helper.assertTrue(wheel.output() != 0 && wheel.capacity() > 0, "Running water turns it: " + wheel.output());
            helper.assertTrue(Kinetics.speed(level, hub.east()) != 0, "and the shaft on its axle with it");

            place(level, used, hub.above(), Blocks.STONE.defaultBlockState());
            wheel.remeasure(level, hub, level.getBlockState(hub));
            helper.assertTrue(wheel.output() == 0, "A block in the wheel's way jams it");
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest
    public void aRiverCurrentIsKeptWithItsChunkAndPushesTheWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(helper.absolutePos(BlockPos.ZERO).getX(), level.getSeaLevel() - 1,
                helper.absolutePos(BlockPos.ZERO).getZ());
        var chunk = level.getChunkAt(at);
        var before = level.getBlockState(at);
        var riverBefore = chunk.getAttached(WaterCurrent.RIVER);
        try {
            level.setBlock(at, Blocks.WATER.defaultBlockState(), 2);
            chunk.setAttached(WaterCurrent.RIVER, new WaterCurrent.River(1.0f, 0.0f));
            Vec3 current = WaterCurrent.at(level, at);
            helper.assertTrue(current.x > 0.5 && Math.abs(current.z) < 1e-6, "A river's water runs along the river: " + current);
            Vec3 flow = level.getFluidState(at).getFlow(level, at);
            helper.assertTrue(flow.x > 0.5, "and what floats in it is carried the same way: " + flow);
            helper.assertTrue(WaterCurrent.at(level, at.above(40)).equals(Vec3.ZERO), "Air has no current");
        } finally {
            chunk.setAttached(WaterCurrent.RIVER, riverBefore);
            level.setBlock(at, before, 2);
        }
        helper.succeed();
    }

    @GameTest
    public void aWindmillWantsHeightAndOpenAir(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        BlockPos hub = new BlockPos(base.getX(), level.getSeaLevel() + 128, base.getZ());
        List<BlockPos> used = new ArrayList<>();
        try {
            place(level, used, hub, ModBlocks.WINDMILL.defaultBlockState().setValue(WindmillBlock.FACING, Direction.NORTH));
            var mill = (WindmillBlockEntity) level.getBlockEntity(hub);
            mill.remeasure(level, hub, level.getBlockState(hub));
            helper.assertTrue(mill.output() > 0 && mill.capacity() > 0,
                    "High up in open air the sails turn: " + mill.output() + " / " + mill.capacity());
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    if (dx != 0 || dy != 0) place(level, used, hub.offset(dx, dy, 0), Blocks.STONE.defaultBlockState());
                }
            }
            mill.remeasure(level, hub, level.getBlockState(hub));
            helper.assertTrue(mill.output() == 0, "Walled in, they catch no wind");
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 420)
    public void anOreDrillIsAsDeepAsTheWeakestBlockOfItsFrame(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE);
        BlockPos head = base.above();
        List<BlockPos> used = new ArrayList<>();
        place(level, used, base, ModBlocks.CRANK_BOX.defaultBlockState().setValue(CrankBoxBlock.TURNING, true));
        place(level, used, head, ModBlocks.ORE_DRILL.defaultBlockState());
        var drill = (de.ipnats.hardwrought.machinery.OreDrillBlockEntity) level.getBlockEntity(head);
        List<BlockPos> frame = de.ipnats.hardwrought.machinery.OreDrillBlockEntity.framePositions(head);
        for (int i = 0; i < frame.size() - 1; i++) place(level, used, frame.get(i), ModBlocks.DRILL_FRAME_IRON.defaultBlockState());
        drill.lookOverFrameNow();
        helper.assertTrue(drill.tier() == null, "With one frame block missing it is no drill");
        place(level, used, frame.getLast(), ModBlocks.DRILL_FRAME_BRONZE.defaultBlockState());
        drill.lookOverFrameNow();
        helper.assertTrue(drill.tier() == de.ipnats.hardwrought.geology.DrillTier.BRONZE,
                "Complete, it is as good as its weakest block: one bronze frame in sixteen iron ones makes a bronze drill");
        helper.assertTrue(drill.yield().entries().stream().allMatch(entry ->
                        entry.ore().getPath().equals("coal_ore") || entry.ore().getPath().equals("iron_ore")),
                "and it reaches coal and iron only");
        helper.assertTrue(Math.abs(Kinetics.speed(level, head)) == CrankBoxBlock.SPEED, "The crank box under it drives it");
        boolean barren = drill.yield().barren();
        helper.runAfterDelay(de.ipnats.hardwrought.geology.DrillTier.BRONZE.intervalTicks() / 2 + 40, () -> {
            try {
                if (!barren) {
                    helper.assertFalse(drill.isEmpty(), "Turned, it brings ore up out of the chunk");
                    var ore = drill.getItem(0);
                    helper.assertTrue(ore.is(net.minecraft.world.item.Items.COAL_ORE) || ore.is(net.minecraft.world.item.Items.IRON_ORE),
                            "and it is ore of the chunk it stands on: " + ore);
                }
            } finally {
                clear(level, used);
            }
            helper.succeed();
        });
    }

    @GameTest
    public void aFinishedDrillIsDrawnAsOneMachineAndWorkedFromAnyBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos head = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE + 1);
        List<BlockPos> used = new ArrayList<>();
        try {
            place(level, used, head, ModBlocks.ORE_DRILL.defaultBlockState());
            List<BlockPos> frame = de.ipnats.hardwrought.machinery.OreDrillBlockEntity.framePositions(head);
            for (BlockPos pos : frame) place(level, used, pos, ModBlocks.DRILL_FRAME_NICKEL.defaultBlockState());
            // No look over by hand: the last frame block set down finishes the drill on its own.
            helper.assertTrue(level.getBlockState(head).getValue(de.ipnats.hardwrought.machinery.OreDrillBlock.FORMED),
                    "The last frame block closes the head in");
            for (int i = 0; i < frame.size(); i++) {
                int part = level.getBlockState(frame.get(i)).getValue(de.ipnats.hardwrought.machinery.DrillFrameBlock.PART);
                helper.assertTrue(part == i + 1, "Every frame block knows its part of the drill: " + frame.get(i) + " is " + part);
                helper.assertTrue(de.ipnats.hardwrought.machinery.OreDrillBlockEntity.headOf(frame.get(i), part).equals(head),
                        "and finds the head from it, so using it works the drill");
            }
            level.setBlockAndUpdate(frame.get(12), Blocks.AIR.defaultBlockState());
            helper.assertFalse(level.getBlockState(head).getValue(de.ipnats.hardwrought.machinery.OreDrillBlock.FORMED),
                    "Taking one block out opens the drill up again");
            helper.assertTrue(level.getBlockState(frame.get(0)).getValue(de.ipnats.hardwrought.machinery.DrillFrameBlock.PART) == 0,
                    "and the rest are loose frames");
        } finally {
            clear(level, used);
        }
        helper.succeed();
    }

    private static void place(ServerLevel level, List<BlockPos> used, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        level.setBlockAndUpdate(pos, state);
        used.add(pos.immutable());
    }

    private static void clear(ServerLevel level, List<BlockPos> used) {
        for (int i = used.size() - 1; i >= 0; i--) level.setBlock(used.get(i), Blocks.AIR.defaultBlockState(), 2);
    }
}
