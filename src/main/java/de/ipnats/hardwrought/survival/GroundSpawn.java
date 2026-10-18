package de.ipnats.hardwrought.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Nobody starts the game, or starts it over, in the crown of a tree. Vanilla puts a spawn on the highest
 * block it finds, and in a forest that is a leaf: the new player stands on the canopy with no axe, no way
 * down but a fall, and leaves that do not carry them besides. A player who arrives, or comes back from
 * death to the world's spawn, standing on or in a tree is set down on the nearest open ground instead.
 * A bed is the player's own choice and is left alone.
 */
public final class GroundSpawn {
    /** How far around the spawn the ground is looked for. */
    static final int REACH = 16;
    /** How far down from the player the ground is looked for: a tall tree, and the slope under it. */
    static final int DEPTH = 40;

    private GroundSpawn() { }

    /** Puts a player who stands on or in a tree down on the ground nearby. Returns whether it moved them. */
    public static boolean settle(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        BlockPos feet = player.blockPosition();
        if (!inTree(level, feet)) return false;
        BlockPos ground = groundNear(level, feet);
        if (ground == null) return false;
        player.teleportTo(level, ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, Set.of(),
                player.getYRot(), player.getXRot(), false);
        player.fallDistance = 0;
        return true;
    }

    /** Whether standing here is standing in a tree: leaves or wood at the feet, the head, or what holds them up. */
    public static boolean inTree(ServerLevel level, BlockPos feet) {
        if (tree(level.getBlockState(feet)) || tree(level.getBlockState(feet.above()))) return true;
        // What the feet rest on: the first block below that is not air, a few blocks down at most.
        for (int down = 1; down <= 3; down++) {
            BlockState below = level.getBlockState(feet.below(down));
            if (below.isAir()) continue;
            return tree(below);
        }
        return false;
    }

    /** The nearest column, spiralling out, whose top is solid ground with room to stand on it; null if none. */
    public static BlockPos groundNear(ServerLevel level, BlockPos around) {
        for (int ring = 0; ring <= REACH; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    BlockPos found = standing(level, around.getX() + dx, around.getY(), around.getZ() + dz);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    /**
     * The highest place in a column, from the player's own height down, where two blocks are free over solid
     * ground that is not part of a tree. Looked for downwards from where the player is rather than off the
     * height map: the ground under a tree is wanted, not the top of the cliff behind it.
     */
    private static BlockPos standing(ServerLevel level, int x, int top, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return null;
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos(x, top, z);
        for (int y = top; y > Math.max(level.getMinY(), top - DEPTH); y--) {
            feet.setY(y);
            BlockState floor = level.getBlockState(feet.below());
            if (tree(floor) || !floor.isSolidRender() || !floor.getFluidState().isEmpty()) continue;
            if (free(level, feet) && free(level, feet.above())) return feet.immutable();
        }
        return null;
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !tree(state) && state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private static boolean tree(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS);
    }
}
