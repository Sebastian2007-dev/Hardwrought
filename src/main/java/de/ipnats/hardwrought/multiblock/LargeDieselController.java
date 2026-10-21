package de.ipnats.hardwrought.multiblock;

import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.power.DieselGeneratorBlock;
import de.ipnats.hardwrought.power.Exhaust;
import de.ipnats.hardwrought.refining.ProcessTank;
import de.ipnats.hardwrought.refining.RefineryMenu;
import de.ipnats.hardwrought.refining.RefineryProduct;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Set;
import java.util.function.Predicate;

/**
 * The large diesel generator: a 3×3×3 hull of engine casing round an engine core, with this controller in
 * one side. Fuel comes in through a fluid port, current goes out through a power port with a copper coil
 * on it. It gives the same {@value #VOLTS} V as the small generator but through a fifth of its
 * resistance, so five times the current, and it gets more out of its fuel. Its exhaust goes out over
 * the top of the hull.
 */
public class LargeDieselController extends ControllerBlock.Entity implements ExtendedMenuProvider<BlockPos>, PortHost {
    public static final double VOLTS = DieselGeneratorBlock.Entity.VOLTS;
    public static final double OHMS = 0.1;
    public static final int BUCKETS = 16;
    /** Fuel burnt idling, in millibuckets a second, and for each watt-second given: a fifth less than the small one. */
    public static final double IDLE_PER_SECOND = 0.5, PER_JOULE = 0.0008;

    public final ProcessTank fuel = ProcessTank.inlet(BUCKETS,
            Set.of(RefineryProduct.DIESEL.source(), RefineryProduct.FUEL_FRACTION.source(), RefineryProduct.KEROSENE.source()),
            this::setChanged);
    private boolean running;
    private double burning, exhaust;
    private float watts;

    public LargeDieselController(BlockPos pos, BlockState state) {
        super(BigMachines.LARGE_DIESEL_ENTITY, pos, state);
    }

    @Override
    protected Block casing() {
        return BigMachines.ENGINE_CASING;
    }

    @Override
    protected Predicate<BlockState> special(int dx, int dy, int dz) {
        return dx == 0 && dy == 0 && dz == 0 ? state -> state.is(BigMachines.ENGINE_CORE) : null;
    }

    @Override
    protected Component specialName(int dx, int dy, int dz) {
        return BigMachines.ENGINE_CORE.getName();
    }

    public boolean running() {
        return running;
    }

    @Override
    public double emf() {
        return running ? VOLTS : 0.0;
    }

    @Override
    public double sourceOhms() {
        return OHMS;
    }

    @Override
    public boolean pulses() {
        return true;
    }

    @Override
    public net.fabricmc.fabric.api.transfer.v1.storage.Storage<net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant> fluid(BlockPos port) {
        return fuel;
    }

    /** Told by its power port what the line took of it. */
    @Override
    public void solved(double volts, double amps, double seconds) {
        watts = running ? (float) Math.abs(volts * amps) : 0.0f;
        Fluid held = fuel.held();
        if (running && seconds > 0 && held != null) {
            burning += seconds * (IDLE_PER_SECOND + watts * PER_JOULE) / worth(held);
        }
    }

    private static double worth(Fluid fuel) {
        if (fuel == RefineryProduct.KEROSENE.source()) return 0.9;
        if (fuel == RefineryProduct.FUEL_FRACTION.source()) return 0.75;
        return 1.0;
    }

    @Override
    protected void work(ServerLevel level, BlockPos pos, BlockState state) {
        boolean run = fuel.millibuckets() >= 1 && port(PortBlock.Kind.POWER) != null && !level.hasNeighborSignal(pos);
        if (run != running) {
            running = run;
            setChanged();
            showRunning(level, run);
            BlockPos power = port(PortBlock.Kind.POWER);
            if (power != null) Electricity.update(level, power);
        }
        while (burning >= 1 && fuel.take(1)) {
            burning -= 1;
            exhaust += 1;
        }
        if (exhaust >= 20) {
            int units = (int) (exhaust / 20);
            exhaust -= units * 20;
            Exhaust.emit(level, centre().above(), units, level.getRandom().nextInt(6) < units ? 1 : 0);
        }
    }

    @Override
    protected void changed(ServerLevel level) {
        if (!formed() && running) {
            running = false;
            showRunning(level, false);
        }
    }

    RefineryMenu.Panel panel() {
        Fluid held = fuel.held();
        return new RefineryMenu.Panel(RefineryMenu.KIND_DIESEL, running, running, 0, Math.round(watts))
                .liquid(held == null ? RefineryProduct.DIESEL.source() : held, fuel, BUCKETS * 1000);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RefineryMenu(id, RefineryMenu.data(this::panel), worldPosition);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        fuel.save(output, "fuel");
        output.putBoolean("running", running);
        output.putDouble("burning", burning);
        output.putDouble("exhaust", exhaust);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        fuel.load(input, "fuel");
        running = input.getBooleanOr("running", false);
        burning = input.getDoubleOr("burning", 0);
        exhaust = input.getDoubleOr("exhaust", 0);
    }
}
