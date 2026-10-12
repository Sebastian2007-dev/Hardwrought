package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.registry.ModBlocks;
import de.ipnats.hardwrought.environment.Season;
import de.ipnats.hardwrought.environment.SeasonCycle;
import de.ipnats.hardwrought.environment.SeasonFogRules;
import de.ipnats.hardwrought.environment.SeasonState;
import de.ipnats.hardwrought.environment.SeasonalGrowth;
import de.ipnats.hardwrought.water.Freezing;
import de.ipnats.hardwrought.water.FrozenWater;
import de.ipnats.hardwrought.water.ThinIceBlock;
import de.ipnats.hardwrought.water.WaterAmounts;
import de.ipnats.hardwrought.water.WaterStorage;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.Locale;

/**
 * The seasons where they touch the world rather than the arithmetic: ice that conserves its water,
 * the calendar, the growth tag and what a fog does besides being looked at.
 */
public class SeasonGameTests {
    /** Built clear of the test structure, as the water tests do, so a fill meets nothing. */
    private static final int WORKSPACE_OFFSET = 30;

    /**
     * The property the whole ice design exists for. Hardwrought water is counted in millibuckets
     * and vanilla ice is a whole block; freezing with vanilla ice would invent water out of nothing
     * and thawing would round it away. A cell that freezes and thaws has to be the same cell.
     */
    @GameTest
    public void freezingAndThawingConservesTheWaterExactly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // A quarter of a block: the awkward amount, and the one vanilla ice could not have held.
        int poured = WaterAmounts.BLOCK / 4;
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1)).offset(WORKSPACE_OFFSET, 0, WORKSPACE_OFFSET);
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        WaterStorage.setAmount(level, pos, poured);
        int before = WaterStorage.amount(level, pos);
        helper.assertTrue(before == poured, String.format(Locale.ROOT,
                "The pour has to land before anything is frozen: %d of %d", before, poured));

        // Freeze it directly rather than waiting for a cold enough day to come round.
        int held = WaterStorage.amount(level, pos);
        WaterStorage.setAmount(level, pos, 0);
        level.setBlockAndUpdate(pos, ModBlocks.THIN_ICE.defaultBlockState()
                .setValue(ThinIceBlock.LAYERS, ThinIceBlock.layersFor(held)));
        FrozenWater.hold(level, pos, held);

        helper.assertTrue(level.getBlockState(pos).is(ModBlocks.THIN_ICE), "The cell is ice now");
        helper.assertTrue(WaterStorage.amount(level, pos) == 0,
                "And the solver can no longer see any water to move: frozen water must not flow");
        helper.assertTrue(FrozenWater.held(level, pos) == poured,
                "The exact amount waits in the frozen table rather than in the block state");

        // The shape of the ice is the shape the water stood at, which is the visible half of this.
        int layers = level.getBlockState(pos).getValue(ThinIceBlock.LAYERS);
        helper.assertTrue(layers == ThinIceBlock.layersFor(poured) && layers < ThinIceBlock.MAX_LAYERS,
                "A quarter of a block of water freezes into a quarter of a block of ice, not a whole one: "
                        + layers + " layers");

        Freezing.thaw(level, pos);
        helper.assertTrue(!level.getBlockState(pos).is(ModBlocks.THIN_ICE), "The thaw takes the ice away");
        int after = WaterStorage.amount(level, pos);
        helper.assertTrue(after == poured, String.format(Locale.ROOT,
                "And gives back exactly what went in: %d of %d millibuckets", after, poured));
        helper.assertTrue(FrozenWater.held(level, pos) == 0,
                "With nothing left behind in the frozen table to thaw a second time");

        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    /** Ice is the shape of its water all the way up the scale, not only at the ends. */
    @GameTest
    public void iceTakesTheShapeOfTheWaterItFroze(GameTestHelper helper) {
        helper.assertTrue(ThinIceBlock.layersFor(WaterAmounts.BLOCK) == ThinIceBlock.MAX_LAYERS,
                "A full block of water is a full block of ice");
        helper.assertTrue(ThinIceBlock.layersFor(WaterAmounts.BLOCK / 2) == 4,
                "Half a block is half a block");
        helper.assertTrue(ThinIceBlock.layersFor(WaterAmounts.DISPLAY_STEP) == 1,
                "One eighth of water is one layer of ice, which is the whole point of the block");
        helper.assertTrue(ThinIceBlock.layersFor(1) == 1,
                "And the merest film of water still freezes into something rather than vanishing");
        helper.assertTrue(ThinIceBlock.layersFor(WaterAmounts.MAX_CELL) == ThinIceBlock.MAX_LAYERS,
                "A cell under pressure holds more than a block but cannot freeze taller than one");
        helper.succeed();
    }

    /** The calendar: four seasons of twelve days, in order, derived from the clock and nothing else. */
    @GameTest
    public void theCalendarTurns(GameTestHelper helper) {
        helper.assertTrue(SeasonCycle.at(0) == Season.SPRING, "A world starts in spring");
        helper.assertTrue(SeasonCycle.at(SeasonCycle.TICKS_PER_SEASON) == Season.SUMMER,
                "Spring is followed by summer");
        helper.assertTrue(SeasonCycle.at(SeasonCycle.TICKS_PER_SEASON * 3) == Season.WINTER,
                "And the fourth quarter is winter");
        helper.assertTrue(SeasonCycle.at(SeasonCycle.TICKS_PER_YEAR) == Season.SPRING,
                "A year later it is spring again");
        helper.assertTrue(SeasonCycle.dayInSeason(0) == 1
                        && SeasonCycle.dayInSeason(SeasonCycle.TICKS_PER_SEASON - 1)
                        == SeasonCycle.DAYS_PER_SEASON,
                "The day number runs one to twelve and no further");
        helper.succeed();
    }

    /**
     * {@code /hardwrought debug season} moves the clock rather than storing an override, so the
     * arithmetic that works out how far to move it is the whole of that command. It has to land on
     * the right season, keep the hour of the day, and never run the clock backwards.
     */
    @GameTest
    public void settingTheSeasonOnlyEverMovesTheClockForward(GameTestHelper helper) {
        long noonInSpring = 6_000;
        for (Season target : Season.values()) {
            for (int day : new int[]{1, 7, SeasonCycle.DAYS_PER_SEASON}) {
                long advance = SeasonCycle.ticksUntil(noonInSpring, target, day);
                helper.assertTrue(advance >= 0, "The calendar is never wound back: " + advance);
                helper.assertTrue(advance % SeasonCycle.TICKS_PER_DAY == 0,
                        "It moves by whole days, so the hour of the day survives it: " + advance);
                long landed = noonInSpring + advance;
                helper.assertTrue(SeasonCycle.at(landed) == target,
                        "It lands in " + target.serializedName() + ", not near it");
                helper.assertTrue(SeasonCycle.dayInSeason(landed) == day,
                        "And on the day that was asked for");
                helper.assertTrue(Math.floorMod(landed, SeasonCycle.TICKS_PER_DAY) == noonInSpring,
                        "A world set to a season at noon is still at noon afterwards");
                helper.assertTrue(advance < SeasonCycle.TICKS_PER_YEAR,
                        "Never more than a year of game time for one command");
            }
        }
        helper.assertTrue(SeasonCycle.ticksUntil(noonInSpring, Season.SPRING, 1) == 0,
                "Asking for the season it already is costs nothing");

        // The day and year readouts the player-facing commands are built on.
        helper.assertTrue(SeasonCycle.dayOfWorld(0) == 1 && SeasonCycle.year(0) == 1,
                "The first morning is day one of year one, not day zero of year zero");
        helper.assertTrue(SeasonCycle.year(SeasonCycle.TICKS_PER_YEAR) == 2, "A year later it is year two");
        helper.assertTrue(SeasonCycle.daysLeftInSeason(0) == SeasonCycle.DAYS_PER_SEASON,
                "On the first day of a season all of it is still to come");
        helper.succeed();
    }

    /**
     * The growth tag has to cover the things a player plants and nothing that merely happens to
     * take random ticks. A gas block told to grow 45 % faster would be a serious bug.
     */
    @GameTest
    public void onlyPlantsFollowTheCalendar(GameTestHelper helper) {
        helper.assertTrue(SeasonalGrowth.seasonal(Blocks.WHEAT.defaultBlockState()),
                "Wheat follows the year");
        helper.assertTrue(SeasonalGrowth.seasonal(Blocks.OAK_SAPLING.defaultBlockState()),
                "So does a sapling");
        helper.assertTrue(SeasonalGrowth.seasonal(Blocks.SUGAR_CANE.defaultBlockState()),
                "And cane, which is not in either vanilla tag");
        helper.assertTrue(!SeasonalGrowth.seasonal(Blocks.STONE.defaultBlockState()),
                "Stone does not grow");
        helper.assertTrue(!SeasonalGrowth.seasonal(ModBlocks.THIN_ICE.defaultBlockState()),
                "And neither does anything of ours that merely takes random ticks");
        helper.succeed();
    }

    /** A fog is weather at the edges and an event in the middle, and both halves arrive together. */
    @GameTest
    public void fogIsAnEventRatherThanWeather(GameTestHelper helper) {
        helper.assertTrue(SeasonState.fogAt(0, 1234L) == 0,
                "There is no fog in spring whatever the roll says");

        long autumn = SeasonCycle.TICKS_PER_SEASON * 2;
        boolean everFoggy = false;
        for (int day = 0; day < SeasonCycle.DAYS_PER_SEASON; day++) {
            if (SeasonState.fogAt(autumn + day * SeasonCycle.TICKS_PER_DAY + 1200, 1234L) > 0) {
                everFoggy = true;
                break;
            }
        }
        helper.assertTrue(everFoggy, "Some autumn morning in twelve has fog on it");

        // Whatever morning it is, it is a morning: nothing is still foggy by the small hours.
        for (int day = 0; day < SeasonCycle.DAYS_PER_SEASON; day++) {
            long midnight = autumn + day * SeasonCycle.TICKS_PER_DAY + 18_000;
            helper.assertTrue(SeasonState.fogAt(midnight, 1234L) == 0,
                    "A fog lasts a morning at most, never into the night");
        }

        helper.assertTrue(SeasonFogRules.SMOTHERING > 0 && SeasonFogRules.SMOTHERING < 1,
                "There is a thickness at which the fog starts keeping the sun off, and it is not either end");
        helper.succeed();
    }
}
