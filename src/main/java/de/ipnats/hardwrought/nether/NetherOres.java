package de.ipnats.hardwrought.nether;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * The ordinary ores, set in netherrack (see docs/endgame.md): a start over in the Nether has to be able
 * to make its tools from nothing, and these are what the first tools are made of.
 *
 * <p>They drop what their overworld kin drop — coal, raw copper, raw tin, raw iron — so everything after
 * the pick treats them as it always did. And they want the pick the overworld ores want: a flint pick
 * takes coal, a stone pick the rest. In the Nether that stone pick is knapped from blackstone
 * ({@link #BLACKSTONE_PIECE}), since there is no ordinary stone to be had.
 */
public final class NetherOres {
    /** The four ores, each named for what it holds. */
    public enum Ore {
        COAL("coal", false),
        COPPER("copper", true),
        TIN("tin", true),
        IRON("iron", true);

        private final String id;
        private final boolean metal;
        private Block block;

        Ore(String id, boolean metal) {
            this.id = id;
            this.metal = metal;
        }

        public String id() {
            return id;
        }

        /** The metal it melts as in the smeltery, or null for coal. */
        public String material() {
            return metal ? id : null;
        }

        public Block block() {
            return block;
        }
    }

    /** Blackstone knocked into pieces by a pick: the Nether's cobblestone piece, for a stone pick. */
    public static final Item BLACKSTONE_PIECE = item("blackstone_piece");

    private NetherOres() { }

    public static void initialize() {
        List<ResourceKey<PlacedFeature>> features = new ArrayList<>();
        for (Ore ore : Ore.values()) {
            String name = "nether_" + ore.id + "_ore";
            ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
            ore.block = Registry.register(BuiltInRegistries.BLOCK, key, new DropExperienceBlock(UniformInt.of(0, 2),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_GOLD_ORE).setId(key)));
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
            BlockItem item = new BlockItem(ore.block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
            item.registerBlocks(Item.BY_BLOCK, item);
            Registry.register(BuiltInRegistries.ITEM, itemKey, item);
            features.add(ResourceKey.create(Registries.PLACED_FEATURE, Hardwrought.id("ore_" + name)));
        }
        BiomeModifications.create(Hardwrought.id("nether_ores")).add(ModificationPhase.ADDITIONS,
                BiomeSelectors.foundInTheNether(), context -> features.forEach(feature ->
                        context.getGenerationSettings().addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, feature)));
    }

    /** The ore blocks, then the blackstone piece, for the creative tab. */
    public static List<Item> items() {
        List<Item> items = new ArrayList<>();
        for (Ore ore : Ore.values()) items.add(ore.block.asItem());
        items.add(BLACKSTONE_PIECE);
        return items;
    }

    private static Item item(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }
}
