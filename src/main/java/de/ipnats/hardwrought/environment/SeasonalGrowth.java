package de.ipnats.hardwrought.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Specification section 24 and section 106: the year decides how fast things grow.
 *
 * <p>Minecraft grows a plant by giving it random ticks, so the honest way to make a season matter
 * is to change how many of those a plant gets rather than to reach inside every crop and rewrite
 * its arithmetic. A season below one swallows some of the ticks; a season above one hands out the
 * occasional extra. Nothing else about any plant changes, which means mod crops, datapack crops and
 * crops from other mods all follow the calendar without knowing it exists.
 *
 * <p>Which blocks this applies to is a tag, {@code hardwrought:seasonal_growth}, so a pack can add
 * its own and — more to the point — so that this never touches a block that merely happens to take
 * random ticks. Gas blocks and budding crystals get their ticks whatever the month is.
 */
public final class SeasonalGrowth {
    public static final TagKey<Block> SEASONAL_GROWTH =
            TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                    Identifier.fromNamespaceAndPath("hardwrought", "seasonal_growth"));

    /**
     * Guards against the extra tick growing a plant that then wants another extra tick, and so on
     * down the stack. One bonus tick per random tick, and never a bonus on a bonus.
     */
    private static final ThreadLocal<Boolean> GRANTING = ThreadLocal.withInitial(() -> false);

    private SeasonalGrowth() { }

    /** Whether the calendar has anything to say about this block at all. */
    public static boolean seasonal(BlockState state) {
        return state.is(SEASONAL_GROWTH);
    }

    /**
     * Decides what happens to one random tick on one plant. Returns true if the tick should be
     * swallowed — winter taking back half of what the world offered.
     *
     * <p>Called from the random-tick hook, which is hot, so this does as little as it can and
     * returns at once for everything that is not a plant.
     */
    public static boolean swallow(BlockState state, RandomSource random) {
        // Ordered by cost. This sits on every random tick in the world, so the overwhelmingly
        // common answer — a season that is not winter, or a block that is not a plant — has to be
        // reached without touching a tag lookup or a thread local.
        double factor = SeasonState.season().growthFactor();
        if (factor >= 1.0) return false;
        if (random.nextDouble() < factor) return false;
        return !GRANTING.get() && seasonal(state);
    }

    /**
     * After a plant has had its ordinary tick, spring and summer sometimes give it another. A
     * factor of 1.45 means every tick has a 45 % chance of being worth two.
     */
    public static void grantBonus(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Same ordering, same reason: the roll is cheaper than the tag and settles it most of the time.
        double factor = SeasonState.season().growthFactor();
        if (factor <= 1.0 || random.nextDouble() >= factor - 1.0) return;
        if (GRANTING.get() || !seasonal(state)) return;
        // The plant may have grown, broken or been replaced by its own tick; re-read before acting.
        BlockState now = level.getBlockState(pos);
        if (!seasonal(now)) return;
        GRANTING.set(true);
        try {
            now.randomTick(level, pos, random);
        } finally {
            GRANTING.set(false);
        }
    }
}
