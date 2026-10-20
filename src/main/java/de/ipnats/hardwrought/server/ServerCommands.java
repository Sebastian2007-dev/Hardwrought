package de.ipnats.hardwrought.server;

import de.ipnats.hardwrought.core.CoreRuntime;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.encounter.Encounter;
import de.ipnats.hardwrought.encounter.Encounters;
import de.ipnats.hardwrought.party.Party;
import de.ipnats.hardwrought.party.PartySystem;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;
import java.util.UUID;

import static net.minecraft.commands.Commands.literal;

/**
 * What an operator wants to know about a running server, in one place:
 *
 * <pre>
 * /hardwrought server          ticks per second, tick time, players (away, sleeping), worlds, boss fights
 * /hardwrought server parties  every party, its leader and members
 * /hardwrought server bosses   every boss fight going on: fighters, health, where
 * </pre>
 *
 * Read-only. The mod's own simulation profile is under {@code /hardwrought status}.
 */
public final class ServerCommands {
    private ServerCommands() { }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
                literal("hardwrought")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(literal("server")
                                .executes(context -> status(context.getSource()))
                                .then(literal("parties").executes(context -> parties(context.getSource())))
                                .then(literal("bosses").executes(context -> bosses(context.getSource()))))));
    }

    /** The mean time of the last hundred ticks, in milliseconds. */
    public static double tickMillis(MinecraftServer server) {
        long[] times = server.getTickTimesNanos();
        if (times.length == 0) return 0;
        long sum = 0;
        for (long time : times) sum += time;
        return sum / (double) times.length / 1_000_000.0;
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        double mspt = tickMillis(server);
        float rate = server.tickRateManager().tickrate();
        double tps = Math.min(rate, mspt <= 0 ? rate : 1000.0 / mspt);
        int online = server.getPlayerList().getPlayerCount(), away = 0, sleeping = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (Afk.away(player)) away++;
            if (player.isSleeping()) sleeping++;
        }
        CoreRuntime runtime = CoreLifecycle.require(server);
        MutableComponent text = Component.translatable("message.hardwrought.server.header").withStyle(ChatFormatting.GOLD);
        ChatFormatting health = mspt < 40 ? ChatFormatting.GREEN : mspt < 50 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        text.append(line(Component.translatable("message.hardwrought.server.ticks",
                String.format(Locale.ROOT, "%.1f", tps), String.format(Locale.ROOT, "%.0f", rate),
                String.format(Locale.ROOT, "%.1f", mspt)).withStyle(health)));
        text.append(line(Component.translatable("message.hardwrought.server.players", online, away, sleeping,
                runtime.party().parties().size())));
        for (ServerLevel level : server.getAllLevels()) {
            int entities = 0;
            for (var ignored : level.getAllEntities()) entities++;
            text.append(line(Component.translatable("message.hardwrought.server.level", level.dimension().identifier().toString(),
                    level.getChunkSource().getLoadedChunksCount(), entities, level.players().size())));
        }
        text.append(line(Component.translatable("message.hardwrought.server.bosses", Encounters.all().size())));
        source.sendSuccess(() -> text, false);
        return online;
    }

    private static int parties(CommandSourceStack source) {
        PartySystem parties = CoreLifecycle.require(source.getServer()).party();
        MutableComponent text = Component.translatable("message.hardwrought.server.parties", parties.parties().size())
                .withStyle(ChatFormatting.GOLD);
        for (Party party : parties.parties()) {
            MutableComponent members = Component.empty();
            boolean first = true;
            for (UUID id : party.members()) {
                if (!first) members.append(", ");
                first = false;
                boolean online = source.getServer().getPlayerList().getPlayer(id) != null;
                members.append(parties.name(id).copy().withStyle(online ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
            }
            text.append(line(Component.literal("★ ").withStyle(ChatFormatting.YELLOW).append(parties.name(party.leader()))
                    .append(Component.literal(" (" + party.size() + "): ").withStyle(ChatFormatting.GRAY)).append(members)
                    .append(party.friendlyFire() ? Component.literal(" ⚔").withStyle(ChatFormatting.RED) : Component.empty())));
        }
        source.sendSuccess(() -> text, false);
        return parties.parties().size();
    }

    private static int bosses(CommandSourceStack source) {
        MutableComponent text = Component.translatable("message.hardwrought.server.bosses", Encounters.all().size())
                .withStyle(ChatFormatting.GOLD);
        for (Encounter fight : Encounters.all()) {
            LivingEntity boss = Encounters.boss(fight);
            var pos = boss.blockPosition();
            text.append(line(boss.getDisplayName().copy().withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(String.format(Locale.ROOT, "  %.0f/%.0f ❤  ", boss.getHealth(), boss.getMaxHealth()))
                            .withStyle(ChatFormatting.RED))
                    .append(Component.translatable("message.hardwrought.server.fighters", fight.active().size(),
                            String.format(Locale.ROOT, "%.2f", fight.scale())).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format(Locale.ROOT, "  %s %d %d %d",
                            boss.level().dimension().identifier().getPath(), pos.getX(), pos.getY(), pos.getZ())).withStyle(ChatFormatting.DARK_GRAY))));
        }
        source.sendSuccess(() -> text, false);
        return Encounters.all().size();
    }

    private static Component line(Component content) {
        return Component.literal("\n ").append(content);
    }
}
