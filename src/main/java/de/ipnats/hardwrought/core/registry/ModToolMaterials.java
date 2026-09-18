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
    private static final TagKey<Item> FLINT_REPAIR =
            TagKey.create(Registries.ITEM, Hardwrought.id("flint_tool_materials"));

    /** A knapped edge: sharp and quick, but brittle and quickly worn out. */
    public static final ToolMaterial FLINT =
            new ToolMaterial(BlockTags.INCORRECT_FOR_WOODEN_TOOL, 96, 2.5F, 1.0F, 5, FLINT_REPAIR);

    private ModToolMaterials() { }
}
