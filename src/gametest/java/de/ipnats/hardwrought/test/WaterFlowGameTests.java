package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.water.WaterAmounts;
import de.ipnats.hardwrought.water.WaterDraw;
import de.ipnats.hardwrought.water.WaterFlow;
import de.ipnats.hardwrought.water.WaterStorage;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.Map;

public final class WaterFlowGameTests {
    private static final int WORKSPACE_OFFSET = 40;

    @GameTest
    public void amountsAndDisplayLevelsAgree(GameTestHelper helper) {
        helper.assertTrue(WaterAmounts.BLOCK == 1000 && WaterAmounts.BUCKET == 1000
                        && WaterAmounts.BOTTLE == 100,
                "A block and a bucket hold a thousand millibuckets, a bottle a hundred");
        helper.assertTrue(WaterAmounts.WATERSKIN == 8 * WaterAmounts.DRINK,
                "A skin is eight drinks, and a drink is a bottle");

        helper.assertTrue(WaterAmounts.displayLevel(1000) == 0, "A full block is drawn full");
        helper.assertTrue(WaterAmounts.displayLevel(875) == 1, "Seven eighths is one step down");
        helper.assertTrue(WaterAmounts.displayLevel(125) == 7, "One eighth is the lowest step drawn");
        helper.assertTrue(WaterAmounts.displayLevel(100) == 7,
                "and a bottle's worth is drawn at that same lowest step");
        helper.assertTrue(WaterAmounts.displayLevel(1) == 7, "as is anything smaller that is still there");

        helper.assertTrue(WaterAmounts.nominalAmount(Blocks.WATER.defaultBlockState()) == 1000,
                "Water from world generation counts as a full block");
        helper.assertTrue(WaterAmounts.nominalAmount(
                        Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 7)) == 125,
                "and a shallow one at its drawn value");
        helper.assertTrue(WaterAmounts.room(300) == 700 && WaterAmounts.room(1000) == 0,
                "Room left is what is missing to a full block");
        helper.assertTrue(WaterAmounts.clamp(-5) == 0 && WaterAmounts.clamp(5000) == WaterAmounts.MAX_CELL,
                "Amounts stay inside what one cell can hold under pressure");
        helper.succeed();
    }

    @GameTest
    public void waterFallsAndConservesItsVolume(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        var flow = CoreLifecycle.require(level.getServer()).waterFlow();
        try {
            // A closed shaft three deep with a full block of water at the top.
            column(level, base, 4, Blocks.STONE.defaultBlockState());
            for (int y = 0; y <= 2; y++) level.setBlockAndUpdate(base.above(y), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(base.above(2), Blocks.WATER.defaultBlockState());
            int before = total(level, base, 3);
            helper.assertTrue(before == WaterAmounts.BLOCK, "One full block is a thousand millibuckets");

            for (int pass = 0; pass < 12; pass++) {
                for (int y = 2; y >= 0; y--) flow.step(level, base.above(y));
            }
            int after = total(level, base, 3);
            helper.assertTrue(after == before,
                    "Water that falls is moved, not created or destroyed: " + before + " became " + after);
            helper.assertTrue(WaterStorage.amount(level, base) == WaterAmounts.BLOCK,
                    "and all of it ends up at the bottom of the shaft");
            helper.assertTrue(WaterStorage.amount(level, base.above(2)) == 0,
                    "leaving nothing where it started");
        } finally {
            column(level, base, 4, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest
    public void waterLevelsOutWithoutMakingMore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        var flow = CoreLifecycle.require(level.getServer()).waterFlow();
        try {
            // A closed trough five long, with a full block at one end.
            trough(level, base, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(base, Blocks.WATER.defaultBlockState());
            int before = troughTotal(level, base);
            helper.assertTrue(before == WaterAmounts.BLOCK, "One full block to start with");

            // Pairwise equilibration now moves half the difference, so a short run is enough even
            // across the whole trough. This guards against accidentally restoring sluggish flow.
            for (int pass = 0; pass < 12; pass++) {
                for (int x = 0; x <= 4; x++) flow.step(level, base.offset(x, 0, 0));
            }
            int after = troughTotal(level, base);
            helper.assertTrue(after == before,
                    "Levelling out moves water, it never makes more: " + before + " became " + after);
            helper.assertTrue(WaterStorage.amount(level, base) < WaterAmounts.BLOCK,
                    "The block it started in has given water away");
            helper.assertTrue(WaterStorage.amount(level, base.offset(1, 0, 0)) > 0,
                    "and its neighbour has received some");

            // The whole point of section 23.1: no cell ever becomes a source that makes more.
            for (int x = 0; x <= 4; x++) {
                helper.assertTrue(WaterStorage.amount(level, base.offset(x, 0, 0)) <= WaterAmounts.BLOCK,
                        "No cell holds more than a block");
            }
        } finally {
            trough(level, base, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest
    public void partialAmountsSurviveBeingStored(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        try {
            level.setBlockAndUpdate(base.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(base, Blocks.AIR.defaultBlockState());

            WaterStorage.setAmount(level, base, 340);
            helper.assertTrue(WaterStorage.amount(level, base) == 340,
                    "An exact amount is kept, not rounded to what can be drawn");
            helper.assertTrue(level.getBlockState(base).getValue(LiquidBlock.LEVEL)
                            == WaterAmounts.displayLevel(340),
                    "and the block shows the nearest step it can");

            WaterStorage.setAmount(level, base, WaterAmounts.BLOCK);
            helper.assertTrue(WaterStorage.amount(level, base) == WaterAmounts.BLOCK
                            && level.getBlockState(base).getValue(LiquidBlock.LEVEL) == 0,
                    "A full block needs nothing stored: full is what a full block means");

            WaterStorage.setAmount(level, base, 0);
            helper.assertTrue(level.getBlockState(base).isAir() && WaterStorage.amount(level, base) == 0,
                    "and emptying it removes the water entirely");

            // Persistent map codecs may return immutable maps after loading a real saved chunk.
            // The simulation must make that data writable once instead of disabling itself.
            level.setBlockAndUpdate(base, Blocks.WATER.defaultBlockState());
            level.getChunkAt(base).setAttached(WaterStorage.PARTIAL_WATER,
                    WaterStorage.WaterChunkData.fromSerialized(
                            Map.of(Long.toString(base.asLong()), 340)));
            WaterStorage.setAmount(level, base, 500);
            helper.assertTrue(WaterStorage.amount(level, base) == 500,
                    "Water loaded from an immutable attachment remains writable");
            WaterStorage.setAmount(level, base, 0);

            helper.assertFalse(WaterStorage.canHold(level, base.below()), "Stone holds no water");
            helper.assertTrue(WaterStorage.canHold(level, base), "Empty space does");
        } finally {
            level.setBlockAndUpdate(base, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(base.below(), Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest
    public void waterloggedBlocksAndBubbleColumnsHoldFiniteWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        var flow = CoreLifecycle.require(level.getServer()).waterFlow();
        try {
            // Leave only the eastern neighbour open so the exact thousand millibuckets have one
            // route out of each carrier.
            for (int x = 0; x <= 3; x++) {
                level.setBlockAndUpdate(base.offset(x, -1, 0), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(base.offset(x, 0, -1), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(base.offset(x, 0, 1), Blocks.STONE.defaultBlockState());
            }
            level.setBlockAndUpdate(base.west(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(base.offset(1, 0, 0), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(base, Blocks.OAK_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.WATERLOGGED, true));

            helper.assertTrue(WaterStorage.amount(level, base) == WaterAmounts.BLOCK,
                    "A newly waterlogged slab starts with one finite block of water");
            flow.step(level, base);
            helper.assertTrue(WaterStorage.amount(level, base) + WaterStorage.amount(level, base.east())
                            == WaterAmounts.BLOCK,
                    "Water leaves the slab without being created");
            WaterStorage.setAmount(level, base, WaterAmounts.BLOCK / 2);
            helper.assertTrue(WaterStorage.carrierLevel(level, base) == WaterAmounts.displayLevel(WaterAmounts.BLOCK / 2),
                    "A half-filled waterlogged slab tells the client to draw half a block of water");
            WaterStorage.setAmount(level, base, WaterAmounts.BLOCK);
            helper.assertTrue(WaterStorage.carrierLevel(level, base) == 0,
                    "A full waterlogged slab is drawn full again");
            WaterStorage.setAmount(level, base, 0);
            helper.assertTrue(level.getBlockState(base).is(Blocks.OAK_SLAB)
                            && !level.getBlockState(base).getValue(BlockStateProperties.WATERLOGGED),
                    "An empty waterlogged block remains in place but becomes dry");

            BlockPos bubble = base.offset(2, 0, 0);
            WaterStorage.setAmount(level, base.east(), 0);
            level.setBlockAndUpdate(bubble.west(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(bubble, Blocks.BUBBLE_COLUMN.defaultBlockState());
            level.setBlockAndUpdate(bubble.east(), Blocks.AIR.defaultBlockState());
            helper.assertTrue(WaterStorage.amount(level, bubble) == WaterAmounts.BLOCK,
                    "A bubble column also contains one finite block of water");
            flow.step(level, bubble);
            helper.assertTrue(WaterStorage.amount(level, bubble)
                            + WaterStorage.amount(level, bubble.east()) == WaterAmounts.BLOCK,
                    "A bubble column can drain without creating a source");
            WaterStorage.setAmount(level, bubble, 0);
            helper.assertTrue(level.getBlockState(bubble).isAir(),
                    "An empty bubble column disappears");
        } finally {
            for (int x = -1; x <= 4; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 120)
    public void lakeInteriorKeepsFeedingADrainingEdge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        for (int x = -1; x <= 6; x++) {
            level.setBlockAndUpdate(base.offset(x, -1, 0), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(base.offset(x, 0, -1), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(base.offset(x, 0, 1), Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(base.west(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(base.offset(6, 0, 0), Blocks.STONE.defaultBlockState());
        for (int x = 0; x <= 4; x++) WaterStorage.setAmount(level, base.offset(x, 0, 0), WaterAmounts.BLOCK);
        level.setBlockAndUpdate(base.offset(5, 0, 0), Blocks.AIR.defaultBlockState());
        WaterFlow.disturb(level, base.offset(4, 0, 0));

        helper.runAfterDelay(60, () -> {
            int total = 0;
            for (int x = 0; x <= 5; x++) total += WaterStorage.amount(level, base.offset(x, 0, 0));
            helper.assertTrue(total == 5 * WaterAmounts.BLOCK,
                    "A draining lake edge conserves all water: " + total + " of 5000 mB");
            helper.assertTrue(WaterStorage.amount(level, base.offset(3, 0, 0)) < WaterAmounts.BLOCK,
                    "The lake interior must keep feeding its disturbed shoreline");
            for (int x = -1; x <= 6; x++) {
                for (int y = -1; y <= 0; y++) {
                    for (int z = -1; z <= 1; z++) {
                        level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
            helper.succeed();
        });
    }

    /**
     * The scene from a real world: a full block of water put on a ledge with open air beside it. This
     * runs no solver call of its own — only real ticks — so it covers the path that actually matters
     * in play: something disturbs the water, the disturbance reaches the simulation, and the water
     * runs off the ledge.
     */
    @GameTest(maxTicks = 200)
    public void waterPutOnALedgeRunsOffIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        // A single stone block to stand the water on. A basin far below catches fast-moving water,
        // so the conservation assertion does not mistake leaving the old scan box for water loss.
        for (int x = -4; x <= 4; x++) {
            for (int y = -5; y <= 2; y++) {
                for (int z = -4; z <= 4; z++) {
                    boolean basin = y == -5 || (Math.abs(x) == 4 || Math.abs(z) == 4) && y <= 0;
                    level.setBlockAndUpdate(base.offset(x, y, z), basin
                            ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(base.below(), Blocks.STONE.defaultBlockState());
        // The ledge is overgrown, the way any real hillside is. Water washes grass away and runs on.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (x == 0 && z == 0) continue;
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.DIRT.defaultBlockState());
                level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.SHORT_GRASS.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(base, Blocks.WATER.defaultBlockState());

        helper.runAfterDelay(120, () -> {
            int left = WaterStorage.amount(level, base);
            // A crash in the simulation job would leave it switched off for the rest of the session,
            // and the water would simply stop wherever the last pass left it. That has happened, so
            // it is checked first and explicitly.
            var flow = CoreLifecycle.require(level.getServer()).scheduler().profiles().stream()
                    .filter(profile -> profile.id().equals("hardwrought:water_flow"))
                    .findFirst().orElseThrow();
            helper.assertFalse(flow.disabled(),
                    "The water simulation must survive running: it has been disabled by a failure");
            helper.assertTrue(flow.calls() > 1,
                    "and it has to have run repeatedly, not once");

            // Not merely "it moved once": the ledge has to end up genuinely drained.
            helper.assertTrue(left < WaterAmounts.SPREAD_THRESHOLD,
                    "Water on an overgrown ledge has to wash the growth away and run off it, not "
                            + "shuffle once and stop: " + left + " mB still in place");
            int total = 0;
            for (int x = -3; x <= 3; x++) {
                for (int y = -4; y <= 2; y++) {
                    for (int z = -3; z <= 3; z++) {
                        total += WaterStorage.amount(level, base.offset(x, y, z));
                    }
                }
            }
            helper.assertTrue(total == WaterAmounts.BLOCK,
                    "Washing a plant out of the way must not create or destroy water: "
                            + total + " mB of the thousand that went in");
            helper.assertFalse(level.getBlockState(base.north()).is(Blocks.SHORT_GRASS),
                    "and the growth the water ran through is gone");
            for (int x = -4; x <= 4; x++) {
                for (int y = -5; y <= 2; y++) {
                    for (int z = -4; z <= 4; z++) {
                        level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
            level.setBlockAndUpdate(base.below(), Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }

    @GameTest
    public void waterPressedInFromBelowRisesToTheTop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        var flow = CoreLifecycle.require(level.getServer()).waterFlow();
        try {
            // A sealed shaft filled to the brim, with room above it.
            column(level, base, 5, Blocks.STONE.defaultBlockState());
            for (int y = 0; y <= 4; y++) level.setBlockAndUpdate(base.above(y), Blocks.AIR.defaultBlockState());
            for (int y = 0; y <= 2; y++) WaterStorage.setAmount(level, base.above(y), WaterAmounts.BLOCK);
            int before = total(level, base, 5);
            helper.assertTrue(before == 3 * WaterAmounts.BLOCK, "Three full blocks to start with");

            // A bucket pressed in at the very bottom, where everything around it is already full.
            WaterStorage.setAmount(level, base, WaterAmounts.BLOCK + WaterAmounts.BUCKET);
            helper.assertTrue(WaterStorage.amount(level, base) == 2000,
                    "A cell under pressure keeps what was pushed into it instead of losing it");

            for (int pass = 0; pass < 20; pass++) {
                for (int y = 0; y <= 4; y++) flow.step(level, base.above(y));
            }
            int after = total(level, base, 5);
            helper.assertTrue(after == before + WaterAmounts.BUCKET,
                    "The bucket is still there afterwards: " + after + " of "
                            + (before + WaterAmounts.BUCKET));
            helper.assertTrue(WaterStorage.amount(level, base.above(3)) > 0,
                    "and it came out at the top, raising the level");
            helper.assertTrue(WaterStorage.amount(level, base) >= WaterAmounts.BLOCK,
                    "while the bottom carries at least a full block, and a little more for the "
                            + "weight standing on it");
        } finally {
            column(level, base, 5, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    /**
     * The same rise, but driven only by real ticks: a bucket goes into the bottom of a full shaft and
     * has to come out at the top. Calling the solver by hand proves the rule; this proves that the
     * rule is actually reached in a running game.
     */
    @GameTest(maxTicks = 300)
    public void pressureReachesTheTopThroughTheRunningSimulation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        column(level, base, 5, Blocks.STONE.defaultBlockState());
        for (int y = 0; y <= 4; y++) level.setBlockAndUpdate(base.above(y), Blocks.AIR.defaultBlockState());
        for (int y = 0; y <= 2; y++) WaterStorage.setAmount(level, base.above(y), WaterAmounts.BLOCK);

        // Exactly what emptying a bucket into the bottom of the shaft does.
        int present = WaterStorage.amount(level, base);
        WaterStorage.setAmount(level, base, present + WaterAmounts.BUCKET);
        WaterFlow.disturb(level, base);

        helper.runAfterDelay(200, () -> {
            var job = CoreLifecycle.require(level.getServer()).scheduler().profiles().stream()
                    .filter(profile -> profile.id().equals("hardwrought:water_flow"))
                    .findFirst().orElseThrow();
            helper.assertFalse(job.disabled(), "The water simulation must still be running");

            int total = total(level, base, 5);
            helper.assertTrue(total == 4 * WaterAmounts.BLOCK,
                    "The bucket is still in the shaft: " + total + " of 4000 mB; " + describeShaft(level, base, 5));
            helper.assertTrue(WaterStorage.amount(level, base.above(3)) > 0,
                    "and the level rose without anyone stepping the solver by hand");
            helper.assertTrue(WaterStorage.amount(level, base) >= WaterAmounts.BLOCK,
                    "while the bottom carries the weight of what stands on it");
            column(level, base, 5, Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }

    /**
     * A tank that is only part full, with the bucket poured in at the top of the shaft — where water
     * arriving from above would land. It has to end up at the bottom of the tank, stacked, with
     * nothing left hanging in the air where it was poured.
     */
    @GameTest(maxTicks = 300)
    public void waterPouredInAtTheTopEndsUpAtTheBottom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        column(level, base, 6, Blocks.STONE.defaultBlockState());
        for (int y = 0; y <= 5; y++) level.setBlockAndUpdate(base.above(y), Blocks.AIR.defaultBlockState());
        for (int y = 0; y <= 1; y++) WaterStorage.setAmount(level, base.above(y), WaterAmounts.BLOCK);

        BlockPos top = base.above(5);
        int present = WaterStorage.amount(level, top);
        WaterStorage.setAmount(level, top, present + WaterAmounts.BUCKET);
        WaterFlow.disturb(level, top);

        helper.runAfterDelay(200, () -> {
            var job = CoreLifecycle.require(level.getServer()).scheduler().profiles().stream()
                    .filter(profile -> profile.id().equals("hardwrought:water_flow"))
                    .findFirst().orElseThrow();
            helper.assertFalse(job.disabled(), "The water simulation must still be running");

            int total = total(level, base, 6);
            helper.assertTrue(total == 3 * WaterAmounts.BLOCK,
                    "Two blocks were in the tank and one was poured in: " + total + " of 3000 mB");
            helper.assertTrue(WaterStorage.amount(level, top) == 0,
                    "Nothing may be left hanging where the bucket was emptied: "
                            + WaterStorage.amount(level, top) + " mB still up there");
            helper.assertTrue(WaterStorage.amount(level, base.above(2)) > 0,
                    "and the tank stands a block higher than it did");
            column(level, base, 6, Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }

    @GameTest
    public void aSealedCellRefusesMoreThanItCanHold(GameTestHelper helper) {
        helper.assertTrue(WaterAmounts.surplus(WaterAmounts.BLOCK) == 0,
                "A full block has nothing to pass on");
        helper.assertTrue(WaterAmounts.surplus(1400) == 400, "What is over a block is the surplus");
        helper.assertTrue(WaterAmounts.pressureRoom(WaterAmounts.BLOCK) >= WaterAmounts.BUCKET,
                "A full block can still take at least one bucket of pressure");
        helper.assertTrue(WaterAmounts.pressureRoom(WaterAmounts.MAX_CELL) == 0,
                "and past that it refuses, which is what stops a sealed pipe swallowing the world");
        helper.assertTrue(WaterAmounts.room(WaterAmounts.BLOCK) == 0,
                "while ordinary flow still treats a full block as full");
        helper.assertTrue(WaterAmounts.displayLevel(WaterAmounts.MAX_CELL) == 0,
                "A pressed cell is drawn as an ordinary full block");
        helper.assertTrue(WaterAmounts.clamp(9999) == WaterAmounts.MAX_CELL, "Amounts stay bounded");
        helper.succeed();
    }

    /**
     * Communicating vessels: two shafts joined only at the floor, all the water standing in one of
     * them. The weight of the tall column has to push water up the empty one until both come to rest
     * at the same height. This is what a tank with a full column on one side and a shallow pool on
     * the other has to do, and nothing but real pressure produces it.
     */
    @GameTest(maxTicks = 400)
    public void twoConnectedShaftsComeToRestAtTheSameHeight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        buildVessels(level, base);
        // Four blocks of water, all of it on the left.
        for (int y = 0; y <= 3; y++) WaterStorage.setAmount(level, base.offset(0, y, 0), WaterAmounts.BLOCK);
        WaterFlow.disturb(level, base);

        helper.runAfterDelay(300, () -> {
            var job = CoreLifecycle.require(level.getServer()).scheduler().profiles().stream()
                    .filter(profile -> profile.id().equals("hardwrought:water_flow"))
                    .findFirst().orElseThrow();
            helper.assertFalse(job.disabled(), "The water simulation must still be running");

            int left = 0;
            int right = 0;
            for (int y = 0; y <= 4; y++) {
                left += WaterStorage.amount(level, base.offset(0, y, 0));
                right += WaterStorage.amount(level, base.offset(2, y, 0));
            }
            int joint = WaterStorage.amount(level, base.offset(1, 0, 0));
            helper.assertTrue(left + right + joint == 4 * WaterAmounts.BLOCK,
                    "None of it may be lost on the way: " + (left + right + joint) + " of 4000 mB");
            helper.assertTrue(right > 0,
                    "The empty shaft has to fill: nothing arrived in it at all");
            helper.assertTrue(Math.abs(left - right) <= WaterAmounts.BLOCK,
                    "and the two have to come to rest at about the same height: left " + left
                            + " mB against right " + right + " mB");
            clearVessels(level, base);
            helper.succeed();
        });
    }

    @GameTest
    public void onlyWaterPressedIntoTooSmallASpaceIsSqueezed(GameTestHelper helper) {
        helper.assertTrue(WaterAmounts.stableState(600) == 600,
                "Less than a block simply sits in the lower cell");
        helper.assertTrue(WaterAmounts.stableState(WaterAmounts.BLOCK) == WaterAmounts.BLOCK,
                "Exactly a block stays a block");
        helper.assertTrue(WaterAmounts.stableState(1600) == WaterAmounts.BLOCK,
                "Water standing on a full cell leaves that cell full and no more");
        // The property a lake rests on. It used to be asserted the other way round, and that is
        // what made every full cell of a lake want to change places with the one above it.
        helper.assertTrue(WaterAmounts.stableState(2 * WaterAmounts.BLOCK) == WaterAmounts.BLOCK,
                "Two full cells are at rest exactly as they are");
        int squeezed = WaterAmounts.stableState(2 * WaterAmounts.BLOCK + 400);
        helper.assertTrue(squeezed > WaterAmounts.BLOCK + 200 && squeezed < WaterAmounts.BLOCK + 400,
                "Past two blocks the lower cell carries more than half of what is over, but not "
                        + "all of it: " + squeezed);
        helper.assertTrue(WaterAmounts.stableState(2 * WaterAmounts.BLOCK + 1) - WaterAmounts.BLOCK <= 1,
                "and the first millibucket over moves a millibucket, not a jolt");
        helper.assertTrue(WaterAmounts.stableState(4 * WaterAmounts.BLOCK) > squeezed,
                "The more is pressed in, the more it carries");
        helper.assertTrue(WaterAmounts.stableState(0) == 0, "Nothing carries nothing");
        helper.assertTrue(WaterAmounts.displayLevel(990) == 0 && WaterAmounts.displayLevel(900) == 1,
                "A cell within half a step of full is drawn full");
        helper.succeed();
    }

    @GameTest
    public void theFlowJobIsRegisteredAndBounded(GameTestHelper helper) {
        var scheduler = CoreLifecycle.require(helper.getLevel().getServer()).scheduler();
        helper.assertTrue(scheduler.profiles().stream()
                        .anyMatch(profile -> profile.id().equals("hardwrought:water_flow")
                                && profile.tier() == de.ipnats.hardwrought.core.simulation.SimulationTier.CRITICAL
                                && !profile.disabled()),
                "The water simulation runs every tick and has not failed");
        helper.assertTrue(WaterFlow.BUDGET_PER_PASS > 0 && WaterFlow.MAX_ACTIVE > 0
                        && WaterFlow.MAX_NANOS_PER_TICK > 0,
                "Cell count, wall-clock time and the queue of disturbed water are capped");
        helper.assertTrue(WaterFlow.NEAR_PLAYER_RADIUS > 0
                        && WaterFlow.MID_PLAYER_RADIUS > WaterFlow.NEAR_PLAYER_RADIUS,
                "Water close to a player has a smaller, higher-priority distance band");
        helper.assertTrue(CoreLifecycle.require(helper.getLevel().getServer()).waterFlow().failedCells() == 0,
                "No water cell may have failed silently during the complete test run");
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    /**
     * A bucket dipped into shallow water takes the rest of its bucket from the water around it,
     * nearest first, and takes nothing at all when the water in reach does not add up to a bucket.
     */
    @GameTest
    public void aBucketDrawsFromTheWaterAroundWhereItIsDipped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        int length = 8;
        try {
            // A sealed trough one block deep, every cell a third full: no single block holds a bucket.
            for (int x = -1; x <= length; x++) {
                level.setBlock(base.offset(x, -1, 0), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(base.offset(x, 0, -1), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(base.offset(x, 0, 1), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(base.offset(x, 1, 0), Blocks.STONE.defaultBlockState(), 2);
            }
            level.setBlock(base.offset(-1, 0, 0), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(base.offset(length, 0, 0), Blocks.STONE.defaultBlockState(), 2);
            for (int x = 0; x < length; x++) {
                level.setBlock(base.east(x), Blocks.WATER.defaultBlockState(), 2);
                WaterStorage.setAmount(level, base.east(x), 300);
            }

            helper.assertTrue(WaterDraw.draw(level, base, WaterAmounts.BUCKET),
                    "Four thirds of a block within reach fill a bucket although no block holds one");
            helper.assertTrue(WaterStorage.amount(level, base) == 0
                            && WaterStorage.amount(level, base.east(1)) == 0
                            && WaterStorage.amount(level, base.east(2)) == 0,
                    "The block dipped into and its nearest neighbours give everything first");
            helper.assertTrue(WaterStorage.amount(level, base.east(3)) == 200,
                    "the next one only what was still missing");
            helper.assertTrue(WaterStorage.amount(level, base.east(4)) == 300,
                    "and water further off is left alone");

            // From the far end, five blocks lie within reach. Four of 200 and one of 100 make 900,
            // which is short of a bucket.
            for (int x = 4; x < length; x++) WaterStorage.setAmount(level, base.east(x), 200);
            WaterStorage.setAmount(level, base.east(3), 100);
            int before = 0;
            for (int x = 0; x < length; x++) before += WaterStorage.amount(level, base.east(x));
            helper.assertFalse(WaterDraw.draw(level, base.east(length - 1), WaterAmounts.BUCKET),
                    "When the water in reach falls short there is no bucket");
            int after = 0;
            for (int x = 0; x < length; x++) after += WaterStorage.amount(level, base.east(x));
            helper.assertTrue(before == after, "and a refused draw takes nothing: " + before + " -> " + after);
        } finally {
            for (int x = -1; x <= length; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
        helper.succeed();
    }

    /**
     * A dug pit, filled from one corner, has to come to rest with a flat surface.
     *
     * <p>This is the shape a player actually makes and the one that was reported standing as a
     * frozen wedge. It is several blocks deep, so it is the multi-layer case rather than the single
     * sheet, and it runs the real simulation rather than stepping cells by hand.
     */
    @GameTest(maxTicks = 600)
    public void aDugPitComesToRestLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        int width = 5;
        int depth = 3;
        for (int x = -1; x <= width; x++) {
            for (int z = -1; z <= width; z++) {
                for (int y = -depth - 1; y <= 2; y++) {
                    boolean wall = x < 0 || z < 0 || x == width || z == width || y == -depth - 1;
                    level.setBlockAndUpdate(base.offset(x, y, z), wall
                            ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        // Three blocks of water heaped in one corner. Deliberately little: spread over this floor
        // it comes to well under the threshold at which a film stops wetting dry ground, so the
        // water cannot reach every corner — and whatever it does reach has to come to rest level
        // with itself. That is the case that stood as a frozen wedge in play, and the reason is
        // that the threshold used to stop thin water levelling at all, not merely spreading.
        int poured = 0;
        for (int y = -depth; y <= -1; y++) {
            BlockPos corner = base.offset(0, y, 0);
            level.setBlockAndUpdate(corner, Blocks.WATER.defaultBlockState());
            WaterStorage.setAmount(level, corner, WaterAmounts.BLOCK);
            poured += WaterAmounts.BLOCK;
            WaterFlow.disturb(level, corner);
        }
        final int total = poured;

        helper.runAfterDelay(400, () -> {
            try {
                int[][] columns = new int[width][width];
                int sum = 0;
                int lowest = Integer.MAX_VALUE;
                int highest = Integer.MIN_VALUE;
                for (int x = 0; x < width; x++) {
                    for (int z = 0; z < width; z++) {
                        int column = 0;
                        for (int y = -depth; y <= 2; y++) {
                            column += WaterStorage.amount(level, base.offset(x, y, z));
                        }
                        columns[x][z] = column;
                        sum += column;
                        // Only the wet part has to be level. Water too thin to wet new ground is
                        // meant to leave corners dry; what it must never do is leave the part it
                        // did reach standing at different heights.
                        if (column > 0) {
                            lowest = Math.min(lowest, column);
                            highest = Math.max(highest, column);
                        }
                    }
                }
                StringBuilder profile = new StringBuilder();
                for (int x = 0; x < width; x++) {
                    profile.append(System.lineSeparator()).append("      ");
                    for (int z = 0; z < width; z++) {
                        profile.append(String.format(java.util.Locale.ROOT, "%5d", columns[x][z]));
                    }
                }
                helper.assertTrue(sum == total, String.format(java.util.Locale.ROOT,
                        "A pit loses nothing: %d of %d mB.%s", sum, total, profile));
                // One visible eighth is the bound, not LEVELLING_THRESHOLD: pairs stop equalising
                // once they are within ten millibuckets of each other, and across a five by five
                // floor those tolerances add up. Under an eighth is flat to the eye, which is the
                // property that matters.
                helper.assertTrue(highest - lowest < WaterAmounts.DISPLAY_STEP, String.format(
                        java.util.Locale.ROOT,
                        "and the water it reached comes to rest level: deepest wet column %d mB, "
                                + "shallowest %d mB.%s", highest, lowest, profile));
            } finally {
                for (int x = -1; x <= width; x++) {
                    for (int z = -1; z <= width; z++) {
                        for (int y = -depth - 1; y <= 2; y++) {
                            level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
            helper.succeed();
        });
    }

    private static int total(ServerLevel level, BlockPos base, int height) {
        int sum = 0;
        for (int y = 0; y < height; y++) sum += WaterStorage.amount(level, base.above(y));
        return sum;
    }

    private static int troughTotal(ServerLevel level, BlockPos base) {
        int sum = 0;
        for (int x = 0; x <= 4; x++) sum += WaterStorage.amount(level, base.offset(x, 0, 0));
        return sum;
    }

    /**
     * What each cell of a shaft holds from the floor up, and any part of its stone that is no longer
     * stone: when water goes missing from a closed shaft, this says whether it leaked or vanished.
     */
    private static String describeShaft(ServerLevel level, BlockPos base, int height) {
        StringBuilder text = new StringBuilder("cells");
        for (int y = 0; y < height; y++) text.append(' ').append(WaterStorage.amount(level, base.above(y)));
        for (int y = -1; y <= height; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    boolean inside = x == 0 && z == 0 && y >= 0 && y < height;
                    BlockPos at = base.offset(x, y, z);
                    if (!inside && !level.getBlockState(at).is(Blocks.STONE)) {
                        text.append(", ").append(level.getBlockState(at)).append(" in the wall at ").append(x).append(' ').append(y).append(' ').append(z);
                    }
                }
            }
        }
        return text.toString();
    }

    /** A closed stone shaft with an empty inside, open nowhere. */
    private static void column(ServerLevel level, BlockPos base, int height,
                               net.minecraft.world.level.block.state.BlockState wall) {
        for (int y = -1; y <= height; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    boolean inside = x == 0 && z == 0 && y >= 0 && y < height;
                    level.setBlockAndUpdate(base.offset(x, y, z), inside
                            ? Blocks.AIR.defaultBlockState() : wall);
                }
            }
        }
    }

    /** Two shafts five high, side by side, joined only by the floor between them. */
    private static void buildVessels(ServerLevel level, BlockPos base) {
        for (int x = -1; x <= 3; x++) {
            for (int y = -1; y <= 5; y++) {
                for (int z = -1; z <= 1; z++) {
                    boolean inside = z == 0 && y >= 0 && y <= 4 && x >= 0 && x <= 2;
                    level.setBlockAndUpdate(base.offset(x, y, z), inside
                            ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState());
                }
            }
        }
        // The dividing wall, open only at the very bottom.
        for (int y = 1; y <= 4; y++) {
            level.setBlockAndUpdate(base.offset(1, y, 0), Blocks.STONE.defaultBlockState());
        }
    }

    private static void clearVessels(ServerLevel level, BlockPos base) {
        for (int x = -1; x <= 3; x++) {
            for (int y = -1; y <= 5; y++) {
                for (int z = -1; z <= 1; z++) {
                    level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** A closed stone trough five long and one high. */
    private static void trough(ServerLevel level, BlockPos base,
                               net.minecraft.world.level.block.state.BlockState wall) {
        for (int x = -1; x <= 5; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    boolean inside = y == 0 && z == 0 && x >= 0 && x <= 4;
                    level.setBlockAndUpdate(base.offset(x, y, z), inside
                            ? Blocks.AIR.defaultBlockState() : wall);
                }
            }
        }
    }

    /**
     * Water moves at a rate, not all at once. It may be quicker than vanilla — this is a mod about
     * water and a player should not wait a minute to watch a bucket settle — but a poured bucket
     * that reaches the far wall the same tick reads as a bug even when the arithmetic is right.
     */
    @GameTest
    public void waterSpreadsAtARateRatherThanAllAtOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        var flow = CoreLifecycle.require(level.getServer()).waterFlow();
        try {
            // A dry channel seven long with a full block at one end.
            for (int x = -1; x <= 7; x++) {
                level.setBlockAndUpdate(base.offset(x, -1, 0), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(base.offset(x, 0, 0), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(base.offset(x, 1, 0), Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(base.offset(-1, 0, 0), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(base.offset(7, 0, 0), Blocks.STONE.defaultBlockState());
            for (int z = -1; z <= 1; z += 2) {
                for (int x = 0; x <= 6; x++) {
                    level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                }
            }
            WaterStorage.setAmount(level, base, WaterAmounts.BLOCK);

            // One pass hands a neighbour a rate, not half of everything there is.
            flow.step(level, base);
            int handed = WaterStorage.amount(level, base.east());
            helper.assertTrue(handed <= WaterAmounts.MAX_SPREAD_PER_PASS,
                    "One pass moves at most the spread rate, not half the difference: " + handed);
            helper.assertTrue(handed > 0, "But it does move");

            // And the front is not at the far wall already.
            helper.assertTrue(WaterStorage.amount(level, base.offset(6, 0, 0)) == 0,
                    "A single pass does not carry water six blocks");

            int reached = -1;
            for (int pass = 1; pass <= 200 && reached < 0; pass++) {
                for (int x = 0; x <= 6; x++) flow.step(level, base.offset(x, 0, 0));
                if (WaterStorage.amount(level, base.offset(6, 0, 0)) > 0) reached = pass;
            }
            helper.assertTrue(reached > 0, "Water still gets there in the end");
            // Vanilla covers six blocks in about thirty ticks. Faster is the point; instant is not.
            helper.assertTrue(reached >= 5,
                    "Six blocks take about a pass each: reached the wall on pass " + reached);
            helper.assertTrue(reached <= 20,
                    "And it is quicker than vanilla, not slower: reached the wall on pass " + reached);

            // Falling is allowed to be quicker than spreading, because gravity is.
            helper.assertTrue(WaterAmounts.MAX_FALL_PER_PASS > WaterAmounts.MAX_SPREAD_PER_PASS,
                    "Water falls faster than it creeps");
        } finally {
            for (int x = -1; x <= 7; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        WaterStorage.setAmount(level, base.offset(x, y, z), 0);
                        level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        helper.succeed();
    }

    /**
     * A transfer wakes eleven neighbours and nearly all of them are already queued. Waking one has
     * to be cheap, because it happens tens of thousands of times a tick: the queue is asked first,
     * and only a position it has never heard of costs a chunk lookup and a block state.
     */
    @GameTest
    public void wakingACellThatIsAlreadyQueuedCostsNoWorldLookup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        var flow = CoreLifecycle.require(level.getServer()).waterFlow();
        try {
            for (int x = -1; x <= 5; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        level.setBlockAndUpdate(base.offset(x, y, z), y == -1 || z != 0
                                ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    }
                }
            }
            // A full cell at one end of a dry channel: the front creeps along it one transfer at a
            // time, and every transfer wakes eleven cells, most of them already waiting.
            WaterStorage.setAmount(level, base, WaterAmounts.BLOCK);

            long signalsBefore = flow.signals();
            long lookupsBefore = flow.worldLookups();
            for (int pass = 0; pass < 20; pass++) {
                for (int x = 0; x <= 4; x++) flow.step(level, base.offset(x, 0, 0));
            }
            long signals = flow.signals() - signalsBefore;
            long lookups = flow.worldLookups() - lookupsBefore;

            helper.assertTrue(signals > 0, "The passes woke cells at all");
            helper.assertTrue(lookups < signals,
                    "Most wake-ups are repeats and must not touch the world: " + lookups
                            + " lookups for " + signals + " wake-ups");
            // Measured at four in ten saved in a walled channel, where every side is stone and a
            // wall has to be looked at every time because it could have become water. In open water
            // the saving is larger. A quarter is the floor this may not fall below.
            helper.assertTrue(lookups * 4 <= signals * 3,
                    "At least a quarter of the wake-ups avoid the world: " + lookups + " of " + signals);
        } finally {
            for (int x = -1; x <= 5; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        WaterStorage.setAmount(level, base.offset(x, y, z), 0);
                        level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        helper.succeed();
    }

    /**
     * Water standing beside a field leaves the field alone: a puddle the rain left, a channel that
     * runs over, must not clear the wheat it reaches. It washes wild growth away and goes round what
     * was planted.
     */
    @GameTest(maxTicks = 200)
    public void waterGoesRoundWhatWasPlanted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(4, 6, 4));
        for (int x = -3; x <= 3; x++) {
            for (int y = -2; y <= 1; y++) {
                for (int z = -3; z <= 3; z++) {
                    boolean basin = y == -2 || (Math.abs(x) == 3 || Math.abs(z) == 3) && y <= 0;
                    level.setBlockAndUpdate(base.offset(x, y, z), basin
                            ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        // A field of wheat with one bare place in the middle, a sapling at one corner, and one tuft of wild grass.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean middle = x == 0 && z == 0;
                level.setBlockAndUpdate(base.offset(x, -1, z), middle ? Blocks.STONE.defaultBlockState() : Blocks.FARMLAND.defaultBlockState());
                if (!middle) level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.WHEAT.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(base.offset(2, -1, 2), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(base.offset(2, 0, 2), Blocks.OAK_SAPLING.defaultBlockState());
        level.setBlockAndUpdate(base.offset(1, -1, 0), Blocks.DIRT.defaultBlockState());
        level.setBlockAndUpdate(base.offset(1, 0, 0), Blocks.SHORT_GRASS.defaultBlockState());
        level.setBlockAndUpdate(base, Blocks.WATER.defaultBlockState());

        helper.runAfterDelay(120, () -> {
            int wheat = 0;
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) if (level.getBlockState(base.offset(x, 0, z)).is(Blocks.WHEAT)) wheat++;
            }
            helper.assertTrue(wheat == 22, "Every stalk of the field still stands beside the water: " + wheat + " of 22");
            helper.assertTrue(level.getBlockState(base.offset(2, 0, 2)).is(Blocks.OAK_SAPLING), "and so does the sapling");
            helper.assertFalse(level.getBlockState(base.offset(1, 0, 0)).is(Blocks.SHORT_GRASS),
                    "while wild grass in its way is washed out as before");
            helper.assertTrue(WaterStorage.amount(level, base.offset(1, 0, 0)) > 0, "and the water goes where the grass was");
            for (int x = -3; x <= 3; x++) {
                for (int y = -2; y <= 1; y++) {
                    for (int z = -3; z <= 3; z++) {
                        WaterStorage.setAmount(level, base.offset(x, y, z), 0);
                        level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
            helper.succeed();
        });
    }

    /**
     * A lake at rest stays at rest, and a bucket taken out of it is a dent in its surface and nothing
     * else.
     *
     * <p>This is the property an ocean stands or falls on. Water that rearranges itself merely because
     * it was looked at turns one bucket into work for every cell of the body: each of them gets a
     * stored amount, each of them is queued, and the surface sinks while the depths swell. Here every
     * cell is stepped by hand, far more often than play would, and the deep water has to come out of
     * it exactly as it went in.
     */
    @GameTest
    public void aRestingLakeStaysAtRest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        var flow = CoreLifecycle.require(level.getServer()).waterFlow();
        int radius = 3;
        int depth = 3;
        try {
            for (int x = -radius - 1; x <= radius + 1; x++) {
                for (int z = -radius - 1; z <= radius + 1; z++) {
                    for (int y = -1; y <= depth + 1; y++) {
                        boolean wall = Math.abs(x) > radius || Math.abs(z) > radius || y < 0;
                        boolean water = !wall && y < depth;
                        // Flag 2: the way world generation leaves a lake, with nothing scheduled.
                        level.setBlock(base.offset(x, y, z), wall ? Blocks.STONE.defaultBlockState()
                                : water ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            int cells = (2 * radius + 1) * (2 * radius + 1) * depth;
            int drawn = WaterDraw.drawUpTo(level, base.above(depth - 1), WaterAmounts.BUCKET);
            helper.assertTrue(drawn == WaterAmounts.BUCKET, "A bucket comes out of a full lake");

            for (int pass = 0; pass < 60; pass++) stepLake(flow, level, base, radius, depth);

            int total = 0;
            int lowest = Integer.MAX_VALUE;
            int highest = Integer.MIN_VALUE;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    for (int y = 0; y < depth; y++) {
                        int amount = WaterStorage.amount(level, base.offset(x, y, z));
                        total += amount;
                        if (y < depth - 1) {
                            helper.assertTrue(amount == WaterAmounts.BLOCK,
                                    "Water under the surface is left exactly as it was: " + amount
                                            + " mB at depth " + (depth - 1 - y));
                        } else {
                            lowest = Math.min(lowest, amount);
                            highest = Math.max(highest, amount);
                        }
                    }
                }
            }
            helper.assertTrue(total == cells * WaterAmounts.BLOCK - WaterAmounts.BUCKET,
                    "Only the bucket is missing: " + total + " mB");
            helper.assertTrue(highest - lowest < WaterAmounts.DISPLAY_STEP,
                    "The dent has levelled out across the surface: " + lowest + " to " + highest + " mB");

            long before = flow.signals();
            stepLake(flow, level, base, radius, depth);
            helper.assertTrue(flow.signals() == before,
                    "A settled lake moves nothing and wakes nothing when it is looked at again: "
                            + (flow.signals() - before) + " wake-ups from one more pass");
        } finally {
            for (int x = -radius - 1; x <= radius + 1; x++) {
                for (int z = -radius - 1; z <= radius + 1; z++) {
                    for (int y = -1; y <= depth + 1; y++) {
                        WaterStorage.setAmount(level, base.offset(x, y, z), 0);
                        level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
        helper.succeed();
    }

    private static void stepLake(WaterFlow flow, ServerLevel level, BlockPos base, int radius, int depth) {
        for (int y = 0; y <= depth; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) flow.step(level, base.offset(x, y, z));
            }
        }
    }

    /**
     * Communicating vessels with enough water to tell: five blocks in one shaft leave two standing
     * in each once the floor is covered. Four blocks cannot show this, because one block of
     * difference is then what a model without any pressure at all would also leave behind.
     */
    @GameTest(maxTicks = 500)
    public void aTallColumnLiftsWaterUpTheOtherShaft(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        buildVessels(level, base);
        for (int y = 0; y <= 4; y++) WaterStorage.setAmount(level, base.offset(0, y, 0), WaterAmounts.BLOCK);
        WaterFlow.disturb(level, base);

        helper.runAfterDelay(400, () -> {
            try {
                assertShaftsLevel(helper, level, base, 5 * WaterAmounts.BLOCK);
            } finally {
                clearVessels(level, base);
            }
            helper.succeed();
        });
    }

    /**
     * Two shafts standing level and at rest, and a bucket emptied into one of them. Nothing on the
     * far side is awake, so the water that was added has to find its own way under the wall.
     */
    @GameTest(maxTicks = 400)
    public void waterAddedToOneShaftRaisesBoth(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        buildVessels(level, base);
        for (int y = 0; y <= 1; y++) {
            WaterStorage.setAmount(level, base.offset(0, y, 0), WaterAmounts.BLOCK);
            WaterStorage.setAmount(level, base.offset(2, y, 0), WaterAmounts.BLOCK);
        }
        WaterStorage.setAmount(level, base.offset(1, 0, 0), WaterAmounts.BLOCK);

        helper.runAfterDelay(60, () -> {
            BlockPos top = base.offset(0, 2, 0);
            WaterStorage.setAmount(level, top, WaterAmounts.BUCKET);
            WaterFlow.disturb(level, top);
        });
        helper.runAfterDelay(300, () -> {
            try {
                assertShaftsLevel(helper, level, base, 6 * WaterAmounts.BLOCK);
            } finally {
                clearVessels(level, base);
            }
            helper.succeed();
        });
    }

    /**
     * A full column held back by a plug in the other shaft. Pulling the plug wakes only the water
     * under it, which has to fetch what the standing column owes it.
     */
    @GameTest(maxTicks = 500)
    public void openingAShaftLetsTheStandingColumnFillIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        buildVessels(level, base);
        BlockPos plug = base.offset(2, 1, 0);
        level.setBlockAndUpdate(plug, Blocks.STONE.defaultBlockState());
        for (int y = 0; y <= 3; y++) WaterStorage.setAmount(level, base.offset(0, y, 0), WaterAmounts.BLOCK);
        WaterStorage.setAmount(level, base.offset(1, 0, 0), WaterAmounts.BLOCK);
        WaterStorage.setAmount(level, base.offset(2, 0, 0), WaterAmounts.BLOCK);

        helper.runAfterDelay(60, () -> level.setBlockAndUpdate(plug, Blocks.AIR.defaultBlockState()));
        helper.runAfterDelay(400, () -> {
            try {
                assertShaftsLevel(helper, level, base, 6 * WaterAmounts.BLOCK);
            } finally {
                clearVessels(level, base);
            }
            helper.succeed();
        });
    }

    private static void assertShaftsLevel(GameTestHelper helper, ServerLevel level, BlockPos base, int expected) {
        int left = 0;
        int right = 0;
        for (int y = 0; y <= 4; y++) {
            left += WaterStorage.amount(level, base.offset(0, y, 0));
            right += WaterStorage.amount(level, base.offset(2, y, 0));
        }
        int joint = WaterStorage.amount(level, base.offset(1, 0, 0));
        helper.assertTrue(left + right + joint == expected,
                "None of it may be lost on the way: " + (left + right + joint) + " of " + expected + " mB");
        helper.assertTrue(Math.abs(left - right) <= 2 * WaterAmounts.DISPLAY_STEP,
                "Both shafts stand at the same height: left " + left + " mB against right " + right + " mB");
    }


    /**
     * A sea drains through a hole in its bed, all of it, through the real queue.
     *
     * <p>The one thing an ocean asks of the simulation that a pond does not: the disturbed front
     * grows far larger than any one tick can serve, and it has to be <em>remembered</em>, not
     * dropped. A queue that forgets cells leaves the far side of a sea standing still for ever,
     * which is what "the water does not go very far" looked like in play. The passes here are run
     * by hand, within a tick, with the limits the scheduler would use, so a long drain fits in a
     * gametest without changing how the queue is served.
     */
    @GameTest(maxTicks = 100)
    public void aSeaDrainsThroughAHoleInItsBed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET + 20);
        var flow = CoreLifecycle.require(level.getServer()).waterFlow();
        int half = 10;
        int depth = 3;
        int cavity = 5;
        // Sea above the bed, an empty cavern under it, stone everywhere else.
        for (int x = -half - 1; x <= half + 1; x++) {
            for (int z = -half - 1; z <= half + 1; z++) {
                for (int y = -cavity - 2; y <= depth + 1; y++) {
                    boolean wall = Math.abs(x) > half || Math.abs(z) > half || y == -1 || y <= -cavity - 2;
                    boolean water = !wall && y >= 0 && y < depth;
                    level.setBlock(base.offset(x, y, z), wall ? Blocks.STONE.defaultBlockState()
                            : water ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        int cells = (2 * half + 1) * (2 * half + 1) * depth;
        long expected = (long) cells * WaterAmounts.BLOCK;
        // The counts of what has soaked in or dried up run from server start; only what goes now is ours.
        long goneBefore = flow.seeped() + flow.dried();
        try {
            // A two by two hole knocked into the bed.
            for (int x = 0; x <= 1; x++) {
                for (int z = 0; z <= 1; z++) {
                    level.setBlock(base.offset(x, -1, z), Blocks.AIR.defaultBlockState(), 2);
                    WaterFlow.disturb(level, base.offset(x, 0, z));
                }
            }
            long started = System.nanoTime();
            long stepped = 0;
            int passes = 0;
            long inSea = expected;
            int peak = 0;
            int quietPasses = 0;
            for (; passes < 40_000 && inSea > 0; passes++) {
                stepped += flow.runPass(Long.MAX_VALUE, WaterFlow.BUDGET_PER_PASS);
                peak = Math.max(peak, flow.activeCells());
                if (passes % 50 == 0) inSea = seaTotal(level, base, half, 0, depth);
                // An empty queue is not the end: cells that found nothing to do are woken again
                // after a while, and the last films on the bed go on the second try.
                if (flow.activeCells() > 0) quietPasses = 0;
                if (flow.activeCells() == 0 && ++quietPasses > 450) {
                    inSea = seaTotal(level, base, half, 0, depth);
                    break;
                }
            }
            long elapsed = System.nanoTime() - started;
            // The loop stops the moment the sea is found empty, and the last of it may then still be
            // falling through the hole. The hole is part of the way down, so it is counted with the
            // cavern: leaving it out was water "lost" whenever the count came at that moment.
            long inCavern = seaTotal(level, base, half, -cavity - 1, 0);
            long gone = flow.seeped() + flow.dried() - goneBefore;
            String report = String.format(java.util.Locale.ROOT,
                    "%d ticks, %d cell steps, %.1f ms, peak queue %d, %d mB left in the sea, %d mB in the cavern and the hole, dropped %d",
                    passes, stepped, elapsed / 1_000_000.0, peak, inSea, inCavern, flow.droppedCells());
            System.out.println("[drain] " + report);
            helper.assertTrue(inSea + inCavern + gone == expected, "The sea is moved, not lost: " + report + " seeped or dried " + gone);
            helper.assertTrue(flow.droppedCells() == 0, "Nothing may be forgotten on the way: " + report);
            helper.assertTrue(inSea == 0, "and all of it ends up in the cavern: " + report);
        } finally {
            for (int x = -half - 1; x <= half + 1; x++) {
                for (int z = -half - 1; z <= half + 1; z++) {
                    for (int y = -cavity - 2; y <= depth + 1; y++) {
                        WaterStorage.setAmount(level, base.offset(x, y, z), 0);
                        level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
        helper.succeed();
    }

    private static long seaTotal(ServerLevel level, BlockPos base, int half, int fromY, int toY) {
        long total = 0;
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                for (int y = fromY; y < toY; y++) total += WaterStorage.amount(level, base.offset(x, y, z));
            }
        }
        return total;
    }

    /**
     * Water that reaches the bottom of the world with nothing under it is gone, and goes at a rate.
     *
     * <p>The shaft is cut through the very bottom layer, so the lowest cell stands over the void.
     * What stands in it has to fall out, the column above has to follow, and none of it may come
     * back or pile up on the floor of the world.
     */
    @GameTest
    public void waterThatReachesTheVoidIsGone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos bottom = new BlockPos(origin.getX(), level.getMinY(), origin.getZ());
        var flow = CoreLifecycle.require(level.getServer()).waterFlow();
        int height = 3;
        java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> before = new java.util.HashMap<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y <= height; y++) {
                    BlockPos at = bottom.offset(x, y, z);
                    before.put(at, level.getBlockState(at));
                    boolean inside = x == 0 && z == 0 && y < height;
                    level.setBlock(at, inside ? Blocks.WATER.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
        try {
            long voidedBefore = flow.voided();
            flow.step(level, bottom);
            helper.assertTrue(flow.voided() - voidedBefore == WaterAmounts.MAX_FALL_PER_PASS,
                    "One pass lets what falls in a pass out of the world, not the whole cell: "
                            + (flow.voided() - voidedBefore) + " mB gone");
            for (int pass = 0; pass < 40; pass++) {
                for (int y = 0; y < height; y++) flow.step(level, bottom.above(y));
            }
            int left = 0;
            for (int y = 0; y < height; y++) left += WaterStorage.amount(level, bottom.above(y));
            helper.assertTrue(left == 0, "The whole column has run out through the bottom: " + left + " mB left");
            helper.assertTrue(flow.voided() - voidedBefore == (long) height * WaterAmounts.BLOCK,
                    "and exactly what stood there is counted as lost: " + (flow.voided() - voidedBefore) + " mB");
        } finally {
            before.forEach((at, state) -> {
                WaterStorage.setAmount(level, at, 0);
                level.setBlock(at, state, 2);
            });
        }
        helper.succeed();
    }
}
