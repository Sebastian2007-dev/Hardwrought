package de.ipnats.hardwrought.haven;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Locale;

/**
 * An arc lamp: a ballast holds an arc between two tungsten electrodes behind a mirror, on the 240-volt
 * line. While it burns, no monster spawns within {@link Floodlights#SPAWN_REACH} of it — not even in the
 * thick fog of autumn, where a torch's light counts for nothing — and those already near keep out of
 * {@link Floodlights#AVOID_REACH}. Its electrodes burn back while it is lit, {@link #ELECTRODE_SECONDS},
 * and a new pair goes in.
 */
public class FloodlightBlockEntity extends ApplianceBlockEntity {
    public static final float ELECTRODE_SECONDS = 7200.0f;
    /** Below this share of its power the arc will not hold. */
    public static final double ARC_HOLDS = 0.5;

    private float electrodes = ELECTRODE_SECONDS;

    public FloodlightBlockEntity(BlockPos pos, BlockState state) {
        super(HavenBlocks.FLOODLIGHT_ENTITY, pos, state);
    }

    public float electrodes() {
        return electrodes / ELECTRODE_SECONDS;
    }

    @Override
    protected void work(ServerLevel level, double power) {
        if (power < ARC_HOLDS) {
            Floodlights.dark(level, worldPosition);
            return;
        }
        Floodlights.lit(level, worldPosition);
        electrodes -= WORK_TICKS / 20.0f;
        setChanged();
        if (electrodes > 0.0f) return;
        electrodes = ELECTRODE_SECONDS;
        Floodlights.dark(level, worldPosition);
        level.setBlock(worldPosition, getBlockState().setValue(ApplianceBlock.BROKEN, true).setValue(ApplianceBlock.ON, false), Block.UPDATE_ALL);
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.6f);
        electricDirty = true;
    }

    @Override
    public void setRemoved() {
        if (level != null) Floodlights.dark(level, worldPosition);
        super.setRemoved();
    }

    @Override
    protected Component state() {
        return Component.translatable("message.hardwrought.meter.floodlight", String.format(Locale.ROOT, "%.0f", electrodes() * 100.0f));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        electrodes = input.getFloatOr("electrodes", ELECTRODE_SECONDS);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("electrodes", electrodes);
    }
}
