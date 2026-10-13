package de.ipnats.hardwrought.materials;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A chemistry machine opened, a single block or the arc furnace multiblock: the liquid and the gas going
 * in as gauges on the left, two input slots, the work's progress, two output slots, the gas coming out
 * on the right; the player's inventory below.
 */
public class ChemMenu extends AbstractContainerMenu {
    public static final ExtendedMenuType<ChemMenu, BlockPos> TYPE = Registry.register(BuiltInRegistries.MENU,
            Hardwrought.id("chem_machine"), new ExtendedMenuType<>(ChemMenu::new, BlockPos.STREAM_CODEC));

    public static final int PROGRESS = 0, VOLTS = 1, STATE = 2, FLUID = 3, FLUID_AMOUNT = 4, FLUID_CAPACITY = 5,
            GAS_IN = 6, GAS_IN_AMOUNT = 7, GAS_OUT = 8, GAS_OUT_AMOUNT = 9, DATA_COUNT = 10;
    public static final int IDLE = 0, RUNNING = 1, STALLED = 2;
    public static final int INPUT_X = 56, OUTPUT_X = 116, TOP_Y = 24, SECOND_Y = 46;

    private final Container machine;
    private final ContainerData data;
    private final BlockPos pos;

    public ChemMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, new SimpleContainer(ChemMachineBlockEntity.SIZE), new SimpleContainerData(DATA_COUNT), pos);
    }

    public ChemMenu(int id, Inventory inventory, Container machine, ContainerData data, BlockPos pos) {
        super(TYPE, id);
        this.machine = machine;
        this.data = data;
        this.pos = pos;
        addSlot(new Slot(machine, 0, INPUT_X, TOP_Y));
        addSlot(new Slot(machine, 1, INPUT_X, SECOND_Y));
        addSlot(output(machine, 2, OUTPUT_X, TOP_Y));
        addSlot(output(machine, 3, OUTPUT_X, SECOND_Y));
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    private static Slot output(Container machine, int index, int x, int y) {
        return new Slot(machine, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        };
    }

    public BlockPos pos() { return pos; }

    public int get(int index) {
        return data.get(index);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int own = ChemMachineBlockEntity.SIZE;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, ChemMachineBlockEntity.INPUTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return machine.stillValid(player);
    }

    /** Touching the class registers the menu type. */
    public static void initialize() { }
}
