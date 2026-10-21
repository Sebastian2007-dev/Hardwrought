package de.ipnats.hardwrought.nuclear;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
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
 * The reactor's control desk: the nine fuel channels as a 3×3 grid, laid out as they stand in the core;
 * the readings — power, temperature, pressure, reactivity, rods, xenon, decay heat, flow, feedwater,
 * steam — and the buttons that drive the rods in and out by hand, set a power for the automatic control
 * to hold, scram the core and reset it.
 */
public class ReactorMenu extends AbstractContainerMenu {
    public static final ExtendedMenuType<ReactorMenu, BlockPos> TYPE = Registry.register(BuiltInRegistries.MENU,
            Hardwrought.id("reactor"), new ExtendedMenuType<>(ReactorMenu::new, BlockPos.STREAM_CODEC));

    public static final int POWER = 0, TEMPERATURE = 1, PRESSURE = 2, REACTIVITY = 3, RODS = 4, RODS_SET = 5, XENON = 6,
            DECAY = 7, TRIP = 8, DRIVES = 9, FLOW = 10, WATER = 11, STEAM = 12, VOLTS = 13, VENTING = 14, SETPOINT = 15, FUEL = 16, LOG_POWER = 17, PERIOD = 18, DATA_COUNT = 19;
    public static final int INSERT_MORE = 0, INSERT = 1, WITHDRAW = 2, WITHDRAW_MORE = 3, SCRAM = 4, RESET = 5,
            SETPOINT_DOWN = 6, SETPOINT_UP = 7;
    public static final int CHANNELS_X = 8, CHANNELS_Y = 18, INVENTORY_Y = 158;

    private final Container reactor;
    private final ContainerData data;
    private final BlockPos pos;

    public ReactorMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, new SimpleContainer(ReactorController.CHANNELS), new SimpleContainerData(DATA_COUNT), pos);
    }

    public ReactorMenu(int id, Inventory inventory, Container reactor, ContainerData data, BlockPos pos) {
        super(TYPE, id);
        this.reactor = reactor;
        this.data = data;
        this.pos = pos;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new Channel(reactor, row * 3 + column, CHANNELS_X + column * 18, CHANNELS_Y + row * 18));
            }
        }
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        addDataSlots(data);
    }

    /** A fuel channel: opened only while the core is shut down. */
    private final class Channel extends Slot {
        Channel(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return ReactorController.fits(stack) && shutDown();
        }

        @Override
        public boolean mayPickup(Player player) {
            return shutDown();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** Whether the core is shut down, as the readings tell it: rods in, no power to speak of. */
    public boolean shutDown() {
        return data.get(RODS) >= 990 && data.get(POWER) < 10;
    }

    public BlockPos pos() {
        return pos;
    }

    public int get(int index) {
        return data.get(index);
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (button < INSERT_MORE || button > SETPOINT_UP) return false;
        if (reactor instanceof ReactorController controller && player.level() instanceof ServerLevel level) {
            controller.press(button, level);
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int own = ReactorController.CHANNELS;
        if (index < own) {
            if (!slot.mayPickup(player) || !moveItemStackTo(stack, own, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, own, false)) {
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
        return reactor.stillValid(player);
    }

    /** Touching the class registers the menu type. */
    public static void initialize() { }
}
