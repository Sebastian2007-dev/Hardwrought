package de.ipnats.hardwrought.electricity;

import de.ipnats.hardwrought.machinery.KineticHolder;
import de.ipnats.hardwrought.machinery.Kinetics;
import de.ipnats.hardwrought.parts.Parts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Locale;

/**
 * The dynamo the other way round: current in, turns out (docs/electricity-roadmap.md, stage E2).
 *
 * <p><b>Speed follows voltage</b>: {@link #RPM_PER_VOLT} turns a minute for every volt at its coil, the
 * dynamo's two volts a turn read backwards — 60 V turn it at 30. <b>Strength follows current</b>: it
 * gives its line {@link #STRESS_PER_AMP} for every ampere it may draw, up to {@link #RATED_AMPS}, at
 * every turn a minute; and it draws what the line asks of it, so an idle drive takes next to nothing
 * and one carrying its full load its rated current. A gearbox does the rest.
 *
 * <p><b>Started and stalled.</b> A motor standing still draws far more than it does turning. For
 * {@link #INRUSH_SECONDS} after it starts it takes {@link #STALL_FACTOR} times its load; held still —
 * asked more than it can give, or its line jammed — it takes that many times its rating for as long
 * as it is held, which trips a breaker. A <b>motor controller</b> fitted to it starts it gently, holds a
 * stalled one to its rating, and sets its speed in quarters of the voltage, throwing the rest away in its
 * resistors.
 *
 * <p><b>Brushes.</b> Two graphite brushes carry the current onto the turning commutator and wear while it
 * turns: {@link #BRUSH_SECONDS} of running, half that in Ultra. Worn through, it stops; a new pair goes
 * in once the old are down to a quarter. Over {@link MachineBlockEntity#STRAIN_VOLTS} its windings cook
 * as a machine's do, and a coil of thin wire rewinds them.
 */
public class ElectricDriveBlockEntity extends ElectricBlockEntity implements KineticHolder {
    public static final double RPM_PER_VOLT = 1.0 / DynamoBlockEntity.VOLTS_PER_RPM;
    public static final double RATED_AMPS = 4.0;
    public static final double STRESS_PER_AMP = DynamoBlockEntity.IMPACT_PER_AMP;
    /** What it takes turning with nothing on its shaft: its own friction and its field. */
    public static final double IDLE_AMPS = 0.1;
    /** Below this it does not turn at all. */
    public static final double MIN_VOLTS = 6.0;
    /** A motor held still draws this many times what it does turning. */
    public static final double STALL_FACTOR = 5.0;
    public static final double INRUSH_SECONDS = 2.0;
    public static final float BRUSH_SECONDS = 7200.0f;
    /** How often it asks its line how hard it is being worked. */
    public static final int LOOK_TICKS = 10;
    private static final float STRAIN_LIMIT = 20.0f;

    /** How fast its line turns it, as last worked out. */
    private float speed;
    /** The speed it drives at, from the voltage it was last given. */
    private float drive;
    /** What its line asks of it, in amperes, while it turns. */
    private float load;
    private boolean stalled;
    private float inrush;
    private float strain;
    private float brushes = BRUSH_SECONDS;
    /** With a controller fitted, the share of the voltage it is set to run on, in quarters. */
    private int quarters = 4;
    private boolean kineticDirty;
    private boolean electricDirty;

    public ElectricDriveBlockEntity(BlockPos pos, BlockState state) {
        super(ElectricBlocks.ELECTRIC_DRIVE_ENTITY, pos, state);
    }

    private boolean broken() {
        return getBlockState().getValue(ElectricDriveBlock.BROKEN);
    }

    public boolean controlled() {
        return getBlockState().getValue(ElectricDriveBlock.CONTROLLED);
    }

    /** Whether it is held still against its current: stalled, or jammed. */
    public boolean stalled() {
        return stalled;
    }

    /** How much of a pair of brushes is left, 0 to 1. */
    public float brushes() {
        return brushes / BRUSH_SECONDS;
    }

    /** The share of the voltage a controller lets through, in quarters; four without one. */
    public int quarters() {
        return controlled() ? quarters : 4;
    }

    /** The speed it drives its line at, in its own frame; nothing while it cannot turn. */
    public float drive() {
        return broken() || brushes <= 0.0f ? 0.0f : drive;
    }

    /** The strength it gives its line: so much for every ampere it may draw, at every turn a minute. */
    public float capacity() {
        return (float) (STRESS_PER_AMP * RATED_AMPS * Math.abs(drive()));
    }

    /** What it draws from the line now, in amperes. */
    public double wanted() {
        if (broken() || brushes <= 0.0f) return 0.0;
        if (Math.abs(volts()) < MIN_VOLTS) return IDLE_AMPS;
        if (stalled) return controlled() ? RATED_AMPS : RATED_AMPS * STALL_FACTOR;
        if (inrush > 0.0f && !controlled()) return IDLE_AMPS + load * STALL_FACTOR;
        return IDLE_AMPS + load;
    }

    @Override
    public double conductance() {
        return wanted() / Math.max(Math.abs(volts()), MIN_VOLTS);
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
    public float kineticSpeed() {
        return speed;
    }

    @Override
    public void setKineticSpeed(float speed) {
        if (Math.abs(this.speed - speed) < 1e-4f) return;
        this.speed = speed;
        setChanged();
    }

    @Override
    protected void solved(ServerLevel level, double volts, double amps, boolean wet, double seconds) {
        super.solved(level, volts, amps, wet, seconds);
        if (broken()) return;
        float now = Math.abs(volts) < MIN_VOLTS ? 0.0f : (float) (Math.abs(volts) * RPM_PER_VOLT * quarters() / 4.0);
        if (Math.abs(now - drive) >= 0.5f || (now == 0.0f) != (drive == 0.0f)) {
            // Standing still, the next moment it starts: it takes several times its load until it is up to speed.
            if (drive == 0.0f && now > 0.0f) inrush = (float) INRUSH_SECONDS;
            drive = now;
            setChanged();
            kineticDirty = true;
        }
        if (seconds <= 0.0) return;
        // The windings cook as a machine's do; a controller turned down spares them.
        double live = Math.abs(volts) * quarters() / 4.0;
        if (live >= MachineBlockEntity.STRAIN_VOLTS) {
            double over = (live - MachineBlockEntity.STRAIN_VOLTS) / (MachineBlockEntity.RATED_VOLTS * 1.5 - MachineBlockEntity.STRAIN_VOLTS);
            strain += (float) ((0.25 + over * over) * seconds);
        } else {
            strain = Math.max(0.0f, strain - (float) seconds * 0.25f);
        }
        if (live >= MachineBlockEntity.BURN_VOLTS || strain >= STRAIN_LIMIT) burn(level);
    }

    private void burn(ServerLevel level) {
        strain = 0.0f;
        drive = 0.0f;
        stalled = false;
        level.setBlock(worldPosition, getBlockState().setValue(ElectricDriveBlock.BROKEN, true), Block.UPDATE_ALL);
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 0.8f);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                worldPosition.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.01);
        kineticDirty = true;
        electricDirty = true;
    }

    /** Asks the line how hard it is worked, and says so to the network where that changed. */
    public void look(ServerLevel level) {
        float load = 0.0f;
        boolean stalled = false;
        if (drive() > 0.0f) {
            Kinetics.Network line = Kinetics.solve(level, worldPosition);
            if (line != null) {
                switch (line.status()) {
                    case OVERSTRESSED, JAMMED -> stalled = true;
                    case RUNNING -> load = line.capacity() > 0.0f
                            ? (float) (RATED_AMPS * Math.min(1.0f, line.stress() / line.capacity())) : 0.0f;
                    default -> { }
                }
            }
        }
        if (Math.abs(load - this.load) < 0.05f && stalled == this.stalled) return;
        this.load = load;
        this.stalled = stalled;
        setChanged();
        electricDirty = true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ElectricDriveBlockEntity drive) {
        if (!(level instanceof ServerLevel server)) return;
        if (drive.kineticDirty) {
            drive.kineticDirty = false;
            Kinetics.update(server, pos);
        }
        if ((level.getGameTime() + pos.asLong()) % LOOK_TICKS == 0) drive.look(server);
        if (drive.inrush > 0.0f) {
            drive.inrush -= 0.05f;
            if (drive.inrush <= 0.0f) {
                drive.inrush = 0.0f;
                drive.electricDirty = true;
            }
        }
        if (drive.speed != 0.0f && drive.brushes > 0.0f) {
            boolean ultra = de.ipnats.hardwrought.survival.Ultra.active(server.getServer());
            drive.brushes -= ultra ? 0.1f : 0.05f;
            if (drive.brushes <= 0.0f) {
                // Worn through to the springs: the current no longer reaches the commutator.
                drive.brushes = 0.0f;
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 0.6f);
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.1);
                drive.kineticDirty = true;
                drive.electricDirty = true;
            }
            drive.setChanged();
        }
        boolean running = drive.speed != 0.0f;
        if (state.getValue(ElectricDriveBlock.RUNNING) != running) {
            level.setBlock(pos, state.setValue(ElectricDriveBlock.RUNNING, running), Block.UPDATE_CLIENTS);
        }
        if (drive.electricDirty) {
            drive.electricDirty = false;
            Electricity.update(server, pos);
        }
    }

    /** Wears the brushes as this much running would. The tick does it a twentieth at a time; tests in one go. */
    public void wearBrushes(float seconds) {
        boolean had = brushes > 0.0f;
        brushes = Math.max(0.0f, brushes - seconds);
        if (had && brushes <= 0.0f) {
            kineticDirty = true;
            electricDirty = true;
        }
        setChanged();
    }

    /** A new pair of brushes, where the old are down to a quarter. False where they are not worn enough yet. */
    public boolean newBrushes() {
        if (brushes > BRUSH_SECONDS / 4.0f) return false;
        boolean worn = brushes <= 0.0f;
        brushes = BRUSH_SECONDS;
        setChanged();
        if (worn) {
            kineticDirty = true;
            electricDirty = true;
        }
        return true;
    }

    /** With a controller: one quarter less, round from a quarter back to the whole. Returns the new setting. */
    public int turnDown() {
        quarters = quarters <= 1 ? 4 : quarters - 1;
        setChanged();
        // The speed follows at once: the voltage it is let have has changed.
        if (level instanceof ServerLevel server) {
            drive = 0.0f;
            Electricity.update(server, worldPosition);
            kineticDirty = true;
        }
        return quarters;
    }

    /** Rewound: its windings whole again. */
    public void rewound() {
        strain = 0.0f;
        electricDirty = true;
        setChanged();
    }

    @Override
    public Component note() {
        if (broken()) return Component.translatable("message.hardwrought.meter.motor_burnt");
        if (brushes <= 0.0f) return Component.translatable("message.hardwrought.meter.drive.brushes_worn");
        String wear = String.format(Locale.ROOT, "%.0f", brushes() * 100.0f);
        if (stalled) return Component.translatable(controlled() ? "message.hardwrought.meter.drive.held" : "message.hardwrought.meter.drive.stalled");
        if (drive() <= 0.0f) return Component.translatable("message.hardwrought.meter.drive.still", wear);
        return Component.translatable("message.hardwrought.meter.drive.turning", String.format(Locale.ROOT, "%.0f", Math.abs(speed)),
                String.format(Locale.ROOT, "%.0f", Math.min(1.0, load / RATED_AMPS) * 100.0), wear);
    }

    /** Taken down, the controller comes off with it. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide() && state.getValue(ElectricDriveBlock.CONTROLLED)) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Parts.MOTOR_CONTROLLER));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        speed = input.getFloatOr("speed", 0.0f);
        drive = input.getFloatOr("drive", 0.0f);
        load = input.getFloatOr("load", 0.0f);
        stalled = input.getBooleanOr("stalled", false);
        brushes = input.getFloatOr("brushes", BRUSH_SECONDS);
        quarters = Math.clamp(input.getIntOr("quarters", 4), 1, 4);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putFloat("speed", speed);
        output.putFloat("drive", drive);
        output.putFloat("load", load);
        output.putBoolean("stalled", stalled);
        output.putFloat("brushes", brushes);
        output.putInt("quarters", quarters);
    }
}
