package de.ipnats.hardwrought.electricity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A lamp is a resistance of {@link #OHMS} between the wire and the ground, made for {@link #RATED_VOLTS}.
 *
 * <p>At that voltage it gives full light and takes half an ampere. With less it glows dimmer, and
 * below a fifth not at all. With more it gives no more light but its filament strains: over
 * {@link #STRAIN_VOLTS} it lasts seconds to minutes, and at {@link #POP_VOLTS} it goes at once.
 */
public class LampBlockEntity extends ElectricBlockEntity {
    public static final double OHMS = 120.0;
    public static final double RATED_VOLTS = 60.0;
    public static final double STRAIN_VOLTS = 75.0;
    public static final double POP_VOLTS = 110.0;
    /** Seconds a filament stands at half as much again as it was made for. */
    private static final float STRAIN_LIMIT = 20.0f;

    private float strain;

    public LampBlockEntity(BlockPos pos, BlockState state) {
        super(ElectricBlocks.LAMP_ENTITY, pos, state);
    }

    @Override
    public double conductance() {
        return getBlockState().getValue(ElectricLampBlock.BROKEN) ? 0.0 : 1.0 / OHMS;
    }

    @Override
    public boolean mindsRain() {
        return true;
    }

    /** The light a lamp gives at this voltage, 0 to 15. */
    public static int light(double volts) {
        double share = Math.abs(volts) / RATED_VOLTS;
        if (share < 0.2) return 0;
        return (int) Math.clamp(Math.round(15.0 * Math.pow(Math.min(1.0, share), 1.3)), 1, 15);
    }

    @Override
    protected void solved(ServerLevel level, double volts, double amps, boolean wet, double seconds) {
        super.solved(level, volts, amps, wet, seconds);
        BlockState state = getBlockState();
        if (state.getValue(ElectricLampBlock.BROKEN)) {
            if (state.getValue(ElectricLampBlock.LIGHT) != 0) {
                level.setBlock(worldPosition, state.setValue(ElectricLampBlock.LIGHT, 0), Block.UPDATE_ALL);
            }
            return;
        }
        double live = Math.abs(volts);
        if (live >= STRAIN_VOLTS) {
            // Twice as far over, four times as fast.
            double over = (live - STRAIN_VOLTS) / (RATED_VOLTS * 1.5 - STRAIN_VOLTS);
            strain += (float) ((0.25 + over * over) * seconds);
        } else {
            strain = Math.max(0.0f, strain - (float) seconds * 0.25f);
        }
        if (seconds > 0.0 && (live >= POP_VOLTS || strain >= STRAIN_LIMIT)) {
            strain = 0.0f;
            level.setBlock(worldPosition, state.setValue(ElectricLampBlock.BROKEN, true)
                    .setValue(ElectricLampBlock.LIGHT, 0), Block.UPDATE_ALL);
            level.playSound(null, worldPosition, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.8f, 1.6f);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, 10, 0.2, 0.2, 0.2, 0.1);
            return;
        }
        int light = light(volts);
        if (state.getValue(ElectricLampBlock.LIGHT) != light) {
            level.setBlock(worldPosition, state.setValue(ElectricLampBlock.LIGHT, light), Block.UPDATE_ALL);
        }
    }
}
