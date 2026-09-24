package de.ipnats.hardwrought.environment;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Section 40: leaves are twigs and foliage, not a floor.
 *
 * <p>Players and anything of a player's size or larger push through them instead of standing on
 * them, so a tree canopy is no longer a platform and a leaf roof no longer a roof. Small animals —
 * chickens, cats, parrots, foxes — are light enough to perch on them as before. Pushing through is
 * slow going, and falling through a canopy is braked but not stopped: the fall still counts.
 */
public final class Leaves {
    /** Anything at least this tall is too heavy for leaves to hold. */
    private static final float HEAVY_HEIGHT = 1.0f;
    /** How much of its speed across the ground something keeps each tick it pushes through leaves. */
    private static final double DRAG_ACROSS = 0.8;
    /** How much of its falling speed it keeps: the branches catch at it. */
    private static final double DRAG_FALLING = 0.85;

    private Leaves() { }

    /** Every kind of leaves. By class, not tag: collision shapes are cached before tags are loaded. */
    public static boolean isLeaves(BlockState state) {
        return state.getBlock() instanceof LeavesBlock;
    }

    /** Whether this entity goes through leaves rather than standing on them. */
    public static boolean passesThrough(Entity entity) {
        return entity instanceof LivingEntity && entity.getBbHeight() >= HEAVY_HEIGHT;
    }

    /**
     * One tick of pushing through the leaves at this position. Applied only for the block the
     * entity's middle is in, so an entity in several leaf blocks at once is not slowed several times.
     */
    public static void pushThrough(Entity entity, BlockPos pos) {
        if (!passesThrough(entity)) return;
        if (!BlockPos.containing(entity.getBoundingBox().getCenter()).equals(pos)) return;
        Vec3 motion = entity.getDeltaMovement();
        double vertical = motion.y < 0 ? motion.y * DRAG_FALLING : motion.y;
        entity.setDeltaMovement(motion.x * DRAG_ACROSS, vertical, motion.z * DRAG_ACROSS);
    }
}
