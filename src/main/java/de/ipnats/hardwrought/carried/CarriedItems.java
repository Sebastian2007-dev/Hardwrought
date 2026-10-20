package de.ipnats.hardwrought.carried;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;
import java.util.function.Function;

/**
 * Carried power (docs/electricity-roadmap.md, stage E4), registered: the three cells, the devices that
 * run on them, and the parts they are assembled from.
 */
public final class CarriedItems {
    /** The rung of the bench ladder where devices are laid out part by part. */
    public static final AssemblyBenchBlock ASSEMBLY_BENCH_BLOCK = Registry.register(BuiltInRegistries.BLOCK,
            ResourceKey.create(Registries.BLOCK, Hardwrought.id("assembly_bench")),
            new AssemblyBenchBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.0f)
                    .setId(ResourceKey.create(Registries.BLOCK, Hardwrought.id("assembly_bench")))));
    public static final Item ASSEMBLY_BENCH = item("assembly_bench", properties -> new BlockItem(ASSEMBLY_BENCH_BLOCK, properties),
            new Item.Properties().useBlockDescriptionPrefix());

    public static final Item LEAD_ACID_PACK = cell(Cell.LEAD_ACID);
    public static final Item NICKEL_IRON_CELL = cell(Cell.NICKEL_IRON);
    public static final Item SILVER_ZINC_CELL = cell(Cell.SILVER_ZINC);

    public static final Item HEADLAMP = item("headlamp", WornDeviceItem::new, worn(EquipmentSlot.HEAD));
    public static final Item NIGHT_VISION_GOGGLES = item("night_vision_goggles", WornDeviceItem::new, worn(EquipmentSlot.HEAD).rarity(Rarity.UNCOMMON));
    public static final Item EXOSKELETON = item("exoskeleton", WornDeviceItem::new, worn(EquipmentSlot.LEGS).rarity(Rarity.RARE));
    public static final Item ELECTRIC_DRILL = item("electric_drill", DrillItem::new, new Item.Properties().stacksTo(1));
    public static final Item CHAINSAW = item("chainsaw", ChainsawItem::new, new Item.Properties().stacksTo(1));
    public static final Item ORE_SCANNER = item("ore_scanner", OreScannerItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final Item HEATED_LINING = item("heated_lining", properties -> new LiningItem(true, properties), new Item.Properties().stacksTo(1));
    public static final Item COOLED_LINING = item("cooled_lining", properties -> new LiningItem(false, properties), new Item.Properties().stacksTo(1));

    // The parts that wear in a device, with their own durability.
    public static final Item STEEL_DRILL_BIT = item("steel_drill_bit", Item::new, new Item.Properties().durability(600));
    public static final Item TUNGSTEN_STEEL_DRILL_BIT = item("tungsten_steel_drill_bit", Item::new, new Item.Properties().durability(2400));
    public static final Item SAW_CHAIN = item("saw_chain", Item::new, new Item.Properties().durability(800));

    // What devices and cells are assembled from.
    public static final Item BATTERY_ACID = part("battery_acid", 16);
    public static final Item POTASH = part("potash", 64);
    public static final Item CELL_CASE = part("cell_case", 16);
    public static final Item TOGGLE_SWITCH = part("toggle_switch", 64);
    public static final Item DEVICE_HOUSING = part("device_housing", 16);
    public static final Item REDUCTION_GEAR = part("reduction_gear", 16);
    public static final Item CHUCK = part("chuck", 16);
    public static final Item SAW_BAR = part("saw_bar", 16);
    public static final Item STRAP = part("strap", 64);
    public static final Item NICHROME_MESH = part("nichrome_mesh", 16);
    public static final Item THERMOELECTRIC_PAD = part("thermoelectric_pad", 16);
    public static final Item REGULATOR = part("regulator", 16);
    public static final Item IMAGE_TUBE = part("image_tube", 16);
    public static final Item PULSE_COIL = part("pulse_coil", 16);
    public static final Item EXO_FRAME = part("exo_frame", 16);
    public static final Item JOINT_ACTUATOR = part("joint_actuator", 16);

    private CarriedItems() { }

    public static void initialize() {
        Cells.initialize();
        de.ipnats.hardwrought.core.registry.ModBlockEntities.KEPT_GRID.addValidBlock(ASSEMBLY_BENCH_BLOCK);
    }

    /** Everything a player can hold, for the creative tab. */
    public static List<Item> items() {
        return List.of(ASSEMBLY_BENCH, LEAD_ACID_PACK, NICKEL_IRON_CELL, SILVER_ZINC_CELL, HEADLAMP, NIGHT_VISION_GOGGLES, EXOSKELETON, ELECTRIC_DRILL,
                CHAINSAW, ORE_SCANNER, HEATED_LINING, COOLED_LINING, STEEL_DRILL_BIT, TUNGSTEN_STEEL_DRILL_BIT, SAW_CHAIN, BATTERY_ACID,
                POTASH, CELL_CASE, TOGGLE_SWITCH, DEVICE_HOUSING, REDUCTION_GEAR, CHUCK, SAW_BAR, STRAP, NICHROME_MESH,
                THERMOELECTRIC_PAD, REGULATOR, IMAGE_TUBE, PULSE_COIL, EXO_FRAME, JOINT_ACTUATOR);
    }

    /** Worn in a slot, put on as armour is, and on until switched off. */
    private static Item.Properties worn(EquipmentSlot slot) {
        return new Item.Properties().stacksTo(1).component(Cells.SWITCHED_ON, true)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(slot).setEquipSound(SoundEvents.ARMOR_EQUIP_IRON).build());
    }

    private static Item cell(Cell cell) {
        return item(cell.id(), properties -> new CellItem(cell, properties), new Item.Properties().stacksTo(1));
    }

    private static Item part(String name, int stack) {
        return item(name, Item::new, new Item.Properties().stacksTo(stack));
    }

    private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }
}
