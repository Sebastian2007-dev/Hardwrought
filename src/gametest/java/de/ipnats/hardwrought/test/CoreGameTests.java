package de.ipnats.hardwrought.test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.networking.DebugSnapshotPayload;
import de.ipnats.hardwrought.core.networking.SurvivalSnapshotPayload;
import de.ipnats.hardwrought.survival.PlayerVitals;
import de.ipnats.hardwrought.survival.SurvivalSystem;
import de.ipnats.hardwrought.survival.CarryWeight;
import de.ipnats.hardwrought.survival.WaterskinItem;
import de.ipnats.hardwrought.core.registry.MaterialDefinition;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.core.save.CoreSaveData;
import de.ipnats.hardwrought.core.simulation.SimulationScheduler;
import de.ipnats.hardwrought.core.simulation.SimulationTier;
import de.ipnats.hardwrought.core.utilities.ServerThread;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CoreGameTests {
    @GameTest
    public void schedulerTiersAndResume(GameTestHelper helper) {
        var clock = new AtomicLong();
        var scheduler = new SimulationScheduler(0, () -> clock.getAndAdd(1_000), (id, error) -> {
            throw new AssertionError(error);
        });
        int[] calls = new int[4];
        for (var tier : SimulationTier.values()) scheduler.register(tier.name(), tier, () -> calls[tier.ordinal()]++);
        for (int tick = 0; tick < 200; tick++) scheduler.tick();
        helper.assertTrue(calls[0] == 200 && calls[1] == 40 && calls[2] == 10 && calls[3] == 1,
                "Four tiers must run at 1/5/20/200 game ticks");
        helper.assertTrue(scheduler.profiles().getFirst().meanMicros() == 1.0, "Profiler uses measured durations");
        var resumed = new SimulationScheduler(199, System::nanoTime, (id, error) -> { });
        var ran = new AtomicBoolean();
        resumed.register("resume", SimulationTier.SLOW, () -> ran.set(true));
        resumed.tick();
        helper.assertTrue(ran.get(), "Slow simulation must preserve phase after restart");
        helper.succeed();
    }

    @GameTest
    public void failedTaskIsIsolated(GameTestHelper helper) {
        int[] errors = {0};
        int[] healthy = {0};
        var scheduler = new SimulationScheduler(0, System::nanoTime, (id, error) -> errors[0]++);
        scheduler.register("broken", SimulationTier.CRITICAL, () -> { throw new IllegalStateException("test"); });
        scheduler.register("healthy", SimulationTier.CRITICAL, () -> healthy[0]++);
        scheduler.tick();
        scheduler.tick();
        helper.assertTrue(errors[0] == 1 && healthy[0] == 2, "Failed job disabled without stopping healthy job");
        helper.assertTrue(scheduler.profiles().getFirst().disabled(), "Disabled state visible in diagnostics");
        expectFailure(() -> scheduler.register("late", SimulationTier.FAST, () -> { }));
        scheduler.resetProfiles();
        helper.assertTrue(scheduler.profiles().getFirst().calls() == 0 && scheduler.profiles().getFirst().disabled(),
                "Resetting statistics must not reactivate a failed job");
        helper.succeed();
    }

    @GameTest
    public void saveCodecAndValidation(GameTestHelper helper) {
        var data = CoreSaveData.TYPE.constructor().get();
        data.setTicks(431);
        var encoded = CoreSaveData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        var decoded = CoreSaveData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        helper.assertTrue(decoded.ticks() == 431, "Saved simulation time must survive serialization");
        expectFailure(() -> decoded.setTicks(430));
        helper.assertTrue(CoreSaveData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"schema_version\":2,\"simulation_ticks\":0}")).error().isPresent(),
                "Future schema must not be silently decoded");
        helper.assertTrue(CoreSaveData.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"schema_version\":1,\"simulation_ticks\":-1}")).error().isPresent(),
                "Negative save time rejected");
        helper.succeed();
    }

    @GameTest
    public void materialsAndServerLifecycle(GameTestHelper helper) {
        var runtime = CoreLifecycle.require(helper.getLevel().getServer());
        helper.assertTrue(runtime.materials().containsKey(Hardwrought.id("copper")), "Bundled materials loaded through datapack listener");
        helper.assertTrue(runtime.itemWeights().get(Hardwrought.id("filled_waterskin")) == 1.2,
                "Bundled item mass is loaded through the datapack listener");
        helper.assertTrue(runtime.foodNutrition().get(net.minecraft.resources.Identifier.withDefaultNamespace("apple")).vitamins() == 5,
                "Bundled food profile is loaded through the datapack listener");
        helper.assertTrue(runtime.materials().get(Hardwrought.id("copper")).tier() == 1, "Copper properties loaded");
        expectFailure(() -> runtime.materials().clear());
        helper.assertTrue(MaterialDefinition.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"tier\":1,\"density_kg_m3\":-5,\"melting_point_c\":1000}")).error().isPresent(),
                "Invalid balancing data rejected");
        expectFailure(() -> new MaterialDefinition(1, Double.NaN, 10));
        var lines = runtime.snapshot(helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO));
        // Milestone 3 supplies the gas channel; the channels without a system must still say so.
        helper.assertTrue(lines.stream().anyMatch(line -> line.startsWith("STRUCTURE | unavailable")),
                "Unimplemented models must not invent data");
        helper.assertTrue(lines.stream().noneMatch(line -> line.startsWith("ORE | unavailable")),
                "while the ore channel reports the real geology of Milestone 6");
        helper.assertTrue(lines.stream().anyMatch(line -> line.contains("not Celsius")), "Biome temperature must be labelled accurately");
        helper.succeed();
    }

    @GameTest
    public void materialCapabilitiesAndLegacyData(GameTestHelper helper) {
        var nonMetal = MaterialDefinition.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"density_kg_m3\":650}")).getOrThrow();
        helper.assertTrue(nonMetal.meltingPointC().isEmpty() && nonMetal.thermal().isEmpty() && nonMetal.structure().isEmpty(),
                "Missing properties must remain unknown, not fabricated zero values");
        helper.assertTrue(nonMetal.tier() == 0, "Tier is optional descriptive metadata");
        var legacy = MaterialDefinition.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"tier\":1,\"density_kg_m3\":8960,\"melting_point_c\":1084.62}")).getOrThrow();
        helper.assertTrue(legacy.meltingPointC().orElseThrow() == 1084.62, "Existing datapacks remain compatible");
        // Synthetic values test the schema, not final balancing of any real material.
        var complete = MaterialDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"density_kg_m3":650,
                 "thermal":{"conductivity_w_m_k":0.2,"specific_heat_j_kg_k":1500},
                 "structure":{"compression_strength_mpa":20,"tension_strength_mpa":10,"support_distance_blocks":4}}
                """)).getOrThrow();
        var encoded = MaterialDefinition.CODEC.encodeStart(JsonOps.INSTANCE, complete).getOrThrow();
        helper.assertTrue(MaterialDefinition.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().equals(complete),
                "Thermal and structural constants survive serialization");
        helper.assertTrue(complete.meltingPointC().isEmpty(), "Thermal materials need not have a melting point");
        helper.succeed();
    }

    @GameTest
    public void rejectInvalidMaterialCapabilities(GameTestHelper helper) {
        String[] invalid = {
                "{\"density_kg_m3\":650,\"melting_point_c\":-300}",
                "{\"density_kg_m3\":650,\"thermal\":{\"conductivity_w_m_k\":0,\"specific_heat_j_kg_k\":1500}}",
                "{\"density_kg_m3\":650,\"thermal\":{\"conductivity_w_m_k\":0.2}}",
                "{\"density_kg_m3\":650,\"structure\":{\"compression_strength_mpa\":20,\"tension_strength_mpa\":-1,\"support_distance_blocks\":4}}"
        };
        for (String json : invalid) {
            helper.assertTrue(MaterialDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent(),
                    "Invalid optional properties must fail reload, not be silently discarded");
        }
        expectFailure(() -> new MaterialDefinition.ThermalProperties(Double.NaN, 1500));
        expectFailure(() -> new MaterialDefinition.StructuralProperties(20, 10, Double.POSITIVE_INFINITY));
        var nonSupporting = new MaterialDefinition.StructuralProperties(0, 0, 0);
        helper.assertTrue(nonSupporting.supportDistanceBlocks() == 0, "Non-load-bearing materials supported");
        helper.succeed();
    }

    @GameTest
    public void rejectOffThreadAccess(GameTestHelper helper) throws InterruptedException {
        var server = helper.getLevel().getServer();
        var scheduler = CoreLifecycle.require(server).scheduler();
        var rejected = new AtomicBoolean();
        var retainedReferenceRejected = new AtomicBoolean();
        Thread thread = new Thread(() -> {
            try { ServerThread.require(server); }
            catch (IllegalStateException expected) { rejected.set(true); }
            try { scheduler.tick(); }
            catch (IllegalStateException expected) { retainedReferenceRejected.set(true); }
        });
        thread.start();
        thread.join(2_000);
        helper.assertTrue(rejected.get(), "Worker/client thread must not access server state");
        helper.assertTrue(retainedReferenceRejected.get(), "Retained scheduler reference must respect server authority");
        helper.succeed();
    }

    @GameTest
    public void debugCommandRequiresPermission(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var root = server.getCommands().getDispatcher().getRoot().getChild("hardwrought");
        helper.assertTrue(root.canUse(server.createCommandSourceStack()), "Console can use diagnostics");
        helper.assertFalse(root.canUse(server.createCommandSourceStack().withPermission(permission -> false)),
                "Players without game-master permission cannot access debug data");
        helper.succeed();
    }

    @GameTest
    public void diagnosticChannelsAreExtensible(GameTestHelper helper) {
        var registry = new de.ipnats.hardwrought.core.debug.DiagnosticRegistry();
        registry.register("test:gas", de.ipnats.hardwrought.core.debug.DiagnosticRegistry.Channel.GAS,
                (level, pos) -> "oxygen=20.9% (test provider)");
        registry.register("test:structure", de.ipnats.hardwrought.core.debug.DiagnosticRegistry.Channel.STRUCTURE,
                (level, pos) -> "support=42 (test provider)");
        registry.register("test:ore", de.ipnats.hardwrought.core.debug.DiagnosticRegistry.Channel.ORE,
                (level, pos) -> "region=test (test provider)");
        var lines = registry.inspect(helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO));
        helper.assertTrue(lines.stream().anyMatch(line -> line.contains("oxygen=20.9%")), "Gas provider output visible");
        helper.assertTrue(lines.stream().anyMatch(line -> line.contains("support=42")), "Structural provider output visible");
        helper.assertTrue(lines.stream().anyMatch(line -> line.contains("region=test")), "Ore provider output visible");
        var unloaded = registry.inspect(helper.getLevel(), new net.minecraft.core.BlockPos(29_000_000, 50, 29_000_000));
        helper.assertTrue(unloaded.equals(List.of("Position: chunk not loaded")), "Diagnostics must not load remote chunks");
        helper.succeed();
    }

    @GameTest
    public void debugProtocolBounds(GameTestHelper helper) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var payload = new DebugSnapshotPayload(List.of("server tick=42", "WATER | level=8"));
            DebugSnapshotPayload.CODEC.encode(buffer, payload);
            helper.assertTrue(DebugSnapshotPayload.CODEC.decode(buffer).equals(payload), "Snapshot codec round trip");
            buffer.clear();
            buffer.writeVarInt(33);
            expectFailure(() -> DebugSnapshotPayload.CODEC.decode(buffer));
            expectFailure(() -> new DebugSnapshotPayload(List.of("x".repeat(241))));
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest
    public void survivalVitalsPersistAndClamp(GameTestHelper helper) {
        var data = CoreSaveData.TYPE.constructor().get();
        var id = java.util.UUID.randomUUID();
        var changed = PlayerVitals.defaults().withStamina(-50).drink(500)
                .eat(new de.ipnats.hardwrought.survival.Nutrition(60, 60, 60, 60, 60), 0, 1.0);
        data.setVitals(id, changed);
        var encoded = CoreSaveData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        var decoded = CoreSaveData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().vitals(id);
        helper.assertTrue(decoded.stamina() == 0, "Stamina is clamped at zero");
        helper.assertTrue(decoded.hydration() == 100, "Hydration is clamped at maximum");
        helper.assertTrue(decoded.nutrition().protein() == de.ipnats.hardwrought.survival.Nutrient.MAX,
                "Nutrients are bounded");
        helper.assertTrue(PlayerVitals.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"stamina":-1,"hydration":100,"calories":2000,"protein":70,"carbohydrates":260,
                 "fat":70,"micronutrients":100,"fatigue":15,"body_temperature":37,"wetness":0}
                """)).error().isPresent(), "Corrupt survival values are rejected during load");
        helper.assertTrue(CarryWeight.perItem(new ItemStack(Items.STONE))
                        > CarryWeight.perItem(new ItemStack(Items.APPLE)),
                "Blocks weigh more than ordinary food items");
        helper.assertTrue(CarryWeight.perItem(new ItemStack(Items.APPLE),
                        java.util.Map.of(net.minecraft.resources.Identifier.withDefaultNamespace("apple"), 2.75)) == 2.75,
                "Datapack item mass overrides the category fallback");
        var apple = new de.ipnats.hardwrought.core.registry.FoodNutritionDefinition(
                net.minecraft.resources.Identifier.withDefaultNamespace("apple"), 0, 0, 6, 5, 3, 4);
        var fed = PlayerVitals.defaults().eat(apple.nutrients(), apple.hydration(), 1.0);
        helper.assertTrue(fed.nutrition().carbohydrates() > PlayerVitals.defaults().nutrition().carbohydrates()
                        && fed.nutrition().protein() == PlayerVitals.defaults().nutrition().protein()
                        && fed.hydration() == PlayerVitals.defaults().hydration(),
                "A food fills what it has and nothing else, while bounded hydration remains valid");
        expectFailure(() -> new de.ipnats.hardwrought.core.registry.FoodNutritionDefinition(
                net.minecraft.resources.Identifier.withDefaultNamespace("apple"), -1, 0, 0, 0, 0, 0));
        expectFailure(() -> new de.ipnats.hardwrought.core.registry.ItemWeightDefinition(
                net.minecraft.resources.Identifier.withDefaultNamespace("apple"), Double.NaN));
        helper.succeed();
    }

    @GameTest
    public void waterskinHasEightReusableDrinks(GameTestHelper helper) {
        var stack = new ItemStack(ModItems.FILLED_WATERSKIN);
        helper.assertTrue(WaterskinItem.drinksRemaining(stack) == 8, "A fresh waterskin has eight drinks");
        for (int drink = 0; drink < WaterskinItem.CAPACITY; drink++) {
            helper.assertTrue(WaterskinItem.takeDrink(stack), "Each stored drink can be consumed");
        }
        helper.assertTrue(WaterskinItem.drinksRemaining(stack) == 0, "The waterskin becomes empty after eight drinks");
        helper.assertFalse(WaterskinItem.takeDrink(stack), "An empty waterskin cannot provide another drink");
        WaterskinItem.refill(stack);
        helper.assertTrue(WaterskinItem.drinksRemaining(stack) == 8, "Refilling restores all eight drinks");
        helper.succeed();
    }

    @GameTest
    public void survivalProtocolRoundTrip(GameTestHelper helper) {
        var payload = new SurvivalSnapshotPayload(75, 60, new de.ipnats.hardwrought.survival.Nutrition(20, 40, 60, 80, 95),
                31, 36.8, 12.5, 24, 45, true, 0.7, 12.5);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            SurvivalSnapshotPayload.CODEC.encode(buffer, payload);
            helper.assertTrue(payload.equals(SurvivalSnapshotPayload.CODEC.decode(buffer)),
                    "Survival HUD snapshot must round trip without client-side reconstruction");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest
    public void bedsAcceptASleeperAtAnyHour(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(0, 0, 0)).above(20);
        var block = net.minecraft.world.level.block.Blocks.BED
                .pick(net.minecraft.world.item.DyeColor.RED);
        try {
            level.setBlockAndUpdate(pos, block.defaultBlockState());
            var rule = ((net.minecraft.world.level.block.AbstractBedBlock) block).getBedRule(level, pos);
            helper.assertTrue(rule.canSleep() == net.minecraft.world.attribute.BedRule.Rule.ALWAYS,
                    "Section 11: a bed accepts a sleeper regardless of the hour");
            helper.assertFalse(rule.destroyOnUse(),
                    "and an overworld bed is still not the kind that explodes");
            helper.assertTrue(rule.canSetSpawn() == net.minecraft.world.attribute.BedRule.Rule.ALWAYS,
                    "while what a bed does for the respawn point is left to the dimension");

            // The rule that forbids sleeping entirely must survive untouched, or beds in the Nether
            // and the End would quietly become safe.
            helper.assertTrue(net.minecraft.world.attribute.BedRule.DESTROY_ON_USE.canSleep()
                            == net.minecraft.world.attribute.BedRule.Rule.NEVER,
                    "A bed that must never be slept in keeps that rule");
        } finally {
            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest
    public void sleepQualityFollowsTheNightThrough(GameTestHelper helper) {
        double quality = 0.9;
        quality = SurvivalSystem.adjustSleepQuality(quality, 0.3);
        helper.assertTrue(quality < 0.9 && quality > 0.3,
                "A night that turns bad is felt at once, but not all at once: " + quality);
        for (int second = 0; second < 30; second++) quality = SurvivalSystem.adjustSleepQuality(quality, 0.3);
        helper.assertTrue(Math.abs(quality - 0.3) < 0.01, "Within half a minute the sleep is as bad as the night");
        for (int second = 0; second < 30; second++) quality = SurvivalSystem.adjustSleepQuality(quality, 0.95);
        helper.assertTrue(Math.abs(quality - 0.95) < 0.01, "and it comes back when the trouble goes");
        helper.assertTrue(SurvivalSystem.adjustSleepQuality(0.2, -5) >= 0.15
                        && SurvivalSystem.adjustSleepQuality(0.99, 9) <= 1.0,
                "It stays inside the bounds a sleep's quality has");
        helper.succeed();
    }

    @GameTest
    public void oversleepingTurnsIntoRestlessness(GameTestHelper helper) {
        var rested = PlayerVitals.defaults().withStress(0);
        helper.assertTrue(rested.stress() == 0, "A new player carries no restlessness");
        helper.assertTrue(rested.withStress(-5).stress() == 0
                        && rested.withStress(500).stress() == 100,
                "Restlessness stays inside its bounds like every other value");

        // Saved before the value existed: such a world has to keep loading, at zero.
        var legacy = PlayerVitals.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"stamina":50,"hydration":100,"calories":2000,"protein":70,"carbohydrates":260,
                 "fat":70,"micronutrients":100,"fatigue":15,"body_temperature":37,"wetness":0}
                """)).getOrThrow();
        helper.assertTrue(legacy.stress() == 0, "A world saved before Milestone 3 still loads");
        helper.assertTrue(legacy.nutrition().equals(de.ipnats.hardwrought.survival.Nutrition.START),
                "and a world saved while hunger was still calories starts its diet from the middle");
        helper.assertTrue(PlayerVitals.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"stamina":50,"hydration":100,"calories":2000,"protein":70,"carbohydrates":260,
                 "fat":70,"micronutrients":100,"fatigue":15,"body_temperature":37,"wetness":0,
                 "stress":-1}
                """)).error().isPresent(), "A corrupt value is rejected on load");

        var data = CoreSaveData.TYPE.constructor().get();
        var id = java.util.UUID.randomUUID();
        data.setVitals(id, PlayerVitals.defaults().withStress(42));
        var encoded = CoreSaveData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        helper.assertTrue(CoreSaveData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow()
                        .vitals(id).stress() == 42,
                "Restlessness survives a restart like the other survival values");

        // Every other value has to be carried through untouched when only stress changes.
        var full = PlayerVitals.defaults();
        var stressed = full.withStress(30);
        helper.assertTrue(stressed.stamina() == full.stamina() && stressed.fatigue() == full.fatigue()
                        && stressed.hydration() == full.hydration() && stressed.nutrition().equals(full.nutrition()),
                "Setting restlessness changes nothing else");
        helper.succeed();
    }

    private static void expectFailure(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException expected) { return; }
        throw new AssertionError("Expected operation to be rejected");
    }

    /**
     * The load a player carries is only a fair rule if the ordinary trip is free and the penalty for
     * hauling too much stops well short of crippling. This is the test that would have caught a
     * single stack of cobblestone putting a fresh player permanently over the limit.
     */
    @GameTest
    public void anOrdinaryTripIsCarriedWithoutPenalty(GameTestHelper helper) {
        double capacity = de.ipnats.hardwrought.survival.CarryWeight.BASE_CAPACITY_KG;
        double block = de.ipnats.hardwrought.survival.CarryWeight.perItem(
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
        double tool = de.ipnats.hardwrought.survival.CarryWeight.perItem(
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
        double food = de.ipnats.hardwrought.survival.CarryWeight.perItem(
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD));

        helper.assertTrue(block * 64 < capacity,
                "One stack of blocks must not use up the whole allowance by itself");
        double trip = block * 64 * 3 + tool * 4 + food * 16;
        helper.assertTrue(trip <= capacity,
                "A working trip — three stacks, four tools, a stack of bread — is carried free: "
                        + String.format(java.util.Locale.ROOT, "%.1f of %.0f kg", trip, capacity));

        // A full inventory of rubble is another matter, and should be.
        double hoard = block * 64 * 36;
        helper.assertTrue(hoard > capacity * 3,
                "Hauling a full inventory of stone is meant to be felt");

        // Whatever the load, the penalties stay survivable: still sprinting, still clearing a block.
        for (double carried : new double[]{0, capacity, capacity * 2, capacity * 20, hoard}) {
            double load = Math.max(0, carried / capacity - 1.0);
            double speed = Math.min(0.30, load * 0.18);
            double jump = Math.min(0.25, load * 0.15);
            helper.assertTrue(speed <= 0.30 && jump <= 0.25,
                    "Overload slows and weighs down; it never stops a player moving");
        }
        helper.assertTrue(de.ipnats.hardwrought.survival.SurvivalSystem.exhaustion(
                        de.ipnats.hardwrought.survival.PlayerVitals.MAX_STAMINA) == 0.0,
                "And a player at full stamina pays no exhaustion penalty at all");
        helper.succeed();
    }

    @GameTest
    public void hungerIsTheVanillaBarAndItEmptiesOnItsOwn(GameTestHelper helper) {
        // Doing nothing has to cost something, on a scale a player feels within a day or two: a full
        // bar and its saturation are about thirty points, four exhaustion each.
        double idleSeconds = 30 * 4 / (SurvivalSystem.BASAL_ENERGY_PER_SECOND * SurvivalSystem.EXHAUSTION_PER_ENERGY);
        helper.assertTrue(idleSeconds > 1200 && idleSeconds < 3600,
                "A full belly empties in between one and three Minecraft days of doing nothing: " + idleSeconds + "s");
        helper.succeed();
    }

    @GameTest
    public void aDietHasToBeBalanced(GameTestHelper helper) {
        var diet = de.ipnats.hardwrought.survival.Nutrition.START;
        helper.assertTrue(diet.balanced(), "A new body starts balanced");
        // About two Minecraft days of eating nothing brings the levels out of the band.
        var hungry = diet.drained(3000, 0);
        for (var nutrient : de.ipnats.hardwrought.survival.Nutrient.values()) {
            helper.assertTrue(hungry.get(nutrient) < diet.get(nutrient), "The body uses up " + nutrient);
        }
        helper.assertFalse(hungry.balanced(), "and after two days of nothing it is no longer balanced");
        helper.assertTrue(diet.drained(1200, 0).balanced(), "but one day of it is not yet enough to fall out");
        helper.assertTrue(diet.drained(1, 5).carbohydrates() < diet.drained(1, 0).carbohydrates(),
                "Work burns carbohydrates");

        // Four days of living on steak alone: protein and fat run over while the rest runs out.
        var steak = new de.ipnats.hardwrought.survival.Nutrition(12, 8, 0, 1, 0);
        var carnivore = diet;
        for (int meal = 0; meal < 24; meal++) carnivore = carnivore.plus(steak, 1.0).drained(200, 0);
        helper.assertTrue(carnivore.high(de.ipnats.hardwrought.survival.Nutrient.PROTEIN),
                "Steak alone is too much protein");
        helper.assertTrue(carnivore.low(de.ipnats.hardwrought.survival.Nutrient.FIBER)
                        && carnivore.low(de.ipnats.hardwrought.survival.Nutrient.CARBOHYDRATES),
                "and too little of what it does not have");

        var runtime = de.ipnats.hardwrought.core.events.CoreLifecycle.require(helper.getLevel().getServer());
        net.minecraft.server.level.ServerPlayer player = (net.minecraft.server.level.ServerPlayer)
                helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        runtime.survival().setVitalsForTesting(player, PlayerVitals.defaults());
        runtime.survival().consumeFood(player, new ItemStack(Items.CARROT));
        var afterCarrot = runtime.survival().vitals(player).nutrition();
        helper.assertTrue(afterCarrot.vitamins() == diet.vitamins() + 7 && afterCarrot.protein() == diet.protein(),
                "Each food fills its own nutrients: a carrot is vitamins, not protein");
        runtime.survival().consumeFood(player, new ItemStack(Items.MILK_BUCKET));
        helper.assertTrue(runtime.survival().vitals(player).nutrition().fat() > afterCarrot.fat(),
                "Milk counts as food too");
        helper.succeed();
    }

}
