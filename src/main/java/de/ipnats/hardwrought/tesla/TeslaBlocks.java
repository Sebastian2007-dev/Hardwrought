package de.ipnats.hardwrought.tesla;

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

import java.util.List;
import java.util.function.Function;

/**
 * The Tesla tower (docs/electricity-roadmap.md, stage E6), registered: the parts it is stacked from. Its
 * foot is an appliance (see {@code Appliance.TESLA_TOWER}).
 */
public final class TeslaBlocks {
    /** Hundreds of turns of enamelled wire on a porcelain former. Three stand on the foot. */
    public static final Block RESONANT_COIL = block("resonant_coil", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS).strength(2.0f).noOcclusion());
    /** The copper sphere on top, where the arcs leave from. */
    public static final Block TOP_LOAD = block("top_load", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.0f).noOcclusion().lightLevel(state -> 4));
    /** Dozens of capacitors in oil, against the foot: what the tower spends from. */
    public static final Block CAPACITOR_BANK = block("capacitor_bank", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.0f));
    /** Two tungsten points a hair apart in porcelain: what fires the coil. */
    public static final Item SPARK_GAP = item("spark_gap");

    private TeslaBlocks() { }

    public static void initialize() { }

    public static List<Item> items() {
        return List.of(RESONANT_COIL.asItem(), TOP_LOAD.asItem(), CAPACITOR_BANK.asItem(), SPARK_GAP);
    }

    private static Item item(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().stacksTo(16).setId(key)));
    }

    private static <T extends Block> T block(String name, Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
        T block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }
}
