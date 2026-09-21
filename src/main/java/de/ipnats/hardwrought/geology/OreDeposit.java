package de.ipnats.hardwrought.geology;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * One ore body: a patch of ground where the rock carries far more of an ore than the ground around
 * it. Section 52 asks for large deposits rather than uniform ground, and section 53 gives them a
 * grade that varies inside them.
 *
 * <p>The ore blocks themselves are placed by ordinary world generation. A body says how much metal
 * is in the rock there, which is what a broken block pays out and what a prospector reads.
 *
 * <p>A body is not stored anywhere. It is derived from the world seed and the region it belongs to,
 * so the same world always has the same rich ground in the same place, no matter who asks or when.
 *
 * @param ore              the ore block this body is made of
 * @param centre           the middle of the body, which is also its richest point
 * @param horizontalRadius how far it reaches sideways
 * @param verticalRadius   how far it reaches up and down; ore bodies are flatter than they are wide
 * @param coreGrade        the assay at the centre, as a fraction of metal in the rock
 */
public record OreDeposit(Identifier ore, BlockPos centre, int horizontalRadius, int verticalRadius,
                         double coreGrade) {
    /** What the rim of a body assays relative to its core. Nothing is uniformly rich. */
    public static final double RIM_SHARE = 0.35;

    public OreDeposit {
        if (ore == null || centre == null) throw new IllegalArgumentException("A deposit needs an ore and a place");
        if (horizontalRadius <= 0 || verticalRadius <= 0) {
            throw new IllegalArgumentException("A deposit with no size is not a deposit");
        }
        if (!Double.isFinite(coreGrade) || coreGrade < 0 || coreGrade > 1) {
            throw new IllegalArgumentException("Ore grade is a fraction between 0 and 1");
        }
    }

    /**
     * How far into the body a position sits, 0 at the centre and 1 at the rim. Above 1 it is outside.
     * The body is an ellipsoid: wide and flat, the way a real ore body lies in its host rock.
     */
    public double reach(int x, int y, int z) {
        double dx = (x - centre.getX()) / (double) horizontalRadius;
        double dy = (y - centre.getY()) / (double) verticalRadius;
        double dz = (z - centre.getZ()) / (double) horizontalRadius;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public boolean contains(int x, int y, int z) {
        return reach(x, y, z) <= 1.0;
    }

    public boolean contains(BlockPos pos) {
        return contains(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Section 53: what the rock assays here. Richest in the middle and poorest at the rim, so the
     * same body is worth working in one place and barely worth the effort in another.
     */
    public double gradeAt(int x, int y, int z) {
        double reach = reach(x, y, z);
        if (reach > 1.0) return 0;
        return coreGrade * (1.0 - (1.0 - RIM_SHARE) * reach);
    }

    public double gradeAt(BlockPos pos) {
        return gradeAt(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Blocks from a position to the middle of the body, for prospecting hints. */
    public double distanceTo(BlockPos pos) {
        return Math.sqrt(centre.distSqr(pos));
    }
}
