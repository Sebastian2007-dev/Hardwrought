package de.ipnats.hardwrought.client.knowledge;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * One frame of the compendium's structure view: the blocks to draw, and how the structure is turned.
 *
 * @param blocks the blocks, each at its place in the structure
 * @param yaw    degrees the structure is turned about the upright
 * @param pitch  degrees it is tipped toward the reader
 * @param center the middle of the structure, in blocks, which it turns about
 * @param scale  pixels to a block
 */
public record MultiblockRenderState(List<Placed> blocks, float yaw, float pitch, float centerX, float centerY,
                                    float centerZ, int x0, int y0, int x1, int y1, float scale,
                                    ScreenRectangle scissorArea, ScreenRectangle bounds)
        implements PictureInPictureRenderState {

    public record Placed(BlockPos pos, BlockState state) { }

    public MultiblockRenderState(List<Placed> blocks, float yaw, float pitch, float centerX, float centerY,
                                 float centerZ, int x0, int y0, int x1, int y1, float scale) {
        this(blocks, yaw, pitch, centerX, centerY, centerZ, x0, y0, x1, y1, scale, null,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, null));
    }
}
