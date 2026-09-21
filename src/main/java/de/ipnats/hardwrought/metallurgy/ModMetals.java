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
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registers the four things every metal of {@link Metal} needs: the ore in stone, the same ore in
 * deepslate, the raw material that comes out of it and the ingot it smelts into.
 *
 * <p>Nothing here is written per metal. The table is the source, and everything the game sees — the
 * blocks, the items, the models, the loot, the recipes, the tags and the ore generation — is derived
 * from it, so a new metal is one line in the enum and a texture.
 */
public final class ModMetals {
    private static final Map<Metal, Block> ORES = new EnumMap<>(Metal.class);
    private static final Map<Metal, Block> DEEPSLATE_ORES = new EnumMap<>(Metal.class);
    private static final Map<Metal, Item> RAW = new EnumMap<>(Metal.class);
    private static final Map<Metal, Item> INGOTS = new EnumMap<>(Metal.class);

    private ModMetals() { }

    public static void initialize() {
        if (!ORES.isEmpty()) return;
        for (Metal metal : Metal.values()) {
            ORES.put(metal, registerOre(metal.oreId(), Blocks.IRON_ORE));
            DEEPSLATE_ORES.put(metal, registerOre(metal.deepslateOreId(), Blocks.DEEPSLATE_IRON_ORE));
        }
        // Items are registered after every ore block exists, so a block item never looks for a
        // block that has not been registered yet.
        for (Metal metal : Metal.values()) {
            registerBlockItem(metal.oreId(), ORES.get(metal));
            registerBlockItem(metal.deepslateOreId(), DEEPSLATE_ORES.get(metal));
            RAW.put(metal, registerItem(metal.rawId()));
            INGOTS.put(metal, registerItem(metal.ingotId()));
        }
    }

    public static Block ore(Metal metal) {
        return required(ORES, metal);
    }

    public static Block deepslateOre(Metal metal) {
        return required(DEEPSLATE_ORES, metal);
    }

    public static Item raw(Metal metal) {
        return required(RAW, metal);
    }

    public static Item ingot(Metal metal) {
        return required(INGOTS, metal);
    }

    private static <T> T required(Map<Metal, T> table, Metal metal) {
        T value = table.get(metal);
        if (value == null) throw new IllegalStateException("Metals have not been registered yet");
        return value;
    }

    /**
     * Ore drops experience when it is broken for the raw material, exactly as vanilla ore does. The
     * hardness of the host rock comes from the vanilla block it copies: stone or deepslate.
     */
    private static Block registerOre(String name, Block copied) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
        Block block = new DropExperienceBlock(net.minecraft.util.valueproviders.UniformInt.of(0, 2),
                BlockBehaviour.Properties.ofFullCopy(copied).setId(key));
        return Registry.register(BuiltInRegistries.BLOCK, key, block);
    }

    private static void registerBlockItem(String name, Block block) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        // Without this a block item is named "item.hardwrought.x" and looks for a translation key
        // that does not exist; blocks are named under the block prefix.
        BlockItem item = new BlockItem(block,
                new Item.Properties().useBlockDescriptionPrefix().setId(key));
        item.registerBlocks(Item.BY_BLOCK, item);
        Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    private static Item registerItem(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }
}
