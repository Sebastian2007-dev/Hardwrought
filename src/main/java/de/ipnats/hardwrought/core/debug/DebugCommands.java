package de.ipnats.hardwrought.core.debug;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.util.Locale;

import static net.minecraft.commands.Commands.literal;

public final class DebugCommands {
    private DebugCommands() { }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
                literal("hardwrought")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(literal("status").executes(context -> status(context.getSource())))
                        .then(literal("materials").executes(context -> materials(context.getSource())))
                        .then(literal("profile").then(literal("reset").executes(context -> {
                            CoreLifecycle.require(context.getSource().getServer()).scheduler().resetProfiles();
                            context.getSource().sendSuccess(() -> Component.literal("Hardwrought: Profiling zurueckgesetzt."), false);
                            return 1;
                        })))
                        .then(literal("debug")
                                .then(literal("on").executes(context -> toggle(context.getSource(), true)))
                                .then(literal("off").executes(context -> toggle(context.getSource(), false))))));
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

    private static int materials(CommandSourceStack source) {
        var definitions = CoreLifecycle.require(source.getServer()).materials();
        definitions.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).limit(50).forEach(entry ->
                source.sendSuccess(() -> Component.literal(entry.getKey() + " = " + entry.getValue()), false));
        source.sendSuccess(() -> Component.literal(definitions.size() + " Materialien (max. 50 angezeigt). Reload: /reload"), false);
        return definitions.size();
    }
}
