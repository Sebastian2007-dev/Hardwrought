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
            int wetted = 0;
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (WaterStorage.amount(level, base.offset(x, 0, z)) > 0) wetted++;
                }
            }
            helper.assertTrue(wetted >= 4,
                    "and it has to have spread out over the ground it was standing on, not stayed "
                            + "in one place: " + wetted + " cells hold water");
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
                    "The bucket is still in the shaft: " + total + " of 4000 mB");
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
    public void pressureRisesWithWhatStandsOnTop(GameTestHelper helper) {
        helper.assertTrue(WaterAmounts.stableState(600) == 600,
                "Less than a block simply sits in the lower cell");
        helper.assertTrue(WaterAmounts.stableState(WaterAmounts.BLOCK) == WaterAmounts.BLOCK,
                "Exactly a block stays a block");
        int twoDeep = WaterAmounts.stableState(2 * WaterAmounts.BLOCK);
        helper.assertTrue(twoDeep > WaterAmounts.BLOCK,
                "With a block standing on it the lower cell carries more than a block: " + twoDeep);
        helper.assertTrue(twoDeep < 2 * WaterAmounts.BLOCK, "but not all of it");
        helper.assertTrue(WaterAmounts.stableState(4 * WaterAmounts.BLOCK) > twoDeep,
                "and the deeper it is, the more it carries");
        helper.assertTrue(WaterAmounts.stableState(0) == 0, "Nothing carries nothing");
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
            helper.assertTrue(reached >= 6,
                    "Six blocks take at least a pass each: reached the wall on pass " + reached);
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
            for (int x = 0; x <= 4; x++) WaterStorage.setAmount(level, base.offset(x, 0, 0), WaterAmounts.BLOCK);

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
}
