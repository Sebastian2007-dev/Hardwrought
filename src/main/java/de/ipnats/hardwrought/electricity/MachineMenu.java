package de.ipnats.hardwrought.electricity;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A machine opened: what it works on to the left, what it made to the right, and between them how
 * far the piece has got. Beside that, what the wire brings it — the one thing a machine on current
 * has that a furnace has not, and the first place to look when it stands still.
 */
public class MachineMenu extends AbstractContainerMenu {
    public static final MenuType<MachineMenu> TYPE = Registry.register(BuiltInRegistries.MENU,
            Hardwrought.id("machine"), new MenuType<>(MachineMenu::new, FeatureFlags.VANILLA_SET));

    public static final int[] INPUT_AT = {62, 35};
    public static final int[] OUTPUT_AT = {122, 35};
    /** What the machine says about itself, in {@link #status}. */
    public static final int IDLE = 0, RUNNING = 1, STALLED = 2, BURNT = 3;
    public static final int DATA_COUNT = 4;

    private final Container machine;
    private final ContainerData data;

    /** The client's side: an empty stand-in the server fills. */
    public MachineMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(2), new SimpleContainerData(DATA_COUNT));
    }

    public MachineMenu(int id, Inventory inventory, Container machine, ContainerData data) {
        super(TYPE, id);
        this.machine = machine;
        this.data = data;
        addSlot(new Slot(machine, MachineBlockEntity.INPUT, INPUT_AT[0], INPUT_AT[1]) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return machine.canPlaceItem(MachineBlockEntity.INPUT, stack);
            }
        });
        addSlot(new Slot(machine, MachineBlockEntity.OUTPUT, OUTPUT_AT[0], OUTPUT_AT[1]) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    /** How far the piece in it has got, 0 to 1. */
    public float progress() {
        return Math.clamp(data.get(0) / 1000.0f, 0.0f, 1.0f);
    }

    /** The voltage on its wire. */
    public float volts() {
        return data.get(1) / 10.0f;
    }

    /** The current it takes. */
    public float amps() {
        return data.get(2) / 100.0f;
    }

    public int status() {
        return data.get(3);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 1, false)) {
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
