package de.ipnats.hardwrought.nether;

import net.minecraft.util.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Whether a nether portal is stable (see docs/endgame.md). An obsidian frame alone opens an unstable
 * one: it takes the traveller through, but nothing they carry, and no portal forms on the other side.
 * {@link #NEEDED} running stabilisers ({@link PortalStabilizers}) set against its frame make it stable.
 *
 * <p>Asked of the blocks alone, so the client — which draws an unstable portal's sparks and smoke —
 * and the server — which strips the traveller — always give the same answer.
 */
public final class PortalStability {
    /** Running stabilisers a portal needs against its frame. */
    public static final int NEEDED = 2;

    private PortalStability() { }

    /** Whether the portal this block belongs to is stable. */
    public static boolean isStable(Level level, BlockPos portal) {
        return stabilizers(level, portal) >= NEEDED;
    }

    /**
     * Running stabilisers against the frame of the portal this block belongs to: anywhere in the
     * frame's own plane up to one block out from it, or on either face of the frame.
     */
    public static int stabilizers(Level level, BlockPos portal) {
        BlockState state = level.getBlockState(portal);
        if (!state.is(Blocks.NETHER_PORTAL)) return 0;
        Direction.Axis axis = state.getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS).orElse(Direction.Axis.X);
        BlockUtil.FoundRectangle inside = BlockUtil.getLargestRectangleAround(portal, axis, 21, Direction.Axis.Y, 21,
                pos -> level.getBlockState(pos).is(Blocks.NETHER_PORTAL));
        Direction along = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        Direction across = along.getClockWise();
        BlockPos corner = inside.minCorner;
        int count = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int a = -2; a < inside.axis1Size + 2; a++) {
            for (int up = -2; up < inside.axis2Size + 2; up++) {
                boolean inner = a >= 0 && a < inside.axis1Size && up >= 0 && up < inside.axis2Size;
                if (inner) continue;
                for (int c = -1; c <= 1; c++) {
                    pos.set(corner).move(along, a).move(Direction.UP, up).move(across, c);
                    if (PortalStabilizers.running(level.getBlockState(pos))) count++;
                }
            }
        }
        return count;
    }
}
