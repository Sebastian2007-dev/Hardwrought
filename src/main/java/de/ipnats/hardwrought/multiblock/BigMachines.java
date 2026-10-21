package de.ipnats.hardwrought.multiblock;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The big machines built of several blocks — the large arc furnace, the large diesel generator and the
 * battery bank — and the blocks they are built of: casings, the furnace's electrode, the engine core,
 * the cell stacks, controllers and ports.
 */
public final class BigMachines {
    public static final Block REFRACTORY_CASING = block("refractory_casing", Block::new, casing());
    public static final Block ENGINE_CASING = block("engine_casing", Block::new, casing());
    public static final Block GRAPHITE_ELECTRODE = block("graphite_electrode", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.0f, 6.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.STONE));
    public static final Block ENGINE_CORE = block("engine_core", Block::new, casing());
    public static final ControllerBlock ARC_FURNACE_CONTROLLER = block("arc_furnace_controller",
            p -> new ControllerBlock(ControllerBlock.Machine.ARC_FURNACE, p), casing()
                    .lightLevel(state -> state.getValue(ControllerBlock.RUNNING) ? 10 : 0));
    public static final ControllerBlock LARGE_DIESEL_CONTROLLER = block("large_diesel_controller",
            p -> new ControllerBlock(ControllerBlock.Machine.LARGE_DIESEL, p), casing());
    /** Steel and plastic: the hull of the battery bank. */
    public static final Block BATTERY_BANK_CASING = block("battery_bank_casing", Block::new, casing());
    /** A rack of lead-acid cells in plastic cases, wired in series to the bank's voltage. */
    public static final Block BATTERY_CELL_STACK = block("battery_cell_stack", Block::new, casing());
    public static final ControllerBlock BATTERY_BANK_CONTROLLER = block("battery_bank_controller",
            p -> new ControllerBlock(ControllerBlock.Machine.BATTERY_BANK, p), casing());
    public static final PortBlock FLUID_PORT = block("fluid_port", p -> new PortBlock(PortBlock.Kind.FLUID, p), casing());
    public static final PortBlock ITEM_PORT = block("item_port", p -> new PortBlock(PortBlock.Kind.ITEM, p), casing());
    public static final PortBlock POWER_PORT = block("power_port", p -> new PortBlock(PortBlock.Kind.POWER, p), casing());
    /** Steam out of a reactor and into a turbine, uranium hexafluoride through the centrifuges. */
    public static final PortBlock GAS_PORT = block("gas_port", p -> new PortBlock(PortBlock.Kind.GAS, p), casing());

    public static final BlockEntityType<ArcFurnaceController> ARC_FURNACE_ENTITY = entity("arc_furnace_controller",
            new BlockEntityType<>(ArcFurnaceController::new, Set.of(ARC_FURNACE_CONTROLLER)));
    public static final BlockEntityType<LargeDieselController> LARGE_DIESEL_ENTITY = entity("large_diesel_controller",
            new BlockEntityType<>(LargeDieselController::new, Set.of(LARGE_DIESEL_CONTROLLER)));
    public static final BlockEntityType<BatteryBankController> BATTERY_BANK_ENTITY = entity("battery_bank_controller",
            new BlockEntityType<>(BatteryBankController::new, Set.of(BATTERY_BANK_CONTROLLER)));
    public static final BlockEntityType<PortBlock.Entity> PORT_ENTITY = entity("machine_port",
            new BlockEntityType<>(PortBlock.Entity::new, Set.of(FLUID_PORT, ITEM_PORT, POWER_PORT, GAS_PORT)));

    private BigMachines() { }

    /** Touching the class registers everything; then pipes are told what each port leads to. */
    public static void initialize() {
        FluidStorage.SIDED.registerForBlockEntity((port, side) -> port.kind() == PortBlock.Kind.FLUID && port.host() != null
                ? port.host().fluid(port.getBlockPos()) : null, PORT_ENTITY);
        ItemStorage.SIDED.registerForBlockEntity((port, side) -> port.kind() == PortBlock.Kind.ITEM && port.host() != null
                ? port.host().items(port.getBlockPos(), side) : null, PORT_ENTITY);
        de.ipnats.hardwrought.gasline.GasStorage.SIDED.registerForBlockEntity((port, side) -> port.kind() == PortBlock.Kind.GAS
                && port.host() != null ? port.host().gas(port.getBlockPos()) : null, PORT_ENTITY);
    }

    public static List<Item> items() {
        return List.of(ARC_FURNACE_CONTROLLER.asItem(), REFRACTORY_CASING.asItem(), GRAPHITE_ELECTRODE.asItem(),
                LARGE_DIESEL_CONTROLLER.asItem(), ENGINE_CASING.asItem(), ENGINE_CORE.asItem(),
                BATTERY_BANK_CONTROLLER.asItem(), BATTERY_BANK_CASING.asItem(), BATTERY_CELL_STACK.asItem(),
                FLUID_PORT.asItem(), ITEM_PORT.asItem(), POWER_PORT.asItem(), GAS_PORT.asItem());
    }

    private static BlockBehaviour.Properties casing() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0f, 10.0f).requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
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

    private static <T extends BlockEntityType<?>> T entity(String name, T type) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id(name), type);
    }
}
