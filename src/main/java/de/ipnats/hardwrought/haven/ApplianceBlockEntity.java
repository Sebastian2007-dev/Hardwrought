package de.ipnats.hardwrought.haven;

import de.ipnats.hardwrought.electricity.ElectricBlockEntity;
import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.electricity.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Locale;

/**
 * What every device of the safe haven has in common (docs/electricity-roadmap.md, stage E3): a
 * resistance between the wire and the ground made for its {@link Appliance#ratedVolts}, which takes its
 * {@link Appliance#ratedWatts} there and only while it {@link #wants} to — a heater whose room is warm
 * enough takes nothing.
 *
 * <p>With less voltage it does less, by the square of what is missing, and below half of it nothing at
 * all. Above a quarter over its voltage it strains, and at nearly twice it its working part goes at once;
 * what goes is the part {@link Appliance#mends} names, and that part, used on it, mends it.
 *
 * <p>Wire is made fast to it directly, as to a lamp; its casing is earthed. Every {@link #WORK_TICKS} it
 * does a second of what it is for, by the power it is given.
 */
public abstract class ApplianceBlockEntity extends ElectricBlockEntity {
    public static final int WORK_TICKS = 20;
    /** Below this share of its voltage it does nothing. */
    public static final double STALL_SHARE = MachineBlockEntity.STALL_VOLTS / MachineBlockEntity.RATED_VOLTS;
    public static final double STRAIN_SHARE = MachineBlockEntity.STRAIN_VOLTS / MachineBlockEntity.RATED_VOLTS;
    public static final double BURN_SHARE = MachineBlockEntity.BURN_VOLTS / MachineBlockEntity.RATED_VOLTS;
    private static final float STRAIN_LIMIT = 20.0f;

    private float strain;
    /** Whether it asked for current when the network was last worked out. */
    private boolean asking;
    /** Whether the network wants working out again. */
    protected boolean electricDirty;

    protected ApplianceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public Appliance appliance() {
        return getBlockState().getBlock() instanceof ApplianceBlock block ? block.appliance() : Appliance.ELECTRIC_HEATER;
    }

    public boolean broken() {
        return getBlockState().getValue(ApplianceBlock.BROKEN);
    }

    /** Whether it asks for current now. A thermostat, a switch or an empty cabinet says no. */
    protected boolean wants() {
        return true;
    }

    /** What it does with this much of its power, for one second; called while it is asked to and while it is not (with 0). */
    protected abstract void work(ServerLevel level, double power);

    /** What an empty hand on it does: a setting turned. Null where it does nothing; else what to tell the player. */
    protected Component use(ServerPlayer player) {
        return null;
    }

    /** What a meter says about it besides its figures. */
    protected Component state() {
        return null;
    }

    /** Its share of its rated power at this voltage: nothing below half, the square of the share above. */
    public double power() {
        double share = Math.abs(volts()) / appliance().ratedVolts();
        return broken() || share < STALL_SHARE ? 0.0 : share * share;
    }

    @Override
    public double conductance() {
        if (broken() || !wants()) return 0.0;
        double rated = appliance().ratedVolts();
        return appliance().ratedWatts() / (rated * rated);
    }

    @Override
    public boolean mindsRain() {
        return true;
    }

    @Override
    protected void solved(ServerLevel level, double volts, double amps, boolean wet, double seconds) {
        super.solved(level, volts, amps, wet, seconds);
        if (seconds <= 0.0 || broken() || Math.abs(amps) <= 0.0) return;
        double share = Math.abs(volts) / appliance().ratedVolts();
        if (share >= STRAIN_SHARE) {
            double over = (share - STRAIN_SHARE) / (1.5 - STRAIN_SHARE);
            strain += (float) ((0.25 + over * over) * seconds);
        } else {
            strain = Math.max(0.0f, strain - (float) seconds * 0.25f);
        }
        if (share >= BURN_SHARE || strain >= STRAIN_LIMIT) {
            strain = 0.0f;
            level.setBlock(worldPosition, getBlockState().setValue(ApplianceBlock.BROKEN, true)
                    .setValue(ApplianceBlock.ON, false), Block.UPDATE_ALL);
            level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 0.8f);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8,
                    worldPosition.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.01);
            electricDirty = true;
        }
    }

    /** Mended with the part that went: the network takes it up again. */
    public void mended() {
        strain = 0.0f;
        electricDirty = true;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ApplianceBlockEntity appliance) {
        if (!(level instanceof ServerLevel server)) return;
        boolean wants = appliance.conductance() > 0.0;
        if (wants != appliance.asking) {
            appliance.asking = wants;
            appliance.electricDirty = true;
        }
        if (appliance.electricDirty) {
            appliance.electricDirty = false;
            Electricity.update(server, pos);
        }
        if ((level.getGameTime() + pos.asLong()) % WORK_TICKS != 0) return;
        double power = wants ? appliance.power() : 0.0;
        // Stage E6: a lamp with no line to it is lit from a Tesla tower in reach, at its own power.
        if (power <= 0.0 && wants && !appliance.broken() && appliance.appliance().wireless()
                && de.ipnats.hardwrought.tesla.TeslaTowers.draw(server, pos, appliance.appliance().ratedWatts() * WORK_TICKS / 20.0)) {
            power = 1.0;
        }
        appliance.work(server, power);
        state = level.getBlockState(pos);
        boolean on = power > 0.0;
        if (state.getValue(ApplianceBlock.ON) != on) level.setBlock(pos, state.setValue(ApplianceBlock.ON, on), Block.UPDATE_ALL);
    }

    @Override
    public Component note() {
        if (broken()) return Component.translatable("message.hardwrought.meter.appliance.broken", new net.minecraft.world.item.ItemStack(appliance().mends()).getHoverName());
        Component said = state();
        Component power = Component.translatable("message.hardwrought.meter.appliance.power",
                String.format(Locale.ROOT, "%.0f", power() * (conductance() > 0.0 ? appliance().ratedWatts() : 0.0)));
        return said == null ? power : said.copy().append(" · ").append(power);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        strain = input.getFloatOr("strain", 0.0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("strain", strain);
    }
}
