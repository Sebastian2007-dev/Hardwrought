package de.ipnats.hardwrought.water;

import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Water measured in millibuckets. A full block holds 1000 mB, a bucket takes exactly that, a glass
 * bottle a tenth of it.
 *
 * <p>Vanilla can only draw eight steps of water in a block, so the block state is the display and
 * the stored amount is the truth. Everything here is pure arithmetic, which is what makes the rest
 * of the water model checkable without a world.
 */
public final class WaterAmounts {
    /** A full block of water. */
    public static final int BLOCK = 1000;
    public static final int BUCKET = 1000;
    public static final int BOTTLE = 100;
    /** Eight drinks of a hundred, so a skin and a bottle speak the same units. */
    public static final int WATERSKIN = 800;
    public static final int DRINK = 100;

    /**
     * How much a single cell can hold when it is being pressed on from below. Water that is pushed
     * into a block which is already full has to go somewhere, and in a closed column the only way out
     * is upward — so a cell carries the surplus until it can pass it on. Twice a block is enough for
     * the pressure to travel one cell per pass; beyond it a cell refuses more, which is what keeps a
     * sealed pipe from swallowing an unlimited amount.
     */
    public static final int MAX_CELL = 4 * BLOCK;

    /**
     * How much more than a full block the lower of two stacked cells carries. This small excess is
     * the weight of the water above it, and it is the whole reason a tall column can push water up a
     * connected shaft until both stand at the same height. Without it every cell of a column holds
     * exactly one block, nothing is ever in surplus, and communicating vessels do not communicate.
     */
    public static final int COMPRESSION = 50;

    /** Vanilla draws water in eighths, so this is what one visible step is worth. */
    public static final int DISPLAY_STEP = BLOCK / 8;
    /**
     * Below this a film of water no longer spreads sideways. Without it the last few millibuckets
     * would creep on forever across a flat floor, and every one of those cells would be drawn as a
     * full eighth of a block that is not really there.
     */
    public static final int SPREAD_THRESHOLD = DISPLAY_STEP;
    /** A difference smaller than this is left alone, which is what stops water from jittering. */
    public static final int LEVELLING_THRESHOLD = 10;

    /**
     * How much water one transfer may move in a single pass, in millibuckets.
     *
     * <p>Without a cap the solver is a solver and not a fluid: a cell hands a neighbour half its
     * surplus immediately, so a poured bucket arrives at the far wall in as many ticks as there are
     * blocks between. Water that teleports reads as a bug even when the arithmetic is right.
     *
     * <p>These are what make it look like flowing. Sideways is the slow one, because it is what a
     * player watches: a receiving cell needs about two passes to gather the
     * {@link #SPREAD_THRESHOLD} it must hold before it can pass anything on, so a front advances
     * roughly one block every two ticks — around two and a half times vanilla rather than five.
     * Falling and rising stay quick, because gravity and pressure are quick.
     */
    public static final int MAX_SPREAD_PER_PASS = 60;
    public static final int MAX_FALL_PER_PASS = 250;
    public static final int MAX_RISE_PER_PASS = 250;

    private WaterAmounts() { }

    /** The block state that shows this amount. Zero or less means there should be no water at all. */
    public static int displayLevel(int millibuckets) {
        if (millibuckets >= BLOCK) return 0;
        int eighths = Math.max(1, Math.round(millibuckets * 8.0f / BLOCK));
        return Math.clamp(8 - eighths, 1, 7);
    }

    /**
     * What a water block holds when nothing more precise is known about it — a lake from world
     * generation, for instance. The eight steps vanilla can draw are read back at face value.
     */
    public static int nominalAmount(BlockState state) {
        if (!state.hasProperty(LiquidBlock.LEVEL)) return 0;
        int level = state.getValue(LiquidBlock.LEVEL);
        if (level == 0) return BLOCK;
        if (level >= 8) return BLOCK;
        return (8 - level) * DISPLAY_STEP;
    }

    /** How much more this cell could take while staying an ordinary, unpressed block of water. */
    public static int room(int millibuckets) {
        return Math.max(0, BLOCK - millibuckets);
    }

    /** How much more it could take when something is pressing it in from below. */
    public static int pressureRoom(int millibuckets) {
        return Math.max(0, MAX_CELL - millibuckets);
    }

    /** What this cell is holding over and above a full block, and therefore has to pass upward. */
    public static int surplus(int millibuckets) {
        return Math.max(0, millibuckets - BLOCK);
    }

    /**
     * What the lower of two stacked cells settles at when they hold this much between them.
     *
     * <p>Below a full block there is nothing to stack: it all sits in the lower one. Above it the
     * lower cell takes a little more than its share, and that little more is the pressure that
     * travels down a column and up the next one.
     */
    public static int stableState(int totalMillibuckets) {
        if (totalMillibuckets <= BLOCK) return Math.max(0, totalMillibuckets);
        if (totalMillibuckets < 2 * BLOCK + COMPRESSION) {
            return (BLOCK * BLOCK + totalMillibuckets * COMPRESSION) / (BLOCK + COMPRESSION);
        }
        return (totalMillibuckets + COMPRESSION) / 2;
    }

    public static int clamp(int millibuckets) {
        return Math.max(0, Math.min(MAX_CELL, millibuckets));
    }
}
