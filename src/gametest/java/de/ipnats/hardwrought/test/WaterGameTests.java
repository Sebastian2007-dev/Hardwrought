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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class WaterGameTests {
    /** Built clear of the test structure so a fill never meets the test blocks themselves. */
    private static final int WORKSPACE_OFFSET = 30;

    @GameTest
    public void waterQualityOrdersBySafety(GameTestHelper helper) {
        helper.assertTrue(WaterQuality.PURIFIED.safeToDrink(), "Purified water is safe");
        helper.assertFalse(WaterQuality.FRESH.safeToDrink(), "Even a spring is not quite safe raw");
        helper.assertTrue(WaterQuality.FRESH.illnessRisk() < WaterQuality.RIVER.illnessRisk(),
                "though far safer than a river");
        helper.assertTrue(WaterQuality.PURIFIED.hydrationFactor() > WaterQuality.FRESH.hydrationFactor(),
                "and cleaned water refreshes a little more");
        helper.assertFalse(WaterQuality.RIVER.safeToDrink() || WaterQuality.SWAMP.safeToDrink(),
                "Surface water is not safe raw");
        helper.assertTrue(WaterQuality.SWAMP.illnessRisk() > WaterQuality.RIVER.illnessRisk(),
                "Standing organic water is worse than running water");
        helper.assertTrue(WaterQuality.SALT.hydrationFactor() < 0,
                "Section 23.2: seawater takes more water out of the body than it puts in");
        helper.assertTrue(WaterQuality.FRESH.hydrationFactor() == 1.0, "Clean water is the measure");

        helper.assertTrue(WaterQuality.SWAMP.boiled() == WaterQuality.PURIFIED
                        && WaterQuality.RIVER.boiled() == WaterQuality.PURIFIED
                        && WaterQuality.FRESH.boiled() == WaterQuality.PURIFIED,
                "Boiling purifies surface water");
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
        helper.assertTrue(WaterskinItem.quality(stack) == WaterQuality.PURIFIED,
                "Boiling swamp water in the skin purifies it");

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
        // What the client asks for when the player sneaks at water with an empty hand (MinecraftDrinkMixin).
        return de.ipnats.hardwrought.water.WaterEvents.drinkByHand(player);
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
        helper.assertTrue(de.ipnats.hardwrought.water.WaterQuality.PURIFIED.illnessRisk() == 0.0,
                "Purified water never does this");
        helper.assertTrue(de.ipnats.hardwrought.water.WaterQuality.SALT.illnessRisk() > 0.0,
                "Sea water does");
        helper.succeed();
    }

    /**
     * A bucket that holds a measured amount, and the two sums that must come out.
     *
     * <p>Water taken out of the world equals water in the bucket; water poured out of the bucket
     * equals water in the world. The old all-or-nothing bucket kept those true by refusing
     * everything awkward — a puddle could not be dipped and a nearly full cell could not be topped
     * up. Keeping them true while also doing the awkward cases is the whole of this change.
     */
    @GameTest
    public void aBucketHoldsWhateverItCouldGet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1)).offset(WORKSPACE_OFFSET, 0, WORKSPACE_OFFSET);
        try {
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());

            // A puddle holding a third of a block gives a third of a bucket, not nothing.
            int puddle = de.ipnats.hardwrought.water.WaterAmounts.BLOCK / 3;
            de.ipnats.hardwrought.water.WaterStorage.setAmount(level, pos, puddle);
            int drawn = de.ipnats.hardwrought.water.WaterDraw.drawUpTo(level, pos,
                    de.ipnats.hardwrought.water.BucketWater.CAPACITY);
            helper.assertTrue(drawn == puddle, "A dip takes what is there: " + drawn + " of " + puddle);
            helper.assertTrue(de.ipnats.hardwrought.water.WaterStorage.amount(level, pos) == 0,
                    "and leaves the puddle empty rather than partly drained");

            ItemStack bucket = de.ipnats.hardwrought.water.BucketWater.with(
                    new ItemStack(Items.BUCKET), drawn, de.ipnats.hardwrought.water.WaterQuality.FRESH);
            helper.assertTrue(bucket.is(Items.WATER_BUCKET), "Any water at all makes it a water bucket");
            helper.assertTrue(de.ipnats.hardwrought.water.BucketWater.held(bucket) == puddle,
                    "and it carries exactly what was taken");
            helper.assertTrue(!de.ipnats.hardwrought.water.BucketWater.isFull(bucket),
                    "A third of a bucket is not a bucketful, whatever the item id says");
            helper.assertTrue(de.ipnats.hardwrought.water.BucketWater.room(bucket)
                            == de.ipnats.hardwrought.water.BucketWater.CAPACITY - puddle,
                    "and it has room for the rest");

            // Topping it up out of a fuller cell fills it and no further.
            de.ipnats.hardwrought.water.WaterStorage.setAmount(level, pos,
                    de.ipnats.hardwrought.water.WaterAmounts.BLOCK);
            int topped = de.ipnats.hardwrought.water.WaterDraw.drawUpTo(level, pos,
                    de.ipnats.hardwrought.water.BucketWater.room(bucket));
            bucket = de.ipnats.hardwrought.water.BucketWater.with(bucket,
                    de.ipnats.hardwrought.water.BucketWater.held(bucket) + topped,
                    de.ipnats.hardwrought.water.WaterQuality.FRESH);
            helper.assertTrue(de.ipnats.hardwrought.water.BucketWater.isFull(bucket),
                    "A second dip fills it: " + de.ipnats.hardwrought.water.BucketWater.held(bucket));
            helper.assertTrue(de.ipnats.hardwrought.water.WaterStorage.amount(level, pos)
                            == de.ipnats.hardwrought.water.WaterAmounts.BLOCK - topped,
                    "and takes from the world exactly what it gained");

            // An unmarked bucket is a full one: creative and other mods hand those out.
            helper.assertTrue(de.ipnats.hardwrought.water.BucketWater.held(
                            new ItemStack(Items.WATER_BUCKET))
                            == de.ipnats.hardwrought.water.BucketWater.CAPACITY,
                    "A bucket with nothing written on it has to read as full, never as empty");

            // Emptying it down to nothing gives the empty item back.
            ItemStack emptied = de.ipnats.hardwrought.water.BucketWater.with(bucket, 0, null);
            helper.assertTrue(emptied.is(Items.BUCKET)
                            && de.ipnats.hardwrought.water.BucketWater.held(emptied) == 0,
                    "Pouring the last of it out leaves an empty bucket");

            // The visible step never reads as empty while there is water in it.
            helper.assertTrue(de.ipnats.hardwrought.water.BucketWater.modelStep(1) >= 1,
                    "One millibucket still shows as some water");
            helper.assertTrue(de.ipnats.hardwrought.water.BucketWater.modelStep(0) == 0,
                    "and none shows as none");
        } finally {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    /** Uses what the player holds on a block, the way a right click does. */
    private static net.minecraft.world.InteractionResult useOn(GameTestHelper helper,
                                                              net.minecraft.server.level.ServerPlayer player, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.invoker().interact(player, helper.getLevel(),
                net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                        net.minecraft.core.Direction.UP, pos, false));
    }

    @GameTest
    public void aCauldronKeepsWhatIsPouredIntoIt(GameTestHelper helper) {
        BlockPos cauldron = new BlockPos(1, 1, 1);
        helper.setBlock(cauldron, Blocks.CAULDRON);
        @SuppressWarnings("removal") net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack seawater = de.ipnats.hardwrought.water.BucketWater.with(new ItemStack(Items.BUCKET),
                de.ipnats.hardwrought.water.BucketWater.CAPACITY, WaterQuality.SALT);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, seawater);
        useOn(helper, player, cauldron);
        helper.assertBlockPresent(Blocks.WATER_CAULDRON, cauldron);
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "The bucket is emptied into the cauldron");

        useOn(helper, player, cauldron);
        helper.assertBlockPresent(Blocks.CAULDRON, cauldron);
        helper.assertTrue(WaterEvents.qualityOf(player.getMainHandItem()) == WaterQuality.SALT,
                "and what comes back out of it is seawater still, not clean water: " + player.getMainHandItem());
        helper.succeed();
    }

    // World water, beside the water-flow tests that sum the whole world's flow: a batch of its own.
    @GameTest(environment = "hardwrought-test:standing_water")
    public void seawaterDoesNotWaterAField(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos field = new BlockPos(1, 1, 1), water = field.east(2);
        helper.setBlock(field, Blocks.FARMLAND.defaultBlockState()
                .setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE, 7));
        helper.setBlock(water, Blocks.WATER);
        BlockPos at = helper.absolutePos(water);
        de.ipnats.hardwrought.water.WaterQualityStorage.mark(level, at, WaterQuality.SALT);
        helper.assertTrue(de.ipnats.hardwrought.water.WaterQualityStorage.salty(level, at), "The water is marked seawater");
        if (level.isRainingAt(helper.absolutePos(field).above())) {
            helper.succeed();
            return;
        }
        for (int tick = 0; tick < 7; tick++) {
            BlockPos pos = helper.absolutePos(field);
            level.getBlockState(pos).randomTick(level, pos, level.getRandom());
        }
        helper.assertTrue(helper.getBlockState(field).getValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE) < 7,
                "Beside seawater the field dries out");

        de.ipnats.hardwrought.water.WaterQualityStorage.mark(level, at, WaterQuality.PURIFIED);
        helper.setBlock(field, Blocks.FARMLAND.defaultBlockState()
                .setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE, 7));
        for (int tick = 0; tick < 7; tick++) {
            BlockPos pos = helper.absolutePos(field);
            level.getBlockState(pos).randomTick(level, pos, level.getRandom());
        }
        helper.assertTrue(helper.getBlockState(field).getValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE) == 7,
                "and beside clean water it stays wet");
        de.ipnats.hardwrought.water.WaterQualityStorage.clear(level, at);
        helper.succeed();
    }

    @GameTest(maxTicks = 1400)
    public void aBowlOfWaterBoiledOnACampfireIsPurified(GameTestHelper helper) {
        BlockPos fire = new BlockPos(1, 1, 1);
        helper.setBlock(fire, Blocks.CAMPFIRE);
        @SuppressWarnings("removal") net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var campfire = helper.getBlockEntity(fire, net.minecraft.world.level.block.entity.CampfireBlockEntity.class);
        helper.assertFalse(campfire.placeFood(helper.getLevel(), player,
                        de.ipnats.hardwrought.water.WaterBowlItem.of(ModItems.WATER_BOWL, WaterQuality.SALT)),
                "A campfire does not take seawater: boiling only makes it saltier");
        helper.assertFalse(campfire.placeFood(helper.getLevel(), player,
                        de.ipnats.hardwrought.water.WaterBowlItem.of(ModItems.WATER_BOWL, WaterQuality.PURIFIED)),
                "nor water already purified");
        helper.assertTrue(campfire.placeFood(helper.getLevel(), player,
                        de.ipnats.hardwrought.water.WaterBowlItem.of(ModItems.WATER_BOWL, WaterQuality.SWAMP)),
                "A bowl of swamp water goes on like food");

        helper.succeedWhen(() -> {
            var dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(fire)).inflate(2));
            helper.assertTrue(!dropped.isEmpty(), "Boiled through, it comes off the fire");
            ItemStack bowl = dropped.getFirst().getItem();
            helper.assertTrue(WaterEvents.qualityOf(bowl) == WaterQuality.PURIFIED
                            && de.ipnats.hardwrought.water.WaterBowlItem.hot(bowl),
                    "purified, and hot: " + bowl.getComponentsPatch());
            // Drunk straight off the fire, it scalds.
            ItemStack scalding = bowl.copy();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, scalding);
            scalding.getItem().use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(scalding.getItem().getUseDuration(scalding, player) == de.ipnats.hardwrought.water.Boiling.DRINK_TICKS,
                    "Drinking takes a moment");
            ItemStack left = scalding.getItem().finishUsingItem(scalding, helper.getLevel(), player);
            player.stopUsingItem();
            helper.assertTrue(left.is(Items.BOWL), "Drunk, the empty bowl is left");
            // The hurt itself is not checked: a player just come into the world cannot be hurt for a moment.
            helper.assertTrue(player.hasEffect(de.ipnats.hardwrought.core.registry.ModEffects.SCALDED), "Boiling water scalds");
            // Carried, it cools.
            for (int second = 0; second < de.ipnats.hardwrought.water.WaterBowlItem.COOL_SECONDS; second++) {
                bowl.setDamageValue(Math.max(0, bowl.getDamageValue() - 1));
            }
            helper.assertTrue(!de.ipnats.hardwrought.water.WaterBowlItem.hot(bowl), "and cools in its time");
        });
    }

    private static void pour(de.ipnats.hardwrought.water.WaterTreatment treatment, WaterQuality quality, long millibuckets) {
        try (var transaction = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            treatment.both.insert(de.ipnats.hardwrought.chemistry.SaltPan.water(quality),
                    millibuckets * (net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET / 1000), transaction);
            transaction.commit();
        }
    }

    @GameTest
    public void eachStageOfCleaningDoesWhatItCan(GameTestHelper helper) {
        var sand = new de.ipnats.hardwrought.water.WaterTreatment(de.ipnats.hardwrought.water.WaterTreatment.Stage.SAND, () -> { });
        pour(sand, WaterQuality.SWAMP, 1000);
        for (int tick = 0; tick < 40; tick++) sand.work(1.0);
        helper.assertTrue(sand.outputMillibuckets() == 0, "A sand filter with no charcoal in its bed lets nothing through");
        helper.assertTrue(sand.addCharcoal(), "Charcoal goes into the bed");
        for (int tick = 0; tick < 20; tick++) sand.work(1.0);
        helper.assertTrue(sand.outputMillibuckets() == 50 && sand.outputQuality() == WaterQuality.FRESH,
                "Fifty millibuckets a second of swamp water come out fresh: " + sand.outputMillibuckets() + " " + sand.outputQuality());
        helper.assertTrue(sand.inputMillibuckets() + sand.outputMillibuckets() == 1000, "and none is lost");
        helper.assertTrue(sand.mediumMillibuckets() == 4000 - 50, "while the charcoal wears down as much");

        var salty = new de.ipnats.hardwrought.water.WaterTreatment(de.ipnats.hardwrought.water.WaterTreatment.Stage.CERAMIC, () -> { });
        salty.addCharcoal();
        pour(salty, WaterQuality.SALT, 1000);
        for (int tick = 0; tick < 40; tick++) salty.work(1.0);
        helper.assertTrue(salty.outputMillibuckets() == 0, "No filter takes the salt out of seawater");

        var ceramic = new de.ipnats.hardwrought.water.WaterTreatment(de.ipnats.hardwrought.water.WaterTreatment.Stage.CERAMIC, () -> { });
        ceramic.addCharcoal();
        pour(ceramic, WaterQuality.RIVER, 1000);
        for (int tick = 0; tick < 20; tick++) ceramic.work(1.0);
        helper.assertTrue(ceramic.outputMillibuckets() == 150 && ceramic.outputQuality() == WaterQuality.PURIFIED,
                "A ceramic filter purifies, three times as fast: " + ceramic.outputMillibuckets());

        var machine = new de.ipnats.hardwrought.water.WaterTreatment(de.ipnats.hardwrought.water.WaterTreatment.Stage.ELECTRIC, () -> { });
        pour(machine, WaterQuality.SALT, 1000);
        for (int tick = 0; tick < 20; tick++) machine.work(1.0);
        helper.assertTrue(machine.outputMillibuckets() == 250 && machine.outputQuality() == WaterQuality.PURIFIED,
                "The purifier desalts seawater, at a quarter of its pace: " + machine.outputMillibuckets());
        for (int tick = 0; tick < 20; tick++) machine.work(0.0);
        helper.assertTrue(machine.outputMillibuckets() == 250, "and does nothing without current");
        helper.succeed();
    }

    @GameTest(maxTicks = 400, environment = "hardwrought-test:standing_water")
    public void waterPouredOnAFilterComesOutCleanUnderneath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos filter = new BlockPos(2, 2, 2), above = filter.above(), below = filter.below();
        // A stone cup on top of the filter and one under it, so neither water runs off.
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                if (x != 2 || z != 2) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                    helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
                }
            }
        }
        helper.setBlock(filter, de.ipnats.hardwrought.water.WaterTreatmentBlocks.CERAMIC_FILTER);
        var entity = helper.getBlockEntity(filter, de.ipnats.hardwrought.water.WaterTreatmentBlocks.FilterEntity.class);
        entity.treatment.addCharcoal();
        helper.setBlock(above, Blocks.WATER);
        de.ipnats.hardwrought.water.WaterQualityStorage.mark(level, helper.absolutePos(above), WaterQuality.SWAMP);
        helper.succeedWhen(() -> {
            BlockPos out = helper.absolutePos(below);
            int under = de.ipnats.hardwrought.water.WaterStorage.amount(level, out);
            helper.assertTrue(under >= 500, "Water poured on top comes out underneath: " + under + " mB");
            helper.assertTrue(de.ipnats.hardwrought.water.WaterQualityStorage.stored(level, out) == WaterQuality.PURIFIED,
                    "and comes out purified");
            de.ipnats.hardwrought.water.WaterStorage.setAmount(level, out, 0);
            de.ipnats.hardwrought.water.WaterStorage.setAmount(level, helper.absolutePos(above), 0);
        });
    }

    // World water, beside the water-flow tests that sum the whole world's flow: a batch of its own.
    @GameTest(environment = "hardwrought-test:standing_water")
    public void theClientIsToldWhatIsInTheWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos water = new BlockPos(1, 1, 1);
        helper.setBlock(water, Blocks.WATER);
        BlockPos at = helper.absolutePos(water);
        de.ipnats.hardwrought.water.WaterQualityStorage.mark(level, at, WaterQuality.PURIFIED);
        de.ipnats.hardwrought.water.WaterQualityStorage.flush();
        var shown = level.getChunkAt(at).getAttached(de.ipnats.hardwrought.water.WaterQualityStorage.SHOWN);
        helper.assertTrue(shown != null && shown.at(at.asLong()) == WaterQuality.PURIFIED,
                "A purified cell is sent to be drawn as such");
        // Swamp water poured into a small pond is swamp water by the server's own fallback too, but the
        // client would draw it plain, so it is sent all the same.
        de.ipnats.hardwrought.water.WaterQualityStorage.mark(level, at, WaterQuality.SWAMP);
        de.ipnats.hardwrought.water.WaterQualityStorage.flush();
        shown = level.getChunkAt(at).getAttached(de.ipnats.hardwrought.water.WaterQualityStorage.SHOWN);
        helper.assertTrue(shown != null && shown.at(at.asLong()) == WaterQuality.SWAMP,
                "Poured swamp water is drawn as swamp water wherever it is poured");
        de.ipnats.hardwrought.water.WaterQualityStorage.clear(level, at);
        de.ipnats.hardwrought.water.WaterQualityStorage.flush();
        shown = level.getChunkAt(at).getAttached(de.ipnats.hardwrought.water.WaterQualityStorage.SHOWN);
        helper.assertTrue(shown == null || shown.at(at.asLong()) == null, "and forgotten again once it is plain water");
        helper.succeed();
    }
}
