package de.ipnats.hardwrought.machinery;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The thing that turns a shaft: a box with a crank in it.
 *
 * <p>Section 72 lists the crank among the first hand-powered machines, and this is the stub of it.
 * It has no stamina cost, no wind-down and no load, because the point of it here is to be the end
 * of a driveline that can be switched on. Right-click turns it; right-click again stops it.
 *
 * <p>It is the only thing in this prototype that a player interacts with, which is on purpose: a
 * driveline that needs two blocks to demonstrate should need exactly two blocks.
 */
public class CrankBoxBlock extends Block implements KineticBlock {
    public static final BooleanProperty TURNING = BooleanProperty.create("turning");

    public CrankBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TURNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TURNING);
    }

    /** Turns per minute out of the box: a steady, strong source for building and testing lines. */
    public static final float SPEED = 32.0f;
    public static final float CAPACITY = 4096.0f;

    /** A box turns out of every face at once. */
    @Override
    public float port(BlockState state, net.minecraft.core.Direction face) {
        return 1.0f;
    }

    @Override
    public float drive(Level level, net.minecraft.core.BlockPos pos, BlockState state) {
        return state.getValue(TURNING) ? SPEED : 0.0f;
    }

    @Override
    public float capacity(Level level, net.minecraft.core.BlockPos pos, BlockState state) {
        return state.getValue(TURNING) ? CAPACITY : 0.0f;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        boolean turning = !state.getValue(TURNING);
        level.setBlock(pos, state.setValue(TURNING, turning), Block.UPDATE_ALL);
        level.playSound(null, pos, turning ? SoundEvents.WOODEN_BUTTON_CLICK_ON
                : SoundEvents.WOODEN_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.6f, 0.7f);
        Driveline.update(level, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous,
                           boolean moving) {
        if (!level.isClientSide()) Driveline.update(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
                                               BlockPos pos, boolean moving) {
        Driveline.update(level, pos);
    }
}
