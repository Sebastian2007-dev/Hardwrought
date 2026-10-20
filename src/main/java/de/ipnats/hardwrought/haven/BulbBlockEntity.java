package de.ipnats.hardwrought.haven;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Locale;

/**
 * A device that burns a bulb: the electric light and the heat lamp. Its tungsten filament wears while it
 * is lit — {@link #BULB_SECONDS} of light — and then goes, and the lamp is dark until a new bulb goes in.
 * Not the lamp: the bulb.
 *
 * <p>The heat lamp's warmth is read off the lamp itself by the radiant heat of its surroundings, as a
 * fire's is (see {@code RadiantHeat}); this only keeps the bulb.
 */
public class BulbBlockEntity extends ApplianceBlockEntity {
    public static final float BULB_SECONDS = 14400.0f;

    private float bulb = BULB_SECONDS;

    public BulbBlockEntity(BlockPos pos, BlockState state) {
        this(HavenBlocks.BULB_ENTITY, pos, state);
    }

    protected BulbBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** How much of its bulb is left, 0 to 1. */
    public float bulb() {
        return bulb / BULB_SECONDS;
    }

    @Override
    protected void work(ServerLevel level, double power) {
        if (power <= 0.0) return;
        wear(level, (float) (WORK_TICKS / 20.0 * Math.min(1.0, power)));
    }

    /** The filament wears this many seconds; worn through, it goes dark. For tests too. */
    public void wear(ServerLevel level, float seconds) {
        bulb -= seconds;
        setChanged();
        if (bulb > 0.0f) return;
        bulb = BULB_SECONDS;
        level.setBlock(worldPosition, getBlockState().setValue(ApplianceBlock.BROKEN, true).setValue(ApplianceBlock.ON, false), Block.UPDATE_ALL);
        level.playSound(null, worldPosition, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.4f, 1.8f);
        electricDirty = true;
    }

    @Override
    protected Component state() {
        return Component.translatable("message.hardwrought.meter.bulb", String.format(Locale.ROOT, "%.0f", bulb() * 100.0f));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        bulb = input.getFloatOr("bulb", BULB_SECONDS);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("bulb", bulb);
    }
}
