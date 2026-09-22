package de.ipnats.hardwrought.progression;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Section 71: a tool that a recipe needs is used, not used up.
 *
 * <p>A flat working surface cannot be cut out of round timber without an edge equal to the job, and
 * the rule says so by asking for one. Asking is the whole point — swallowing the tool would make the
 * rule read as a price rather than as a requirement. It comes back one point of wear worse and
 * disappears only when it finally wears through.
 *
 * <p>Two ways of qualifying, because they answer different questions. The
 * {@link #CRAFTING_TOOLS} tag names tools outright, which is where the saws, chisels and hammers of
 * the later technology tree will go and how a datapack adds one. Beyond that, <em>any</em> axe of
 * iron tier or better counts, worked out from the item itself: a player who has reached a diamond
 * axe has plainly reached the point this rule exists to gate, and a mod that adds a good axe should
 * not have to know Hardwrought exists for it to work.
 */
public final class ToolCrafting {
    /** Tools a recipe may ask for without consuming them, named outright. */
    public static final TagKey<Item> CRAFTING_TOOLS =
            TagKey.create(Registries.ITEM, Hardwrought.id("crafting_tools"));

    /** What one use of a tool in a recipe costs it. */
    public static final int WEAR_PER_CRAFT = 1;

    /**
     * Timber, so that "is this an axe" can be asked of the item rather than looked up in a list.
     * Anything that cuts a log faster than a fist would is an axe, whoever added it.
     */
    private static final Block TIMBER = Blocks.OAK_LOG;
    /**
     * The block the tier question is asked about. Diamond ore is in {@code #needs_iron_tool}, so
     * everything below iron is refused its drops and everything from iron up is not — which is the
     * line this rule wants, expressed in vanilla's own terms rather than in a list of materials.
     */
    private static final Block IRON_TIER = Blocks.DIAMOND_ORE;

    private ToolCrafting() { }

    public static boolean isCraftingTool(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) return false;
        return stack.is(CRAFTING_TOOLS) || isJoinersAxe(stack);
    }

    /**
     * An axe good enough to join with: iron tier or better. A knapped flint edge, a stone hatchet
     * and a golden axe are all refused, which is the progression the rule is there to hold.
     */
    public static boolean isJoinersAxe(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) return false;
        return BlockBreaking.hasAffinity(stack, TIMBER.defaultBlockState())
                && !refusedTheDropsOf(stack, IRON_TIER);
    }

    /**
     * Whether this tool would be told outright that it is too soft for that block.
     *
     * <p>Not {@code ItemStack#isCorrectToolForDrops}, which cannot answer this: an axe is the wrong
     * <em>kind</em> of tool for ore, so that method says no to every axe at every tier and the
     * question goes unanswered. What is wanted is the narrower one — is this block in the set the
     * tool's own material is refused — and that is the rule the material writes into the item.
     */
    private static boolean refusedTheDropsOf(ItemStack stack, Block block) {
        Tool tool = stack.get(DataComponents.TOOL);
        if (tool == null) return true;
        for (Tool.Rule rule : tool.rules()) {
            // A rule that only sets a speed says nothing about drops and is not an answer.
            if (rule.correctForDrops().isEmpty()) continue;
            if (rule.blocks().contains(block.builtInRegistryHolder())) {
                return !rule.correctForDrops().get();
            }
        }
        return false;
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
