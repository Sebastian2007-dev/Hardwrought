package de.ipnats.hardwrought.test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.environment.BiomeClimate;
import de.ipnats.hardwrought.environment.Season;
import de.ipnats.hardwrought.environment.SeasonCycle;
import de.ipnats.hardwrought.survival.WaterskinItem;
import de.ipnats.hardwrought.water.WaterBody;
import de.ipnats.hardwrought.water.WaterEvents;
import de.ipnats.hardwrought.water.WaterQuality;
import de.ipnats.hardwrought.water.WaterQualityProfile;
import de.ipnats.hardwrought.water.WaterSystem;
import de.ipnats.hardwrought.survival.PlayerVitals;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public final class WaterGameTests {
    /** Built clear of the test structure so a fill never meets the test blocks themselves. */
    private static final int WORKSPACE_OFFSET = 30;

    @GameTest
    public void waterQualityOrdersBySafety(GameTestHelper helper) {
        helper.assertTrue(WaterQuality.FRESH.safeToDrink(), "Fresh water is safe");
        helper.assertFalse(WaterQuality.RIVER.safeToDrink() || WaterQuality.SWAMP.safeToDrink(),
                "Surface water is not safe raw");
        helper.assertTrue(WaterQuality.SWAMP.illnessRisk() > WaterQuality.RIVER.illnessRisk(),
                "Standing organic water is worse than running water");
        helper.assertTrue(WaterQuality.SALT.hydrationFactor() < 0,
                "Section 23.2: seawater takes more water out of the body than it puts in");
        helper.assertTrue(WaterQuality.FRESH.hydrationFactor() == 1.0, "Clean water is the measure");

        helper.assertTrue(WaterQuality.SWAMP.boiled() == WaterQuality.FRESH
                        && WaterQuality.RIVER.boiled() == WaterQuality.FRESH,
                "Boiling makes surface water drinkable");
        helper.assertTrue(WaterQuality.SALT.boiled() == WaterQuality.SALT,
                "but boiling seawater only concentrates the salt");

        helper.assertTrue(WaterQuality.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("\"swamp\"")).getOrThrow() == WaterQuality.SWAMP, "Names round trip");
        helper.assertTrue(WaterQuality.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("\"brackish\"")).error().isPresent(), "Unknown qualities are rejected");
        expectFailure(() -> WaterQuality.byOrdinal(99));
        helper.succeed();
    }

    @GameTest
    public void qualityProfilesAreLoadedAndBounded(GameTestHelper helper) {
        var runtime = CoreLifecycle.require(helper.getLevel().getServer());
        var swamp = runtime.waterQualityProfiles().get(Identifier.withDefaultNamespace("swamp"));
        helper.assertTrue(swamp != null && swamp.standing() == WaterQuality.SWAMP,
                "The bundled swamp profile is loaded through the datapack listener");
        expectFailure(() -> runtime.waterQualityProfiles().clear());

        var profile = WaterQualityProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"biomes":["minecraft:river"],"standing":"river"}
                """)).getOrThrow();
        helper.assertTrue(profile.quality(false) == WaterQuality.RIVER, "Standing water uses the stated value");
        helper.assertTrue(profile.quality(true) == WaterQuality.RIVER,
                "and flowing water falls back to it when nothing else is stated");
        var stagnant = WaterQualityProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"biomes":["minecraft:swamp"],"standing":"swamp"}
                """)).getOrThrow();
        helper.assertTrue(stagnant.quality(true) == WaterQuality.RIVER,
                "Running swamp water is better than the pool it came from");
        helper.assertTrue(WaterQualityProfile.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString("{\"biomes\":[],\"standing\":\"fresh\"}")).result().isPresent(),
                "An empty biome list parses but is rejected by the loader, not by the codec");
        helper.succeed();
    }

    @GameTest
    public void waterBodiesAreMeasuredAndBounded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO).above(WORKSPACE_OFFSET);
        try {
            helper.assertTrue(WaterBody.scan(level, base).size() == WaterBody.Size.NONE,
                    "Empty air is not a water body");

            // A single source block in a stone bowl: the smallest possible body.
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                }
            }
            level.setBlockAndUpdate(base, Blocks.WATER.defaultBlockState());
            WaterBody single = WaterBody.scan(level, base);
            helper.assertTrue(single.size() == WaterBody.Size.MEASURED && single.volume() == 1,
                    "One source block is a body of exactly one block");
            helper.assertTrue(single.sources() == 1 && single.puddle(),
                    "and it is a puddle, small enough for the sun to matter");
            helper.assertTrue(single.depth() == 1, "A single block is one deep");
            helper.assertTrue(single.millibuckets() == de.ipnats.hardwrought.water.WaterAmounts.BLOCK,
                    "and it holds exactly one block of water, measured rather than counted in blocks");
            expectFailure(() -> new WaterBody(WaterBody.Size.MEASURED, 1, 1000, 0, 0, 10, 5));
        } finally {
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 0; y++) {
                    for (int z = -1; z <= 1; z++) {
                        level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest
    public void groundwaterIsRegionalAndRepeatable(GameTestHelper helper) {
        var water = CoreLifecycle.require(helper.getLevel().getServer()).water();
        ServerLevel level = helper.getLevel();
        BlockPos here = helper.absolutePos(BlockPos.ZERO);
        int size = de.ipnats.hardwrought.water.WaterSystem.REGION_SIZE_BLOCKS;
        BlockPos corner = new BlockPos(Math.floorDiv(here.getX(), size) * size, here.getY(),
                Math.floorDiv(here.getZ(), size) * size);
        int table = water.groundwaterLevel(level, corner);

        helper.assertTrue(water.groundwaterLevel(level, corner) == table,
                "The same place always has the same water table");
        helper.assertTrue(water.groundwaterLevel(level,
                        corner.offset(size - 1, 40, size - 1)) == table,
                "The table belongs to the region, not to the block, so a well works anywhere in it");
        helper.assertTrue(table < level.getSeaLevel(),
                "and it lies below sea level, so digging for it is real work");
        helper.assertTrue(water.belowWaterTable(level, new BlockPos(corner.getX(), table, corner.getZ()))
                        && !water.belowWaterTable(level, new BlockPos(corner.getX(), table + 1, corner.getZ())),
                "The table is exactly the line between wet ground and dry");

        boolean varies = false;
        for (int region = 1; region <= 8 && !varies; region++) {
            varies = water.groundwaterLevel(level, corner.offset(region * size, 0, 0)) != table;
        }
        helper.assertTrue(varies, "Different regions carry different water tables");
        helper.succeed();
    }

    @GameTest
    public void onlyNaturalGroundLetsWaterThrough(GameTestHelper helper) {
        var water = CoreLifecycle.require(helper.getLevel().getServer()).water();
        helper.assertTrue(de.ipnats.hardwrought.water.WaterSystem.isWaterBearing(
                        Blocks.STONE.defaultBlockState())
                        && de.ipnats.hardwrought.water.WaterSystem.isWaterBearing(
                        Blocks.GRAVEL.defaultBlockState())
                        && de.ipnats.hardwrought.water.WaterSystem.isWaterBearing(
                        Blocks.DIRT.defaultBlockState()),
                "Section 68: a shaft cut through natural ground seeps");
        helper.assertFalse(de.ipnats.hardwrought.water.WaterSystem.isWaterBearing(
                        Blocks.OAK_PLANKS.defaultBlockState())
                        || de.ipnats.hardwrought.water.WaterSystem.isWaterBearing(
                        Blocks.STONE_BRICKS.defaultBlockState()),
                "while a lined cellar stays dry, which is the first countermeasure");
        helper.assertTrue(water != null, "The water system is part of the runtime");
        helper.succeed();
    }

    @GameTest
    public void containersRememberWhatWasPutIntoThem(GameTestHelper helper) {
        var stack = new ItemStack(ModItems.FILLED_WATERSKIN);
        helper.assertTrue(WaterskinItem.quality(stack) == WaterQuality.FRESH,
                "An unmarked container counts as clean rather than as a guess");

        WaterskinItem.refill(stack, WaterQuality.SALT);
        helper.assertTrue(WaterskinItem.quality(stack) == WaterQuality.SALT
                        && WaterskinItem.drinksRemaining(stack) == WaterskinItem.CAPACITY,
                "Filling records the quality and fills the skin");
        helper.assertTrue(stack.get(ModDataComponents.WATER_QUALITY) == WaterQuality.SALT,
                "and it is stored on the item, so it survives being put down");

        WaterskinItem.takeDrink(stack);
        helper.assertTrue(WaterskinItem.quality(stack) == WaterQuality.SALT,
                "Drinking part of it does not change what the rest is");

        WaterskinItem.refill(stack, WaterQuality.SWAMP);
        stack.set(ModDataComponents.WATER_QUALITY, WaterskinItem.quality(stack).boiled());
        helper.assertTrue(WaterskinItem.quality(stack) == WaterQuality.FRESH,
                "Boiling swamp water in the skin makes it drinkable");

        helper.assertTrue(WaterEvents.qualityOf(new ItemStack(net.minecraft.world.item.Items.POTION))
                        == WaterQuality.FRESH,
                "A bottle with no record counts as clean");
        var marked = new ItemStack(net.minecraft.world.item.Items.POTION);
        marked.set(ModDataComponents.WATER_QUALITY, WaterQuality.SALT);
        helper.assertTrue(WaterEvents.qualityOf(marked) == WaterQuality.SALT,
                "and a bottle filled at sea is still seawater when it is drunk");
        helper.succeed();
    }

    @GameTest
    public void waterJobsAreRegistered(GameTestHelper helper) {
        var scheduler = CoreLifecycle.require(helper.getLevel().getServer()).scheduler();
        var ids = scheduler.profiles().stream().map(profile -> profile.id()).toList();
        helper.assertTrue(ids.contains("hardwrought:water_seepage")
                        && ids.contains("hardwrought:water_evaporation"),
                "Water upkeep runs as registered simulation jobs, not as ad-hoc ticking");
        helper.assertTrue(scheduler.profiles().stream()
                        .filter(profile -> profile.id().startsWith("hardwrought:water"))
                        .noneMatch(profile -> profile.disabled()),
                "No water job has failed and been disabled");
        helper.succeed();
    }

    @GameTest
    public void evaporationFollowsBiomeAndSeason(GameTestHelper helper) {
        double coldSummer = WaterSystem.evaporationChance(30, 0.5, BiomeClimate.COLD, Season.SUMMER);
        double neutralSummer = WaterSystem.evaporationChance(30, 0.5, BiomeClimate.NEUTRAL, Season.SUMMER);
        double warmSummer = WaterSystem.evaporationChance(30, 0.5, BiomeClimate.WARM, Season.SUMMER);
        helper.assertTrue(coldSummer < neutralSummer && neutralSummer < warmSummer,
                "At equal weather, warm biomes evaporate more water than neutral and cold biomes");

        double warmSpring = WaterSystem.evaporationChance(30, 0.5, BiomeClimate.WARM, Season.SPRING);
        double warmAutumn = WaterSystem.evaporationChance(30, 0.5, BiomeClimate.WARM, Season.AUTUMN);
        double warmWinter = WaterSystem.evaporationChance(30, 0.5, BiomeClimate.WARM, Season.WINTER);
        helper.assertTrue(warmSummer > warmSpring && warmSpring > warmAutumn && warmAutumn > warmWinter,
                "Summer is the strongest drying season and winter the weakest");
        helper.assertTrue(WaterSystem.evaporationChance(4, 1, BiomeClimate.WARM, Season.SUMMER) == 0,
                "Cold water does not disappear merely because its biome is normally warm");
        helper.succeed();
    }

    @GameTest
    public void seasonCalendarIsStableAtItsBoundaries(GameTestHelper helper) {
        long length = SeasonCycle.TICKS_PER_SEASON;
        helper.assertTrue(SeasonCycle.at(0) == Season.SPRING
                        && SeasonCycle.at(length) == Season.SUMMER
                        && SeasonCycle.at(length * 2) == Season.AUTUMN
                        && SeasonCycle.at(length * 3) == Season.WINTER
                        && SeasonCycle.at(SeasonCycle.TICKS_PER_YEAR) == Season.SPRING,
                "The persisted world clock advances through all four seasons and wraps to spring");
        helper.assertTrue(SeasonCycle.dayInSeason(0) == 1
                        && SeasonCycle.dayInSeason(length - SeasonCycle.TICKS_PER_DAY) == 12,
                "Each season contains exactly twelve numbered days");
        helper.succeed();
    }

    @GameTest
    public void waterDiagnosticsReportTheBodyAndTheTable(GameTestHelper helper) {
        var runtime = CoreLifecycle.require(helper.getLevel().getServer());
        var lines = runtime.snapshot(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        helper.assertTrue(lines.stream().anyMatch(line -> line.startsWith("WATER | ")
                        && line.contains("groundwater Y=")),
                "The water channel reports the regional water table");
        helper.assertTrue(lines.stream().noneMatch(line -> line.startsWith("WATER | unavailable")),
                "and it is a real measurement, not a placeholder");
        helper.succeed();
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("Expected operation to be rejected");
    }

    /**
     * Section 23.1: a player who has lost their waterskin should not die of thirst standing in a
     * river. Sneak with an empty hand at the water and drink straight out of it.
     */
    @GameTest
    public void sneakingWithAnEmptyHandDrinksStraightFromTheWater(GameTestHelper helper) {
        var level = helper.getLevel();
        var runtime = de.ipnats.hardwrought.core.events.CoreLifecycle.require(level.getServer());
        net.minecraft.core.BlockPos pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
        de.ipnats.hardwrought.water.WaterStorage.setAmount(level, pos,
                de.ipnats.hardwrought.water.WaterAmounts.BLOCK);

        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
        player.setShiftKeyDown(true);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                net.minecraft.world.item.ItemStack.EMPTY);

        int water = de.ipnats.hardwrought.water.WaterStorage.amount(level, pos);
        helper.assertTrue(runtime.survival().mayDrinkByHand(player), "A player who has not drunk may drink");
        helper.assertTrue(drinkAt(player, level, pos) == net.minecraft.world.InteractionResult.SUCCESS,
                "Sneaking at the water with an empty hand drinks from it");
        helper.assertTrue(de.ipnats.hardwrought.water.WaterStorage.amount(level, pos)
                        == water - de.ipnats.hardwrought.water.WaterAmounts.DRINK,
                "A mouthful comes out of the world, the same as one out of a skin");

        // A gesture, not a button to hold down.
        helper.assertTrue(!runtime.survival().mayDrinkByHand(player),
                "And the next mouthful has to wait");
        int left = de.ipnats.hardwrought.water.WaterStorage.amount(level, pos);
        helper.assertTrue(drinkAt(player, level, pos) != net.minecraft.world.InteractionResult.SUCCESS,
                "A second mouthful in the same tick is refused");
        helper.assertTrue(de.ipnats.hardwrought.water.WaterStorage.amount(level, pos) == left,
                "And the refused mouthful takes nothing out of the river");

        // What the mouthful is worth is arithmetic, and it is checked where the arithmetic lives: a
        // mock player is always in creative, and a creative player is never thirsty.
        PlayerVitals thirsty = PlayerVitals.defaults().withHydration(20.0);
        double handful = de.ipnats.hardwrought.survival.SurvivalSystem.HAND_DRINK_HYDRATION;
        helper.assertTrue(thirsty.drink(handful * de.ipnats.hardwrought.water.WaterQuality.FRESH
                        .hydrationFactor()).hydration() == 20.0 + handful,
                "A handful of clean water is worth exactly a handful");
        helper.assertTrue(thirsty.drink(handful * de.ipnats.hardwrought.water.WaterQuality.SALT
                        .hydrationFactor()).hydration() < 20.0,
                "Section 23.2: a handful of sea water leaves the drinker worse off");
        helper.assertTrue(handful < de.ipnats.hardwrought.survival.WaterskinItem.DRINK_HYDRATION,
                "Cupped hands spill; a skin does not");

        // Standing up, or holding anything at all, is somebody else’s interaction.
        player.setShiftKeyDown(false);
        helper.assertTrue(drinkAt(player, level, pos) == net.minecraft.world.InteractionResult.PASS,
                "Standing upright at a river is not drinking from it");
        player.setShiftKeyDown(true);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE));
        helper.assertTrue(drinkAt(player, level, pos) == net.minecraft.world.InteractionResult.PASS,
                "Sneaking with a block in hand still places the block");

        // Dry ground is not a drink.
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                net.minecraft.world.item.ItemStack.EMPTY);
        net.minecraft.core.BlockPos dry = helper.absolutePos(new net.minecraft.core.BlockPos(3, 2, 3));
        player.snapTo(dry.getX() + 0.5, dry.getY() + 4.0, dry.getZ() + 0.5, 0.0f, 90.0f);
        helper.assertTrue(drinkAt(player, level, dry) == net.minecraft.world.InteractionResult.PASS,
                "There is nothing to drink where there is no water");
        helper.succeed();
    }

    private static net.minecraft.world.InteractionResult drinkAt(net.minecraft.server.level.ServerPlayer player,
                                                                 net.minecraft.server.level.ServerLevel level,
                                                                 net.minecraft.core.BlockPos pos) {
        var hit = new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
        return net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker()
                .interact(player, level, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
    }

    /**
     * Section 23.2: bad water is not venom. It leaves the drinker thirsty, which is the water half
     * of what hunger does to a well-fed one.
     */
    @GameTest
    public void badWaterLeavesYouThirstyRatherThanPoisoned(GameTestHelper helper) {
        var thirst = de.ipnats.hardwrought.core.registry.ModEffects.THIRST;
        helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(thirst.value())
                        .equals(de.ipnats.hardwrought.Hardwrought.id("thirst")),
                "The effect is registered where its icon and its name are looked for");
        helper.assertTrue(thirst.value().getCategory()
                        == net.minecraft.world.effect.MobEffectCategory.HARMFUL,
                "And it reads as harmful, so the bar shows it in the right colour");
        helper.assertTrue(thirst.value().getDescriptionId().equals("effect.hardwrought.thirst"),
                "Its name comes from the language file");

        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(de.ipnats.hardwrought.survival.SurvivalSystem.thirstDrain(player) == 0.0,
                "A player without the effect pays nothing for it");
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(thirst,
                de.ipnats.hardwrought.survival.SurvivalSystem.THIRST_DURATION_TICKS, 0));
        helper.assertTrue(de.ipnats.hardwrought.survival.SurvivalSystem.thirstDrain(player)
                        == de.ipnats.hardwrought.survival.SurvivalSystem.THIRST_PER_SECOND,
                "The first level costs one share of the reserve a second");
        player.removeEffect(thirst);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(thirst,
                de.ipnats.hardwrought.survival.SurvivalSystem.THIRST_DURATION_TICKS, 1));
        helper.assertTrue(de.ipnats.hardwrought.survival.SurvivalSystem.thirstDrain(player)
                        == 2 * de.ipnats.hardwrought.survival.SurvivalSystem.THIRST_PER_SECOND,
                "And a worse case costs proportionally more");
        player.removeEffect(thirst);

        // A debuff nobody notices is not a debuff. Resting thirst is 0.035 a second.
        helper.assertTrue(de.ipnats.hardwrought.survival.SurvivalSystem.THIRST_PER_SECOND > 0.035 * 3,
                "The effect has to be felt against the reserve draining on its own");
        helper.assertTrue(de.ipnats.hardwrought.survival.SurvivalSystem.THIRST_PER_SECOND
                        * de.ipnats.hardwrought.survival.SurvivalSystem.THIRST_DURATION_TICKS / 20.0
                        < de.ipnats.hardwrought.survival.PlayerVitals.MAX_HYDRATION / 2.0,
                "And it has to be survivable: one bad drink is not half the reserve");

        // Only water that can make somebody ill does, and salt water is the worst of it.
        helper.assertTrue(de.ipnats.hardwrought.water.WaterQuality.FRESH.illnessRisk() == 0.0,
                "Clean water never does this");
        helper.assertTrue(de.ipnats.hardwrought.water.WaterQuality.SALT.illnessRisk() > 0.0,
                "Sea water does");
        helper.succeed();
    }
}
