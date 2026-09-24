package de.ipnats.hardwrought.water;

import de.ipnats.hardwrought.core.simulation.SimulationScheduler;
import de.ipnats.hardwrought.core.simulation.SimulationTier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Specification section 23.3: rain is where water enters the world from above.
 *
 * <p>Until now rain only soaked into the ground. That left the cycle open at its most visible point:
 * a puddle taken by the sun never came back, a farm channel that had evaporated stayed empty, and a
 * downpour changed nothing anyone could see. Rain now falls as water — the same finite millibuckets
 * as everything else, which then run downhill, collect in hollows and fill what they find.
 *
 * <p>It is not spread evenly over the ground. A pass picks a few columns at random out of the box
 * around each player and gives each of them a visible amount, because a film thinner than the
 * smallest step vanilla can draw is invisible, does not spread, and would cost a block update per
 * column for nothing. Over a long rain the same water arrives either way, but it arrives in drops
 * that run somewhere — so puddles appear in the low places rather than a sheen appearing everywhere.
 *
 * <p>Bounded by construction like the rest: a fixed number of columns per player per slow pass, no
 * flood fill, and never a chunk load.
 */
public final class Rainfall {
    /** How far around a player rain is placed. Random sampling, so a wide box costs no more. */
    public static final int RADIUS = 8;
    public static final int COLUMNS_PER_PASS = 6;
    /** A thunderstorm puts down several times as much, which is what makes low ground flood. */
    public static final int THUNDER_COLUMNS_PER_PASS = 14;
    /** One visible step of water per column, so what lands is something rather than a sheen. */
    public static final int DROP = WaterAmounts.DISPLAY_STEP;
    public static final int THUNDER_DROP = 2 * WaterAmounts.DISPLAY_STEP;
    /**
     * How often rain landing on dry ground stays there as the start of a puddle. Most of it soaks in:
     * a puddle on every patch of grass was more nuisance than weather, and a stubborn one to get rid of.
     */
    public static final double PUDDLE_CHANCE = 0.2;
    public static final double THUNDER_PUDDLE_CHANCE = 0.35;
    /** Rain falling into water that is already there counts this many times over: ponds and rivers rise. */
    public static final int WATER_GAIN = 2;

    private final MinecraftServer server;

    public Rainfall(MinecraftServer server, SimulationScheduler scheduler) {
        this.server = server;
        scheduler.register("hardwrought:rainfall", SimulationTier.SLOW, this::tickRainfall);
    }

    public static int columns(boolean thundering) {
        return thundering ? THUNDER_COLUMNS_PER_PASS : COLUMNS_PER_PASS;
    }

    public static int drop(boolean thundering) {
        return thundering ? THUNDER_DROP : DROP;
    }

    private void tickRainfall() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            ServerLevel level = player.level();
            if (!level.isRaining()) continue;
            boolean thundering = level.isThundering();
            int drop = drop(thundering);
            BlockPos origin = player.blockPosition();
            RandomSource random = level.getRandom();
            for (int column = 0; column < columns(thundering); column++) {
                int x = origin.getX() + random.nextInt(2 * RADIUS + 1) - RADIUS;
                int z = origin.getZ() + random.nextInt(2 * RADIUS + 1) - RADIUS;
                if (!rainsOn(level, x, z)) continue;
                land(level, x, z, drop, thundering, random.nextDouble());
            }
        }
    }

    /**
     * Whether rain is actually reaching this column. Vanilla already answers the hard parts: a roof
     * or an overhang keeps it out, a desert gets nothing, and a cold biome gets snow instead — which
     * is why a frozen landscape fills no puddles until the thaw the model does not have yet.
     */
    public static boolean rainsOn(ServerLevel level, int x, int z) {
        BlockPos column = new BlockPos(x, level.getSeaLevel(), z);
        // Never load a chunk to make it rain somewhere, and never read a heightmap without one.
        if (!level.hasChunkAt(column)) return false;
        if (!level.isRaining()) return false;
        BlockPos sky = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        return level.isRainingAt(sky);
    }

    /**
     * One drop of rain on this column, the way weather gives it: into water that is already there it
     * adds generously; on dry ground it mostly soaks in and only now and then starts a puddle.
     * {@code roll} is a random number in [0, 1). Returns whether any water landed.
     */
    public static boolean land(ServerLevel level, int x, int z, int drop, boolean thundering, double roll) {
        BlockPos target = target(level, x, z);
        if (target == null) return false;
        boolean intoWater = WaterStorage.containsWater(level.getBlockState(target))
                || WaterStorage.containsWater(level.getBlockState(target.below()));
        if (intoWater) return rainOn(level, x, z, drop * WATER_GAIN);
        if (roll >= (thundering ? THUNDER_PUDDLE_CHANCE : PUDDLE_CHANCE)) return false;
        return rainOn(level, x, z, drop);
    }

    /**
     * Where the rain on this column lands, or null where it has nowhere to go.
     *
     * <p>Leaves are not a roof: the topmost block that is not foliage is what the rain runs off, so
     * a forest floor gets wet and the canopy does not carry a pond. Water that is not yet a full
     * block is simply deepened; anything else is laid on top of what it landed on and then falls,
     * spreads and finds the lowest place on its own.
     *
     * <p>Rain only ever lands in empty air or in water. Flowing water washes grass, flowers and
     * crops out of its way — that is right for a burst dam and quite wrong for weather, which would
     * otherwise clear a wheat field every time it rained on it. What the growth catches, it keeps.
     */
    public static BlockPos target(ServerLevel level, int x, int z) {
        BlockPos column = new BlockPos(x, level.getSeaLevel(), z);
        if (!level.hasChunkAt(column)) return null;
        BlockPos open = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        BlockPos surface = open.below();
        if (WaterStorage.containsWater(level.getBlockState(surface))
                && WaterStorage.amount(level, surface) < WaterAmounts.BLOCK) {
            return surface;
        }
        BlockState state = level.getBlockState(open);
        if (state.isAir()) return open;
        return WaterStorage.containsWater(state) && WaterStorage.amount(level, open) < WaterAmounts.BLOCK
                ? open : null;
    }

    /**
     * Puts one column's worth of rain into the world and reports whether any of it landed. Rain is
     * clean water — it has touched nothing yet — but it mixes into what it falls into, so a
     * downpour on a swamp fills the swamp with swamp water and a puddle on bare rock is drinkable.
     *
     * <p>Rain never presses a cell beyond a full block. Water arrives from the sky, not from a pump.
     */
    public static boolean rainOn(ServerLevel level, int x, int z, int millibuckets) {
        if (millibuckets <= 0) return false;
        BlockPos target = target(level, x, z);
        if (target == null) return false;
        int present = WaterStorage.amount(level, target);
        int added = Math.min(millibuckets, WaterAmounts.room(present));
        if (added <= 0) return false;
        WaterStorage.setAmount(level, target, present + added);
        WaterQualityStorage.add(level, target, WaterQuality.FRESH, present, added);
        WaterFlow.disturb(level, target);
        return true;
    }
}
