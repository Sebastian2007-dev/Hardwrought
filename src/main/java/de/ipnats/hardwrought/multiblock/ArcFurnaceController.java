package de.ipnats.hardwrought.multiblock;

import de.ipnats.hardwrought.electricity.Electricity;
import de.ipnats.hardwrought.electricity.MachineBlockEntity;
import de.ipnats.hardwrought.materials.ChemMachine;
import de.ipnats.hardwrought.materials.ChemMenu;
import de.ipnats.hardwrought.materials.ChemWork;
import de.ipnats.hardwrought.materials.Materials;
import de.ipnats.hardwrought.refining.RefineryMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;
import java.util.function.Predicate;

/**
 * The large arc furnace: a 3×3×3 hull of refractory casing, hollow inside — the hearth — with a graphite
 * electrode in the middle of its roof and this controller in one side. Items go in and out through an
 * item port, current through a power port with a copper coil on it.
 *
 * <p>It does what the small arc furnace does, twice over and three times as fast: two silicon out of each
 * charge of quartz and coke. And it makes steel the electric way, out of iron and coke. It draws about
 * thirteen amperes at the line's voltage: it wants copper cable, and a strong source.
 */
public class ArcFurnaceController extends ControllerBlock.Entity implements WorldlyContainer, ExtendedMenuProvider<BlockPos> {
    public static final double OHMS = 5.0;
    private static final int[] ALL_SLOTS = {0, 1, 2, 3};

    private static List<ChemMachine.Recipe> recipes;

    private final ChemWork work = new ChemWork(null, null, null);
    private float progress;
    private boolean asking;
    private ChemMachine.Recipe current;

    public ArcFurnaceController(BlockPos pos, BlockState state) {
        super(BigMachines.ARC_FURNACE_ENTITY, pos, state);
    }

    /** What the large furnace works. Built on first use: the items are registered after this class loads. */
    public static List<ChemMachine.Recipe> recipes() {
        if (recipes == null) {
            recipes = List.of(
                    new ChemMachine.Recipe("large_arc_furnace/silicon",
                            List.of(new ItemStack(Items.QUARTZ, 2), new ItemStack(de.ipnats.hardwrought.smeltery.SmelteryBlocks.COKE)),
                            null, 0, null, 0, List.of(new ItemStack(Materials.RAW_SILICON, 2)), null, 0, 50),
                    new ChemMachine.Recipe("large_arc_furnace/steel",
                            List.of(new ItemStack(Items.IRON_INGOT, 2), new ItemStack(de.ipnats.hardwrought.smeltery.SmelteryBlocks.COKE)),
                            null, 0, null, 0, List.of(new ItemStack(de.ipnats.hardwrought.metallurgy.Alloys.STEEL_INGOT, 2)), null, 0, 80));
        }
        return recipes;
    }

    @Override
    protected Block casing() {
        return BigMachines.REFRACTORY_CASING;
    }

    @Override
    protected Predicate<BlockState> special(int dx, int dy, int dz) {
        if (dx == 0 && dy == 0 && dz == 0) return BlockState::isAir;
        if (dx == 0 && dy == 1 && dz == 0) return state -> state.is(BigMachines.GRAPHITE_ELECTRODE);
        return null;
    }

    @Override
    protected Component specialName(int dx, int dy, int dz) {
        return dy == 0 ? Component.translatable("message.hardwrought.multiblock.hollow") : BigMachines.GRAPHITE_ELECTRODE.getName();
    }

    public double conductance() {
        return current != null ? 1.0 / OHMS : 0.0;
    }

    /** The voltage at its power port. */
    private double volts() {
        BlockPos power = port(PortBlock.Kind.POWER);
        return power != null && level != null && level.getBlockEntity(power) instanceof PortBlock.Entity port ? port.volts() : 0.0;
    }

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
        double rate = wants ? MachineBlockEntity.rate(volts()) : 0.0;
        boolean working = rate > 0.0;
        if (working) {
            progress += (float) rate;
            if (progress >= recipe.ticks()) {
                work.work(recipe);
                progress = 0;
            }
            if (level.getGameTime() % 10 == 0) {
                BlockPos hearth = centre();
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hearth.getX() + 0.5, hearth.getY() + 0.8, hearth.getZ() + 0.5,
                        6, 0.2, 0.2, 0.2, 0.1);
                level.playSound(null, hearth, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.3f, 1.5f);
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
                case ChemMenu.VOLTS -> (int) Math.round(Math.abs(volts()) * 10.0);
                case ChemMenu.STATE -> current == null ? ChemMenu.IDLE
                        : MachineBlockEntity.rate(volts()) <= 0.0 ? ChemMenu.STALLED : ChemMenu.RUNNING;
                case ChemMenu.FLUID, ChemMenu.GAS_IN, ChemMenu.GAS_OUT -> RefineryMenu.NOTHING;
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
        output.putFloat("progress", progress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        work.items.clear();
        ContainerHelper.loadAllItems(input, work.items);
        progress = input.getFloatOr("progress", 0.0f);
    }

    // ---------------------------------------------------------------- container: inputs in, outputs out, whatever the side

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
        return slot < ChemWork.FIRST_OUTPUT && ChemWork.takes(recipes(), stack);
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
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= ChemWork.FIRST_OUTPUT;
    }
}
