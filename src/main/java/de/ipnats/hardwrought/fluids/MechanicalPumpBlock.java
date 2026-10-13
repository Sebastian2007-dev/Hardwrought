package de.ipnats.hardwrought.fluids;

import de.ipnats.hardwrought.machinery.KineticBlock;
import de.ipnats.hardwrought.machinery.KineticHolder;
import de.ipnats.hardwrought.machinery.Kinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A piston pump on a shaft: the first pump, driven from any side a shaft, gear or crank meets it. It
 * pushes {@value Entity#RATED} millibuckets a second at sixteen turns a minute, more or less with the
 * line; a hand crank is enough to turn it. Fluid pipes join it on every side. See {@link FluidPump}.
 */
public class MechanicalPumpBlock extends Block implements EntityBlock, KineticBlock, FluidPumpBlock {
    /** Strength taken per turn per minute: 96 at sixteen turns, within a hand crank's 160. */
    public static final float IMPACT = 6.0f;

    public MechanicalPumpBlock(Properties properties) {
        super(properties);
    }

    @Override
    public float port(BlockState state, Direction face) {
        return 1.0f;
    }

    @Override
    public float impact(Level level, BlockPos pos, BlockState state) {
        return IMPACT;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (!level.isClientSide() && !previous.is(this)) Kinetics.update(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moving) {
        Kinetics.update(level, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    public static class Entity extends BlockEntity implements KineticHolder, FluidPump {
        /** Millibuckets a second at the rated speed. */
        public static final int RATED = 250;
        private static final float RATED_SPEED = 16.0f;

        private float speed;

        public Entity(BlockPos pos, BlockState state) {
            super(FluidBlocks.MECHANICAL_PUMP_ENTITY, pos, state);
        }

        @Override
        public double pumpRate() {
            return RATED / 20.0 * Math.abs(speed) / RATED_SPEED;
        }

        @Override
        public float kineticSpeed() {
            return speed;
        }

        @Override
        public void setKineticSpeed(float speed) {
            this.speed = speed;
            setChanged();
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.putFloat("speed", speed);
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            speed = input.getFloatOr("speed", 0.0f);
        }
    }
}
