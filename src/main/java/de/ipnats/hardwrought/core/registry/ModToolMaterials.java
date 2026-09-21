package de.ipnats.hardwrought.core.registry;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/**
 * Tool materials Hardwrought adds to the vanilla set. Real per-item craftsmanship, heat treatment
 * and wear of specification sections 37 and 78 are separate later state and do not live here.
 */
public final class ModToolMaterials {
    public static final int FLINT_DURABILITY = 32;
    public static final float FLINT_MINING_SPEED = 1.25F;

    /** Section 56: harder than copper and nearly iron, which is what made the age worth naming. */
    public static final int BRONZE_DURABILITY = 375;
    public static final float BRONZE_MINING_SPEED = 6.0F;

    private static final TagKey<Item> FLINT_REPAIR =
            TagKey.create(Registries.ITEM, Hardwrought.id("flint_tool_materials"));

    /** A knapped edge: useful before metal, but deliberately slow and about as fragile as gold. */
    public static final ToolMaterial FLINT =
            new ToolMaterial(BlockTags.INCORRECT_FOR_WOODEN_TOOL, FLINT_DURABILITY,
                    FLINT_MINING_SPEED, 1.0F, 5, FLINT_REPAIR);

    private static final TagKey<Item> BRONZE_REPAIR =
            TagKey.create(Registries.ITEM, Hardwrought.id("bronze_tool_materials"));

    /** Copper and tin: the first metal worth replacing a stone edge with. */
    public static final ToolMaterial BRONZE =
            new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, BRONZE_DURABILITY,
                    BRONZE_MINING_SPEED, 2.0F, 12, BRONZE_REPAIR);

    private ModToolMaterials() { }
}
