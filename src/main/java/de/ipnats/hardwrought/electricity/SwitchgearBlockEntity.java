package de.ipnats.hardwrought.electricity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * A switch, a fuse or a breaker: a block that joins the coils set against it while it is closed, and
 * cuts them apart while it is open. A line is led in on one coil and out on another.
 *
 * <p>A fuse and a breaker open by themselves when more than they are rated for runs through them for
 * a moment — sooner than the wire behind them would burn through, which is what they are for. A fuse
 * is spent by it and takes a new cartridge; a breaker is closed again by hand.
 */
public class SwitchgearBlockEntity extends ElectricBlockEntity {
    private float heat;
    private float carried;

    public SwitchgearBlockEntity(BlockPos pos, BlockState state) {
        super(ElectricBlocks.SWITCHGEAR_ENTITY, pos, state);
    }

    public SwitchgearBlock.Kind kind() {
        return getBlockState().getBlock() instanceof SwitchgearBlock block ? block.kind() : SwitchgearBlock.Kind.SWITCH;
    }

    public boolean closed() {
        return getBlockState().getValue(SwitchgearBlock.CLOSED);
    }

    /** The current last run through it. */
    public float through() {
        return carried;
    }

    @Override
    public boolean takesWires() {
        return false;
    }

    @Override
    public boolean joins() {
        return closed();
    }

    /** Told the current through it and for how long. True where it opened just now. */
    public boolean carried(ServerLevel level, double amps, double seconds) {
        carried = (float) amps;
        SwitchgearBlock.Kind kind = kind();
        if (seconds <= 0.0 || kind.ratedAmps() <= 0.0 || !closed()) return false;
        double ratio = amps / kind.ratedAmps();
        heat = ratio > 1.0 ? heat + (float) ((ratio * ratio - 1.0) * seconds) : 0.0f;
        if (heat < kind.tripHeat()) return false;
        heat = 0.0f;
        level.setBlock(worldPosition, getBlockState().setValue(SwitchgearBlock.CLOSED, false), Block.UPDATE_ALL);
        if (kind == SwitchgearBlock.Kind.FUSE) {
            level.playSound(null, worldPosition, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.5f, 1.8f);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.1);
        } else {
            level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 1.5f);
        }
        return true;
    }

    @Override
    public Component note() {
        SwitchgearBlock.Kind kind = kind();
        String state = closed() ? "closed" : kind == SwitchgearBlock.Kind.FUSE ? "blown"
                : kind == SwitchgearBlock.Kind.BREAKER ? "tripped" : "open";
        Component said = Component.translatable("message.hardwrought.meter.switchgear." + state);
        if (kind.ratedAmps() <= 0.0 || !closed()) return said;
        return said.copy().append(" · ").append(Component.translatable("message.hardwrought.meter.switchgear.rating",
                String.format(Locale.ROOT, "%.1f", carried), String.format(Locale.ROOT, "%.0f", kind.ratedAmps())));
    }
}
