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

    // Never placed: the look of a strung wire, drawn piece by piece along its sag.
    public static final Block WIRE_STRAND = strand("wire_strand", 0);
    public static final Block WIRE_STRAND_HOT = strand("wire_strand_hot", 9);
    public static final Block CABLE_STRAND = strand("cable_strand", 0);
    public static final Block CABLE_STRAND_HOT = strand("cable_strand_hot", 9);

    public static final Item COPPER_WIRE = item("copper_wire", properties -> new WireItem(Gauge.WIRE, properties));
    public static final Item COPPER_CABLE = item("copper_cable", properties -> new WireItem(Gauge.CABLE, properties));
    public static final Item METER = item("voltmeter", properties -> new MeterItem(properties.stacksTo(1)));

    /** The point a coil in hand was first held to, waiting for the second. */
    public static final DataComponentType<BlockPos> WIRE_START = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Hardwrought.id("wire_start"), DataComponentType.<BlockPos>builder()
                    .persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC).build());

    public static final BlockEntityType<ElectricBlockEntity> INSULATOR_ENTITY = entity("insulator",
            new BlockEntityType<>((pos, state) -> new ElectricBlockEntity(ElectricBlocks.INSULATOR_ENTITY, pos, state),
                    Set.of(INSULATOR)));
    public static final BlockEntityType<LampBlockEntity> LAMP_ENTITY = entity("electric_lamp",
            new BlockEntityType<>(LampBlockEntity::new, Set.of(ELECTRIC_LAMP)));
    public static final BlockEntityType<DynamoBlockEntity> DYNAMO_ENTITY = entity("dynamo",
            new BlockEntityType<>(DynamoBlockEntity::new, Set.of(DYNAMO)));

    public static final BlockEntityType<ElectricBlockEntity> COIL_ENTITY = entity("copper_coil",
            new BlockEntityType<>((pos, state) -> new ElectricBlockEntity(ElectricBlocks.COIL_ENTITY, pos, state),
                    Set.of(COPPER_COIL)));
    public static final BlockEntityType<ElectricBlockEntity> MAST_ENTITY = entity("mast",
            new BlockEntityType<>((pos, state) -> new ElectricBlockEntity(ElectricBlocks.MAST_ENTITY, pos, state),
                    Set.of(MAST)));
    public static final BlockEntityType<BatteryBlockEntity> BATTERY_ENTITY = entity("battery",
            new BlockEntityType<>(BatteryBlockEntity::new, Set.of(BATTERY)));
    /** One kind of entity for every machine: which machine it is, its block says. */
    public static final BlockEntityType<MachineBlockEntity> MACHINE_ENTITY = entity("basic_crusher",
            new BlockEntityType<>(MachineBlockEntity::new, Set.of(BASIC_CRUSHER, ELECTRIC_FURNACE, SAWMILL, PLATE_PRESS)));

    private ElectricBlocks() { }

    /** Touching the class registers everything in it. */
    public static void initialize() {
        MachineMenu.initialize();
    }

    /** Everything a player can hold, for the creative tab. */
    public static List<Item> items() {
        return List.of(DYNAMO.asItem(), COPPER_COIL.asItem(), INSULATOR.asItem(), MAST.asItem(), BATTERY.asItem(), ELECTRIC_LAMP.asItem(),
                BASIC_CRUSHER.asItem(), ELECTRIC_FURNACE.asItem(), SAWMILL.asItem(), PLATE_PRESS.asItem(), COPPER_WIRE, COPPER_CABLE, METER);
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
