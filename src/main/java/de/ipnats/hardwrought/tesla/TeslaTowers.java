package de.ipnats.hardwrought.tesla;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where Tesla towers stand that are up and charged, per level, so that what they power without wire can
 * find one and draw on it. A tower says it stands every second it works; one not heard from for
 * {@link #STALE_TICKS} is taken to be down.
 */
public final class TeslaTowers {
    private static final long STALE_TICKS = 60;
    private static final Map<Level, Map<Long, TeslaTowerBlockEntity>> UP = new WeakHashMap<>();
    private static final Map<Level, Map<Long, Long>> HEARD = new WeakHashMap<>();

    private TeslaTowers() { }

    static void up(Level level, TeslaTowerBlockEntity tower) {
        UP.computeIfAbsent(level, l -> new HashMap<>()).put(tower.getBlockPos().asLong(), tower);
        HEARD.computeIfAbsent(level, l -> new HashMap<>()).put(tower.getBlockPos().asLong(), level.getGameTime());
    }

    static void down(Level level, BlockPos pos) {
        Map<Long, TeslaTowerBlockEntity> up = UP.get(level);
        if (up != null) up.remove(pos.asLong());
    }

    /** The nearest working tower that reaches this point, or null. */
    public static TeslaTowerBlockEntity near(Level level, Vec3 at) {
        Map<Long, TeslaTowerBlockEntity> up = UP.get(level);
        Map<Long, Long> heard = HEARD.get(level);
        if (up == null || up.isEmpty()) return null;
        long now = level.getGameTime();
        TeslaTowerBlockEntity best = null;
        double nearest = TeslaTowerBlockEntity.REACH * TeslaTowerBlockEntity.REACH;
        for (var entry : up.entrySet()) {
            Long when = heard.get(entry.getKey());
            TeslaTowerBlockEntity tower = entry.getValue();
            if (when == null || now - when > STALE_TICKS || tower.isRemoved()) continue;
            double distance = Vec3.atCenterOf(tower.getBlockPos()).distanceToSqr(at);
            if (distance <= nearest) {
                nearest = distance;
                best = tower;
            }
        }
        return best;
    }

    /**
     * Something within a tower's reach draws this many watt-seconds from it without wire. True where a
     * tower had them to give.
     */
    public static boolean draw(Level level, BlockPos pos, double joules) {
        TeslaTowerBlockEntity tower = near(level, Vec3.atCenterOf(pos));
        return tower != null && tower.give(joules);
    }
}
