package de.ipnats.hardwrought.test;

import de.ipnats.hardwrought.core.CoreRuntime;
import de.ipnats.hardwrought.core.events.CoreLifecycle;
import de.ipnats.hardwrought.knowledge.KnowledgeLevel;
import de.ipnats.hardwrought.knowledge.KnowledgeSystem;
import de.ipnats.hardwrought.party.PartySystem;
import de.ipnats.hardwrought.server.Afk;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Parties: shared knowledge, no harm between members, the list of the others; and players away from the keyboard. */
public final class PartyGameTests {
    private static ServerPlayer survivor(GameTestHelper helper) {
        return TestPlayers.survivor(helper, 2, 2);
    }

    /** Puts {@code member} into {@code leader}'s party, the way the commands do. */
    private static void join(GameTestHelper helper, PartySystem parties, ServerPlayer leader, ServerPlayer member) {
        helper.assertTrue(parties.invite(leader, member) == PartySystem.Result.OK, "The leader invites");
        helper.assertTrue(parties.accept(member, leader.getUUID()) == PartySystem.Result.OK, "and the invitation is taken");
    }

    @GameTest
    public void partyMembersLearnTogether(GameTestHelper helper) {
        CoreRuntime runtime = CoreLifecycle.require(helper.getLevel().getServer());
        KnowledgeSystem knowledge = runtime.knowledge();
        PartySystem parties = runtime.party();
        ServerPlayer a = survivor(helper), b = survivor(helper), c = survivor(helper);
        for (ServerPlayer player : new ServerPlayer[] {a, b, c}) knowledge.forget(player.getUUID());

        // Knowing something before joining: it is pooled when they join.
        knowledge.study(c, Items.COPPER_INGOT);
        join(helper, parties, a, b);
        helper.assertTrue(parties.together(a.getUUID(), b.getUUID()), "Inviting started a party, and accepting joined it");

        knowledge.study(a, Items.IRON_INGOT);
        helper.assertTrue(knowledge.level(b, Items.IRON_INGOT) == KnowledgeLevel.STUDIED,
                "What one member studies, the other knows too");
        helper.assertTrue(knowledge.pendingNotes(b.getUUID()).stream().anyMatch(note -> note.id().getPath().equals("iron_ingot")),
                "and is shown it");
        knowledge.discover(b, Items.GOLD_INGOT);
        helper.assertTrue(knowledge.level(a, Items.GOLD_INGOT) == KnowledgeLevel.DISCOVERED, "It goes both ways, level for level");
        knowledge.slay(a, net.minecraft.world.entity.EntityTypes.ZOMBIE);
        helper.assertTrue(knowledge.knowledge(b).level(KnowledgeSystem.entityKey(net.minecraft.resources.Identifier.withDefaultNamespace("zombie")))
                == KnowledgeLevel.STUDIED, "Creatures as well");

        join(helper, parties, a, c);
        helper.assertTrue(knowledge.level(a, Items.COPPER_INGOT) == KnowledgeLevel.STUDIED
                        && knowledge.level(b, Items.COPPER_INGOT) == KnowledgeLevel.STUDIED,
                "A newcomer's knowledge is laid beside the party's");
        helper.assertTrue(knowledge.level(c, Items.IRON_INGOT) == KnowledgeLevel.STUDIED, "and the party's beside theirs");

        helper.assertTrue(parties.leave(c) == PartySystem.Result.OK, "Leaving");
        helper.assertTrue(knowledge.level(c, Items.IRON_INGOT) == KnowledgeLevel.STUDIED, "keeps what was learned");
        knowledge.study(a, Items.REDSTONE);
        helper.assertTrue(knowledge.level(c, Items.REDSTONE) == KnowledgeLevel.UNKNOWN, "but what is learned after is not shared");

        parties.leave(a);
        helper.assertTrue(parties.party(b.getUUID()) != null && parties.party(b.getUUID()).isLeader(b.getUUID()),
                "The leader gone, the one who remains leads");
        parties.leave(b);
        helper.assertTrue(parties.party(b.getUUID()) == null, "and the last one out ends it");
        for (ServerPlayer player : new ServerPlayer[] {a, b, c}) knowledge.forget(player.getUUID());
        helper.succeed();
    }

    @GameTest
    public void partyMembersCannotHurtEachOther(GameTestHelper helper) {
        var level = helper.getLevel();
        PartySystem parties = CoreLifecycle.require(level.getServer()).party();
        ServerPlayer a = survivor(helper), b = survivor(helper), stranger = survivor(helper);
        join(helper, parties, a, b);
        float health = b.getHealth();
        b.hurtServer(level, level.damageSources().playerAttack(a), 4);
        helper.assertTrue(b.getHealth() == health, "A blow from a party member does nothing");
        stranger.hurtServer(level, level.damageSources().playerAttack(a), 4);
        helper.assertTrue(stranger.getHealth() < stranger.getMaxHealth(), "a blow at a stranger does");

        helper.assertTrue(parties.friendlyFire(b, true) == PartySystem.Result.NOT_LEADER, "Only the leader allows friendly fire");
        helper.assertTrue(parties.friendlyFire(a, true) == PartySystem.Result.OK, "the leader can");
        b.hurtServer(level, level.damageSources().playerAttack(a), 4);
        helper.assertTrue(b.getHealth() < health, "and then members can hurt each other");

        var list = parties.snapshot(a, parties.party(a.getUUID()));
        helper.assertTrue(list.members().size() == 1 && list.members().getFirst().id().equals(b.getUUID()),
                "Each member is shown the others, not themselves: " + list.members());
        helper.assertTrue(parties.kick(b, a.getUUID()) == PartySystem.Result.NOT_LEADER, "A member cannot remove the leader");
        helper.assertTrue(parties.kick(a, b.getUUID()) == PartySystem.Result.OK, "The leader removes a member");
        parties.leave(a);
        helper.succeed();
    }

    @GameTest
    public void anInvitationIsNeeded(GameTestHelper helper) {
        PartySystem parties = CoreLifecycle.require(helper.getLevel().getServer()).party();
        ServerPlayer a = survivor(helper), b = survivor(helper), c = survivor(helper);
        helper.assertTrue(parties.accept(b, null) == PartySystem.Result.NO_INVITE, "Nobody joins uninvited");
        helper.assertTrue(parties.invite(a, a) == PartySystem.Result.SELF, "Nobody invites themselves");
        join(helper, parties, a, b);
        helper.assertTrue(parties.invite(b, c) == PartySystem.Result.NOT_LEADER, "Only the leader invites");
        helper.assertTrue(parties.invite(c, b) == PartySystem.Result.TARGET_IN_PARTY, "Someone in a party cannot be invited into another");
        helper.assertTrue(parties.invite(a, c) == PartySystem.Result.OK && parties.invite(a, c) == PartySystem.Result.ALREADY_INVITED,
                "One invitation at a time");
        helper.assertTrue(parties.decline(c, a.getUUID()) == PartySystem.Result.OK && parties.accept(c, null) == PartySystem.Result.NO_INVITE,
                "A declined invitation is gone");
        parties.leave(b);
        parties.leave(a);
        helper.succeed();
    }

    @GameTest
    public void someoneAwayDoesNotCount(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        helper.assertTrue(!Afk.away(player), "Someone who just arrived is there");
        Afk.declare(player);
        helper.assertTrue(Afk.away(player), "Saying so makes them away");
        helper.assertTrue(!Afk.present(helper.getLevel().getServer()).contains(player), "and they are not counted as present");
        helper.assertTrue(Afk.tabName(player, null).getString().startsWith("[AFK]"), "The player list marks them");
        Afk.forget(player.getUUID());
        helper.assertTrue(!Afk.away(player), "Forgotten, they are back");
        helper.succeed();
    }
}
