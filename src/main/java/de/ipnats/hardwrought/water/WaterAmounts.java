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
     * How much more the lower of two stacked cells carries than the upper one, once the pair holds
     * more than two full blocks between them. It only describes water that has really been pressed
     * into a space too small for it: a pump, a bucket emptied into the foot of a sealed shaft.
     *
     * <p>It used to apply from the first millibucket over a single block, so that two full cells
     * wanted to be 1047 and 953 rather than a thousand each. That was meant to be the weight of the
     * water, and it made every lake unstable in the literal sense: waking any cell of it rearranged
     * the whole body, gave every cell a stored amount and sank the surface. Standing water now
     * weighs nothing, and what lets a tall column lift water elsewhere is {@code WaterFlow}'s
     * pressure rule, which costs nothing while the water is still.
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
    /**
     * A difference smaller than this is left alone between neighbours, which is what stops water
     * from jittering. A pair moves half its difference rounded down, so even a difference of one
     * could not oscillate; ten is simply the point below which the difference is not worth a
     * block update.
     */
    public static final int LEVELLING_THRESHOLD = 10;
    /**
     * How far apart two surfaces joined only through full water may stand before pressure evens
     * them out: half a visible step. Coarser than {@link #LEVELLING_THRESHOLD} on purpose. Finding
     * the other surface means a search, and at ten millibuckets every cell along a shore went
     * searching again each time a ripple from a bucket reached it.
     */
    public static final int PRESSURE_THRESHOLD = DISPLAY_STEP / 2;

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
    /**
     * The same cap between two cells that both already hold water. The slow rate above is there for
     * the front, which is the part that must not teleport; behind the front it only made a pond
     * settle like treacle, a block of water needing seventeen passes to move one cell along.
     *
     * <p>A quarter of a block a pass, for levelling, falling, rising and for room under a sea being
     * filled from its surface alike. That is five blocks of water a second through one cell: a
     * hole one block wide empties a pond of a hundred blocks in twenty seconds, which can be
     * watched, and four holes do it in five. For a while these were a whole block a pass, and at
     * that rate a bucket was simply gone the tick it was poured and a waterfall arrived all at once.
     * What decides how long a sea takes to drain is the size of the hole, not the simulation.
     */
    public static final int MAX_LEVEL_PER_PASS = 250;
    public static final int MAX_FALL_PER_PASS = 250;
    public static final int MAX_RISE_PER_PASS = 250;
    /** What room under a body takes from its surface in one pass: as fast as water falls into it. */
    public static final int MAX_DRAW_PER_PASS = MAX_FALL_PER_PASS;

    private WaterAmounts() { }

    /**
     * The block state that shows this amount. Zero or less means there should be no water at all.
     *
     * <p>The nearest eighth, including the eighth eighth: a cell within half a step of full is drawn
     * full. Drawing it one step down made a sea look dented for a hundred blocks around every
     * bucket taken out of it, over a difference of a few millibuckets a cell.
     */
    public static int displayLevel(int millibuckets) {
        if (millibuckets >= BLOCK) return 0;
        int eighths = Math.max(1, Math.round(millibuckets * 8.0f / BLOCK));
        return eighths >= 8 ? 0 : 8 - eighths;
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
     * <p>Up to two full blocks there is nothing to press: the lower cell fills, and the rest stands
     * on it. Two full cells are therefore exactly at rest, which is what keeps a lake still. Only
     * what is over two blocks is squeezed, and the lower cell takes the first {@link #COMPRESSION}
     * of that alone before the two share the remainder. Continuous at two blocks, so one millibucket
     * of surplus moves one millibucket and not a jolt.
     */
    public static int stableState(int totalMillibuckets) {
        if (totalMillibuckets <= 2 * BLOCK) return Math.max(0, Math.min(BLOCK, totalMillibuckets));
        int squeezed = totalMillibuckets - 2 * BLOCK;
        return BLOCK + squeezed / 2 + Math.min(squeezed, COMPRESSION) / 2;
    }

    public static int clamp(int millibuckets) {
        return Math.max(0, Math.min(MAX_CELL, millibuckets));
    }
}
