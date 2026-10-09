package de.ipnats.hardwrought.electricity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Locale;

/**
 * A battery is a source of its own: {@link #EMPTY_VOLTS} when empty, rising to {@link #FULL_VOLTS}
 * when full, behind {@link #OHMS} of its own. Where the line stands above that voltage, current runs
 * into it and it charges; where the line stands below, it gives. Nothing switches it — it simply
 * holds the line up.
 *
 * <p>It follows that a battery charges only from a line of more than its own voltage: a dynamo at a
 * hand crank's 32 volts never fills one, a wheel's 64 does. And that it fills ever more slowly, the
 * gap closing as it rises.
 *
 * <p>Of what goes in, {@link #EFFICIENCY} comes out again. It holds {@link #CAPACITY} watt-seconds:
 * two lamps for twenty minutes. More than {@link #MAX_AMPS} in or out and the acid boils; kept up for
 * some seconds, the case bursts.
 */
public class BatteryBlockEntity extends ElectricBlockEntity {
    public static final double EMPTY_VOLTS = 54.0, FULL_VOLTS = 62.0;
    public static final double OHMS = 2.0;
    public static final double CAPACITY = 72_000.0;
    public static final double EFFICIENCY = 0.8;
    public static final double MAX_AMPS = 8.0;
    /** Seconds it stands at half as much again as it is made for. */
    private static final float BOIL_LIMIT = 10.0f;

    private double charge;
    private float boil;

    public BatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ElectricBlocks.BATTERY_ENTITY, pos, state);
    }

    /** What it holds, in watt-seconds. */
    public double charge() {
        return charge;
    }

    public void setCharge(double charge) {
        this.charge = Math.clamp(charge, 0.0, CAPACITY);
        setChanged();
        if (level == null || level.isClientSide()) return;
        int quarters = (int) Math.round(this.charge / CAPACITY * 4.0);
        // The gauge shows a sliver for anything at all, and full only when it is.
        if (quarters == 0 && this.charge > 0.0) quarters = 1;
        BlockState state = getBlockState();
        if (state.getValue(BatteryBlock.CHARGE) != quarters) {
            level.setBlock(worldPosition, state.setValue(BatteryBlock.CHARGE, quarters), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public double emf() {
        return EMPTY_VOLTS + (FULL_VOLTS - EMPTY_VOLTS) * charge / CAPACITY;
    }

    @Override
    public double sourceOhms() {
        return OHMS;
    }

    @Override
    public boolean canGive() {
        return charge > 0.0;
    }

    @Override
    public boolean canTake() {
        return charge < CAPACITY;
    }

    @Override
    public boolean takesWires() {
        return false;
    }

    @Override
    public boolean mindsRain() {
        return true;
    }

    @Override
    public Component note() {
        return Component.translatable("message.hardwrought.meter.battery",
                String.format(Locale.ROOT, "%.0f", charge / CAPACITY * 100.0));
    }

    @Override
    protected void solved(ServerLevel level, double volts, double amps, boolean wet, double seconds) {
        super.solved(level, volts, amps, wet, seconds);
        if (seconds <= 0.0) return;
        // Out of the cells when giving; into them, less what the charging wastes, when taking.
        double watts = emf() * amps;
        if (watts != 0.0) setCharge(charge - (watts > 0.0 ? watts : watts * EFFICIENCY) * seconds);
        double over = Math.abs(amps) / MAX_AMPS;
        if (over > 1.0) {
            boil += (float) ((over * over - 1.0) * seconds * 0.8);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, worldPosition.getX() + 0.5,
                    worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, 2, 0.2, 0.05, 0.2, 0.01);
        } else {
            boil = Math.max(0.0f, boil - (float) seconds);
        }
        if (boil >= BOIL_LIMIT) {
            level.setBlock(worldPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                    2.0f, Level.ExplosionInteraction.NONE);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BatteryBlockEntity battery) {
        if (level instanceof ServerLevel server && level.getGameTime() % Electricity.PULSE_TICKS == 0
                && !battery.wires().isEmpty()) {
            Electricity.pulse(server, pos);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        charge = input.getDoubleOr("charge", 0.0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("charge", charge);
    }
}
