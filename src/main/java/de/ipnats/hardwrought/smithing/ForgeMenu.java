package de.ipnats.hardwrought.smithing;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
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
 * The forge opened: its fuel places on the left, its metal places in the middle, the player's
 * inventory below. How many of each there are depends on how big the forge is built; the client
 * learns that with the opening of the screen.
 */
public class ForgeMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 3;

    public static final ExtendedMenuType<ForgeMenu, Integer> TYPE = Registry.register(BuiltInRegistries.MENU,
            Hardwrought.id("forge"), new ExtendedMenuType<>(ForgeMenu::new, ByteBufCodecs.VAR_INT));

    private final Container forge;
    private final ContainerData data;
    private final int layout;
    private final int fuelCount;
    private final int forgeSlots;

    /** The client's side: an empty stand-in the server fills. */
    public ForgeMenu(int id, Inventory inventory, Integer layout) {
        this(id, inventory, new SimpleContainer(ForgeBlockEntity.CONTAINER_SIZE), new SimpleContainerData(DATA_COUNT), layout);
    }

    public ForgeMenu(int id, Inventory inventory, Container forge, ContainerData data, int layout) {
        super(TYPE, id);
        this.forge = forge;
        this.data = data;
        this.layout = layout;
        this.fuelCount = ForgeBlockEntity.fuelSlots(layout);
        int metalCount = ForgeBlockEntity.metalSlots(layout);
        this.forgeSlots = fuelCount + metalCount;

        for (int i = 0; i < fuelCount; i++) {
            int[] at = fuelPosition(layout, i);
            addSlot(new Slot(forge, i, at[0], at[1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return ForgeBlock.fuelValue(stack) > 0;
                }
            });
        }
        for (int i = 0; i < metalCount; i++) {
            int[] at = metalPosition(layout, i);
            addSlot(new Slot(forge, ForgeBlockEntity.FIRST_METAL + i, at[0], at[1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return ForgeBlockEntity.acceptsMetal(stack);
                }
            });
        }
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    /** Where the fuel places sit: one under the flame, or four in a square below it. */
    public static int[] fuelPosition(int layout, int index) {
        if (layout == 2) return new int[] {17 + 18 * (index % 2), 35 + 18 * (index / 2)};
        return new int[] {26, 53};
    }

    /** Where the metal places sit: a single one, a 2×2 or a 3×3, centred on the same spot. */
    public static int[] metalPosition(int layout, int index) {
        int side = layout == 2 ? 3 : layout == 1 ? 2 : 1;
        int left = 98 - 9 * (side - 1);
        int top = 35 - 9 * (side - 1);
        return new int[] {left + 18 * (index % side), top + 18 * (index / side)};
    }

    public int layout() {
        return layout;
    }

    public int fuelCount() {
        return fuelCount;
    }

    public int temperature() {
        return data.get(0);
    }

    public int target() {
        return data.get(1);
    }

    /** How much of the burning coal is left, 0 to 1000. */
    public int burnLeft() {
        return data.get(2);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < forgeSlots) {
            if (!moveItemStackTo(stack, forgeSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (ForgeBlock.fuelValue(stack) > 0) {
            if (!moveItemStackTo(stack, 0, fuelCount, false)) return ItemStack.EMPTY;
        } else if (ForgeBlockEntity.acceptsMetal(stack)) {
            if (!moveItemStackTo(stack, fuelCount, forgeSlots, false)) return ItemStack.EMPTY;
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
        if (!forge.stillValid(player)) return false;
        return !(forge instanceof ForgeBlockEntity entity) || entity.layout() == layout;
    }

    /** Touching the class registers the menu type; called from the mod initializer. */
    public static void initialize() { }
}
