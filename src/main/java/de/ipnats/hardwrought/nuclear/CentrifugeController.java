package de.ipnats.hardwrought.nuclear;

import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.electricity.MachineBlockEntity;
import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.gasline.GasStorage;
import de.ipnats.hardwrought.gasline.GasStore;
import de.ipnats.hardwrought.materials.ChemMachine;
import de.ipnats.hardwrought.materials.ChemMenu;
import de.ipnats.hardwrought.materials.ChemWork;
import de.ipnats.hardwrought.multiblock.ControllerBlock;
import de.ipnats.hardwrought.multiblock.PortBlock;
import de.ipnats.hardwrought.multiblock.PortHost;
import de.ipnats.hardwrought.refining.RefineryMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

import java.util.List;
import java.util.function.Predicate;

/**
 * The centrifuge cascade: a 3×3×3 hull of centrifuge casing with five gas centrifuges in its middle layer
 * — one in the centre, one in each corner — and this controller in one side.
 *
 * <p>Natural uranium hexafluoride goes in through a gas port in a wall; enriched hexafluoride comes out
 * through a gas port in the roof; the tails, depleted of their uranium-235, come out as depleted uranium
 * through an item port. Of ten units of natural feed, about one comes out enriched to reactor grade, as
 * a real cascade gives about one part of 4 % product for nine or ten of feed.
 */
public class CentrifugeController extends ControllerBlock.Entity implements WorldlyContainer, ExtendedMenuProvider<BlockPos>, PortHost {
    public static final double OHMS = 8.0;
    public static final double FEED = 10, PRODUCT = 1;
    private static final int[] ALL_SLOTS = {0, 1, 2, 3};

    private static List<ChemMachine.Recipe> recipes;

    public final GasStore feed = new GasStore(64, 4, true, kind -> kind == GasKind.URANIUM_HEXAFLUORIDE, this::setChanged);
    public final GasStore product = new GasStore(64, 8, false, this::setChanged);
    private final ChemWork work = new ChemWork(null, feed, product);
    private float progress;
    private boolean asking;
    private ChemMachine.Recipe current;
    private double volts;

    public CentrifugeController(BlockPos pos, BlockState state) {
        super(NuclearBlocks.CENTRIFUGE_ENTITY, pos, state);
    }

    /** The cascade's one piece of work. Built on first use: the items are registered after this class loads. */
    public static List<ChemMachine.Recipe> recipes() {
        if (recipes == null) {
            recipes = List.of(new ChemMachine.Recipe("centrifuge_cascade/enrichment", List.of(), null, 0,
                    GasKind.URANIUM_HEXAFLUORIDE, FEED, List.of(new ItemStack(NuclearItems.DEPLETED_URANIUM)),
                    GasKind.ENRICHED_HEXAFLUORIDE, PRODUCT, 200));
        }
        return recipes;
    }

    @Override
    protected Block casing() {
        return NuclearBlocks.CENTRIFUGE_CASING;
    }

    @Override
    protected Predicate<BlockState> special(int dx, int dy, int dz) {
        if (dy != 0) return null;
        boolean centre = dx == 0 && dz == 0, corner = dx != 0 && dz != 0;
        return centre || corner ? state -> state.is(NuclearBlocks.GAS_CENTRIFUGE) : null;
    }

    @Override
    protected Component specialName(int dx, int dy, int dz) {
        return NuclearBlocks.GAS_CENTRIFUGE.getName();
    }

    // ---------------------------------------------------------------- the ports

    @Override
    public double conductance() {
        return current != null ? 1.0 / OHMS : 0.0;
    }

    @Override
    public void solved(double volts, double amps, double seconds) {
        this.volts = Math.abs(volts);
    }

    /** A gas port in the roof gives the product; one in a wall takes the feed. */
    @Override
    public GasStorage gas(BlockPos port) {
        return port.getY() > centre().getY() ? product : feed;
    }

    @Override
    public Storage<ItemVariant> items(BlockPos port, Direction side) {
        return ContainerStorage.of(this, side == null ? Direction.UP : side);
    }

    // ---------------------------------------------------------------- at work

    @Override
    protected void work(ServerLevel level, BlockPos pos, BlockState state) {
        ChemMachine.Recipe recipe = work.workable(recipes());
        if (recipe != current) {
            current = recipe;
            progress = 0;
        }
        boolean wants = recipe != null && port(PortBlock.Kind.POWER) != null;
        if (wants != asking) {
            asking = wants;
            BlockPos power = port(PortBlock.Kind.POWER);
            if (power != null) Electricity.update(level, power);
        }
        double rate = wants ? MachineBlockEntity.rate(volts) : 0.0;
        boolean working = rate > 0.0;
        if (working) {
            progress += (float) rate;
            if (progress >= recipe.ticks()) {
                work.work(recipe);
                progress = 0;
            }
            if (level.getGameTime() % 30 == 0) {
                level.playSound(null, centre(), SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.4f, 1.8f);
            }
            setChanged();
        }
        showRunning(level, working);
    }

    @Override
    protected void changed(ServerLevel level) {
        if (!formed()) {
            asking = false;
            showRunning(level, false);
        }
    }

    // ---------------------------------------------------------------- the window

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case ChemMenu.PROGRESS -> current == null ? 0 : Math.round(progress * 1000.0f / current.ticks());
                case ChemMenu.VOLTS -> (int) Math.round(volts * 10.0);
                case ChemMenu.STATE -> current == null ? ChemMenu.IDLE
                        : MachineBlockEntity.rate(volts) <= 0.0 ? ChemMenu.STALLED : ChemMenu.RUNNING;
                case ChemMenu.FLUID -> RefineryMenu.NOTHING;
                case ChemMenu.GAS_IN -> feed.kind() == null ? RefineryMenu.NOTHING : RefineryMenu.substance(feed.kind());
                case ChemMenu.GAS_IN_AMOUNT -> (int) Math.round(feed.amount());
                case ChemMenu.GAS_OUT -> product.kind() == null ? RefineryMenu.NOTHING : RefineryMenu.substance(product.kind());
                case ChemMenu.GAS_OUT_AMOUNT -> (int) Math.round(product.amount());
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) { }

        @Override
        public int getCount() {
            return ChemMenu.DATA_COUNT;
        }
    };

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ChemMenu(id, inventory, this, data, worldPosition);
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, work.items);
        feed.save(output, "feed");
        product.save(output, "product");
        output.putFloat("progress", progress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        work.items.clear();
        ContainerHelper.loadAllItems(input, work.items);
        feed.load(input, "feed");
        product.load(input, "product");
        progress = input.getFloatOr("progress", 0.0f);
    }

    // ---------------------------------------------------------------- container: only the tails come out

    @Override
    public int getContainerSize() {
        return ChemWork.SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : work.items) if (!stack.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return work.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(work.items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(work.items, slot);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        work.items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return formed() && Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        work.items.clear();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ALL_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= ChemWork.FIRST_OUTPUT;
    }
}
