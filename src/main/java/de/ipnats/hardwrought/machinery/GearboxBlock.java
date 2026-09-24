package de.ipnats.hardwrought.machinery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A box of bevel gears: an axle on every face, all turning together. It is how a line turns a
 * corner — a shaft comes in from one side and leaves by another at right angles.
 *
 * <p>Speed is kept; the direction is not. Axles on opposite faces turn opposite ways about the axis
 * they share, as the two bevel wheels facing each other inside do.
 */
public class GearboxBlock extends Block implements KineticBlock {
    public GearboxBlock(Properties properties) {
        super(properties);
    }

    @Override
    public float port(BlockState state, Direction face) {
        return face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0f : -1.0f;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (!level.isClientSide() && !previous.is(this)) Kinetics.update(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
                                               BlockPos pos, boolean moving) {
        Kinetics.update(level, pos);
    }
}
