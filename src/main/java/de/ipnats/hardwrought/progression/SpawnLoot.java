package de.ipnats.hardwrought.progression;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * No free equipment at the start. Chests, barrels and chest minecarts generated within
 * {@link #RADIUS} blocks of the world spawn come up empty, so a new player cannot shortcut the
 * early game by raiding the nearest village or shipwreck; farther out, loot is found as usual.
 */
public final class SpawnLoot {
    /** Blocks around the world spawn, measured across the ground, in which containers hold nothing. */
    public static final int RADIUS = 1000;

    private SpawnLoot() { }

    /** Whether a container generated at this position gives no loot. */
    public static boolean emptyHere(Level level, BlockPos pos) {
        if (level == null || level.isClientSide() || pos == null) return false;
        var spawn = level.getRespawnData();
        if (spawn == null || !spawn.dimension().equals(level.dimension())) return false;
        double dx = pos.getX() - spawn.pos().getX();
        double dz = pos.getZ() - spawn.pos().getZ();
        return dx * dx + dz * dz < (double) RADIUS * RADIUS;
    }
}
