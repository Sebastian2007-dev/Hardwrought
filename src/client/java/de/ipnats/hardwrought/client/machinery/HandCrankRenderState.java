package de.ipnats.hardwrought.client.machinery;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

/** The hand crank's snapshot for one frame: which way it faces and where its handle stands. */
public class HandCrankRenderState extends BlockEntityRenderState {
    public float angle;
    public Direction facing = Direction.NORTH;
    public final MovingBlockRenderState handle = new MovingBlockRenderState();
}
