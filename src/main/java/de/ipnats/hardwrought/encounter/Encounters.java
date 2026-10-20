package de.ipnats.hardwrought.encounter;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The boss fights going on, one {@link Encounter} each. A boss asks for its own from its tick; this
 * keeps them going, notices who takes part through the blows struck in and around them, starts a
 * deserted fight over, and lets them go once their boss is gone.
 */
public final class Encounters {
    private static final Map<UUID, Encounter> FIGHTS = new HashMap<>();

    private Encounters() { }

    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(Encounters::tick);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
            if (FIGHTS.isEmpty() || !(victim.level() instanceof ServerLevel level)) return;
            long now = level.getServer().getTickCount();
            ServerPlayer striker = player(source.getEntity());
            Encounter fight = FIGHTS.get(victim.getUUID());
            if (fight != null) {
                // Striking the boss.
                if (striker != null) fight.engage(striker, now);
                return;
            }
            if (victim instanceof ServerPlayer player && source.getEntity() != null) {
                // Being struck by the boss, by its spells, or by what it brought into the fight.
                Encounter by = FIGHTS.get(source.getEntity().getUUID());
                if (by == null && source.getEntity() instanceof Enemy) by = around(level, source.getEntity().position());
                if (by != null) by.engage(player, now);
                return;
            }
            // Fighting what it brought.
            if (striker != null && victim instanceof Enemy) {
                Encounter near = around(level, victim.position());
                if (near != null) near.engage(striker, now);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> FIGHTS.clear());
    }

    /**
     * This boss's fight, begun now if it was not going yet.
     *
     * @param center     the middle of its arena
     * @param reach      how far from that the fight goes
     * @param resetTicks how long without fighters before it starts over at full health; 0 for a boss
     *                   that sees to that itself
     */
    public static Encounter of(LivingEntity boss, Supplier<Vec3> center, double reach, int resetTicks) {
        Encounter fight = FIGHTS.get(boss.getUUID());
        if (fight == null || fight.boss != boss) {
            MinecraftServer server = boss.level().getServer();
            fight = new Encounter(boss, center, reach, resetTicks, server == null ? 0 : server.getTickCount());
            FIGHTS.put(boss.getUUID(), fight);
        }
        return fight;
    }

    /** This boss's fight, or null if it has none going. */
    public static Encounter find(LivingEntity boss) {
        return FIGHTS.get(boss.getUUID());
    }

    /** Every fight going on, for the server status. */
    public static List<Encounter> all() {
        return List.copyOf(FIGHTS.values());
    }

    public static LivingEntity boss(Encounter fight) {
        return fight.boss;
    }

    private static void tick(MinecraftServer server) {
        if (FIGHTS.isEmpty()) return;
        long now = server.getTickCount();
        for (Iterator<Encounter> it = new ArrayList<>(FIGHTS.values()).iterator(); it.hasNext(); ) {
            Encounter fight = it.next();
            if (fight.boss.isRemoved() || !fight.boss.isAlive() || !(fight.boss.level() instanceof ServerLevel level)) {
                FIGHTS.remove(fight.boss.getUUID());
                continue;
            }
            fight.tick(level, now);
            if (fight.abandoned(now)) fight.reset(now);
        }
    }

    /** The fight whose arena this place lies in, if any. */
    private static Encounter around(ServerLevel level, Vec3 at) {
        for (Encounter fight : FIGHTS.values()) {
            if (fight.boss.level() != level) continue;
            if (fight.center.get().distanceToSqr(at) <= fight.reach * fight.reach) return fight;
        }
        return null;
    }

    /** The player behind a blow: the striker, the archer, the caster, the master of a wolf. */
    private static ServerPlayer player(Entity entity) {
        if (entity instanceof ServerPlayer player) return player;
        if (entity instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) return player;
        if (entity instanceof OwnableEntity owned && owned.getOwner() instanceof ServerPlayer player) return player;
        return null;
    }
}
