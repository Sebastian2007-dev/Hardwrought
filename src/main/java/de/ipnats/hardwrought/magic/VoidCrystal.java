package de.ipnats.hardwrought.magic;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.biome.v1.BiomeModification;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.List;

/** The complete amethyst-style life cycle and geode world generation for void crystal. */
public final class VoidCrystal {
    public static final Block BLOCK = registerBlock("void_crystal_block",
            new AmethystBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK)
                    .setId(blockKey("void_crystal_block"))));
    public static final Block BUDDING = registerBlock("budding_void_crystal",
            new BuddingVoidCrystalBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST)
                    .setId(blockKey("budding_void_crystal"))));
    public static final Block SMALL_BUD = registerCluster("small_void_crystal_bud", 3, 4,
            Blocks.SMALL_AMETHYST_BUD);
    public static final Block MEDIUM_BUD = registerCluster("medium_void_crystal_bud", 4, 3,
            Blocks.MEDIUM_AMETHYST_BUD);
    public static final Block LARGE_BUD = registerCluster("large_void_crystal_bud", 5, 3,
            Blocks.LARGE_AMETHYST_BUD);
    public static final Block CLUSTER = registerCluster("void_crystal_cluster", 7, 3,
            Blocks.AMETHYST_CLUSTER);
    public static final Item SHARD = registerItem("void_crystal_shard");

    public static final ResourceKey<PlacedFeature> GEODE = ResourceKey.create(Registries.PLACED_FEATURE,
            Hardwrought.id("void_crystal_geode"));

    private static boolean worldgenInitialized;

    private VoidCrystal() { }

    public static void initialize() {
        // Loading this class performs registration in declaration order.
    }

    public static void initializeWorldgen() {
        if (worldgenInitialized) return;
        worldgenInitialized = true;
        BiomeModification modification = BiomeModifications.create(Hardwrought.id("void_crystal_geodes"));
        modification.add(ModificationPhase.ADDITIONS, BiomeSelectors.foundInOverworld(), context ->
                context.getGenerationSettings().addFeature(GenerationStep.Decoration.LOCAL_MODIFICATIONS, GEODE));
    }

    public static List<Item> items() {
        return List.of(BLOCK.asItem(), BUDDING.asItem(), SMALL_BUD.asItem(), MEDIUM_BUD.asItem(),
                LARGE_BUD.asItem(), CLUSTER.asItem(), SHARD);
    }

    private static Block registerCluster(String name, float height, float width, Block copied) {
        ResourceKey<Block> key = blockKey(name);
        return registerBlock(name, new AmethystClusterBlock(height, width,
                BlockBehaviour.Properties.ofFullCopy(copied).setId(key)));
    }

    private static Block registerBlock(String name, Block block) {
        ResourceKey<Block> key = blockKey(name);
        Registry.register(BuiltInRegistries.BLOCK, key, block);
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        BlockItem item = new BlockItem(block,
                new Item.Properties().useBlockDescriptionPrefix().setId(itemKey));
        item.registerBlocks(Item.BY_BLOCK, item);
        Registry.register(BuiltInRegistries.ITEM, itemKey, item);
        return block;
    }

    private static Item registerItem(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }

    private static ResourceKey<Block> blockKey(String name) {
        return ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
    }
}
