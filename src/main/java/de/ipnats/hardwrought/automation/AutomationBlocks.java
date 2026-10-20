package de.ipnats.hardwrought.automation;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Automation (docs/electricity-roadmap.md, stage E5), registered: the conveyor and the assembler. */
public final class AutomationBlocks {
    public static final ConveyorBlock CONVEYOR = block("conveyor", ConveyorBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(1.5f).noOcclusion());
    public static final BlockEntityType<ConveyorBlockEntity> CONVEYOR_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hardwrought.id("conveyor"), new BlockEntityType<>(ConveyorBlockEntity::new, Set.of(CONVEYOR)));

    /** A bottle of oil for a machine's bearings (section 78). */
    public static final Item MACHINE_OIL = item("machine_oil", 16);
    /** New bearings and gaskets for a worn machine. */
    public static final Item SERVICE_KIT = item("service_kit", 16);
    /** An assembler's arm: two joints, a reach and a gripping chuck. */
    public static final Item GRIPPER_ARM = item("gripper_arm", 16);
    /** A drawing for an assembler: blank, or with the plan of one thing on it. */
    public static final Item DRAWING = Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, Hardwrought.id("drawing")),
            new DrawingItem(new Item.Properties().stacksTo(16).setId(ResourceKey.create(Registries.ITEM, Hardwrought.id("drawing")))));

    private AutomationBlocks() { }

    static Item item(String name, int stack) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().stacksTo(stack).setId(key)));
    }

    /** Touching the class registers everything in it. */
    public static void initialize() { }

    /** Everything a player can hold, for the creative tab. */
    public static List<Item> items() {
        List<Item> items = new ArrayList<>();
        items.add(CONVEYOR.asItem());
        items.add(MACHINE_OIL);
        items.add(SERVICE_KIT);
        items.add(DRAWING);
        items.add(GRIPPER_ARM);
        return items;
    }

    static <T extends Block> T block(String name, Function<BlockBehaviour.Properties, T> factory, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
        T block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }
}
