package de.ipnats.hardwrought.knowledge;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import java.util.Locale;

/**
 * The shelves of the compendium, from specification section 80.
 *
 * <p>The list is the one the specification gives, in its order, because the compendium replaces the
 * wiki as well as the recipe book and a player will learn where things live. Which shelf an entry
 * sits on is worked out from the item itself rather than written down per item: a mod that adds a
 * thousand items should not need a thousand lines here.
 */
public enum KnowledgeCategory {
    MATERIALS("materials"),
    CRAFTING("crafting"),
    METALLURGY("metallurgy"),
    ENGINEERING("engineering"),
    AGRICULTURE("agriculture"),
    BIOLOGY("biology"),
    MEDICINE("medicine"),
    CHEMISTRY("chemistry"),
    MAGIC("magic"),
    ELECTRICITY("electricity");

    private final String serializedName;

    KnowledgeCategory(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public String translationKey() {
        return "knowledge.hardwrought.category." + serializedName;
    }

    /**
     * Which shelf this belongs on. Read off what the item <em>is</em> — tags, components and the
     * block behind it — so the answer stays right for items nobody here has heard of.
     */
    public static KnowledgeCategory of(ItemLike itemLike) {
        if (itemLike == null) return MATERIALS;
        Item item = itemLike.asItem();
        ItemStack stack = new ItemStack(item);
        if (stack.has(DataComponents.POTION_CONTENTS)) return MEDICINE;
        if (stack.has(DataComponents.FOOD)) return AGRICULTURE;
        if (stack.has(DataComponents.TOOL) || stack.is(ItemTags.SWORDS)
                || stack.has(DataComponents.EQUIPPABLE)) {
            return CRAFTING;
        }
        if (isMetal(stack)) return METALLURGY;
        if (isMechanism(item)) return ENGINEERING;
        if (isCircuitry(stack)) return ELECTRICITY;
        if (isLiving(item)) return BIOLOGY;
        return MATERIALS;
    }

    private static boolean isMetal(ItemStack stack) {
        if (stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
                && blockItem.getBlock().defaultBlockState().is(BlockTags.ORES)) {
            return true;
        }
        String path = key(stack.getItem());
        return path.endsWith("_ingot") || path.endsWith("_nugget") || path.startsWith("raw_")
                || path.endsWith("_ore") || path.contains("bronze") || path.contains("steel");
    }

    private static boolean isMechanism(Item item) {
        if (!(item instanceof net.minecraft.world.item.BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        return block.defaultBlockState().is(BlockTags.DOORS) || block.defaultBlockState().is(BlockTags.RAILS)
                || key(item).contains("piston") || key(item).contains("dispenser")
                || key(item).contains("hopper") || key(item).contains("drill");
    }

    private static boolean isCircuitry(ItemStack stack) {
        String path = key(stack.getItem());
        return path.contains("redstone") || path.contains("copper_bulb") || path.contains("comparator")
                || path.contains("repeater") || path.contains("lever") || path.contains("wire");
    }

    private static boolean isLiving(Item item) {
        if (!(item instanceof net.minecraft.world.item.BlockItem blockItem)) return false;
        var state = blockItem.getBlock().defaultBlockState();
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.CROPS);
    }

    private static String key(Item item) {
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "" : id.getPath();
    }

    public static KnowledgeCategory byName(String name) {
        if (name == null) return null;
        for (KnowledgeCategory category : values()) {
            if (category.serializedName.equals(name.toLowerCase(Locale.ROOT))) return category;
        }
        return null;
    }

    public static KnowledgeCategory byOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            throw new IllegalArgumentException("Unknown knowledge category: " + ordinal);
        }
        return values()[ordinal];
    }
}
