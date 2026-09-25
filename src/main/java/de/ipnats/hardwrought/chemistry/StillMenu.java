package de.ipnats.hardwrought.chemistry;

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
import net.minecraft.world.item.Items;

/**
 * The still opened: the charge over the fire on the left, the empty bottles under it, the products
 * on the right in two rows of three, and the empty container beside them.
 */
public class StillMenu extends AbstractContainerMenu {
    public static final MenuType<StillMenu> TYPE = Registry.register(BuiltInRegistries.MENU,
            Hardwrought.id("still"), new MenuType<>(StillMenu::new, FeatureFlags.VANILLA_SET));

    public static final int[] CHARGE_AT = {26, 17};
    public static final int[] BOTTLES_AT = {26, 53};
    public static final int OUTPUT_LEFT = 98;
    public static final int OUTPUT_TOP = 26;
    public static final int[] RETURNED_AT = {152, 53};

    private final Container still;
    private final ContainerData data;

    /** The client's side: an empty stand-in the server fills. */
    public StillMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(StillBlockEntity.SIZE), new SimpleContainerData(StillBlockEntity.DATA_COUNT));
    }

    public StillMenu(int id, Inventory inventory, Container still, ContainerData data) {
        super(TYPE, id);
        this.still = still;
        this.data = data;
        addSlot(new Slot(still, StillBlockEntity.CHARGE, CHARGE_AT[0], CHARGE_AT[1]) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return StillRecipes.accepts(stack);
            }
        });
        addSlot(new Slot(still, StillBlockEntity.BOTTLES, BOTTLES_AT[0], BOTTLES_AT[1]) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.GLASS_BOTTLE);
            }
        });
        for (int i = 0; i < StillBlockEntity.OUTPUTS; i++) {
            int[] at = outputAt(i);
            addSlot(new Slot(still, StillBlockEntity.FIRST_OUTPUT + i, at[0], at[1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        addSlot(new Slot(still, StillBlockEntity.RETURNED, RETURNED_AT[0], RETURNED_AT[1]) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    public static int[] outputAt(int index) {
        return new int[] {OUTPUT_LEFT + 18 * (index % 3), OUTPUT_TOP + 18 * (index / 3) - 9};
    }

    /** How far the batch in the pot has got, 0 to 1. */
    public float progress() {
        return Math.min(1.0f, data.get(0) / (float) StillRecipes.BATCH_TICKS);
    }

    public boolean heated() {
        return data.get(1) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int stillSlots = StillBlockEntity.SIZE;
        if (index < stillSlots) {
            if (!moveItemStackTo(stack, stillSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (StillRecipes.accepts(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (stack.is(Items.GLASS_BOTTLE)) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
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
        return still.stillValid(player);
    }

    /** Touching the class registers the menu type; called from the mod initializer. */
    public static void initialize() { }
}
