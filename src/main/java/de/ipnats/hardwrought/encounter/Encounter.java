package de.ipnats.hardwrought.encounter;

import de.ipnats.hardwrought.server.Afk;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One boss fight, and who is really in it (specification §§ 97 and 100). Three kinds of player near a
 * boss are told apart: those online, those nearby, and those taking part — and only the last kind
 * changes the fight. A player takes part by hitting the boss, by being hit by it, by fighting what
 * it brought, or by moving about in its arena; not by standing at the edge of it away from the
 * keyboard.
 *
 * <p>The number of fighters the boss answers to is not the head count of the moment. It climbs
 * slowly as people join ({@link #JOIN_TICKS} per fighter) and falls slowly, after a grace, as they
 * leave ({@link #LEAVE_GRACE}, then {@link #LEAVE_TICKS} per fighter): a fight cannot be made easier
 * by stepping out of it as the boss turns, nor harder for a friend who arrives at the last moment.
 *
 * <p>What a boss does with the number is its own business, and never more health: more of what it
 * brings, more of its attention spread over more people, and mechanics that only a group can answer
 * ({@link Hazard}). Nobody left fighting for {@code resetTicks}, and it starts over at full strength
 * (§ 101).
 */
public final class Encounter {
    /** How long one act keeps a player counted, in ticks: half a minute. */
    public static final int ACTIVE_TICKS = 600;
    /** How long it takes for one more fighter to be felt in full, in ticks. */
    public static final int JOIN_TICKS = 400;
    /** How long fewer fighters are ignored before the count starts falling, in ticks. */
    public static final int LEAVE_GRACE = 1200;
    /** How long, after the grace, it takes the count to fall by one, in ticks. */
    public static final int LEAVE_TICKS = 600;
    /** How far a player has to move in a second for moving about to count as taking part. */
    static final double MOVING = 1.5;

    final LivingEntity boss;
    /** The middle of its arena, which for a boss that flies is not where the boss is. */
    final java.util.function.Supplier<Vec3> center;
    /** How far from the boss's arena the fight reaches. */
    final double reach;
    /** Ticks without anyone fighting before the boss starts over; 0 for a boss that looks after that itself. */
    final int resetTicks;
    private final Map<UUID, Long> engaged = new HashMap<>();
    private final Map<UUID, Vec3> lastSeen = new HashMap<>();
    private final List<Hazard> hazards = new ArrayList<>();
    private final Map<String, Long> timers = new HashMap<>();
    /** The server tick of the last look. */
    private long clock;
    private List<ServerPlayer> active = List.of();
    private double scale = 1;
    private long fewerSince = -1, lastFought;
    private int told = 1;

    Encounter(LivingEntity boss, java.util.function.Supplier<Vec3> center, double reach, int resetTicks, long now) {
        this.boss = boss;
        this.center = center;
        this.reach = reach;
        this.resetTicks = resetTicks;
        this.lastFought = now;
    }

    // ---------------------------------------------------------------- who is in it

    /** This player has just done something in the fight. */
    public void engage(ServerPlayer player, long now) {
        if (!counts(player)) return;
        engaged.put(player.getUUID(), now);
        lastFought = now;
    }

    /** The players taking part, as of the last look. */
    public List<ServerPlayer> active() {
        return active;
    }

    /**
     * How many fighters the boss answers to: at least one, and following the people taking part only
     * slowly, either way.
     */
    public int fighters() {
        return Math.max(1, (int) Math.floor(scale + 1.0e-6));
    }

    /** The fighter count with its fraction: how far the next one has come. For the status command. */
    public double scale() {
        return scale;
    }

    /**
     * How hard the group mechanics press: the fighters, and one more in an Ultra world, where the
     * fight starts as if there were company. Mechanics that need two players still need two players.
     */
    public int pressure() {
        return fighters() + (de.ipnats.hardwrought.survival.Ultra.active() ? 1 : 0);
    }

    /** Whether the boss should bring what only a group can answer. */
    public boolean crowded() {
        return pressure() >= 2;
    }

    /** One of the fighters, at random, other than this one if there is another; null with nobody there. */
    public ServerPlayer another(RandomSource random, LivingEntity not) {
        List<ServerPlayer> others = new ArrayList<>(active);
        if (others.size() > 1 && not != null) others.removeIf(player -> player == not);
        return others.isEmpty() ? null : others.get(random.nextInt(others.size()));
    }

    /** A danger only a group can answer, set going now. */
    public void add(Hazard hazard) {
        hazards.add(hazard);
    }

    /** Whether one of this kind of danger is under way already. */
    public boolean running(Class<? extends Hazard> kind) {
        return hazards.stream().anyMatch(kind::isInstance);
    }

    /**
     * Whether it is time again for something the boss does every so often in company: true at most
     * once per {@code every} ticks, the first time {@code every} ticks after it was first asked.
     */
    public boolean ready(String what, int every) {
        Long last = timers.putIfAbsent(what, clock);
        if (last == null || clock - last < every) return false;
        timers.put(what, clock);
        return true;
    }

    /** For tests: makes this ready the next time it is asked about. */
    public void hurry(String what) {
        timers.put(what, Long.MIN_VALUE / 2);
    }

    /** For tests: forget the slow climb and set the count outright. */
    public void forceScale(double scale) {
        this.scale = Math.max(1, scale);
        fewerSince = -1;
    }

    // ---------------------------------------------------------------- upkeep

    /** Whether someone could count as fighting at all. */
    static boolean counts(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && !Afk.away(player);
    }

    /** Each tick: the dangers under way; once a second, who is in the fight and how many it answers to. */
    void tick(ServerLevel level, long now) {
        clock = now;
        // A hazard may set another going; those wait for the next tick.
        List<Hazard> running = List.copyOf(hazards);
        for (Hazard hazard : running) if (hazard.tick(level, this)) hazards.remove(hazard);
        if (now % 20 != 0) return;
        Vec3 middle = center.get();
        for (ServerPlayer player : level.players()) {
            if (!counts(player) || player.position().distanceToSqr(middle) > reach * reach) {
                lastSeen.remove(player.getUUID());
                continue;
            }
            Vec3 before = lastSeen.put(player.getUUID(), player.position());
            if (before != null && before.distanceTo(player.position()) > MOVING) engage(player, now);
        }
        List<ServerPlayer> fighting = new ArrayList<>();
        engaged.entrySet().removeIf(entry -> now - entry.getValue() > ACTIVE_TICKS);
        for (UUID id : engaged.keySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player == null || player.level() != level || !counts(player)) continue;
            if (player.position().distanceToSqr(middle) > reach * reach * 2.25) continue;
            fighting.add(player);
        }
        active = List.copyOf(fighting);
        follow(active.size(), 20);
        if (fighters() != told) {
            boolean more = fighters() > told;
            told = fighters();
            for (ServerPlayer player : active) {
                player.sendOverlayMessage(Component.translatable(more ? "message.hardwrought.encounter.grows"
                        : "message.hardwrought.encounter.shrinks", told).withStyle(more ? ChatFormatting.RED : ChatFormatting.GRAY));
            }
        }
    }

    /** Moves the count toward the number of people taking part, by {@code ticks} worth. Public for tests. */
    public void follow(int people, int ticks) {
        double target = Math.max(1, people);
        if (target > scale) {
            fewerSince = -1;
            scale = Math.min(target, scale + ticks / (double) JOIN_TICKS);
        } else if (target < scale) {
            if (fewerSince < 0) fewerSince = 0;
            fewerSince += ticks;
            if (fewerSince > LEAVE_GRACE) scale = Math.max(target, scale - ticks / (double) LEAVE_TICKS);
        } else {
            fewerSince = -1;
        }
    }

    /** Nobody has fought for long enough that the boss starts over. */
    boolean abandoned(long now) {
        return resetTicks > 0 && active.isEmpty() && now - lastFought > resetTicks;
    }

    /** Starts the fight over: full health, nobody counted, no danger under way. */
    void reset(long now) {
        boss.setHealth(boss.getMaxHealth());
        engaged.clear();
        lastSeen.clear();
        hazards.clear();
        timers.clear();
        active = List.of();
        scale = 1;
        told = 1;
        fewerSince = -1;
        lastFought = now;
    }
}
