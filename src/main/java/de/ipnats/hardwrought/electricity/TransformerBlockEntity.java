package de.ipnats.hardwrought.electricity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * Two windings on one core of electrical steel, one with {@link #RATIO} times the turns of the other.
 * What stands on the few turns stands on the many {@code RATIO} times over; what is drawn from the
 * many is drawn {@code RATIO} times over from the few. Nothing is made: a line at four times the
 * voltage carries the same power on a quarter of the current, and loses a sixteenth as much on the way.
 *
 * <p>The block itself is the low side, joined to a line through coils on any face, as a machine is.
 * The high side is the coil on its marked face. It works both ways: fed from the high side, it steps
 * down.
 *
 * <p>Its windings keep {@link #OHMS}, referred to the high side. More than {@link #RATED_AMPS} out of
 * the high side and they heat; kept up, the varnish chars and the transformer is burnt out, which a
 * new winding mends.
 */
public class TransformerBlockEntity extends ElectricBlockEntity {
    public static final double RATIO = 4.0;
    public static final double OHMS = 1.0;
    public static final double RATED_AMPS = 4.0;
    /** How much heat its windings stand, in the same measure as a wire's. */
    private static final float BURN_HEAT = 20.0f;

    private float heat;
    private float load;

    public TransformerBlockEntity(BlockPos pos, BlockState state) {
        super(ElectricBlocks.TRANSFORMER_ENTITY, pos, state);
    }

    /** The face its high side is on. */
    public Direction highFace() {
        return getBlockState().getValue(TransformerBlock.FACING);
    }

    public boolean burnt() {
        return getBlockState().getValue(TransformerBlock.BURNT);
    }

    /** The coil on its high face, where it works and there is one; null otherwise. */
    public ElectricBlockEntity highSide(ServerLevel level) {
        if (burnt()) return null;
        Direction face = highFace();
        BlockPos at = worldPosition.relative(face);
        if (!level.isLoaded(at)) return null;
        BlockState there = level.getBlockState(at);
        return there.getBlock() instanceof CoilBlock && there.getValue(CoilBlock.FACING) == face
                && level.getBlockEntity(at) instanceof ElectricBlockEntity coil ? coil : null;
    }

    /** The current last drawn from its high side, as a share of what it is rated for. */
    public float load() {
        return load;
    }

    @Override
    public boolean takesWires() {
        return false;
    }

    /**
     * Told the current out of its high side and for how long. True where it burnt out just now.
     */
    public boolean carried(ServerLevel level, double amps, double seconds) {
        load = (float) (Math.abs(amps) / RATED_AMPS);
        if (seconds <= 0.0 || burnt()) return false;
        heat = load > 1.0f ? heat + (load * load - 1.0f) * (float) seconds : Math.max(0.0f, heat - (float) seconds);
        if (heat > 1.0f && level.getRandom().nextInt(2) == 0) {
            level.sendParticles(ParticleTypes.SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 1.05,
                    worldPosition.getZ() + 0.5, 2, 0.25, 0.02, 0.25, 0.0);
            level.playSound(null, worldPosition, SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 0.5f, 0.5f);
        }
        if (heat < BURN_HEAT) return false;
        heat = 0.0f;
        level.setBlock(worldPosition, getBlockState().setValue(TransformerBlock.BURNT, true), Block.UPDATE_ALL);
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 0.6f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                worldPosition.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.02);
        return true;
    }

    @Override
    public Component note() {
        if (burnt()) return Component.translatable("message.hardwrought.meter.transformer_burnt");
        return Component.translatable("message.hardwrought.meter.transformer",
                (int) RATIO, String.format(Locale.ROOT, "%.0f", load * 100.0f));
    }
}
