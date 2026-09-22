package de.ipnats.hardwrought.equipment;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;

/**
 * A pack worn on the back. Its tier is the item, not a component, so a pack cannot be edited into a
 * better one and the two are told apart by what they are rather than by what they carry.
 *
 * <p>What is inside rides on the stack in the vanilla container component, the same one a shulker
 * box uses. That keeps a pack whole: pick it up off the ground, put it in a chest, hand it to
 * somebody, and its contents come with it without a line of code here knowing about any of those.
 */
public class BackpackItem extends Item {
    private final BackpackTier tier;

    public BackpackItem(BackpackTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public BackpackTier tier() {
        return tier;
    }

    /** What this stack is worth as a pack, or null where it is not one. */
    public static BackpackTier tierOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return stack.getItem() instanceof BackpackItem pack ? pack.tier() : null;
    }

    public static boolean isBackpack(ItemStack stack) {
        return tierOf(stack) != null;
    }

    /**
     * What is in this pack, always exactly as many slots as its tier has. A pack whose component is
     * missing, short or overlong reads as the right shape anyway, so a hand-edited stack or a pack
     * from an older save cannot put the menu out of step with itself.
     */
    public static NonNullList<ItemStack> contentsOf(ItemStack stack) {
        BackpackTier tier = tierOf(stack);
        int size = tier == null ? 0 : tier.slots();
        NonNullList<ItemStack> items = NonNullList.withSize(size, ItemStack.EMPTY);
        if (size == 0) return items;
        ItemContainerContents stored = stack.get(DataComponents.CONTAINER);
        if (stored != null) stored.copyInto(items);
        return items;
    }

    /** Writes the slots back onto the stack. */
    public static void setContents(ItemStack stack, List<ItemStack> items) {
        BackpackTier tier = tierOf(stack);
        if (tier == null) return;
        if (tier.slots() == 0) {
            stack.remove(DataComponents.CONTAINER);
            return;
        }
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }
}
