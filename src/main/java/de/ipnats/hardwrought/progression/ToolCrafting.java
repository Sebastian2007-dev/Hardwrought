package de.ipnats.hardwrought.progression;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Section 71: a tool that a recipe needs is used, not used up.
 *
 * <p>A workbench cannot be knocked together without something to cut the timber with, and the
 * recipe says so by asking for the hatchet. Asking for it is the whole point — swallowing it would
 * make the rule read as a price rather than as a requirement. The tool comes back out of the grid
 * one point of wear worse and disappears only when it finally wears through.
 *
 * <p>Which items count is a datapack tag, because every later tool-assisted recipe the technology
 * tree adds — saw, chisel, hammer — wants the same treatment.
 */
public final class ToolCrafting {
    /** Tools a recipe may ask for without consuming them. */
    public static final TagKey<Item> CRAFTING_TOOLS =
            TagKey.create(Registries.ITEM, Hardwrought.id("crafting_tools"));

    /** What one use of a tool in a recipe costs it. */
    public static final int WEAR_PER_CRAFT = 1;

    private ToolCrafting() { }

    public static boolean isCraftingTool(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(CRAFTING_TOOLS) && stack.isDamageableItem();
    }

    /**
     * The same tool, one use older. A tool that has just worn through comes back as nothing, which
     * is the one case where the recipe really does cost it.
     */
    public static ItemStack worn(ItemStack used) {
        if (!isCraftingTool(used)) return ItemStack.EMPTY;
        ItemStack returned = used.copyWithCount(1);
        int damage = returned.getDamageValue() + WEAR_PER_CRAFT;
        if (damage >= returned.getMaxDamage()) return ItemStack.EMPTY;
        returned.setDamageValue(damage);
        return returned;
    }
}
