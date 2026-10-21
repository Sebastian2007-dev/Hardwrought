package de.ipnats.hardwrought.nuclear;

import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.gasline.GasStorage;
import de.ipnats.hardwrought.gasline.GasStore;
import de.ipnats.hardwrought.multiblock.ControllerBlock;
import de.ipnats.hardwrought.multiblock.PortBlock;
import de.ipnats.hardwrought.multiblock.PortHost;
import de.ipnats.hardwrought.refining.RefineryMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.function.Predicate;

/**
 * The steam turbine and its generator: a 3×3×3 hull of turbine casing round a rotor, a condenser under
 * it, and this controller in one side.
 *
 * <p>Steam comes in through a gas port; the generator drives {@value #VOLTS} V through a power port
 * with a copper coil on it, as stiff as the large diesel; the condensate goes out through a fluid port
 * as purified water, ready to go back to the reactor. A third of the steam's heat becomes current, as in
 * any steam plant; the rest goes into the condenser. With little steam in its chest the turbine slows
 * and its voltage sags; with none it stops.
 */
public class TurbineController extends ControllerBlock.Entity implements ExtendedMenuProvider<BlockPos>, PortHost {
    public static final double VOLTS = 240.0;
    public static final double OHMS = 0.3;
    /** Of the heat in the steam, what becomes current. */
    public static final double EFFICIENCY = 0.33;
    /** Of the water that went in as steam, what the condenser gets back. */
    public static final double CONDENSED = 0.95;
    public static final int STEAM_CAPACITY = 400, CONDENSATE_BUCKETS = 16;
    /** The share of a full steam chest at which the turbine runs at its full speed. */
    static final double FULL_CHEST = 0.2;

    public final GasStore steam = new GasStore(STEAM_CAPACITY, ReactorController.STEAM_BAR, true, kind -> kind == GasKind.STEAM,
            this::setChanged);
    public final FeedwaterTank condensate = new FeedwaterTank(CONDENSATE_BUCKETS, false, this::setChanged);
    private double used, condensing;
    private double emf;
    private float watts;

    public TurbineController(BlockPos pos, BlockState state) {
        super(NuclearBlocks.TURBINE_ENTITY, pos, state);
    }

    @Override
    protected Block casing() {
        return NuclearBlocks.TURBINE_CASING;
    }

    @Override
    protected Predicate<BlockState> special(int dx, int dy, int dz) {
        if (dx != 0 || dz != 0) return null;
        if (dy == 0) return state -> state.is(NuclearBlocks.TURBINE_ROTOR);
        if (dy == -1) return state -> state.is(NuclearBlocks.CONDENSER);
        return null;
    }

    @Override
    protected Component specialName(int dx, int dy, int dz) {
        return dy == 0 ? NuclearBlocks.TURBINE_ROTOR.getName() : NuclearBlocks.CONDENSER.getName();
    }

    /** The voltage at the speed the steam in its chest allows. */
    private double speed() {
        if (!formed() || steam.amount() < 1.0 || port(PortBlock.Kind.POWER) == null) return 0.0;
        return VOLTS * Math.min(1.0, steam.amount() / (STEAM_CAPACITY * FULL_CHEST));
    }

    @Override
    public double emf() {
        return emf;
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
    public GasStorage gas(BlockPos port) {
        return steam;
    }

    @Override
    public Storage<FluidVariant> fluid(BlockPos port) {
        return condensate;
    }

    @Override
    public void solved(double volts, double amps, double seconds) {
        watts = emf > 0 ? (float) Math.abs(volts * amps) : 0.0f;
        if (emf > 0 && seconds > 0) used += watts * seconds / (ReactorController.STEAM_UNIT_JOULES * EFFICIENCY);
    }

    @Override
    protected void work(ServerLevel level, BlockPos pos, BlockState state) {
        double now = speed();
        // The governor: the line is solved again only when the speed has changed enough to tell.
        if (Math.abs(now - emf) > VOLTS * 0.05 || (now == 0) != (emf == 0)) {
            emf = now;
            BlockPos power = port(PortBlock.Kind.POWER);
            if (power != null) Electricity.update(level, power);
        }
        double taken = steam.extract(Math.min(used, steam.amount()));
        used -= taken;
        if (used > STEAM_CAPACITY) used = STEAM_CAPACITY;
        condensing += taken * ReactorController.WATER_PER_STEAM * CONDENSED;
        if (condensing >= 1) {
            long whole = (long) condensing;
            condensing -= whole;
            condensate.fill(whole);
        }
        boolean running = emf > 0;
        showRunning(level, running);
        if (running && level.getGameTime() % 40 == 0) {
            BlockPos top = centre().above(2);
            level.sendParticles(ParticleTypes.CLOUD, top.getX() + 0.5, top.getY() + 0.1, top.getZ() + 0.5, 2, 0.3, 0.05, 0.3, 0.02);
            level.playSound(null, centre(), SoundEvents.MINECART_INSIDE, SoundSource.BLOCKS, 0.15f, 0.5f + (float) (emf / VOLTS));
        }
        if (level.getGameTime() % 20 == 0) setChanged();
    }

    @Override
    protected void changed(ServerLevel level) {
        if (!formed() && emf > 0) {
            emf = 0;
            watts = 0;
            showRunning(level, false);
        }
    }

    RefineryMenu.Panel panel() {
        // The window's numbers are sent as shorts: the output goes in tens of watts.
        return new RefineryMenu.Panel(RefineryMenu.KIND_TURBINE, emf > 0, emf > 0, (int) Math.round(emf / VOLTS * 1000), Math.round(watts / 10))
                .gauge(RefineryMenu.substance(GasKind.STEAM), Math.round(steam.amount()), STEAM_CAPACITY)
                .gauge(RefineryMenu.substance(Fluids.WATER), condensate.millibuckets(), condensate.capacityMillibuckets());
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
        steam.save(output, "steam");
        condensate.save(output, "condensate");
        output.putDouble("used", used);
        output.putDouble("condensing", condensing);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        steam.load(input, "steam");
        condensate.load(input, "condensate");
        used = input.getDoubleOr("used", 0);
        condensing = input.getDoubleOr("condensing", 0);
    }
}
