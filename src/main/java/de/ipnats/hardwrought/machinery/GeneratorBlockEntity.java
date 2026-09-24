package de.ipnats.hardwrought.machinery;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A source whose output changes with the world around it: a wheel in water, sails in wind.
 *
 * <p>It looks again every {@link #interval()} ticks and works its line out again only when what it
 * gives has changed by enough to matter — a gust that barely moves the sails does not walk a line of
 * a hundred shafts. What it last gave is saved, so a line starts turning again on load straight away.
 */
public abstract class GeneratorBlockEntity extends KineticBlockEntity {
    private float output;
    private float capacity;

    protected GeneratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The speed this source drives at, in its own frame. */
    public float output() {
        return output;
    }

    /** The strength it gives its line. */
    public float capacity() {
        return capacity;
    }

    /** How often it looks at the world again, in ticks. */
    protected abstract int interval();

    /** Works out {speed, capacity} from the world as it is now. */
    protected abstract float[] measure(ServerLevel level, BlockPos pos, BlockState state);

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeneratorBlockEntity source) {
        if (!(level instanceof ServerLevel server)) return;
        if ((level.getGameTime() + pos.asLong()) % source.interval() != 0) return;
        source.remeasure(server, pos, state);
    }

    /** Looks at the world now, and works the line out again if the answer changed. For tests too. */
    public void remeasure(ServerLevel level, BlockPos pos, BlockState state) {
        float[] measured = measure(level, pos, state);
        boolean changed = Math.abs(measured[0] - output) >= 0.5f
                || Math.abs(measured[1] - capacity) >= Math.max(4.0f, capacity * 0.05f)
                || (measured[0] == 0.0f) != (output == 0.0f);
        if (!changed) return;
        output = measured[0];
        capacity = measured[1];
        setChanged();
        Kinetics.update(level, pos);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        output = input.getFloatOr("output", 0.0f);
        capacity = input.getFloatOr("capacity", 0.0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("output", this.output);
        output.putFloat("capacity", capacity);
    }
}
