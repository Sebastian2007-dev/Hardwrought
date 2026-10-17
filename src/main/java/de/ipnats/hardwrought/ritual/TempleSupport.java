package de.ipnats.hardwrought.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The altar holds its temple up (endgame § 5). The building physics allows stone to reach out two
 * blocks and copper five; no dome, pyramid or spire wide enough for the ritual hall would stand by that
 * alone. So every block of a temple that is just where its plan has it, round a standing ritual altar,
 * counts to the physics as anchored, the way the ground is. Everything else — the courtyard, the
 * builder's own additions, a temple without its altar — stands or falls as anything built does.
 *
 * <p>The altar is therefore set first, where the middle of the hall will be, and the temple built round
 * it.
 */
public final class TempleSupport {
    /** How far from an altar a temple reaches: its terrace and stairs, and its spire. */
    static final int REACH = Temple.HALF + Temple.STAIRS, BELOW = 8, ABOVE = Temple.TOP;

    private static final Map<ServerLevel, Set<BlockPos>> ALTARS = new WeakHashMap<>();

    private TempleSupport() { }

    static void standing(ServerLevel level, BlockPos altar) {
        ALTARS.computeIfAbsent(level, l -> new HashSet<>()).add(altar.immutable());
    }

    static void gone(ServerLevel level, BlockPos altar) {
        Set<BlockPos> altars = ALTARS.get(level);
        if (altars != null) altars.remove(altar);
    }

    /** Whether the altar of a temple holds this block up: it is where a temple round a standing altar has it. */
    public static boolean holds(ServerLevel level, BlockPos pos, BlockState state) {
        Set<BlockPos> altars = ALTARS.get(level);
        if (altars == null || altars.isEmpty()) return false;
        for (BlockPos altar : altars) {
            int dx = pos.getX() - altar.getX(), dy = pos.getY() - altar.getY(), dz = pos.getZ() - altar.getZ();
            if (Math.abs(dx) > REACH || Math.abs(dz) > REACH || dy < -BELOW || dy > ABOVE) continue;
            if (!level.getBlockState(altar).is(RitualContent.ALTAR)) continue;
            for (Temple.Theme theme : Temple.Theme.values()) {
                if (Temple.fits(theme, altar, pos, state)) return true;
            }
        }
        return false;
    }
}
