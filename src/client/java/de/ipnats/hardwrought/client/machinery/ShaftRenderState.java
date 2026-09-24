package de.ipnats.hardwrought.client.machinery;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/**
 * Everything the shaft renderer needs, pulled off the world once per frame.
 *
 * <p>The render state is the whole point of the pipeline this version uses: the renderer never
 * touches the level, it is handed a snapshot. That is what lets the drawing happen off the game
 * thread, and it is why the angle has to be interpolated here rather than looked up later.
 */
public class ShaftRenderState extends BlockEntityRenderState {
    /** Where the bar stands this frame, in degrees, already interpolated between two ticks. */
    public float angle;
    /** Which way the bar lies, and therefore which way it turns. */
    public Direction.Axis axis = Direction.Axis.Y;
    /** The bar's own block state, dressed up as something the block renderer will draw. */
    public final MovingBlockRenderState bar = new MovingBlockRenderState();
    /** From this shaft to the other end of its belt, when this end draws the belt; else null. */
    public net.minecraft.world.phys.Vec3 belt;
    /** The belt's own block state, drawn stretched between the two shafts. */
    public final MovingBlockRenderState strip = new MovingBlockRenderState();
}
