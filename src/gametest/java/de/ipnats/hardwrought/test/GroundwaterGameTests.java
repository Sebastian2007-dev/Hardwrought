package de.ipnats.hardwrought.test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.environment.Season;
import de.ipnats.hardwrought.water.AquiferProfile;
import de.ipnats.hardwrought.water.AquiferState;
import de.ipnats.hardwrought.water.Groundwater;
import de.ipnats.hardwrought.water.WaterAmounts;
import de.ipnats.hardwrought.water.WaterQuality;
import de.ipnats.hardwrought.water.WaterQualityStorage;
import de.ipnats.hardwrought.water.WaterStorage;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Milestone 4, the ground half: the water table is a quantity that can be drawn down, and water
 * carries what is dissolved in it.
 */
public final class GroundwaterGameTests {
    /** Built clear of the test structure so nothing here meets the test blocks themselves. */
    private static final int WORKSPACE_OFFSET = 30;

    @GameTest
    public void aquiferArithmeticIsPureAndBounded(GameTestHelper helper) {
        AquiferState full = new AquiferState(1_000, 0);
        helper.assertTrue(full.fill(1_000) == 1.0 && full.drawn(400, 10).stored() == 600,
                "What is drawn out is gone, and what is left is what is left");
        helper.assertTrue(full.drawn(4_000, 10).stored() == 0,
                "A region cannot be overdrawn into a debt, it simply runs out");

        AquiferState empty = new AquiferState(0, 0);
        helper.assertTrue(empty.rechargedTo(200, 1_000, 1.0, 10_000).stored() == 200,
                "Recharge accumulates at its rate for the time that has passed");
        helper.assertTrue(empty.rechargedTo(9_999, 1_000, 1.0, 10_000).stored() == 1_000,
                "and stops at the capacity of the ground rather than overfilling it");
        helper.assertTrue(empty.rechargedTo(1_000_000, 1_000, 1.0, 20).stored() == 20,
                "A region left alone for a year is not simulated for a year");
        helper.assertTrue(empty.rechargedTo(0, 1_000, 1.0, 10_000).stored() == 0,
                "and time that has not passed adds nothing");
        expectFailure(() -> new AquiferState(-1, 0));
        expectFailure(() -> full.drawn(-1, 0));

        helper.assertTrue(Groundwater.drawdown(1.0) == 0
                        && Groundwater.drawdown(0.0) == Groundwater.MAX_DRAWDOWN,
                "A full region stands at its natural table and an empty one at the bottom of it");
        helper.assertTrue(Groundwater.drawdown(0.5) > 0
                        && Groundwater.drawdown(0.5) < Groundwater.MAX_DRAWDOWN,
                "and it sinks gradually in between, so a well fails before it fails completely");
        expectFailure(() -> Groundwater.drawdown(Double.NaN));

        helper.assertTrue(Groundwater.yieldAt(0) == Groundwater.SEEP_YIELD,
                "An opening at the table itself is a seep");
        helper.assertTrue(Groundwater.yieldAt(Groundwater.PRESSURE_DEPTH) == Groundwater.BREACH_YIELD
                        && Groundwater.yieldAt(1_000) == Groundwater.BREACH_YIELD,
                "Section 68: deep under the table it is a flood, and no worse than a flood");
        helper.assertTrue(Groundwater.yieldAt(12) > Groundwater.yieldAt(4),
                "The deeper the cut, the harder the ground presses");
        helper.assertTrue(Groundwater.yieldAt(-5) == 0, "Ground above the table yields nothing");

        helper.assertTrue(Groundwater.infiltration(Season.SPRING) > Groundwater.infiltration(Season.SUMMER)
                        && Groundwater.infiltration(Season.SUMMER) > Groundwater.infiltration(Season.WINTER),
                "The melt fills an aquifer, a summer downpour runs off, and frozen ground takes almost nothing");
        helper.succeed();
    }

    @GameTest
    public void aquiferProfilesAreLoadedAndBounded(GameTestHelper helper) {
        var runtime = CoreLifecycle.require(helper.getLevel().getServer());
        var coastal = runtime.aquiferProfiles().get(Identifier.withDefaultNamespace("beach"));
        helper.assertTrue(coastal != null && coastal.quality() == WaterQuality.SALT,
                "A well dug next to the sea yields brine, however deep it goes");
        var desert = runtime.aquiferProfiles().get(Identifier.withDefaultNamespace("desert"));
        helper.assertTrue(desert != null && desert.richness() < 1.0 && desert.tableOffset() < 0,
                "Desert ground holds little water and holds it far down: section 23.6 scarcity");
        expectFailure(() -> runtime.aquiferProfiles().clear());

        var minimal = AquiferProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"biomes":["minecraft:plains"]}
                """)).getOrThrow();
        helper.assertTrue(minimal.richness() == 1.0 && minimal.tableOffset() == 0
                        && minimal.quality() == WaterQuality.FRESH,
                "Ordinary ground is the default: average water, at its natural depth, clean");
        helper.assertFalse(minimal.dry(), "and it is not dry rock");
        var barren = AquiferProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"biomes":["minecraft:the_void"],"richness":0.0}
                """)).getOrThrow();
        helper.assertTrue(barren.dry(), "Ground stated to hold nothing never yields anything");
        helper.assertTrue(AquiferProfile.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString("{\"biomes\":[\"minecraft:plains\"],\"richness\":-1}"))
                .error().isPresent(), "A negative amount of water is rejected by the codec");
        helper.assertTrue(AquiferProfile.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString("{\"biomes\":[\"minecraft:plains\"],\"quality\":\"brine\"}"))
                .error().isPresent(), "and so is a quality nothing knows");
        helper.succeed();
    }

    @GameTest
    public void drainingARegionLowersItsWaterTable(GameTestHelper helper) {
        var groundwater = CoreLifecycle.require(helper.getLevel().getServer()).groundwater();
        ServerLevel level = helper.getLevel();
        BlockPos here = helper.absolutePos(BlockPos.ZERO);
        try {
            int capacity = groundwater.capacity(level, here);
            helper.assertTrue(capacity > 0 && groundwater.reserve(level, here) == capacity,
                    "Ground nobody has drawn on is full, and it is full without being stored");
            int natural = Groundwater.naturalTable(level, here);
            helper.assertTrue(groundwater.table(level, here) == natural,
                    "so the table stands where the seed put it");

            helper.assertTrue(groundwater.draw(level, here, 1_000) == 1_000,
                    "A spring takes what it asks for while the ground still has it");
            helper.assertTrue(groundwater.reserve(level, here) <= capacity - 1_000,
                    "and what it took is gone from the region");

            int remaining = groundwater.reserve(level, here);
            helper.assertTrue(groundwater.draw(level, here, remaining + 5_000) == remaining,
                    "Asking for more than is there yields exactly what is there");
            helper.assertTrue(groundwater.draw(level, here, 1_000) == 0,
                    "and after that nothing: this is a well running dry");
            helper.assertTrue(groundwater.table(level, here) == natural - Groundwater.MAX_DRAWDOWN,
                    "A drained region carries its table twelve blocks lower than a full one");

            // The reserve belongs to the region, not to the block: a well works anywhere inside it.
            BlockPos across = here.offset(Groundwater.REGION_SIZE_BLOCKS - 1, 0, 0);
            helper.assertTrue(Groundwater.regionKey(level, here).equals(Groundwater.regionKey(level, here.above(40)))
                            && !Groundwater.regionKey(level, here)
                            .equals(Groundwater.regionKey(level, here.offset(Groundwater.REGION_SIZE_BLOCKS * 2, 0, 0))),
                    "One reserve per region and dimension, and the next region over is its own");
            helper.assertTrue(Groundwater.regionKey(level, across).equals(Groundwater.regionKey(level, here))
                            == (Math.floorDiv(across.getX(), Groundwater.REGION_SIZE_BLOCKS)
                            == Math.floorDiv(here.getX(), Groundwater.REGION_SIZE_BLOCKS)),
                    "and the region boundary is where the key says it is");
        } finally {
            groundwater.refill(level, here);
        }
        helper.assertTrue(groundwater.reserve(level, here) == groundwater.capacity(level, here),
                "Refilling a region puts it back to what untouched ground is");
        helper.succeed();
    }

    @GameTest
    public void contaminationTravelsEasilyAndCleanlinessDoesNot(GameTestHelper helper) {
        helper.assertTrue(WaterQuality.worse(WaterQuality.FRESH, WaterQuality.SALT) == WaterQuality.SALT
                        && WaterQuality.worse(WaterQuality.RIVER, WaterQuality.SWAMP) == WaterQuality.SWAMP,
                "The declaration order is the order of usefulness");

        helper.assertTrue(WaterQuality.mix(WaterQuality.FRESH, 3_000, WaterQuality.SALT, 1_000)
                        == WaterQuality.SALT,
                "A quarter of a mixture is enough to spoil the whole of it");
        helper.assertTrue(WaterQuality.mix(WaterQuality.FRESH, 10_000, WaterQuality.SALT, 100)
                        == WaterQuality.FRESH,
                "while a splash of it disappears into a large clean body");
        helper.assertTrue(WaterQuality.mix(WaterQuality.SALT, 1_000, WaterQuality.FRESH, 1_000)
                        == WaterQuality.SALT,
                "Diluting seawater by half does not make it drinkable");
        helper.assertTrue(WaterQuality.mix(WaterQuality.SALT, 100, WaterQuality.FRESH, 10_000)
                        == WaterQuality.FRESH,
                "but washing a marked cell out with clean water eventually does");
        helper.assertTrue(WaterQuality.mix(WaterQuality.SALT, 0, WaterQuality.FRESH, 500)
                        == WaterQuality.FRESH,
                "Water poured into an empty cell is simply what was poured");
        helper.assertTrue(WaterQuality.mix(WaterQuality.SWAMP, 500, WaterQuality.SWAMP, 500)
                        == WaterQuality.SWAMP,
                "and mixing water with its own kind changes nothing");
        expectFailure(() -> WaterQuality.mix(WaterQuality.FRESH, -1, WaterQuality.FRESH, 1));
        expectFailure(() -> WaterQuality.worse(null, WaterQuality.FRESH));
        helper.succeed();
    }

    @GameTest
    public void waterCarriesWhatIsDissolvedInIt(GameTestHelper helper) {
        var runtime = CoreLifecycle.require(helper.getLevel().getServer());
        ServerLevel level = helper.getLevel();
        BlockPos left = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        BlockPos right = left.east();
        try {
            buildTrough(level, left, right);
            WaterStorage.setAmount(level, left, WaterAmounts.BLOCK);
            WaterQualityStorage.mark(level, left, WaterQuality.SALT);
            helper.assertTrue(runtime.water().qualityAt(level, left) == WaterQuality.SALT,
                    "Marked water is what it was marked as, whatever biome it stands in");

            // One pass of the real solver: half of it levels into the empty neighbour.
            runtime.waterFlow().step(level, left);
            helper.assertTrue(WaterStorage.amount(level, right) > 0,
                    "The water moved, so there is something to have carried");
            helper.assertTrue(WaterQualityStorage.stored(level, right) == WaterQuality.SALT,
                    "and what was dissolved in it went along: seawater stays seawater when it flows");

            int present = WaterStorage.amount(level, right);
            WaterQualityStorage.add(level, right, WaterQuality.FRESH, present, WaterAmounts.BOTTLE);
            helper.assertTrue(runtime.water().qualityAt(level, right) == WaterQuality.SALT,
                    "A bottle of clean water does not rescue a cell of brine");
            WaterQualityStorage.add(level, right, WaterQuality.FRESH, present, 20 * WaterAmounts.BLOCK);
            helper.assertTrue(runtime.water().qualityAt(level, right) != WaterQuality.SALT,
                    "but enough clean water washes the mark out of it");

            WaterStorage.setAmount(level, left, 0);
            helper.assertTrue(WaterQualityStorage.stored(level, left) == null,
                    "Water that is no longer there cannot still be salt water");
        } finally {
            clearTrough(level, left, right);
        }
        helper.succeed();
    }

    @GameTest
    public void groundwaterIsRegisteredAndReported(GameTestHelper helper) {
        var runtime = CoreLifecycle.require(helper.getLevel().getServer());
        var ids = runtime.scheduler().profiles().stream().map(profile -> profile.id()).toList();
        helper.assertTrue(ids.contains("hardwrought:groundwater_recharge"),
                "Recharge runs as a registered simulation job, not as ad-hoc ticking");
        helper.assertTrue(runtime.scheduler().profiles().stream()
                        .filter(profile -> profile.id().equals("hardwrought:groundwater_recharge"))
                        .noneMatch(profile -> profile.disabled()),
                "and it has not failed and been disabled");

        var lines = runtime.snapshot(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        helper.assertTrue(lines.stream().anyMatch(line -> line.startsWith("WATER | aquifer")
                        && line.contains("reserve=")),
                "The water channel reports the regional reserve, not only the table");
        helper.succeed();
    }

    private static void buildTrough(ServerLevel level, BlockPos left, BlockPos right) {
        for (BlockPos pos : new BlockPos[]{left, right}) {
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(pos.north(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(pos.south(), Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(left.west(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(right.east(), Blocks.STONE.defaultBlockState());
    }

    private static void clearTrough(ServerLevel level, BlockPos left, BlockPos right) {
        WaterStorage.setAmount(level, left, 0);
        WaterStorage.setAmount(level, right, 0);
        for (BlockPos pos : new BlockPos[]{left, right}) {
            level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos.north(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos.south(), Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(left.west(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(right.east(), Blocks.AIR.defaultBlockState());
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Expected operation to be rejected");
    }
}
