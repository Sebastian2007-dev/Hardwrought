package de.ipnats.hardwrought.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Snow gathering, snow going, water freezing and ice thawing — all of it at the one place vanilla
 * already does this work.
 *
 * <p>This used to be two passes of its own that walked outward from each player, and it was wrong
 * in a way that is obvious the moment anybody plays it: snow piled up in a small circle that
 * followed the player about, and the world ten blocks further on had none. Covering a circle of
 * radius twelve at ten columns every ten seconds takes the better part of ten minutes, by which
 * time the player has walked somewhere else and started a new circle.
 *
 * <p>Vanilla has the right shape for this and has had all along: {@code tickChunk} rolls a position
 * in <em>every</em> ticking chunk and hands it to the precipitation tick. Riding on that gives
 * uniform coverage over the whole loaded world, at a rate the game already considers reasonable,
 * and with no notion of where any player is standing. A frozen lake is frozen because it is winter,
 * not because somebody is looking at it.
 */
public final class Weathering {
    private Weathering() { }

    /**
     * One column, at the position vanilla's own precipitation tick picked.
     *
     * <p>Runs whatever the weather: snow only falls while it is snowing, but a thaw, a freeze and
     * ice going soft happen on clear days too — in fact mostly on clear days.
     */
    public static void tick(ServerLevel level, BlockPos pos) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        RandomSource random = level.getRandom();
        if (level.isRaining()) Snowfall.settle(level, top, random);
        Snowfall.thaw(level, top, random);
        de.ipnats.hardwrought.water.Freezing.tickSurface(level, top.below(), random);
    }
}
