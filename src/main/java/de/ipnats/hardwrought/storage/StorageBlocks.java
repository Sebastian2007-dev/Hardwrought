package de.ipnats.hardwrought.storage;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.electricity.ElectricBlocks;
import de.ipnats.hardwrought.knowledge.WorldRecipes;
import de.ipnats.hardwrought.smithing.MetalStock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The store room: chests strung together with tubes and worked from one desk.
 *
 * <p>Nothing is turned into numbers and kept in a box. Every item stays in the chest it was put
 * in; a {@link StoragePortBlock} against the chest is the works that sorts into it and fetches out
 * of it, {@link StorageTubeBlock}s lead from the ports to a {@link StorageDeskBlock}, and the desk
 * is where a player stands, and it wants current to work. It is dear on purpose: every chest wants a port, every port a sorting
 * mechanism, and the gears and tube those are made of come off two machines of their own — the
 * lathe and the tube mill, in {@link de.ipnats.hardwrought.electricity.Machine}.
 */
public final class StorageBlocks {
    public static final StorageTubeBlock TUBE = block("storage_tube", StorageTubeBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(1.5f).noOcclusion());
    public static final StoragePortBlock PORT = block("storage_port", StoragePortBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.0f));
    public static final StorageDeskBlock DESK = block("storage_desk", StorageDeskBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.0f));

    public static final BlockEntityType<StorageDeskBlockEntity> DESK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id("storage_desk"),
            new BlockEntityType<>(StorageDeskBlockEntity::new, Set.of(DESK)));

    /** Turned on the lathe out of a steel rod: small enough, and true enough, to count with. */
    public static final Item FINE_GEAR = item("fine_gear");
    /** A bronze plate rolled round and seamed in the tube mill. */
    public static final Item TUBE_SECTION = item("tube_section");
    /** The works of a port: what tells one thing from another and sends it the right way. */
    public static final Item SORTING_MECHANISM = item("sorting_mechanism");

    private StorageBlocks() { }

    /** Touching the class registers everything in it. */
    public static void initialize() {
        StorageDeskMenu.initialize();
    }

    /** The machines and blocks, for the machinery tab. */
    public static List<Item> items() {
        return List.of(ElectricBlocks.LATHE.asItem(), ElectricBlocks.TUBE_MILL.asItem(),
                TUBE.asItem(), PORT.asItem(), DESK.asItem());
    }

    /** The small parts the store room is built of, for the parts tab. */
    public static List<Item> parts() {
        return List.of(FINE_GEAR, TUBE_SECTION, SORTING_MECHANISM);
    }

    /** What the two machines make, as the compendium shows it. */
    public static List<WorldRecipes.WorldRecipe> worldRecipes() {
        return List.of(
                new WorldRecipes.WorldRecipe(Hardwrought.id("lathe/fine_gear"),
                        WorldRecipes.one(new ItemStack(MetalStock.rod("steel"))), new ItemStack(FINE_GEAR),
                        new ItemStack(ElectricBlocks.LATHE)),
                new WorldRecipes.WorldRecipe(Hardwrought.id("tube_mill/tube_section"),
                        WorldRecipes.one(new ItemStack(MetalStock.plate("bronze"))), new ItemStack(TUBE_SECTION),
                        new ItemStack(ElectricBlocks.TUBE_MILL)));
    }

    private static <T extends Block> T block(String name, Function<BlockBehaviour.Properties, T> factory,
                                             BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
        T block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    private static Item item(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }
}
