package de.ipnats.hardwrought.electricity;

import de.ipnats.hardwrought.machinery.KineticHolder;
import de.ipnats.hardwrought.machinery.Kinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A dynamo gives {@link #VOLTS_PER_RPM} for every turn per minute it is driven at, and is the harder
 * to turn the more current is drawn from it: {@link #IMPACT_PER_AMP} for every ampere, on top of its
 * own friction. So voltage is bought with gearing and current with strength, and the strength a line
 * has to spare is the power it can give — a water wheel in a good river runs four lamps, a hand on a
 * crank one, dimly.
 *
 * <p>Where the line cannot turn it against the current asked of it, it does not stall the whole
 * driveline: it <b>lets go</b> for {@link #TRIP_TICKS}, gives nothing, and then tries again. Lamps
 * that flash every few seconds are a dynamo with too much hung on it.
 */
public class DynamoBlockEntity extends ElectricBlockEntity implements KineticHolder {
    public static final double VOLTS_PER_RPM = 2.0;
    public static final double OHMS = 1.0;
    public static final float IDLE_IMPACT = 1.0f;
    public static final float IMPACT_PER_AMP = 6.0f;
    public static final int TRIP_TICKS = 100;

    private float speed;
    /** The current it is taken to be giving, for what it weighs on the line. */
    private float load;
    private int tripped;
    private boolean dirty;

    public DynamoBlockEntity(BlockPos pos, BlockState state) {
        super(ElectricBlocks.DYNAMO_ENTITY, pos, state);
    }

    @Override
    public float kineticSpeed() {
        return speed;
    }

    @Override
    public void setKineticSpeed(float speed) {
        if (Math.abs(this.speed - speed) < 1e-4f) return;
        this.speed = speed;
        // Not worked out from in here: this is called from the middle of the driveline working itself out.
        dirty = true;
        setChanged();
    }

    public float impact() {
        return IDLE_IMPACT + IMPACT_PER_AMP * load;
    }

    /** Whether it has let go because the line could not turn it. */
    public boolean tripped() {
        return tripped > 0;
    }

    @Override
    public double emf() {
        return tripped > 0 ? 0.0 : VOLTS_PER_RPM * Math.abs(speed);
    }

    @Override
    public double sourceOhms() {
        return OHMS;
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
    protected void solved(ServerLevel level, double volts, double amps, boolean wet, double seconds) {
        super.solved(level, volts, amps, wet, seconds);
        float drawn = (float) Math.abs(amps);
        if (Math.abs(drawn - load) < 0.05f) return;
        load = drawn;
        setChanged();
        Kinetics.update(level, worldPosition);
        var line = Kinetics.solve(level, worldPosition);
        if (load > 0.0f && line != null && line.status() == Kinetics.Status.OVERSTRESSED) {
            tripped = TRIP_TICKS;
            load = 0.0f;
            Kinetics.update(level, worldPosition);
            dirty = true;
            level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 0.6f);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DynamoBlockEntity dynamo) {
        if (!(level instanceof ServerLevel server)) return;
        if (dynamo.tripped > 0 && --dynamo.tripped == 0) dynamo.dirty = true;
        if (dynamo.dirty) {
            dynamo.dirty = false;
            Electricity.update(server, pos);
        }
        if (level.getGameTime() % Electricity.PULSE_TICKS == 0) Electricity.pulse(server, pos);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        speed = input.getFloatOr("speed", 0.0f);
        load = input.getFloatOr("load", 0.0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("speed", speed);
        output.putFloat("load", load);
    }
}
