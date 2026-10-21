package de.ipnats.hardwrought.nuclear;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.multiblock.ControllerBlock;
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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The blocks of nuclear engineering: fluorite in the ground, the three big machines — the reactor, the
 * steam turbine and the centrifuge cascade — and what they are built of, and what is left when a core
 * melts.
 */
public final class NuclearBlocks {
    // ---------------------------------------------------------------- the ground

    public static final Block FLUORITE_ORE = block("fluorite_ore",
            p -> new DropExperienceBlock(UniformInt.of(1, 3), p), BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE));
    public static final Block DEEPSLATE_FLUORITE_ORE = block("deepslate_fluorite_ore",
            p -> new DropExperienceBlock(UniformInt.of(1, 3), p), BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_IRON_ORE));

    // ---------------------------------------------------------------- the reactor

    /** Steel and lead in concrete: the pressure vessel and the first of the shield. */
    public static final Block REACTOR_VESSEL_WALL = block("reactor_vessel_wall", Block::new, heavy().strength(8.0f, 60.0f));
    /** A zircaloy channel a fuel assembly is lowered into, the coolant streaming up past it. */
    public static final Block FUEL_CHANNEL = block("fuel_channel", Block::new, casing());
    /** A motor on the vessel's head holding a control rod up by an electromagnet: lose the current, and the rod drops. */
    public static final Block CONTROL_ROD_DRIVE = block("control_rod_drive", Block::new, casing());
    public static final ControllerBlock REACTOR_CONTROLLER = block("reactor_controller",
            p -> new ControllerBlock(ControllerBlock.Machine.REACTOR, p), heavy()
                    .lightLevel(state -> state.getValue(ControllerBlock.RUNNING) ? 7 : 0));

    // ---------------------------------------------------------------- the turbine

    public static final Block TURBINE_CASING = block("turbine_casing", Block::new, casing());
    /** Rows of blades on a shaft, with the generator's rotor at its end. */
    public static final Block TURBINE_ROTOR = block("turbine_rotor", Block::new, casing());
    /** Bundles of copper tube with cold water through them: the spent steam turns back to water on them. */
    public static final Block CONDENSER = block("condenser", Block::new, casing());
    public static final ControllerBlock TURBINE_CONTROLLER = block("turbine_controller",
            p -> new ControllerBlock(ControllerBlock.Machine.TURBINE, p), casing());

    // ---------------------------------------------------------------- the centrifuges

    public static final Block CENTRIFUGE_CASING = block("centrifuge_casing", Block::new, casing());
    /** A tall rotor in a vacuum: the heavier uranium-238 is flung to the wall, the lighter 235 stays by the axis. */
    public static final Block GAS_CENTRIFUGE = block("gas_centrifuge", Block::new, casing());
    public static final ControllerBlock CENTRIFUGE_CONTROLLER = block("centrifuge_controller",
            p -> new ControllerBlock(ControllerBlock.Machine.CENTRIFUGE, p), casing());

    // ---------------------------------------------------------------- what a meltdown leaves

    /** Molten core, set into a glowing lava-like rock: fuel, cladding and steel together. Deadly to stand near. */
    public static final Block CORIUM = block("corium", CoriumBlock::new, BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLACK).strength(50.0f, 1200.0f).requiresCorrectToolForDrops().sound(SoundType.BASALT)
            .lightLevel(state -> 10));
    /** Rubble a core threw out when it burst: radioactive for a long time. */
    public static final Block RADIOACTIVE_DEBRIS = block("radioactive_debris", Block::new, BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GRAY).strength(1.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.GRAVEL));

    public static final BlockEntityType<ReactorController> REACTOR_ENTITY = entity("reactor_controller",
            new BlockEntityType<>(ReactorController::new, Set.of(REACTOR_CONTROLLER)));
    public static final BlockEntityType<TurbineController> TURBINE_ENTITY = entity("turbine_controller",
            new BlockEntityType<>(TurbineController::new, Set.of(TURBINE_CONTROLLER)));
    public static final BlockEntityType<CentrifugeController> CENTRIFUGE_ENTITY = entity("centrifuge_controller",
            new BlockEntityType<>(CentrifugeController::new, Set.of(CENTRIFUGE_CONTROLLER)));

    private NuclearBlocks() { }

    /** Touching the class registers everything in it. */
    public static void initialize() { }

    public static List<Item> items() {
        return List.of(FLUORITE_ORE.asItem(), DEEPSLATE_FLUORITE_ORE.asItem(),
                REACTOR_CONTROLLER.asItem(), REACTOR_VESSEL_WALL.asItem(), FUEL_CHANNEL.asItem(), CONTROL_ROD_DRIVE.asItem(),
                TURBINE_CONTROLLER.asItem(), TURBINE_CASING.asItem(), TURBINE_ROTOR.asItem(), CONDENSER.asItem(),
                CENTRIFUGE_CONTROLLER.asItem(), CENTRIFUGE_CASING.asItem(), GAS_CENTRIFUGE.asItem(),
                CORIUM.asItem(), RADIOACTIVE_DEBRIS.asItem());
    }

    private static BlockBehaviour.Properties casing() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0f, 10.0f).requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
    }

    private static BlockBehaviour.Properties heavy() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK);
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
