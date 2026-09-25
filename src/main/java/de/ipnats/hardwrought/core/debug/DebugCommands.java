package de.ipnats.hardwrought.core.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class DebugCommands {
    private DebugCommands() { }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
                literal("hardwrought")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(literal("status").executes(context -> status(context.getSource())))
                        .then(literal("materials").executes(context -> materials(context.getSource())))
                        .then(literal("combat").executes(context -> combat(context.getSource())))
                        .then(literal("water").executes(context -> water(context.getSource())))
                        .then(literal("nutrition").then(literal("satisfy")
                                .executes(context -> satisfyNutrition(context.getSource()))))
                        .then(literal("air").executes(context -> air(context.getSource()))
                                .then(literal("gas")
                                        .then(gas(de.ipnats.hardwrought.environment.Gas.CARBON_DIOXIDE))
                                        .then(gas(de.ipnats.hardwrought.environment.Gas.DECAYED_CARBON_DIOXIDE))
                                        .then(gas(de.ipnats.hardwrought.environment.Gas.CARBON_MONOXIDE))
                                        .then(gas(de.ipnats.hardwrought.environment.Gas.METHANE))))
                        .then(literal("profile").then(literal("reset").executes(context -> {
                            CoreLifecycle.require(context.getSource().getServer()).scheduler().resetProfiles();
                            context.getSource().sendSuccess(() -> Component.literal("Hardwrought: Profiling zurueckgesetzt."), false);
                            return 1;
                        })))
                        .then(knowledge(registries))
                        .then(literal("debug")
                                .then(literal("on").executes(context -> toggle(context.getSource(), true)))
                                .then(literal("off").executes(context -> toggle(context.getSource(), false))))));
    }

    /**
     * Testing and building tool for the compendium: {@code knowledge unlock <item>|all [players]}
     * studies one entry or every entry, {@code knowledge forget [players]} empties it again. Without
     * players it acts on whoever ran it.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> knowledge(CommandBuildContext registries) {
        return literal("knowledge")
                .then(literal("unlock")
                        .then(literal("all")
                                .executes(context -> unlockAll(context.getSource(), self(context.getSource())))
                                .then(argument("players", EntityArgument.players())
                                        .executes(context -> unlockAll(context.getSource(),
                                                EntityArgument.getPlayers(context, "players")))))
                        .then(argument("item", ItemArgument.item(registries))
                                .executes(context -> unlock(context.getSource(),
                                        ItemArgument.getItem(context, "item").item().value(),
                                        self(context.getSource())))
                                .then(argument("players", EntityArgument.players())
                                        .executes(context -> unlock(context.getSource(),
                                                ItemArgument.getItem(context, "item").item().value(),
                                                EntityArgument.getPlayers(context, "players"))))))
                .then(literal("forget")
                        .executes(context -> forget(context.getSource(), self(context.getSource())))
                        .then(argument("players", EntityArgument.players())
                                .executes(context -> forget(context.getSource(),
                                        EntityArgument.getPlayers(context, "players")))));
    }

    private static Collection<ServerPlayer> self(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return List.of(source.getPlayerOrException());
    }

    private static int unlockAll(CommandSourceStack source, Collection<ServerPlayer> players) {
        var knowledge = CoreLifecycle.require(source.getServer()).knowledge();
        for (ServerPlayer player : players) {
            int learned = knowledge.studyAll(player);
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "Hardwrought: %s kennt jetzt alles (%d neue Eintraege).",
                    player.getName().getString(), learned)), true);
        }
        return players.size();
    }

    private static int unlock(CommandSourceStack source, Item item, Collection<ServerPlayer> players) {
        var knowledge = CoreLifecycle.require(source.getServer()).knowledge();
        int changed = 0;
        for (ServerPlayer player : players) {
            boolean news = knowledge.study(player, item);
            if (news) changed++;
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    news ? "Hardwrought: %s hat %s studiert." : "Hardwrought: %s kannte %s schon.",
                    player.getName().getString(), BuiltInRegistries.ITEM.getKey(item))), true);
        }
        return changed;
    }

    private static int forget(CommandSourceStack source, Collection<ServerPlayer> players) {
        var knowledge = CoreLifecycle.require(source.getServer()).knowledge();
        for (ServerPlayer player : players) {
            knowledge.forget(player.getUUID());
            source.sendSuccess(() -> Component.literal(
                    "Hardwrought: " + player.getName().getString() + " hat alles vergessen."), true);
        }
        return players.size();
    }

    private static int toggle(CommandSourceStack source, boolean enabled) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        boolean success = CoreLifecycle.require(source.getServer()).setDebugViewer(player, enabled);
        if (!success) {
            source.sendFailure(Component.literal("Hardwrought-Debug: Berechtigung oder Client-Unterstuetzung fehlt."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Hardwrought Debug-HUD " + (enabled ? "aktiviert" : "deaktiviert")), false);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        var runtime = CoreLifecycle.require(source.getServer());
        runtime.snapshot(source.getLevel(), net.minecraft.core.BlockPos.containing(source.getPosition()))
                .forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        runtime.scheduler().profiles().forEach(profile -> source.sendSuccess(() -> Component.literal(
                String.format(Locale.ROOT, "%s: avg=%.2fus max=%.2fus disabled=%s", profile.id(),
                        profile.meanMicros(), profile.maxNanos() / 1_000.0, profile.disabled())), false));
        return 1;
    }

    /**
     * Balancing and testing tool: sets how much of one gas is in the block at the player's head. The
     * gas then does what gas does, so this is also the quickest way to watch it move.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> gas(de.ipnats.hardwrought.environment.Gas gas) {
        return literal(gas.id()).then(argument("units",
                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, de.ipnats.hardwrought.environment.Gas.CAPACITY))
                .executes(context -> setGas(context.getSource(), gas,
                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "units"))));
    }

    private static int setGas(CommandSourceStack source, de.ipnats.hardwrought.environment.Gas gas, int units)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        var level = player.level();
        var pos = net.minecraft.core.BlockPos.containing(player.getEyePosition());
        var state = level.getBlockState(pos);
        if (!de.ipnats.hardwrought.environment.Gases.passable(state)) {
            source.sendFailure(Component.literal("Hardwrought: hier ist kein Platz fuer Gas."));
            return 0;
        }
        int now = de.ipnats.hardwrought.environment.Gases.units(state, gas);
        int others = de.ipnats.hardwrought.environment.Gases.total(state) - now;
        int wanted = Math.min(units, de.ipnats.hardwrought.environment.Gas.CAPACITY - others);
        level.setBlock(pos, de.ipnats.hardwrought.environment.Gases.with(state, gas, wanted - now),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Hardwrought: %s am Kopf auf %d von %d Einheiten gesetzt.", gas.id(), wanted,
                de.ipnats.hardwrought.environment.Gas.CAPACITY)), false);
        return 1;
    }

    /**
     * What the simulation actually thinks about the water being looked at. "It does not flow" and
     * "it flows but there is less of it than it looks" are very different problems, and only the
     * exact amount in a cell tells them apart.
     */
    private static int water(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        var runtime = CoreLifecycle.require(source.getServer());
        var level = player.level();
        var eye = player.getEyePosition();
        var reach = eye.add(player.getViewVector(1.0f).scale(6.0));
        var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, reach,
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.ANY, player));
        var pos = hit instanceof net.minecraft.world.phys.BlockHitResult block
                ? block.getBlockPos() : player.blockPosition();

        var job = runtime.scheduler().profiles().stream()
                .filter(profile -> profile.id().equals("hardwrought:water_flow")).findFirst();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Simulation: %s | wartende Zellen %d | Zellfehler %d | Durchlaeufe %d | mittel %.1f us | max %.1f us",
                job.map(profile -> profile.disabled() ? "ABGESCHALTET" : "laeuft").orElse("fehlt"),
                runtime.waterFlow().activeCells(), runtime.waterFlow().failedCells(),
                job.map(profile -> profile.calls()).orElse(0L),
                job.map(de.ipnats.hardwrought.core.simulation.SimulationScheduler.Profile::meanMicros)
                        .orElse(0.0),
                job.map(profile -> profile.maxNanos() / 1_000.0).orElse(0.0))), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Letzter Tick: %d Zellen in %.3f ms (Budget %.3f ms) | Servermittel %.3f ms",
                runtime.waterFlow().lastProcessedCells(), runtime.waterFlow().lastWorkNanos() / 1_000_000.0,
                runtime.waterFlow().lastBudgetNanos() / 1_000_000.0,
                source.getServer().getAverageTickTimeNanos() / 1_000_000.0)), false);

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Weckrufe: %d | davon mit Weltzugriff %d (%.1f %%)",
                runtime.waterFlow().signals(), runtime.waterFlow().worldLookups(),
                runtime.waterFlow().signals() == 0 ? 0.0
                        : 100.0 * runtime.waterFlow().worldLookups() / runtime.waterFlow().signals())), false);

        var body = de.ipnats.hardwrought.water.WaterBody.scan(level, pos);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "%s: %d mB (Stufe %s) | darueber %d mB | darunter %d mB",
                pos.toShortString(),
                de.ipnats.hardwrought.water.WaterStorage.amount(level, pos),
                level.getBlockState(pos).hasProperty(net.minecraft.world.level.block.LiquidBlock.LEVEL)
                        ? level.getBlockState(pos).getValue(net.minecraft.world.level.block.LiquidBlock.LEVEL)
                        : "kein Wasser",
                de.ipnats.hardwrought.water.WaterStorage.amount(level, pos.above()),
                de.ipnats.hardwrought.water.WaterStorage.amount(level, pos.below()))), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Koerper: %s, %d Bloecke, %d mB gesamt", body.size(), body.volume(), body.millibuckets())), false);
        // Ein trockener Brunnen und ein zu hoch gegrabener Brunnen sehen gleich aus. Nur der
        // Vorrat der Region sagt, welches von beiden es ist.
        var groundwater = runtime.groundwater();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Grundwasser %s: Spiegel Y=%d (natuerlich %d) | Vorrat %d/%d L (%.0f%%) | Aquifer %s | Wasser hier %s",
                de.ipnats.hardwrought.water.Groundwater.regionKey(level, pos),
                groundwater.table(level, pos),
                de.ipnats.hardwrought.water.Groundwater.naturalTable(level, pos),
                groundwater.reserve(level, pos) / 1000, groundwater.capacity(level, pos) / 1000,
                groundwater.fill(level, pos) * 100,
                de.ipnats.hardwrought.water.Groundwater.qualityOf(level, pos).serializedName(),
                runtime.water().qualityAt(level, pos).serializedName())), false);
        // "Warum fuellt sich mein Feld nicht?" ist fast immer ein Dach, ein Biom ohne Regen oder
        // Schnee statt Regen. Was die Simulation darueber denkt, steht hier.
        var rainTarget = de.ipnats.hardwrought.water.Rainfall.target(level, pos.getX(), pos.getZ());
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Niederschlag: %s | Ziel %s | %d mB pro Durchgang%s",
                de.ipnats.hardwrought.water.Rainfall.rainsOn(level, pos.getX(), pos.getZ())
                        ? "faellt hier" : "kommt hier nicht an",
                rainTarget == null ? "keines" : rainTarget.toShortString(),
                de.ipnats.hardwrought.water.Rainfall.drop(level.isThundering()),
                level.isThundering() ? " (Gewitter)" : "")), false);
        return 1;
    }

    /** Balancing and testing tool: every nutrient back to the middle of its healthy band. */
    private static int satisfyNutrition(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        var survival = CoreLifecycle.require(source.getServer()).survival();
        survival.setVitalsForTesting(player, survival.vitals(player)
                .withNutrition(de.ipnats.hardwrought.survival.Nutrition.START));
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Hardwrought: alle Naehrstoffe auf %.0f gesetzt.", de.ipnats.hardwrought.survival.Nutrient.START)), false);
        return 1;
    }

    private static int air(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        var reading = CoreLifecycle.require(source.getServer()).environment().reading(player);
        var gases = reading.gases();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "%s, %d Bloecke | O2=%.3f%% CO2=%.3f%% CH4=%.3f%% CO=%.0fppm",
                reading.sealed() ? "Geschlossener Raum" : "Im Freien", reading.volume(),
                gases.oxygen() * 100, gases.carbonDioxide() * 100, gases.methane() * 100,
                gases.carbonMonoxide() * 1_000_000)), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Raumtemperatur %.1f C | Wind %.2f | Isolierung %.2f",
                reading.temperature(), reading.wind(), reading.insulation())), false);
        // Abschnitt 43: duenne Luft sieht im Raum genauso aus wie verbrauchte Luft. Nur die Hoehe
        // sagt, ob geluftet werden kann oder ob draussen auch nicht mehr drin ist.
        int y = player.blockPosition().getY();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Zone %s (Y=%d) | Luftdruck %.2f | O2 draussen %.2f%% | Geothermie +%.1f C | Hoehenwind %.2f%s",
                de.ipnats.hardwrought.environment.VerticalZone.at(y).serializedName(), y,
                de.ipnats.hardwrought.environment.Altitude.pressure(y),
                de.ipnats.hardwrought.environment.Altitude.outsideAir(y).oxygen() * 100,
                de.ipnats.hardwrought.environment.Altitude.geothermal(y),
                de.ipnats.hardwrought.environment.Altitude.gale(y),
                de.ipnats.hardwrought.environment.Altitude.tooThinToBurn(y)
                        ? " | zu duenn fuer offenes Feuer" : "")), false);
        return 1;
    }

    private static int combat(CommandSourceStack source) {
        var runtime = CoreLifecycle.require(source.getServer());
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Profile: %d Waffen, %d Ruestungsteile, %d Schilde", runtime.weaponProfiles().size(),
                runtime.armorProfiles().size(), runtime.shieldProfiles().size())), false);
        var player = source.getPlayer();
        if (player != null) {
            var coverage = runtime.combat().armorCoverage(player);
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "Getragen: Schnitt %.0f%% Stich %.0f%% Wucht %.0f%% | Ausdauerlast %.2f",
                    coverage.slashResistance() * 100, coverage.pierceResistance() * 100,
                    coverage.bluntResistance() * 100, coverage.staminaDrain())), false);
            var weapon = runtime.combat().weaponProfile(player.getMainHandItem());
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "Hand: Schnitt %.0f%% Stich %.0f%% Wucht %.0f%% | Durchschlag %.0f%% Wucht-Impuls %.1f Ausdauer %.2f",
                    weapon.damage().slash() * 100, weapon.damage().pierce() * 100, weapon.damage().blunt() * 100,
                    weapon.armorPenetration() * 100, weapon.impact(), weapon.staminaCost())), false);
        }
        return 1;
    }

    private static int materials(CommandSourceStack source) {
        var definitions = CoreLifecycle.require(source.getServer()).materials();
        definitions.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).limit(50).forEach(entry ->
                source.sendSuccess(() -> Component.literal(entry.getKey() + " = " + entry.getValue()), false));
        source.sendSuccess(() -> Component.literal(definitions.size() + " Materialien (max. 50 angezeigt). Reload: /reload"), false);
        return definitions.size();
    }
}
