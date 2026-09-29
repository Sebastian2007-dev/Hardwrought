package de.ipnats.hardwrought.mobs;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The cornered animal. A mob that keeps its distance or backs off after a hit would be easy prey if it
 * only ever ran: chase it down, and it never fights back. So a fleeing mob panics and turns on its
 * pursuer
 *
 * <ul>
 *   <li>when it is hit while fleeing,</li>
 *   <li>when fleeing gets it nowhere — after a second on the run it is no farther from the pursuer —
 *   and</li>
 *   <li>when it has had to flee {@value #FLIGHTS_TO_PANIC} times within {@value #FLIGHT_WINDOW_TICKS}
 *   ticks.</li>
 * </ul>
 * For {@value #PANIC_TICKS} ticks after that it does not flee at all: an archer shoots at point blank,
 * a cave spider or a vex keeps biting and stabbing. Then it may flee again — and panic again.
 */
public final class Panic {
    static final int PANIC_TICKS = 100;
    static final int FLIGHTS_TO_PANIC = 3;
    static final int FLIGHT_WINDOW_TICKS = 200;
    /** How long a flight may run before it has to show it is getting anywhere. */
    static final int PROGRESS_CHECK_TICKS = 20;
    /** The distance a flight has to gain by then, in blocks. */
    static final double PROGRESS = 0.75;

    private record Flight(long started, double distance) { }

    private static final Map<UUID, Long> panicking = new ConcurrentHashMap<>();
    private static final Map<UUID, Flight> fleeing = new ConcurrentHashMap<>();
    private static final Map<UUID, Deque<Long>> flights = new ConcurrentHashMap<>();

    private Panic() { }

    public static boolean panicking(LivingEntity mob) {
        Long until = panicking.get(mob.getUUID());
        if (until == null) return false;
        if (until <= mob.level().getGameTime()) {
            panicking.remove(mob.getUUID());
            return false;
        }
        return true;
    }

    /** Whether the mob is on the run right now. */
    public static boolean fleeing(LivingEntity mob) {
        return fleeing.containsKey(mob.getUUID());
    }

    /**
     * A mob starts to flee from its target. Returns false, and puts it in a panic instead, where this
     * is one flight too many.
     */
    public static boolean startFlight(Mob mob) {
        if (panicking(mob)) return false;
        long now = mob.level().getGameTime();
        Deque<Long> recent = flights.computeIfAbsent(mob.getUUID(), ignored -> new ArrayDeque<>());
        while (!recent.isEmpty() && recent.peekFirst() < now - FLIGHT_WINDOW_TICKS) recent.pollFirst();
        recent.addLast(now);
        if (recent.size() >= FLIGHTS_TO_PANIC) {
            panic(mob);
            return false;
        }
        LivingEntity target = mob.getTarget();
        fleeing.put(mob.getUUID(), new Flight(now, target == null ? 0 : mob.distanceTo(target)));
        return true;
    }

    /**
     * One tick of a flight. Returns false, and puts the mob in a panic, once the flight has run long
     * enough to judge and has gained nothing.
     */
    public static boolean keepFleeing(Mob mob) {
        Flight flight = fleeing.get(mob.getUUID());
        LivingEntity target = mob.getTarget();
        if (flight == null || target == null) return true;
        if (mob.level().getGameTime() - flight.started() < PROGRESS_CHECK_TICKS) return true;
        if (mob.distanceTo(target) >= flight.distance() + PROGRESS) {
            // Getting away: judged again from here.
            fleeing.put(mob.getUUID(), new Flight(mob.level().getGameTime(), mob.distanceTo(target)));
            return true;
        }
        panic(mob);
        return false;
    }

    public static void endFlight(Mob mob) {
        fleeing.remove(mob.getUUID());
    }

    /** Hit while running: it turns on whoever hit it. */
    static void hurt(Mob mob) {
        if (fleeing(mob)) panic(mob);
    }

    static void panic(Mob mob) {
        fleeing.remove(mob.getUUID());
        flights.remove(mob.getUUID());
        panicking.put(mob.getUUID(), mob.level().getGameTime() + PANIC_TICKS);
        // Heard, not only seen: a mob that has stopped running calls out.
        mob.playAmbientSound();
    }
}
