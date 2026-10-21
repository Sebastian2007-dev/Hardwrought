package de.ipnats.hardwrought.electricity;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Section 76: everything electricity is made of. See {@link Electricity} for how it works. */
public final class ElectricBlocks {
    public static final InsulatorBlock INSULATOR = block("insulator", InsulatorBlock::new,
            // Solid for water's purposes: a line that crosses a stream is not washed off its posts.
            BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS).strength(1.0f).noOcclusion().forceSolidOn());
    public static final ElectricLampBlock ELECTRIC_LAMP = block("electric_lamp", ElectricLampBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).strength(0.5f).noOcclusion().forceSolidOn()
                    .lightLevel(state -> state.getValue(ElectricLampBlock.LIGHT)));
    public static final DynamoBlock DYNAMO = block("dynamo", DynamoBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.0f).noOcclusion());
    /** The dynamo the other way round: current in, turns out. */
    public static final ElectricDriveBlock ELECTRIC_DRIVE = block("electric_drive", ElectricDriveBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.0f).noOcclusion());

    public static final CoilBlock COPPER_COIL = block("copper_coil", CoilBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(1.0f).noOcclusion().forceSolidOn());
    public static final MastBlock MAST = block("mast", MastBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_PLANKS).noOcclusion().forceSolidOn());
    public static final BatteryBlock BATTERY = block("battery", BatteryBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(2.5f).noOcclusion());
    public static final MachineBlock BASIC_CRUSHER = machine(Machine.CRUSHER);
    public static final MachineBlock ELECTRIC_FURNACE = machine(Machine.FURNACE);
    public static final MachineBlock SAWMILL = machine(Machine.SAWMILL);
    public static final MachineBlock PLATE_PRESS = machine(Machine.PRESS);
    public static final MachineBlock LATHE = machine(Machine.LATHE);
    public static final MachineBlock TUBE_MILL = machine(Machine.TUBE_MILL);
    public static final MachineBlock BASIC_CRUSHER_MK2 = machine(Machine.CRUSHER_MK2);
    public static final MachineBlock BASIC_CRUSHER_MK3 = machine(Machine.CRUSHER_MK3);
    public static final MachineBlock ELECTRIC_FURNACE_MK2 = machine(Machine.FURNACE_MK2);
    public static final MachineBlock ELECTRIC_FURNACE_MK3 = machine(Machine.FURNACE_MK3);
    public static final MachineBlock SAWMILL_MK2 = machine(Machine.SAWMILL_MK2);
    public static final MachineBlock SAWMILL_MK3 = machine(Machine.SAWMILL_MK3);
    public static final MachineBlock PLATE_PRESS_MK2 = machine(Machine.PRESS_MK2);
    public static final MachineBlock PLATE_PRESS_MK3 = machine(Machine.PRESS_MK3);
    public static final MachineBlock BASIC_CRUSHER_MK4 = machine(Machine.CRUSHER_MK4);
    public static final MachineBlock ELECTRIC_FURNACE_MK4 = machine(Machine.FURNACE_MK4);
    public static final MachineBlock SAWMILL_MK4 = machine(Machine.SAWMILL_MK4);
    public static final MachineBlock PLATE_PRESS_MK4 = machine(Machine.PRESS_MK4);
    public static final MachineBlock COIL_WINDER = machine(Machine.COIL_WINDER);
    public static final MachineBlock WIRE_DRAWER = machine(Machine.WIRE_DRAWER);
    public static final MachineBlock GLASS_KILN = machine(Machine.GLASS_KILN);
    public static final MachineBlock AUTOCLAVE = machine(Machine.AUTOCLAVE);
    public static final MachineBlock COIL_WINDER_MK2 = machine(Machine.COIL_WINDER_MK2);
    public static final MachineBlock WIRE_DRAWER_MK2 = machine(Machine.WIRE_DRAWER_MK2);
    public static final MachineBlock GLASS_KILN_MK2 = machine(Machine.GLASS_KILN_MK2);

    // The grid: transformers for long lines, porcelain where the voltage is high, and switchgear.
    public static final TransformerBlock TRANSFORMER = block("transformer", TransformerBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.5f).noOcclusion());
    /** A power station's transformer: oil-cooled, its windings of heavy copper. Fifteen times the current. */
    public static final TransformerBlock POWER_TRANSFORMER = block("power_transformer",
            properties -> new TransformerBlock(properties, TransformerBlockEntity.POWER_RATED_AMPS, TransformerBlockEntity.POWER_OHMS),
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(5.0f).noOcclusion());
    public static final InsulatorBlock PORCELAIN_INSULATOR = block("porcelain_insulator", InsulatorBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS).strength(1.0f).noOcclusion().forceSolidOn());
    /** A coil in a porcelain sleeve: where a line at high voltage goes into a transformer or a machine. */
    public static final CoilBlock BUSHING = block("bushing", CoilBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BRICKS).strength(1.0f).noOcclusion().forceSolidOn());
    public static final MastBlock HIGH_VOLTAGE_MAST = block("high_voltage_mast", MastBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_PLANKS).noOcclusion().forceSolidOn());
    public static final SwitchgearBlock KNIFE_SWITCH = switchgear("knife_switch", SwitchgearBlock.Kind.SWITCH);
    public static final SwitchgearBlock FUSE_BOX = switchgear("fuse_box", SwitchgearBlock.Kind.FUSE);
    public static final SwitchgearBlock BREAKER = switchgear("breaker", SwitchgearBlock.Kind.BREAKER);

    // Never placed: the look of a strung wire, drawn piece by piece along its sag.
    public static final Block WIRE_STRAND = strand("wire_strand", 0);
    public static final Block WIRE_STRAND_HOT = strand("wire_strand_hot", 9);
    public static final Block CABLE_STRAND = strand("cable_strand", 0);
    public static final Block CABLE_STRAND_HOT = strand("cable_strand_hot", 9);
    public static final Block INSULATED_STRAND = strand("insulated_strand", 0);
    public static final Block INSULATED_STRAND_HOT = strand("insulated_strand_hot", 9);
    public static final Block HEAVY_CABLE_STRAND = strand("heavy_cable_strand", 0);
    public static final Block HEAVY_CABLE_STRAND_HOT = strand("heavy_cable_strand_hot", 9);
    public static final Block OVERHEAD_STRAND = strand("overhead_strand", 0);
    public static final Block OVERHEAD_STRAND_HOT = strand("overhead_strand_hot", 9);

    public static final Item COPPER_WIRE = item("copper_wire", properties -> new WireItem(Gauge.WIRE, properties));
    public static final Item COPPER_CABLE = item("copper_cable", properties -> new WireItem(Gauge.CABLE, properties));
    public static final Item INSULATED_WIRE = item("insulated_wire", properties -> new WireItem(Gauge.INSULATED, properties));
    public static final Item HEAVY_POWER_CABLE = item("heavy_power_cable", properties -> new WireItem(Gauge.HEAVY_CABLE, properties));
    public static final Item OVERHEAD_CONDUCTOR = item("overhead_conductor", properties -> new WireItem(Gauge.OVERHEAD, properties));
    public static final Item METER = item("voltmeter", properties -> new MeterItem(properties.stacksTo(1)));

    /** The point a coil in hand was first held to, waiting for the second. */
    public static final DataComponentType<BlockPos> WIRE_START = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Hardwrought.id("wire_start"), DataComponentType.<BlockPos>builder()
                    .persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC).build());

    public static final BlockEntityType<ElectricBlockEntity> INSULATOR_ENTITY = entity("insulator",
            new BlockEntityType<>((pos, state) -> new ElectricBlockEntity(ElectricBlocks.INSULATOR_ENTITY, pos, state),
                    Set.of(INSULATOR, PORCELAIN_INSULATOR)));
    public static final BlockEntityType<LampBlockEntity> LAMP_ENTITY = entity("electric_lamp",
            new BlockEntityType<>(LampBlockEntity::new, Set.of(ELECTRIC_LAMP)));
    public static final BlockEntityType<DynamoBlockEntity> DYNAMO_ENTITY = entity("dynamo",
            new BlockEntityType<>(DynamoBlockEntity::new, Set.of(DYNAMO)));
    public static final BlockEntityType<ElectricDriveBlockEntity> ELECTRIC_DRIVE_ENTITY = entity("electric_drive",
            new BlockEntityType<>(ElectricDriveBlockEntity::new, Set.of(ELECTRIC_DRIVE)));

    public static final BlockEntityType<ElectricBlockEntity> COIL_ENTITY = entity("copper_coil",
            new BlockEntityType<>((pos, state) -> new ElectricBlockEntity(ElectricBlocks.COIL_ENTITY, pos, state),
                    Set.of(COPPER_COIL, BUSHING)));
    public static final BlockEntityType<ElectricBlockEntity> MAST_ENTITY = entity("mast",
            new BlockEntityType<>((pos, state) -> new ElectricBlockEntity(ElectricBlocks.MAST_ENTITY, pos, state),
                    Set.of(MAST, HIGH_VOLTAGE_MAST)));
    public static final BlockEntityType<TransformerBlockEntity> TRANSFORMER_ENTITY = entity("transformer",
            new BlockEntityType<>(TransformerBlockEntity::new, Set.of(TRANSFORMER, POWER_TRANSFORMER)));
    public static final BlockEntityType<SwitchgearBlockEntity> SWITCHGEAR_ENTITY = entity("switchgear",
            new BlockEntityType<>(SwitchgearBlockEntity::new, Set.of(KNIFE_SWITCH, FUSE_BOX, BREAKER)));
    public static final BlockEntityType<BatteryBlockEntity> BATTERY_ENTITY = entity("battery",
            new BlockEntityType<>(BatteryBlockEntity::new, Set.of(BATTERY)));
    /** One kind of entity for every machine: which machine it is, its block says. */
    public static final BlockEntityType<MachineBlockEntity> MACHINE_ENTITY = entity("basic_crusher",
            new BlockEntityType<>(MachineBlockEntity::new, Set.of(BASIC_CRUSHER, ELECTRIC_FURNACE, SAWMILL, PLATE_PRESS,
                    BASIC_CRUSHER_MK2, BASIC_CRUSHER_MK3, ELECTRIC_FURNACE_MK2, ELECTRIC_FURNACE_MK3, SAWMILL_MK2, SAWMILL_MK3,
                    PLATE_PRESS_MK2, PLATE_PRESS_MK3, LATHE, TUBE_MILL, BASIC_CRUSHER_MK4, ELECTRIC_FURNACE_MK4,
                    SAWMILL_MK4, PLATE_PRESS_MK4, COIL_WINDER, WIRE_DRAWER, GLASS_KILN, AUTOCLAVE,
                    COIL_WINDER_MK2, WIRE_DRAWER_MK2, GLASS_KILN_MK2)));

    private ElectricBlocks() { }

    /** Touching the class registers everything in it. */
    public static void initialize() {
        MachineMenu.initialize();
    }

    /** Everything a player can hold, for the creative tab. */
    public static List<Item> items() {
        return List.of(DYNAMO.asItem(), ELECTRIC_DRIVE.asItem(), COPPER_COIL.asItem(), INSULATOR.asItem(), MAST.asItem(), BATTERY.asItem(), ELECTRIC_LAMP.asItem(),
                BASIC_CRUSHER.asItem(), ELECTRIC_FURNACE.asItem(), SAWMILL.asItem(), PLATE_PRESS.asItem(),
                BASIC_CRUSHER_MK2.asItem(), BASIC_CRUSHER_MK3.asItem(), ELECTRIC_FURNACE_MK2.asItem(), ELECTRIC_FURNACE_MK3.asItem(),
                SAWMILL_MK2.asItem(), SAWMILL_MK3.asItem(), PLATE_PRESS_MK2.asItem(), PLATE_PRESS_MK3.asItem(),
                BASIC_CRUSHER_MK4.asItem(), ELECTRIC_FURNACE_MK4.asItem(), SAWMILL_MK4.asItem(), PLATE_PRESS_MK4.asItem(),
                COIL_WINDER.asItem(), WIRE_DRAWER.asItem(), GLASS_KILN.asItem(), AUTOCLAVE.asItem(),
                COIL_WINDER_MK2.asItem(), WIRE_DRAWER_MK2.asItem(), GLASS_KILN_MK2.asItem(), TRANSFORMER.asItem(), POWER_TRANSFORMER.asItem(), BUSHING.asItem(), PORCELAIN_INSULATOR.asItem(),
                HIGH_VOLTAGE_MAST.asItem(), KNIFE_SWITCH.asItem(), FUSE_BOX.asItem(), BREAKER.asItem(),
                COPPER_WIRE, COPPER_CABLE, INSULATED_WIRE, HEAVY_POWER_CABLE, OVERHEAD_CONDUCTOR, METER);
    }

    /** Whether this is a support of porcelain, which stands high voltage in the rain. */
    public static boolean porcelain(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(PORCELAIN_INSULATOR) || state.is(BUSHING) || state.is(HIGH_VOLTAGE_MAST);
    }

    private static SwitchgearBlock switchgear(String name, SwitchgearBlock.Kind kind) {
        return block(name, properties -> new SwitchgearBlock(kind, properties),
                BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(2.0f).noOcclusion());
    }

    private static MachineBlock machine(Machine machine) {
        return block(machine.id(), properties -> new MachineBlock(machine, properties),
                BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(3.0f).noOcclusion());
    }

    private static Block strand(String name, int light) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, new Block(BlockBehaviour.Properties.of()
                .noCollision().noOcclusion().noLootTable().lightLevel(state -> light).setId(key)));
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

    private static Item item(String name, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
    }

    private static <T extends BlockEntityType<?>> T entity(String name, T type) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id(name), type);
    }
}
