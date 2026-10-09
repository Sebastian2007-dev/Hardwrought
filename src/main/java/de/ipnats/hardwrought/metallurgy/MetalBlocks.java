package de.ipnats.hardwrought.metallurgy;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A block of every metal that has bars: nine bars make one, and it gives its nine back. Iron, gold
 * and copper have vanilla's; this is the rest — bronze, the steels and every ore metal but mercury,
 * which does not stack into anything.
 *
 * <p>Assets and recipes are written by {@code tools/metal_block_assets.py}.
 */
public final class MetalBlocks {
    private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();

    private MetalBlocks() { }

    /** The material names, in the order of the creative tab. Kept in step with the asset script. */
    public static List<String> materials() {
        List<String> materials = new ArrayList<>(List.of("bronze", "steel", "stainless_steel", "tungsten_steel"));
        for (Metal metal : Metal.values()) if (metal != Metal.MERCURY) materials.add(metal.id());
        return materials;
    }

    public static void initialize() {
        if (!BLOCKS.isEmpty()) return;
        for (String material : materials()) {
            String name = material + "_block";
            ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
            Block block = Registry.register(BuiltInRegistries.BLOCK, key,
                    new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).setId(key)));
            ResourceKey<Item> item = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
            Registry.register(BuiltInRegistries.ITEM, item,
                    new BlockItem(block, new Item.Properties().setId(item).useBlockDescriptionPrefix()));
            BLOCKS.put(material, block);
        }
    }

    /** The block of this material, or null where it has none of ours. */
    public static Block block(String material) {
        return BLOCKS.get(material);
    }

    /** Every block as an item, for the creative tab. */
    public static List<Item> items() {
        List<Item> items = new ArrayList<>();
        BLOCKS.values().forEach(block -> items.add(block.asItem()));
        return items;
    }
}
