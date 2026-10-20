package de.ipnats.hardwrought.party;

import de.ipnats.hardwrought.core.networking.PartySnapshotPayload;
import de.ipnats.hardwrought.core.simulation.SimulationScheduler;
import de.ipnats.hardwrought.core.simulation.SimulationTier;
import de.ipnats.hardwrought.knowledge.KnowledgeSystem;
import de.ipnats.hardwrought.server.Afk;
import de.ipnats.hardwrought.server.ServerConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Parties: players who play together. What being in one means:
 *
 * <ul>
 *   <li><b>Shared knowledge</b> — what one member learns, every member learns, online or not, and
 *   joining lays everyone's knowledge together (see {@link KnowledgeSystem#pool}). Specification
 *   milestone 18 calls it shared research: one player studying ores while another builds the forge
 *   is a division of labour, not twice the reading.</li>
 *   <li><b>Friends</b> — their spells meant for friends reach each other, and unless the leader
 *   allows it they cannot hurt one another.</li>
 *   <li><b>Word of each other</b> — a list at the side of the screen with everyone's health and
 *   distance, a channel of their own, where a member died, and who has come online.</li>
 * </ul>
 *
 * <p>The parties are saved with the world ({@link PartySaveData}); invitations are not, and run out
 * after {@link #INVITE_TICKS}.
 */
public final class PartySystem {
    /** How long an invitation stands, in ticks: a minute. */
    public static final int INVITE_TICKS = 1200;

    /** What a party command came to; each has a message of its own. */
    public enum Result {
        OK, NOT_IN_PARTY, ALREADY_IN_PARTY, NOT_LEADER, SELF, TARGET_IN_PARTY, FULL, NO_INVITE, NOT_MEMBER, ALREADY_INVITED
    }

    private record Invite(UUID party, UUID inviter, long expires) { }

    private final MinecraftServer server;
    private final PartySaveData data;
    private final KnowledgeSystem knowledge;
    /** Open invitations, by the invited player, newest last. */
    private final Map<UUID, List<Invite>> invites = new HashMap<>();
    /** Who was sent a party list at the last pass, so a player who left is sent an empty one once. */
    private final Set<UUID> listed = new HashSet<>();

    public PartySystem(MinecraftServer server, KnowledgeSystem knowledge, SimulationScheduler scheduler) {
        this.server = server;
        this.knowledge = knowledge;
        data = server.getDataStorage().computeIfAbsent(PartySaveData.TYPE);
        knowledge.setCircle(this::learners);
        scheduler.register("hardwrought:party_sync", SimulationTier.MEDIUM, this::sync);
    }

    // ---------------------------------------------------------------- questions

    /** The party this player is in, or null. */
    public Party party(UUID player) {
        return data.of(player);
    }

    /** Whether two players are in the same party; a player is always together with themselves. */
    public boolean together(UUID a, UUID b) {
        if (a.equals(b)) return true;
        Party party = data.of(a);
        return party != null && party.contains(b);
    }

    /** Whether the party these two share forbids them to hurt each other. False when they share none. */
    public boolean shielded(UUID a, UUID b) {
        if (a.equals(b)) return false;
        Party party = data.of(a);
        return party != null && party.contains(b) && !party.friendlyFire();
    }

    /** Everyone this player is with: their party, or only themselves. */
    public Collection<UUID> circle(UUID player) {
        Party party = data.of(player);
        return party == null ? List.of(player) : party.members();
    }

    /** Who learns along with this player: their circle, while the server shares knowledge. */
    private Collection<UUID> learners(UUID player) {
        return ServerConfig.get().shareKnowledge() ? circle(player) : List.of(player);
    }

    public Collection<Party> parties() {
        return List.copyOf(data.all());
    }

    // ---------------------------------------------------------------- what the commands do

    /** Starts a party with this player leading it. */
    public Result create(ServerPlayer leader) {
        if (data.of(leader.getUUID()) != null) return Result.ALREADY_IN_PARTY;
        data.create(leader.getUUID());
        return Result.OK;
    }

    /**
     * Invites someone into the inviter's party — starting one if the inviter has none, since asking
     * someone to play together is how a party begins. Only the leader invites.
     */
    public Result invite(ServerPlayer from, ServerPlayer to) {
        if (from.getUUID().equals(to.getUUID())) return Result.SELF;
        Party party = data.of(from.getUUID());
        if (party != null && !party.isLeader(from.getUUID())) return Result.NOT_LEADER;
        if (data.of(to.getUUID()) != null) return Result.TARGET_IN_PARTY;
        if (party != null && party.size() >= ServerConfig.get().partyMaxSize()) return Result.FULL;
        if (party == null) party = data.create(from.getUUID());
        List<Invite> open = invites.computeIfAbsent(to.getUUID(), id -> new ArrayList<>());
        purge(open);
        UUID partyId = party.id();
        if (open.stream().anyMatch(invite -> invite.party().equals(partyId))) return Result.ALREADY_INVITED;
        open.add(new Invite(partyId, from.getUUID(), server.getTickCount() + INVITE_TICKS));
        to.sendSystemMessage(Component.translatable("message.hardwrought.party.invited", from.getDisplayName())
                .withStyle(ChatFormatting.GOLD)
                .append(" ").append(button("message.hardwrought.party.button.accept", "/party accept " + from.getGameProfile().name(), ChatFormatting.GREEN))
                .append(" ").append(button("message.hardwrought.party.button.decline", "/party decline " + from.getGameProfile().name(), ChatFormatting.RED)));
        return Result.OK;
    }

    /** Accepts an invitation: from this player, or the newest one when {@code inviter} is null. */
    public Result accept(ServerPlayer player, UUID inviter) {
        if (data.of(player.getUUID()) != null) return Result.ALREADY_IN_PARTY;
        Invite invite = take(player.getUUID(), inviter);
        if (invite == null) return Result.NO_INVITE;
        Party party = data.all().stream().filter(p -> p.id().equals(invite.party())).findFirst().orElse(null);
        if (party == null) return Result.NO_INVITE;
        if (party.size() >= ServerConfig.get().partyMaxSize()) return Result.FULL;
        data.join(party, player.getUUID());
        invites.remove(player.getUUID());
        tell(party, player.getUUID(), Component.translatable("message.hardwrought.party.joined", player.getDisplayName())
                .withStyle(ChatFormatting.GOLD));
        if (ServerConfig.get().shareKnowledge()) {
            Map<UUID, Integer> gained = knowledge.pool(party.members());
            for (UUID member : party.members()) {
                int count = gained.getOrDefault(member, 0);
                ServerPlayer online = server.getPlayerList().getPlayer(member);
                if (online != null && count > 0) {
                    online.sendSystemMessage(Component.translatable("message.hardwrought.party.knowledge_pooled", count)
                            .withStyle(ChatFormatting.AQUA));
                }
            }
        }
        return Result.OK;
    }

    /** Turns down an invitation: from this player, or the newest one. */
    public Result decline(ServerPlayer player, UUID inviter) {
        Invite invite = take(player.getUUID(), inviter);
        if (invite == null) return Result.NO_INVITE;
        ServerPlayer from = server.getPlayerList().getPlayer(invite.inviter());
        if (from != null) {
            from.sendSystemMessage(Component.translatable("message.hardwrought.party.declined", player.getDisplayName())
                    .withStyle(ChatFormatting.GRAY));
        }
        return Result.OK;
    }

    /** Leaves the party. What was learned in it stays learned. */
    public Result leave(ServerPlayer player) {
        Party party = data.of(player.getUUID());
        if (party == null) return Result.NOT_IN_PARTY;
        boolean led = party.isLeader(player.getUUID());
        data.leave(player.getUUID());
        tell(party, null, Component.translatable("message.hardwrought.party.left", player.getDisplayName())
                .withStyle(ChatFormatting.GRAY));
        // The one who left led it: whoever leads it now is told so with the rest.
        if (led && party.size() > 0) {
            tell(party, null, Component.translatable("message.hardwrought.party.leader", name(party.leader())).withStyle(ChatFormatting.GOLD));
        }
        return Result.OK;
    }

    /** The leader sends a member away. */
    public Result kick(ServerPlayer leader, UUID member) {
        Party party = data.of(leader.getUUID());
        if (party == null) return Result.NOT_IN_PARTY;
        if (!party.isLeader(leader.getUUID())) return Result.NOT_LEADER;
        if (member.equals(leader.getUUID())) return Result.SELF;
        if (!party.contains(member)) return Result.NOT_MEMBER;
        data.leave(member);
        Component name = name(member);
        tell(party, null, Component.translatable("message.hardwrought.party.kicked", name).withStyle(ChatFormatting.GRAY));
        ServerPlayer gone = server.getPlayerList().getPlayer(member);
        if (gone != null) gone.sendSystemMessage(Component.translatable("message.hardwrought.party.you_were_kicked").withStyle(ChatFormatting.RED));
        return Result.OK;
    }

    /** The leader hands the party to another member. */
    public Result promote(ServerPlayer leader, UUID member) {
        Party party = data.of(leader.getUUID());
        if (party == null) return Result.NOT_IN_PARTY;
        if (!party.isLeader(leader.getUUID())) return Result.NOT_LEADER;
        if (member.equals(leader.getUUID())) return Result.SELF;
        if (!party.contains(member)) return Result.NOT_MEMBER;
        party.promote(member);
        data.changed();
        tell(party, null, Component.translatable("message.hardwrought.party.leader", name(member)).withStyle(ChatFormatting.GOLD));
        return Result.OK;
    }

    /** The leader allows or forbids members to hurt each other. */
    public Result friendlyFire(ServerPlayer leader, boolean allowed) {
        Party party = data.of(leader.getUUID());
        if (party == null) return Result.NOT_IN_PARTY;
        if (!party.isLeader(leader.getUUID())) return Result.NOT_LEADER;
        party.setFriendlyFire(allowed);
        data.changed();
        tell(party, null, Component.translatable(allowed ? "message.hardwrought.party.friendly_fire_on"
                : "message.hardwrought.party.friendly_fire_off").withStyle(ChatFormatting.YELLOW));
        return Result.OK;
    }

    /** A line to the party alone. */
    public Result chat(ServerPlayer from, String message) {
        Party party = data.of(from.getUUID());
        if (party == null) return Result.NOT_IN_PARTY;
        tell(party, null, Component.translatable("message.hardwrought.party.chat", from.getDisplayName(), message)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        return Result.OK;
    }

    /** Tells the party where this player stands, clickable for anyone who wants to mark it. */
    public Result ping(ServerPlayer from) {
        Party party = data.of(from.getUUID());
        if (party == null) return Result.NOT_IN_PARTY;
        tell(party, null, Component.translatable("message.hardwrought.party.ping", from.getDisplayName(), where(from))
                .withStyle(ChatFormatting.AQUA));
        return Result.OK;
    }

    // ---------------------------------------------------------------- what happens to members

    /** A member died: everyone with them is told where, and so are they, before the screen goes red. */
    public void died(ServerPlayer player) {
        if (!ServerConfig.get().deathCoordinates()) return;
        Component at = where(player);
        player.sendSystemMessage(Component.translatable("message.hardwrought.death_at", at).withStyle(ChatFormatting.GRAY));
        Party party = data.of(player.getUUID());
        if (party != null) {
            tell(party, player.getUUID(), Component.translatable("message.hardwrought.party.died", player.getDisplayName(), at)
                    .withStyle(ChatFormatting.RED));
        }
    }

    public void joined(ServerPlayer player) {
        Party party = data.of(player.getUUID());
        if (party != null) {
            tell(party, player.getUUID(), Component.translatable("message.hardwrought.party.online", player.getDisplayName())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    public void disconnected(ServerPlayer player) {
        invites.remove(player.getUUID());
        listed.remove(player.getUUID());
        Party party = data.of(player.getUUID());
        if (party != null) {
            tell(party, player.getUUID(), Component.translatable("message.hardwrought.party.offline", player.getDisplayName())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // ---------------------------------------------------------------- the list at the side of the screen

    /** Once a second: every member online is told how the others are doing. */
    private void sync() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, PartySnapshotPayload.TYPE)) continue;
            Party party = data.of(player.getUUID());
            if (party == null) {
                if (listed.remove(player.getUUID())) ServerPlayNetworking.send(player, new PartySnapshotPayload(List.of()));
                continue;
            }
            listed.add(player.getUUID());
            ServerPlayNetworking.send(player, snapshot(player, party));
        }
    }

    /** What one member is shown of the others. Visible for tests. */
    public PartySnapshotPayload snapshot(ServerPlayer viewer, Party party) {
        List<PartySnapshotPayload.Member> members = new ArrayList<>();
        for (UUID id : party.members()) {
            if (id.equals(viewer.getUUID())) continue;
            if (members.size() >= PartySnapshotPayload.MAX_MEMBERS) break;
            ServerPlayer other = server.getPlayerList().getPlayer(id);
            String name = plainName(id);
            if (other == null) {
                members.add(new PartySnapshotPayload.Member(id, name, party.isLeader(id), false, false, 0, 0, -1));
                continue;
            }
            int distance = other.level() == viewer.level() ? (int) Math.round(other.distanceTo(viewer)) : -1;
            members.add(new PartySnapshotPayload.Member(id, name, party.isLeader(id), true, Afk.shown(id),
                    Math.max(0, other.getHealth()), Math.max(1, other.getMaxHealth()), distance));
        }
        return new PartySnapshotPayload(members);
    }

    // ---------------------------------------------------------------- helpers

    /** Sends a line to every member online, but the one left out. */
    private void tell(Party party, UUID except, Component message) {
        for (UUID member : party.members()) {
            if (member.equals(except)) continue;
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) online.sendSystemMessage(message);
        }
    }

    /** Takes an invitation off the list: from this inviter, or the newest. Null when there is none. */
    private Invite take(UUID player, UUID inviter) {
        List<Invite> open = invites.get(player);
        if (open == null) return null;
        purge(open);
        Invite found = null;
        for (int i = open.size() - 1; i >= 0; i--) {
            if (inviter == null || open.get(i).inviter().equals(inviter)) {
                found = open.remove(i);
                break;
            }
        }
        if (open.isEmpty()) invites.remove(player);
        return found;
    }

    private void purge(List<Invite> open) {
        long now = server.getTickCount();
        open.removeIf(invite -> invite.expires() < now || data.all().stream().noneMatch(party -> party.id().equals(invite.party())));
    }

    /** A member's name: as they are now if online, as the server last knew them if not. */
    public Component name(UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        return online != null ? online.getDisplayName() : Component.literal(plainName(player));
    }

    private String plainName(UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        String name = online != null ? online.getGameProfile().name()
                : server.services().nameToIdCache().get(player).map(net.minecraft.server.players.NameAndId::name)
                .orElse(player.toString().substring(0, 8));
        return name.length() > PartySnapshotPayload.MAX_NAME ? name.substring(0, PartySnapshotPayload.MAX_NAME) : name;
    }

    /** Where a player is, as a line of chat: the place, and the world it is in. */
    private static Component where(ServerPlayer player) {
        var pos = player.blockPosition();
        String dimension = player.level().dimension().identifier().getPath();
        return Component.translatable("message.hardwrought.position", pos.getX(), pos.getY(), pos.getZ(),
                Component.translatableWithFallback("dimension.hardwrought." + dimension, dimension).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.WHITE);
    }

    private static MutableComponent button(String key, String command, ChatFormatting color) {
        return Component.literal("[").append(Component.translatable(key)).append("]").withStyle(style -> style.withColor(color)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
    }
}
