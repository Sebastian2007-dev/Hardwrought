package de.ipnats.hardwrought.haven;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where arc floodlights burn, per level: nothing monstrous spawns within {@link #SPAWN_REACH} of a lit
 * one, and monsters within {@link #AVOID_REACH} keep out of its light (see
 * {@code mobs.AvoidFloodlightGoal}). A floodlight says it is lit every second it is; one not heard from
 * for {@link #STALE_TICKS} is taken to be dark — broken, unpowered or gone.
 */
public final class Floodlights {
    public static final double SPAWN_REACH = 32.0;
    public static final double AVOID_REACH = 24.0;
    private static final long STALE_TICKS = 60;

    private static final Map<Level, Long2LongOpenHashMap> LIT = new WeakHashMap<>();

    private Floodlights() { }

    /** A floodlight at this position is burning now. */
    public static void lit(ServerLevel level, BlockPos pos) {
        LIT.computeIfAbsent(level, l -> new Long2LongOpenHashMap()).put(pos.asLong(), level.getGameTime());
    }

    /** A floodlight at this position went dark. */
    public static void dark(Level level, BlockPos pos) {
        Long2LongOpenHashMap lit = LIT.get(level);
        if (lit != null) lit.remove(pos.asLong());
    }

    /** The nearest lit floodlight within this reach of a point, or null. */
    public static BlockPos near(Level level, Vec3 at, double reach) {
        Long2LongOpenHashMap lit = LIT.get(level);
        if (lit == null || lit.isEmpty()) return null;
        long now = level.getGameTime();
        BlockPos best = null;
        double nearest = reach * reach;
        var entries = lit.long2LongEntrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            if (now - entry.getLongValue() > STALE_TICKS) {
                entries.remove();
                continue;
            }
            BlockPos pos = BlockPos.of(entry.getLongKey());
            double distance = Vec3.atCenterOf(pos).distanceToSqr(at);
            if (distance <= nearest) {
                nearest = distance;
                best = pos;
            }
        }
        return best;
    }

    /** Whether a monster may spawn here, as far as floodlights go. */
    public static boolean spawnBarred(Level level, BlockPos pos) {
        return near(level, Vec3.atCenterOf(pos), SPAWN_REACH) != null;
    }
}
