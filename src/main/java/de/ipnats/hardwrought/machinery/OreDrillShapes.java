package de.ipnats.hardwrought.machinery;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * What of a finished ore drill each of its blocks holds, so that the outline and what a player stands
 * on match what is drawn: the open derrick above is open, not a full block.
 *
 * <p>The boxes are the drill as tools/ore_drill_formed.py lays it out, in pixels over the whole
 * structure (48 x 32 x 48, the head at the middle of the bottom layer). Change the two together.
 */
public final class OreDrillShapes {
    private static final int[][] MACHINE = {
            {0, 0, 0, 48, 11, 48}, {1, 11, 1, 47, 13, 47},
            {14, 13, 14, 34, 23, 34}, {12, 23, 12, 36, 25, 36}, {20, 25, 20, 28, 28, 28},
            {0, 13, 0, 4, 32, 4}, {44, 13, 0, 48, 32, 4}, {0, 13, 44, 4, 32, 48}, {44, 13, 44, 48, 32, 48},
            {4, 20, 1, 44, 22, 3}, {4, 20, 45, 44, 22, 47}, {1, 20, 4, 3, 22, 44}, {45, 20, 4, 47, 22, 44},
            {4, 28, 0, 44, 32, 4}, {4, 28, 44, 44, 32, 48}, {0, 28, 4, 4, 32, 44}, {44, 28, 4, 48, 32, 44},
            {4, 28, 20, 44, 32, 28},
    };

    /** The head closed in, then parts 1 to 17 in the order of {@link OreDrillBlockEntity#framePositions}. */
    private static final VoxelShape[] SHAPES = new VoxelShape[OreDrillBlockEntity.FRAME_BLOCKS + 1];

    static {
        SHAPES[0] = cell(0, 0, 0);
        List<BlockPos> frame = OreDrillBlockEntity.framePositions(BlockPos.ZERO);
        for (int i = 0; i < frame.size(); i++) {
            BlockPos offset = frame.get(i);
            SHAPES[i + 1] = cell(offset.getX(), offset.getY(), offset.getZ());
        }
    }

    private OreDrillShapes() { }

    /** The head of a finished drill. */
    public static VoxelShape head() {
        return SHAPES[0];
    }

    /** A frame block of a finished drill, by its part. */
    public static VoxelShape part(int part) {
        return SHAPES[part];
    }

    private static VoxelShape cell(int dx, int dy, int dz) {
        int ox = (dx + 1) * 16;
        int oy = dy * 16;
        int oz = (dz + 1) * 16;
        VoxelShape shape = Shapes.empty();
        for (int[] box : MACHINE) {
            int x0 = Math.max(box[0], ox), y0 = Math.max(box[1], oy), z0 = Math.max(box[2], oz);
            int x1 = Math.min(box[3], ox + 16), y1 = Math.min(box[4], oy + 16), z1 = Math.min(box[5], oz + 16);
            if (x0 >= x1 || y0 >= y1 || z0 >= z1) continue;
            shape = Shapes.or(shape, Block.box(x0 - ox, y0 - oy, z0 - oz, x1 - ox, y1 - oy, z1 - oz));
        }
        return shape.optimize();
    }
}
