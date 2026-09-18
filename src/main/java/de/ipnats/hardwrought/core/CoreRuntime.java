package de.ipnats.hardwrought.core;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.combat.ArmorProfile;
import de.ipnats.hardwrought.combat.ArmorProfiles;
import de.ipnats.hardwrought.combat.CombatSystem;
import de.ipnats.hardwrought.combat.ShieldProfile;
import de.ipnats.hardwrought.combat.ShieldProfiles;
import de.ipnats.hardwrought.combat.WeaponProfile;
import de.ipnats.hardwrought.combat.WeaponProfiles;
import de.ipnats.hardwrought.core.debug.DiagnosticRegistry;
import de.ipnats.hardwrought.environment.EnvironmentSystem;
import de.ipnats.hardwrought.core.debug.VanillaDiagnostics;
import de.ipnats.hardwrought.core.networking.DebugSnapshotPayload;
import de.ipnats.hardwrought.core.registry.MaterialDefinition;
import de.ipnats.hardwrought.core.registry.MaterialDefinitions;
import de.ipnats.hardwrought.core.registry.ItemWeightDefinitions;
import de.ipnats.hardwrought.core.registry.FoodNutritionDefinition;
import de.ipnats.hardwrought.core.registry.FoodNutritionDefinitions;
import de.ipnats.hardwrought.core.save.CoreSaveData;
import de.ipnats.hardwrought.core.simulation.SimulationScheduler;
import de.ipnats.hardwrought.core.simulation.SimulationTier;
import de.ipnats.hardwrought.core.utilities.ServerThread;
import de.ipnats.hardwrought.survival.SurvivalSystem;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** All mutable gameplay services belong to one server session, never to the physical client. */
public final class CoreRuntime {
    private final MinecraftServer server;
    private final CoreSaveData save;
    private final SimulationScheduler scheduler;
    private final DiagnosticRegistry diagnostics = new DiagnosticRegistry();
    private final EnvironmentSystem environment;
    private final SurvivalSystem survival;
    private final CombatSystem combat;
    private final Set<UUID> debugViewers = new HashSet<>();

    public CoreRuntime(MinecraftServer server) {
        ServerThread.require(server);
        this.server = server;
        save = server.getDataStorage().computeIfAbsent(CoreSaveData.TYPE);
        scheduler = new SimulationScheduler(save.ticks(), System::nanoTime,
                (id, exception) -> Hardwrought.LOGGER.error("Simulation task {} disabled until restart", id, exception));
        VanillaDiagnostics.register(diagnostics);
        environment = new EnvironmentSystem(server, save, scheduler);
        environment.registerDiagnostics(diagnostics);
        survival = new SurvivalSystem(server, save, scheduler, environment);
        combat = new CombatSystem(server, survival, scheduler);
        scheduler.register("hardwrought:debug_sync", SimulationTier.MEDIUM, this::syncDebugViewers);
    }

    public void tick() {
        ServerThread.require(server);
        if (!server.tickRateManager().runsNormally()) return;
        scheduler.tick();
        save.setTicks(scheduler.ticks());
    }

    public SimulationScheduler scheduler() {
        ServerThread.require(server);
        return scheduler;
    }

    public DiagnosticRegistry diagnostics() {
        ServerThread.require(server);
        return diagnostics;
    }

    public SurvivalSystem survival() {
        ServerThread.require(server);
        return survival;
    }

    public EnvironmentSystem environment() {
        ServerThread.require(server);
        return environment;
    }

    public CombatSystem combat() {
        ServerThread.require(server);
        return combat;
    }

    public Map<Identifier, WeaponProfile> weaponProfiles() {
        ServerThread.require(server);
        return server.getOrThrow(WeaponProfiles.KEY);
    }

    public Map<Identifier, ArmorProfile> armorProfiles() {
        ServerThread.require(server);
        return server.getOrThrow(ArmorProfiles.KEY);
    }

    public Map<Identifier, ShieldProfile> shieldProfiles() {
        ServerThread.require(server);
        return server.getOrThrow(ShieldProfiles.KEY);
    }

    public Map<Identifier, MaterialDefinition> materials() {
        ServerThread.require(server);
        return server.getOrThrow(MaterialDefinitions.KEY);
    }

    public Map<Identifier, Double> itemWeights() {
        ServerThread.require(server);
        return server.getOrThrow(ItemWeightDefinitions.KEY);
    }

    public Map<Identifier, FoodNutritionDefinition> foodNutrition() {
        ServerThread.require(server);
        return server.getOrThrow(FoodNutritionDefinitions.KEY);
    }

    public static boolean mayDebug(ServerPlayer player) {
        return player.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public boolean setDebugViewer(ServerPlayer player, boolean enabled) {
        ServerThread.require(server);
        if (enabled && !mayDebug(player)) return false;
        if (!ServerPlayNetworking.canSend(player, DebugSnapshotPayload.TYPE)) return false;
        if (enabled) debugViewers.add(player.getUUID());
        else debugViewers.remove(player.getUUID());
        sendSnapshot(player, enabled);
        return true;
    }

    public void removeViewer(UUID id) {
        ServerThread.require(server);
        debugViewers.remove(id);
    }

    private void syncDebugViewers() {
        var iterator = debugViewers.iterator();
        while (iterator.hasNext()) {
            ServerPlayer player = server.getPlayerList().getPlayer(iterator.next());
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (!mayDebug(player)) {
                iterator.remove();
                sendSnapshot(player, false);
            } else sendSnapshot(player, true);
        }
    }

    private void sendSnapshot(ServerPlayer player, boolean enabled) {
        if (ServerPlayNetworking.canSend(player, DebugSnapshotPayload.TYPE)) {
            ServerPlayNetworking.send(player, new DebugSnapshotPayload(enabled
                    ? snapshot(player.level(), player.blockPosition()) : List.of()));
        }
    }

    public List<String> snapshot(ServerLevel level, BlockPos pos) {
        ServerThread.require(server);
        List<String> lines = new ArrayList<>();
        lines.add("Hardwrought | server tick=" + scheduler.ticks());
        lines.add(level.dimension().identifier() + " @ " + pos.toShortString());
        lines.addAll(diagnostics.inspect(level, pos));
        lines.add("Materials=" + materials().size() + " weapons=" + weaponProfiles().size()
                + " armor=" + armorProfiles().size() + " shields=" + shieldProfiles().size());
        for (var tier : SimulationTier.values()) {
            var profiles = scheduler.profiles().stream().filter(profile -> profile.tier() == tier).toList();
            long calls = profiles.stream().mapToLong(SimulationScheduler.Profile::calls).sum();
            long failures = profiles.stream().mapToLong(SimulationScheduler.Profile::failures).sum();
            long nanos = profiles.stream().mapToLong(SimulationScheduler.Profile::totalNanos).sum();
            lines.add(String.format(Locale.ROOT, "%s /%dt | jobs=%d calls=%d total=%.2fms errors=%d",
                    tier, tier.interval(), profiles.size(), calls, nanos / 1_000_000.0, failures));
        }
        return lines.stream().limit(DebugSnapshotPayload.MAX_LINES)
                .map(line -> line.substring(0, Math.min(line.length(), DebugSnapshotPayload.MAX_LINE_LENGTH))).toList();
    }
}
