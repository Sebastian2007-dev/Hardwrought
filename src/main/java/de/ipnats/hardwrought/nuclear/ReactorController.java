package de.ipnats.hardwrought.nuclear;

import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.electricity.MachineBlockEntity;
import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.gasline.GasStorage;
import de.ipnats.hardwrought.gasline.GasStore;
import de.ipnats.hardwrought.multiblock.BigMachines;
import de.ipnats.hardwrought.multiblock.ControllerBlock;
import de.ipnats.hardwrought.multiblock.PortBlock;
import de.ipnats.hardwrought.multiblock.PortHost;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.function.Predicate;

/**
 * The reactor: a 5×5×5 vessel with this controller in the middle of one side.
 *
 * <pre>
 *   top (y+2)      wall, with up to nine control rod drives in its middle 3×3
 *   y+1            graphite reflector, 3×3, inside the wall
 *   y 0            nine fuel channels, 3×3, inside the wall — the core
 *   y-1            graphite reflector, 3×3, inside the wall
 *   bottom (y-2)   wall
 * </pre>
 * Ports may take the place of any wall block but the head's middle: a fluid port for purified feedwater,
 * a gas port the steam leaves by, an item port to load and unload fuel, and a power port for the main
 * coolant pumps and the rod drives. The rods hang on their drives by electromagnets: cut the current
 * and they drop, which is why a reactor needs power from somewhere else before it can make its own.
 *
 * <p>Fuel goes into nine channels, one assembly or one thorium breeder rod each, through the window or
 * the item port, but only while the core is shut down, its rods in. The physics is in {@link ReactorCore}.
 */
public class ReactorController extends ControllerBlock.Entity implements WorldlyContainer, ExtendedMenuProvider<BlockPos>, PortHost {
    public static final int CHANNELS = 9;
    /** The pumps and the drives, as a load on the line. */
    public static final double OHMS = 15.0;
    /** The heat one unit of steam carries off, in joules; and the feedwater it is made of, in millibuckets. */
    public static final double STEAM_UNIT_JOULES = 4000;
    public static final int WATER_PER_STEAM = 2;
    public static final int FEEDWATER_BUCKETS = 16, STEAM_CAPACITY = 400;
    /** The steam drum's rating, in bar: a line to it wants chromium or titanium. */
    public static final double STEAM_BAR = 30;
    /** Dose rate at a metre from the core, at full power and per share of decay heat, in Sv/h. */
    static final double RADIATION_AT_POWER = 1000, RADIATION_PER_DECAY = 3000;
    private static final double TICK = 0.05;
    private static final int[] ALL_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};

    private final NonNullList<ItemStack> channels = NonNullList.withSize(CHANNELS, ItemStack.EMPTY);
    private final ReactorCore core = new ReactorCore();
    public final FeedwaterTank feedwater = new FeedwaterTank(FEEDWATER_BUCKETS, true, this::setChanged);
    public final GasStore steam = new GasStore(STEAM_CAPACITY, STEAM_BAR, false, this::setChanged);
    private double steamCarried;
    private double volts;
    private int drives;
    private boolean venting;
    private ReactorCore.Trip told = ReactorCore.Trip.NONE;

    public ReactorController(BlockPos pos, BlockState state) {
        super(NuclearBlocks.REACTOR_ENTITY, pos, state);
    }

    public ReactorCore core() {
        return core;
    }

    @Override
    protected int reach() {
        return 2;
    }

    @Override
    protected Block casing() {
        return NuclearBlocks.REACTOR_VESSEL_WALL;
    }

    /** Whether a place is inside the vessel's walls. */
    private static boolean inside(int dx, int dy, int dz) {
        return Math.abs(dx) <= 1 && Math.abs(dy) <= 1 && Math.abs(dz) <= 1;
    }

    /** Whether a place is in the middle of the vessel's head, where the rod drives stand. */
    private static boolean head(int dx, int dy, int dz) {
        return dy == 2 && Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
    }

    @Override
    protected Predicate<BlockState> special(int dx, int dy, int dz) {
        if (inside(dx, dy, dz)) {
            return dy == 0 ? state -> state.is(NuclearBlocks.FUEL_CHANNEL) : state -> state.is(BigMachines.GRAPHITE_ELECTRODE);
        }
        if (head(dx, dy, dz)) {
            return state -> state.is(NuclearBlocks.CONTROL_ROD_DRIVE) || state.is(NuclearBlocks.REACTOR_VESSEL_WALL);
        }
        return null;
    }

    @Override
    protected Component specialName(int dx, int dy, int dz) {
        if (inside(dx, dy, dz)) return dy == 0 ? NuclearBlocks.FUEL_CHANNEL.getName() : BigMachines.GRAPHITE_ELECTRODE.getName();
        return NuclearBlocks.CONTROL_ROD_DRIVE.getName();
    }

    /** The control rod drives on the head. */
    private int countDrives(ServerLevel level) {
        int count = 0;
        BlockPos centre = centre();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.getBlockState(centre.offset(dx, 2, dz)).is(NuclearBlocks.CONTROL_ROD_DRIVE)) count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- the line

    @Override
    public double conductance() {
        return formed() ? 1.0 / OHMS : 0.0;
    }

    @Override
    public void solved(double volts, double amps, double seconds) {
        this.volts = Math.abs(volts);
    }

    @Override
    public Storage<FluidVariant> fluid(BlockPos port) {
        return feedwater;
    }

    @Override
    public GasStorage gas(BlockPos port) {
        return steam;
    }

    @Override
    public Storage<ItemVariant> items(BlockPos port, Direction side) {
        return ContainerStorage.of(this, side == null ? Direction.UP : side);
    }

    /** What the pumps and drives get: nothing below thirty volts, full above sixty. */
    private double supply() {
        if (port(PortBlock.Kind.POWER) == null) return 0.0;
        return Math.min(1.0, MachineBlockEntity.rate(volts));
    }

    // ---------------------------------------------------------------- the core at work

    private ReactorCore.Conditions conditions(ServerLevel level, boolean whole) {
        ReactorCore.Load[] loads = new ReactorCore.Load[CHANNELS];
        float[] burnup = new float[CHANNELS];
        for (int i = 0; i < CHANNELS; i++) {
            ItemStack stack = channels.get(i);
            loads[i] = stack.is(NuclearItems.FUEL_ASSEMBLY) ? ReactorCore.Load.FUEL
                    : stack.is(NuclearItems.THORIUM_BREEDER_ROD) ? ReactorCore.Load.BREEDER
                    : stack.isEmpty() ? ReactorCore.Load.EMPTY : ReactorCore.Load.SPENT;
            burnup[i] = ReactorFuelItem.burnup(stack);
        }
        double supply = whole ? supply() : 0.0;
        boolean water = feedwater.millibuckets() >= WATER_PER_STEAM;
        // The rods hang in the core whether the hull is whole or not: a broken hull cuts their drives' current,
        // and they drop, but they are still there to stop the reaction.
        return new ReactorCore.Conditions(loads, burnup, drives, supply > 0.0,
                Math.max(ReactorCore.NATURAL_FLOW, supply), water, level.hasNeighborSignal(worldPosition));
    }

    @Override
    protected void work(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 20 == 0) drives = countDrives(level);
        run(level, true);
    }

    @Override
    protected void idle(ServerLevel level, BlockPos pos, BlockState state) {
        // A broken vessel: the rods drop and the pumps stop, but the heat of the core is still there.
        if (core.power() > 0 || core.decay() > 1e-6 || core.temperature() > 25) run(level, false);
        else Radiation.source(level, centre(), 0);
    }

    private void run(ServerLevel level, boolean whole) {
        ReactorCore.Result result = core.step(conditions(level, whole), TICK);

        // Steam out of the heat carried off; the water it was made of out of the tank.
        steamCarried += result.steamJoules() / STEAM_UNIT_JOULES;
        venting = false;
        while (steamCarried >= 1.0 && feedwater.take(WATER_PER_STEAM)) {
            steamCarried -= 1.0;
            if (steam.produce(GasKind.STEAM, 1.0) < 1.0) venting = true;
        }
        if (steamCarried > 1.0) steamCarried = 1.0;
        if (venting && level.getGameTime() % 10 == 0) {
            // The relief valve lifts: the steam no line takes goes up out of the head.
            BlockPos top = centre().above(3);
            level.sendParticles(ParticleTypes.CLOUD, top.getX() + 0.5, top.getY() + 0.2, top.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.05);
            if (level.getGameTime() % 40 == 0) level.playSound(null, top, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 0.6f);
        }

        burn(result.burnPerAssembly());
        Radiation.source(level, centre(), RADIATION_AT_POWER * core.power() + RADIATION_PER_DECAY * core.decay());
        tellTrip(level);
        if (core.temperature() >= ReactorCore.MELTS) {
            Meltdown.melt(level, this);
            return;
        }
        if (core.temperature() >= ReactorCore.CLADDING_FAILS && level.getGameTime() % 100 == 0) Meltdown.leak(level, this);
        showRunning(level, core.power() > 0.01);
        if (level.getGameTime() % 20 == 0) setChanged();
    }

    /** Burns the fuel and breeds the thorium by a step's share; what is done is changed for what it has become. */
    private void burn(double share) {
        if (share <= 0) return;
        for (int i = 0; i < CHANNELS; i++) {
            ItemStack stack = channels.get(i);
            boolean fuel = stack.is(NuclearItems.FUEL_ASSEMBLY), breeder = stack.is(NuclearItems.THORIUM_BREEDER_ROD);
            if (!fuel && !breeder) continue;
            float burnup = ReactorFuelItem.burnup(stack) + (float) (breeder ? share * 0.5 : share);
            if (burnup >= 1.0f) {
                channels.set(i, new ItemStack(fuel ? NuclearItems.SPENT_FUEL_ASSEMBLY : NuclearItems.IRRADIATED_THORIUM_ROD));
            } else {
                ReactorFuelItem.setBurnup(stack, burnup);
            }
        }
    }

    /** Rings once when the rods drop by themselves. */
    private void tellTrip(ServerLevel level) {
        ReactorCore.Trip trip = core.trip();
        if (trip == told) return;
        told = trip;
        if (trip == ReactorCore.Trip.NONE) return;
        level.playSound(null, worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0f, 0.7f);
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(worldPosition) < 48 * 48) {
                player.sendOverlayMessage(Component.translatable("message.hardwrought.reactor.scram",
                        Component.translatable("gui.hardwrought.reactor.trip." + trip.name().toLowerCase(java.util.Locale.ROOT))));
            }
        }
        setChanged();
    }

    @Override
    protected void changed(ServerLevel level) {
        drives = countDrives(level);
        BlockPos power = port(PortBlock.Kind.POWER);
        if (power != null) Electricity.update(level, power);
    }

    // ---------------------------------------------------------------- the operator

    /** A button of the window: the rods set in or out, the setpoint up or down, a scram, or a reset. */
    public void press(int button, ServerLevel level) {
        switch (button) {
            case ReactorMenu.INSERT_MORE -> core.setRods(core.rodsSet() + 0.1);
            case ReactorMenu.INSERT -> core.setRods(core.rodsSet() + 0.01);
            case ReactorMenu.WITHDRAW -> core.setRods(core.rodsSet() - 0.01);
            case ReactorMenu.WITHDRAW_MORE -> core.setRods(core.rodsSet() - 0.1);
            case ReactorMenu.SCRAM -> core.scram();
            case ReactorMenu.RESET -> core.reset(conditions(level, formed()));
            // The setpoint in steps of a tenth; from below a tenth down it goes back to the hand.
            case ReactorMenu.SETPOINT_UP -> core.setSetpoint(core.setpoint() < 0 ? 0.1 : core.setpoint() + 0.1);
            case ReactorMenu.SETPOINT_DOWN -> core.setSetpoint(core.setpoint() < 0.15 ? -1 : core.setpoint() - 0.1);
            default -> { }
        }
        setChanged();
    }

    /** Whether the core may be opened: rods in and the chain reaction stopped. */
    public boolean refuelable() {
        return core.shutDown();
    }

    // ---------------------------------------------------------------- the window

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case ReactorMenu.POWER -> clamp(core.power() * 1000);
                case ReactorMenu.TEMPERATURE -> clamp(core.temperature());
                case ReactorMenu.PRESSURE -> clamp(core.pressure() * 10);
                case ReactorMenu.REACTIVITY -> clamp(core.reactivity() * 1e4);
                case ReactorMenu.RODS -> clamp(core.rods() * 1000);
                case ReactorMenu.RODS_SET -> clamp(core.rodsSet() * 1000);
                case ReactorMenu.XENON -> clamp(core.xenon() * 1000);
                case ReactorMenu.DECAY -> clamp(core.decay() * 1e4);
                case ReactorMenu.TRIP -> core.trip().ordinal();
                case ReactorMenu.DRIVES -> drives;
                case ReactorMenu.FLOW -> clamp(Math.max(ReactorCore.NATURAL_FLOW, formed() ? supply() : 0.0) * 1000);
                case ReactorMenu.WATER -> clamp(feedwater.millibuckets());
                case ReactorMenu.STEAM -> clamp(steam.amount());
                case ReactorMenu.VOLTS -> clamp(volts);
                case ReactorMenu.VENTING -> venting ? 1 : 0;
                case ReactorMenu.SETPOINT -> core.setpoint() < 0 ? -1 : clamp(core.setpoint() * 1000);
                // The power on a log scale, in hundredths of a decade: a startup climbs through five of them.
                case ReactorMenu.LOG_POWER -> clamp(Math.log10(Math.max(core.power(), 1e-9)) * 100);
                case ReactorMenu.PERIOD -> clamp(core.period());
                case ReactorMenu.FUEL -> (int) channels.stream().filter(stack -> stack.is(NuclearItems.FUEL_ASSEMBLY)).count();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) { }

        @Override
        public int getCount() {
            return ReactorMenu.DATA_COUNT;
        }
    };

    private static int clamp(double value) {
        return (int) Math.round(Math.clamp(value, Short.MIN_VALUE, Short.MAX_VALUE));
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ReactorMenu(id, inventory, this, data, worldPosition);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, channels);
        core.save(output);
        feedwater.save(output, "feedwater");
        steam.save(output, "steam");
        output.putDouble("steam_carried", steamCarried);
        output.putInt("drives", drives);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        channels.clear();
        ContainerHelper.loadAllItems(input, channels);
        core.load(input);
        told = core.trip();
        feedwater.load(input, "feedwater");
        steam.load(input, "steam");
        steamCarried = input.getDoubleOr("steam_carried", 0);
        drives = input.getIntOr("drives", 0);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel server) Radiation.source(server, centre(), 0);
        super.setRemoved();
    }

    // ---------------------------------------------------------------- the channels, as a container

    /** What a channel takes: fresh fuel or a breeder rod, one each. */
    public static boolean fits(ItemStack stack) {
        return stack.is(NuclearItems.FUEL_ASSEMBLY) || stack.is(NuclearItems.THORIUM_BREEDER_ROD);
    }

    @Override
    public int getContainerSize() {
        return CHANNELS;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : channels) if (!stack.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return channels.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(channels, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(channels, slot);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        channels.set(slot, stack);
        stack.limitSize(1);
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return refuelable() && channels.get(slot).isEmpty() && fits(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        channels.clear();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ALL_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    /** Through a port only what is done comes out: spent fuel and bred thorium, and only with the core shut down. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return refuelable() && !fits(stack);
    }

    /** The channels, for the meltdown: what is in them is lost in it. */
    NonNullList<ItemStack> channels() {
        return channels;
    }
}
