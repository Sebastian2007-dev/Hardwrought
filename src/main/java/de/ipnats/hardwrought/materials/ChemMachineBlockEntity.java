package de.ipnats.hardwrought.materials;

import de.ipnats.hardwrought.electricity.ElectricBlockEntity;
import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.electricity.MachineBlockEntity;
import de.ipnats.hardwrought.gasline.GasKind;
import de.ipnats.hardwrought.gasline.GasStore;
import de.ipnats.hardwrought.refining.ProcessTank;
import de.ipnats.hardwrought.refining.RefineryMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;


/**
 * A chemistry machine at work. Two input slots and two output slots, a tank for the liquid it takes, a
 * store for the gas it takes in and one for the gas it gives out. It looks for the first of its
 * {@link ChemMachine#recipes} that what it holds will do, and works it as the crusher works a piece of
 * ore: on current, at the square of its voltage against the rated sixty, nothing below thirty, and
 * taking current only while there is work it can do.
 *
 * <p>Hoppers and item pipes fill the inputs from the top and sides and draw the outputs from the bottom.
 */
public class ChemMachineBlockEntity extends ElectricBlockEntity implements WorldlyContainer, ExtendedMenuProvider<BlockPos> {
    public static final int FIRST_INPUT = ChemWork.FIRST_INPUT, INPUTS = ChemWork.INPUTS, FIRST_OUTPUT = ChemWork.FIRST_OUTPUT,
            SIZE = ChemWork.SIZE;
    private static final int[] INPUT_SLOTS = {0, 1};
    private static final int[] OUTPUT_SLOTS = {2, 3};

    private final ChemWork work;
    private final NonNullList<ItemStack> items;
    private final ProcessTank fluid;
    public final GasStore gasIn;
    public final GasStore gasOut;
    private float progress;
    private boolean asking;
    private ChemMachine.Recipe current;

    public ChemMachineBlockEntity(BlockPos pos, BlockState state) {
        super(MaterialBlocks.CHEM_MACHINE_ENTITY, pos, state);
        ChemMachine machine = machineOf(state);
        fluid = machine.fluids().isEmpty() ? null : ProcessTank.inlet(4, machine.fluids(), this::setChanged);
        gasIn = new GasStore(64, 2, !machine.gases().isEmpty(), machine.gases()::contains, this::setChanged);
        gasOut = new GasStore(64, 8, false, this::setChanged);
        work = new ChemWork(fluid, gasIn, gasOut);
        items = work.items;
    }

    private static ChemMachine machineOf(BlockState state) {
        return state.getBlock() instanceof ChemMachineBlock block ? block.machine() : ChemMachine.ELECTROLYZER;
    }

    public ChemMachine machine() {
        return machineOf(getBlockState());
    }

    /** The tank pipes fill; null for a machine that takes no liquid. */
    public ProcessTank fluid() {
        return fluid;
    }

    /** The recipe it would work now, or null for none it can. */
    public ChemMachine.Recipe workable() {
        return work.workable(machine().recipes());
    }

    @Override
    public double conductance() {
        return current != null ? 1.0 / machine().ohms() : 0.0;
    }

    @Override
    public boolean takesWires() {
        return false;
    }

    @Override
    public boolean mindsRain() {
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChemMachineBlockEntity entity) {
        if (!(level instanceof ServerLevel server)) return;
        ChemMachine.Recipe recipe = entity.workable();
        if (recipe != entity.current) {
            entity.current = recipe;
            entity.progress = 0;
        }
        boolean wants = recipe != null;
        if (wants != entity.asking) {
            entity.asking = wants;
            Electricity.update(server, pos);
        }
        double rate = wants ? MachineBlockEntity.rate(entity.volts()) : 0.0;
        boolean working = rate > 0.0;
        if (working) {
            int before = (int) entity.progress;
            entity.progress += (float) rate;
            if (entity.progress >= recipe.ticks()) {
                entity.work.work(recipe);
                entity.progress = 0;
            }
            if (before / 20 != (int) entity.progress / 20) {
                level.playSound(null, pos, entity.machine() == ChemMachine.ARC_FURNACE ? SoundEvents.FIRECHARGE_USE
                        : SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.3f, 0.9f + level.getRandom().nextFloat() * 0.2f);
            }
            entity.setChanged();
        }
        if (state.getValue(ChemMachineBlock.RUNNING) != working) {
            level.setBlock(pos, state.setValue(ChemMachineBlock.RUNNING, working), Block.UPDATE_CLIENTS);
        }
    }

    // ---------------------------------------------------------------- the window

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case ChemMenu.PROGRESS -> current == null ? 0 : Math.round(progress * 1000.0f / current.ticks());
                case ChemMenu.VOLTS -> Math.round(Math.abs(volts()) * 10.0f);
                case ChemMenu.STATE -> current == null ? ChemMenu.IDLE
                        : MachineBlockEntity.rate(volts()) <= 0.0 ? ChemMenu.STALLED : ChemMenu.RUNNING;
                case ChemMenu.FLUID -> fluid == null || fluid.held() == null ? RefineryMenu.NOTHING : RefineryMenu.substance(fluid.held());
                case ChemMenu.FLUID_AMOUNT -> fluid == null ? 0 : (int) fluid.millibuckets();
                case ChemMenu.FLUID_CAPACITY -> fluid == null ? 0 : 4000;
                case ChemMenu.GAS_IN -> gasSubstance(gasIn);
                case ChemMenu.GAS_IN_AMOUNT -> (int) Math.round(gasIn.amount());
                case ChemMenu.GAS_OUT -> gasSubstance(gasOut);
                case ChemMenu.GAS_OUT_AMOUNT -> (int) Math.round(gasOut.amount());
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

    private static int gasSubstance(GasStore store) {
        GasKind kind = store.kind();
        return kind == null ? RefineryMenu.NOTHING : RefineryMenu.substance(kind);
    }

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

    // ---------------------------------------------------------------- saving

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        if (fluid != null) fluid.load(input, "fluid");
        gasIn.load(input, "gas_in");
        gasOut.load(input, "gas_out");
        progress = input.getFloatOr("progress", 0.0f);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        if (fluid != null) fluid.save(output, "fluid");
        gasIn.save(output, "gas_in");
        gasOut.save(output, "gas_out");
        output.putFloat("progress", progress);
    }

    // ---------------------------------------------------------------- container

    /** Whether any of its recipes takes this item. */
    public boolean takes(ItemStack stack) {
        return ChemWork.takes(machine().recipes(), stack);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) if (!stack.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot < FIRST_OUTPUT && takes(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= FIRST_OUTPUT;
    }
}
