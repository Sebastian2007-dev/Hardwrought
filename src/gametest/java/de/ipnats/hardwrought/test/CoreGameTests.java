package de.ipnats.hardwrought.test;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.core.networking.DebugSnapshotPayload;
import de.ipnats.hardwrought.core.networking.SurvivalSnapshotPayload;
import de.ipnats.hardwrought.survival.PlayerVitals;
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
        helper.assertTrue(runtime.foodNutrition().get(net.minecraft.resources.Identifier.withDefaultNamespace("apple")).calories() == 95,
                "Bundled food profile is loaded through the datapack listener");
        helper.assertTrue(runtime.materials().get(Hardwrought.id("copper")).tier() == 1, "Copper properties loaded");
        expectFailure(() -> runtime.materials().clear());
        helper.assertTrue(MaterialDefinition.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"tier\":1,\"density_kg_m3\":-5,\"melting_point_c\":1000}")).error().isPresent(),
                "Invalid balancing data rejected");
        expectFailure(() -> new MaterialDefinition(1, Double.NaN, 10));
        var lines = runtime.snapshot(helper.getLevel(), helper.absolutePos(net.minecraft.core.BlockPos.ZERO));
        helper.assertTrue(lines.stream().anyMatch(line -> line.startsWith("GAS | unavailable")), "Unimplemented gas model must not invent data");
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
        var changed = PlayerVitals.defaults().withStamina(-50).drink(500).eat(40, 20);
        data.setVitals(id, changed);
        var encoded = CoreSaveData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        var decoded = CoreSaveData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().vitals(id);
        helper.assertTrue(decoded.stamina() == 0, "Stamina is clamped at zero");
        helper.assertTrue(decoded.hydration() == 100, "Hydration is clamped at maximum");
        helper.assertTrue(decoded.calories() <= PlayerVitals.MAX_CALORIES, "Calories are bounded");
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
                net.minecraft.resources.Identifier.withDefaultNamespace("apple"), 95, 0.5, 25, 0.3, 8, 4);
        var fed = PlayerVitals.defaults().eat(apple);
        helper.assertTrue(fed.carbohydrates() > PlayerVitals.defaults().carbohydrates()
                        && fed.hydration() == PlayerVitals.defaults().hydration(),
                "Food profiles update macros while bounded hydration remains valid");
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
        var payload = new SurvivalSnapshotPayload(75, 60, 1800, 82, 31, 36.8,
                12.5, 24, 45, true, 0.7);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            SurvivalSnapshotPayload.CODEC.encode(buffer, payload);
            helper.assertTrue(payload.equals(SurvivalSnapshotPayload.CODEC.decode(buffer)),
                    "Survival HUD snapshot must round trip without client-side reconstruction");
        } finally { buffer.release(); }
        helper.succeed();
    }

    private static void expectFailure(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException expected) { return; }
        throw new AssertionError("Expected operation to be rejected");
    }
}
