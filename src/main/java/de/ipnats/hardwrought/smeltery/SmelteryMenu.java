package de.ipnats.hardwrought.smeltery;

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
 * The smeltery opened: the fuel on the left, the pieces melting in three rows, the tank and a
 * thermometer on the right, the player's inventory below.
 */
public class SmelteryMenu extends AbstractContainerMenu {
    public static final int SELECT_FLUID_BUTTON = 100;
    public static final ExtendedMenuType<SmelteryMenu, BlockPos> TYPE = Registry.register(BuiltInRegistries.MENU,
            Hardwrought.id("smeltery"), new ExtendedMenuType<>(SmelteryMenu::new, BlockPos.STREAM_CODEC));
    public static final int FUEL_X = 8, FUEL_Y = 36;
    public static final int GRID_X = 30, GRID_Y = 18;
    public static final int COLUMNS = 7;

    private final Container smeltery;
    private final ContainerData data;
    private final BlockPos pos;

    public SmelteryMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, new SimpleContainer(SmelteryControllerBlockEntity.CONTAINER_SIZE), new SimpleContainerData(SmelteryControllerBlockEntity.DATA_COUNT), pos);
    }

    public SmelteryMenu(int id, Inventory inventory, Container smeltery, ContainerData data, BlockPos pos) {
        super(TYPE, id);
        this.smeltery = smeltery;
        this.data = data;
        this.pos = pos;
        addSlot(new Slot(smeltery, SmelteryControllerBlockEntity.FUEL_SLOT, FUEL_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return SmelteryControllerBlockEntity.fuelTicks(stack) > 0;
            }
        });
        for (int i = 0; i < SmelteryControllerBlockEntity.MELT_SLOTS; i++) {
            int[] at = meltPosition(i);
            addSlot(new Slot(smeltery, 1 + i, at[0], at[1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return MoltenMetals.melt(stack.getItem()) != null;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addStandardInventorySlots(inventory, 8, 104);
        addDataSlots(data);
    }

    /** The melting places, seven to a row: 27 of them in four rows. */
    public static int[] meltPosition(int index) {
        return new int[] {GRID_X + 18 * (index % COLUMNS), GRID_Y + 18 * (index / COLUMNS)};
    }

    public BlockPos pos() { return pos; }

    public int temperature() { return data.get(0); }

    public int target() { return data.get(1); }

    public int burnLeft() { return data.get(2); }

    public int capacity() { return data.get(3); }

    /**
     * A melting place: {@link SmelteryControllerBlockEntity#TOO_COLD}, {@link SmelteryControllerBlockEntity#TANK_FULL},
     * or how far along what lies there is, in thousandths.
     */
    public int meltState(int place) { return data.get(4 + place); }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        int material = button - SELECT_FLUID_BUTTON;
        if (!(smeltery instanceof SmelteryControllerBlockEntity controller)
                || material < 0 || material >= MoltenMetals.ORDER.size()) return false;
        return controller.selectForCasting(MoltenMetals.ORDER.get(material));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int own = SmelteryControllerBlockEntity.CONTAINER_SIZE;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) return ItemStack.EMPTY;
        } else if (SmelteryControllerBlockEntity.fuelTicks(stack) > 0) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (MoltenMetals.melt(stack.getItem()) != null) {
            if (!moveItemStackTo(stack, 1, own, false)) return ItemStack.EMPTY;
        } else {
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
        return smeltery.stillValid(player);
    }

    /** Touching the class registers the menu type. */
    public static void initialize() { }
}
