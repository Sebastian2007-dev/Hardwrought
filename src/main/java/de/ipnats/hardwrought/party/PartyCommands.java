package de.ipnats.hardwrought.party;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.server.Afk;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /party} for everyone, no operator rights needed:
 *
 * <pre>
 * /party                      who is in it, and how they are
 * /party create
 * /party invite &lt;player&gt;       starts a party if there is none yet
 * /party accept|decline [player]
 * /party leave
 * /party kick|promote &lt;member&gt;  leader only; works on members who are offline
 * /party friendlyfire &lt;true|false&gt;
 * /party chat &lt;message&gt;        also /pc &lt;message&gt;
 * /party ping                 tells the party where you stand
 * /afk
 * </pre>
 */
final class PartyCommands {
    private PartyCommands() { }

    /** Members of the asking player's party, by name: for kicking and promoting. */
    private static final SuggestionProvider<CommandSourceStack> MEMBERS = (context, builder) -> {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return builder.buildFuture();
        PartySystem parties = CoreLifecycle.require(context.getSource().getServer()).party();
        Party party = parties.party(player.getUUID());
        if (party == null) return builder.buildFuture();
        return SharedSuggestionProvider.suggest(party.members().stream().filter(id -> !id.equals(player.getUUID()))
                .map(id -> parties.name(id).getString()), builder);
    };

    static void initialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
            dispatcher.register(literal("party")
                    .executes(context -> list(context.getSource()))
                    .then(literal("create").executes(context -> report(context, system(context).create(player(context)))))
                    .then(literal("invite").then(argument("player", EntityArgument.player()).executes(context ->
                            report(context, system(context).invite(player(context), EntityArgument.getPlayer(context, "player")),
                                    EntityArgument.getPlayer(context, "player").getDisplayName()))))
                    .then(literal("accept")
                            .executes(context -> report(context, system(context).accept(player(context), null)))
                            .then(argument("player", StringArgumentType.word()).executes(context ->
                                    report(context, system(context).accept(player(context), byName(context))))))
                    .then(literal("decline")
                            .executes(context -> report(context, system(context).decline(player(context), null)))
                            .then(argument("player", StringArgumentType.word()).executes(context ->
                                    report(context, system(context).decline(player(context), byName(context))))))
                    .then(literal("leave").executes(context -> report(context, system(context).leave(player(context)))))
                    .then(literal("kick").then(argument("player", StringArgumentType.word()).suggests(MEMBERS).executes(context ->
                            report(context, system(context).kick(player(context), member(context))))))
                    .then(literal("promote").then(argument("player", StringArgumentType.word()).suggests(MEMBERS).executes(context ->
                            report(context, system(context).promote(player(context), member(context))))))
                    .then(literal("friendlyfire").then(argument("allowed", BoolArgumentType.bool()).executes(context ->
                            report(context, system(context).friendlyFire(player(context), BoolArgumentType.getBool(context, "allowed"))))))
                    .then(literal("chat").then(argument("message", StringArgumentType.greedyString()).executes(context ->
                            report(context, system(context).chat(player(context), StringArgumentType.getString(context, "message"))))))
                    .then(literal("ping").executes(context -> report(context, system(context).ping(player(context))))));
            dispatcher.register(literal("pc").then(argument("message", StringArgumentType.greedyString()).executes(context ->
                    report(context, system(context).chat(player(context), StringArgumentType.getString(context, "message"))))));
            dispatcher.register(literal("afk").executes(context -> {
                Afk.declare(player(context));
                return 1;
            }));
        });
    }

    private static PartySystem system(CommandContext<CommandSourceStack> context) {
        return CoreLifecycle.require(context.getSource().getServer()).party();
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }

    /** The player named in the command, online or as the server last knew them; a stranger is no one. */
    private static UUID byName(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "player");
        var server = context.getSource().getServer();
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();
        return server.services().nameToIdCache().get(name).map(net.minecraft.server.players.NameAndId::id)
                .orElse(new UUID(0, 0));
    }

    /** A member of the asker's party by name, whether online or not. */
    private static UUID member(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "player");
        PartySystem parties = system(context);
        Party party = parties.party(player(context).getUUID());
        if (party != null) {
            for (UUID id : party.members()) if (parties.name(id).getString().equalsIgnoreCase(name)) return id;
        }
        return byName(context);
    }

    private static int report(CommandContext<CommandSourceStack> context, PartySystem.Result result) {
        return report(context, result, Component.empty());
    }

    private static int report(CommandContext<CommandSourceStack> context, PartySystem.Result result, Component subject) {
        CommandSourceStack source = context.getSource();
        String key = "message.hardwrought.party.result." + result.name().toLowerCase(java.util.Locale.ROOT);
        String command = context.getNodes().size() > 1 ? context.getNodes().get(1).getNode().getName() : "";
        if (result == PartySystem.Result.OK) {
            // Most commands are answered by the message the party is sent; these are not.
            if (command.equals("create") || command.equals("invite")) {
                source.sendSuccess(() -> Component.translatable("message.hardwrought.party.ok." + command, subject)
                        .withStyle(ChatFormatting.GOLD), false);
            }
            if (command.equals("leave") || command.equals("decline")) {
                source.sendSuccess(() -> Component.translatable("message.hardwrought.party.ok." + command)
                        .withStyle(ChatFormatting.GRAY), false);
            }
            return 1;
        }
        source.sendFailure(Component.translatable(key, subject));
        return 0;
    }

    /** {@code /party}: everyone in it, with how they are; or how to start one. */
    private static int list(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PartySystem parties = CoreLifecycle.require(source.getServer()).party();
        Party party = parties.party(player.getUUID());
        if (party == null) {
            source.sendSuccess(() -> Component.translatable("message.hardwrought.party.none").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        MutableComponent text = Component.translatable("message.hardwrought.party.header", party.size(),
                de.ipnats.hardwrought.server.ServerConfig.get().partyMaxSize()).withStyle(ChatFormatting.GOLD);
        List<UUID> members = List.copyOf(party.members());
        for (UUID id : members) {
            ServerPlayer online = source.getServer().getPlayerList().getPlayer(id);
            MutableComponent line = Component.literal("\n ").append(party.isLeader(id) ? Component.literal("★ ").withStyle(ChatFormatting.YELLOW)
                    : Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY)).append(parties.name(id).copy().withStyle(ChatFormatting.WHITE));
            if (online == null) {
                line.append(Component.translatable("message.hardwrought.party.member_offline").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                line.append(Component.literal(String.format(java.util.Locale.ROOT, "  %.0f/%.0f ❤", online.getHealth(), online.getMaxHealth()))
                        .withStyle(ChatFormatting.RED));
                if (Afk.shown(id)) line.append(Component.literal(" [AFK]").withStyle(ChatFormatting.DARK_GRAY));
            }
            text.append(line);
        }
        text.append(Component.literal("\n ").append(Component.translatable(party.friendlyFire()
                ? "message.hardwrought.party.friendly_fire_on" : "message.hardwrought.party.friendly_fire_off")).withStyle(ChatFormatting.GRAY));
        source.sendSuccess(() -> text, false);
        return party.size();
    }
}
