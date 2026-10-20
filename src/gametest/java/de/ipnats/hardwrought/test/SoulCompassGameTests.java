package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.underworld.Underworld;
import de.ipnats.hardwrought.witchking.Citadel;
import de.ipnats.hardwrought.witchking.FallenKings;
import de.ipnats.hardwrought.witchking.SoulCompass;
import de.ipnats.hardwrought.witchking.WitchKingContent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/** The soul compass: it points to the nearest Witch King still living, and passes over the beaten. */
public final class SoulCompassGameTests {
    @GameTest
    public void theSearchPassesOverBeatenKings(GameTestHelper helper) {
        // A hall in every grid square, at its middle chunk; the nearest two are beaten.
        java.util.function.BiFunction<Integer, Integer, BlockPos> everywhere = (gx, gz) ->
                new BlockPos((gx * 16 + 8) * 16 + 8, 20, (gz * 16 + 8) * 16 + 8);
        BlockPos from = new BlockPos(140, 64, 140);
        BlockPos nearest = Citadel.nearest(from, 4, everywhere, home -> true);
        helper.assertTrue(nearest.equals(new BlockPos(136, 20, 136)), "With every king alive the nearest hall: " + nearest);
        Set<BlockPos> beaten = Set.of(nearest, new BlockPos(136 - 256, 20, 136));
        BlockPos next = Citadel.nearest(from, 4, everywhere, home -> !beaten.contains(home));
        helper.assertTrue(next != null && !beaten.contains(next) && next.distSqr(from) > nearest.distSqr(from),
                "With the nearest beaten, the next living one: " + next);
        helper.assertTrue(Citadel.nearest(from, 4, everywhere, home -> false) == null, "With every king beaten, none");
        helper.succeed();
    }

    @GameTest
    public void theNeedleTurnsToALivingKing(GameTestHelper helper) {
        ItemStack compass = new ItemStack(WitchKingContent.SOUL_COMPASS);
        helper.assertTrue(!compass.has(DataComponents.LODESTONE_TRACKER), "A compass never in the Underworld points nowhere");
        BlockPos hall = new BlockPos(136, 20, 136);
        SoulCompass.aim(compass, Underworld.LEVEL, hall);
        var tracker = compass.get(DataComponents.LODESTONE_TRACKER);
        helper.assertTrue(tracker != null && !tracker.tracked() && tracker.target().get().dimension() == Underworld.LEVEL
                        && tracker.target().get().pos().equals(hall),
                "It points at the hall in the Underworld, without needing a lodestone: " + tracker);
        SoulCompass.aim(compass, Underworld.LEVEL, null);
        helper.assertTrue(!compass.has(DataComponents.LODESTONE_TRACKER), "With no living king in reach it spins");
        helper.succeed();
    }

    @GameTest
    public void aBeatenKingIsRemembered(GameTestHelper helper) {
        var level = helper.getLevel();
        // A hall whose throne stands inside the test: the throne is a block above the floor, at the head of the hall.
        BlockPos throne = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos home = throne.offset(0, -1, Citadel.THRONE_DISTANCE);
        try {
            helper.assertTrue(Citadel.standing(level, home), "A king nobody has beaten stands");
            FallenKings.fall(level, home);
            helper.assertTrue(!Citadel.standing(level, home), "Beaten, he is written down as fallen");
            FallenKings.raise(level, home);
            level.setBlockAndUpdate(throne, WitchKingContent.THRONE.defaultBlockState()
                    .setValue(de.ipnats.hardwrought.witchking.WitchKingThrone.COLD, true));
            helper.assertTrue(!Citadel.standing(level, home), "A cold throne counts as a fallen king, even one never written down");
            helper.assertTrue(FallenKings.fallen(level, home), "and is written down once seen");
        } finally {
            // The test world persists; the hall must not stay beaten for the next run.
            FallenKings.raise(level, home);
            level.removeBlock(throne, false);
        }
        helper.succeed();
    }
}
