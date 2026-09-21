package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.water.Rainfall;
import de.ipnats.hardwrought.water.WaterAmounts;
import de.ipnats.hardwrought.water.WaterQuality;
import de.ipnats.hardwrought.water.WaterQualityStorage;
import de.ipnats.hardwrought.water.WaterStorage;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Milestone 4, the sky half: rain is water arriving, not just weather happening. The tests place the
 * rain themselves rather than waiting for the sky, so they never touch the shared world weather that
 * the other batches are running in.
 */
public final class RainfallGameTests {
    /** Built clear of the test structure so a puddle never meets the test blocks themselves. */
    private static final int WORKSPACE_OFFSET = 40;

    @GameTest
    public void aStormPutsDownMoreThanADrizzle(GameTestHelper helper) {
        helper.assertTrue(Rainfall.drop(true) > Rainfall.drop(false)
                        && Rainfall.columns(true) > Rainfall.columns(false),
                "A thunderstorm puts down several times what ordinary rain does");
        helper.assertTrue(Rainfall.drop(false) >= WaterAmounts.DISPLAY_STEP,
                "and even a drizzle arrives in steps vanilla can actually draw");
        helper.succeed();
    }

    @GameTest
    public void rainCollectsOnTheGroundAndIsCleanWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        BlockPos above = ground.above();
        try {
            level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
            helper.assertTrue(Rainfall.target(level, ground.getX(), ground.getZ()).equals(above),
                    "Rain lands on top of the highest thing in the column");

            helper.assertTrue(Rainfall.rainOn(level, ground.getX(), ground.getZ(), Rainfall.DROP),
                    "and it really arrives as water");
            helper.assertTrue(WaterStorage.amount(level, above) == Rainfall.DROP,
                    "exactly as much as fell, not a full block");
            helper.assertTrue(WaterQualityStorage.stored(level, above) == WaterQuality.FRESH
                            || CoreLifecycle.require(level.getServer()).water().qualityAt(level, above)
                            == WaterQuality.FRESH,
                    "Rainwater has touched nothing yet, so it is clean");

            // A second shower deepens the same puddle rather than starting a new one.
            Rainfall.rainOn(level, ground.getX(), ground.getZ(), Rainfall.DROP);
            helper.assertTrue(WaterStorage.amount(level, above) == 2 * Rainfall.DROP,
                    "A longer rain deepens the puddle it already made");

            // Rain never presses a cell: water arrives from the sky, not from a pump. What falls on
            // something already full stands on top of it, which is how a channel rises and spills.
            WaterStorage.setAmount(level, above, WaterAmounts.BLOCK);
            helper.assertTrue(Rainfall.target(level, ground.getX(), ground.getZ()).equals(above.above()),
                    "Rain on water that is already full lands on top of it, not inside it");
            helper.assertTrue(Rainfall.rainOn(level, ground.getX(), ground.getZ(), Rainfall.DROP),
                    "and it still arrives");
            helper.assertTrue(WaterStorage.amount(level, above) == WaterAmounts.BLOCK,
                    "The cell underneath is never pressed past a full block by the weather");
            helper.assertTrue(WaterStorage.amount(level, above.above()) == Rainfall.DROP,
                    "and what fell is standing on top of it, where it can run off");
        } finally {
            WaterStorage.setAmount(level, above.above(), 0);
            WaterStorage.setAmount(level, above, 0);
            level.setBlockAndUpdate(above.above(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(ground, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest
    public void rainRefillsWhatEvaporatedAndStaysOutOfCoveredGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET).east(4);
        BlockPos channel = floor.above();
        BlockPos roof = channel.above(3);
        try {
            level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
            // A farm channel the sun has taken half of: rain tops up the water that is there.
            WaterStorage.setAmount(level, channel, WaterAmounts.BLOCK / 2);
            helper.assertTrue(Rainfall.target(level, floor.getX(), floor.getZ()).equals(channel),
                    "Rain falling on water that is not full deepens it rather than stacking on it");
            Rainfall.rainOn(level, floor.getX(), floor.getZ(), Rainfall.DROP);
            helper.assertTrue(WaterStorage.amount(level, channel) == WaterAmounts.BLOCK / 2 + Rainfall.DROP,
                    "so an evaporated channel fills back up over a rain");

            // A roof is a roof, whatever is under it.
            level.setBlockAndUpdate(roof, Blocks.STONE.defaultBlockState());
            BlockPos covered = Rainfall.target(level, floor.getX(), floor.getZ());
            helper.assertTrue(covered != null && covered.getY() > roof.getY(),
                    "Rain runs off a roof instead of through it");
        } finally {
            WaterStorage.setAmount(level, channel, 0);
            for (BlockPos pos : new BlockPos[]{floor, channel, roof, roof.above()}) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
        helper.succeed();
    }

    @GameTest
    public void rainDoesNotWashAwayWhatItFallsOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos soil = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET).east(8);
        BlockPos crop = soil.above();
        try {
            level.setBlockAndUpdate(soil, Blocks.FARMLAND.defaultBlockState());
            level.setBlockAndUpdate(crop, Blocks.WHEAT.defaultBlockState());
            helper.assertTrue(Rainfall.target(level, soil.getX(), soil.getZ()) == null,
                    "Rain has nowhere to land in a block the growth already occupies");
            helper.assertFalse(Rainfall.rainOn(level, soil.getX(), soil.getZ(), Rainfall.THUNDER_DROP),
                    "so even a downpour puts nothing there");
            helper.assertTrue(level.getBlockState(crop).is(Blocks.WHEAT),
                    "and the field is still standing: flowing water washes crops away, weather does not");
        } finally {
            level.setBlockAndUpdate(crop, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(soil, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest
    public void rainfallRunsAsARegisteredJob(GameTestHelper helper) {
        var runtime = CoreLifecycle.require(helper.getLevel().getServer());
        helper.assertTrue(runtime.rainfall() != null, "Rain belongs to the server runtime");
        var job = runtime.scheduler().profiles().stream()
                .filter(profile -> profile.id().equals("hardwrought:rainfall")).findFirst();
        helper.assertTrue(job.isPresent() && !job.get().disabled(),
                "and it runs as a registered simulation job that has not failed");
        helper.succeed();
    }
}
