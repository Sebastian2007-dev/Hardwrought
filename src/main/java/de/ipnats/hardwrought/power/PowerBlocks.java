package de.ipnats.hardwrought.power;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.gasline.GasStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
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

/** What oil and gas are burnt in for power, heat and light: engines, a burner, a lamp. */
public final class PowerBlocks {
    public static final DieselGeneratorBlock DIESEL_GENERATOR = block("diesel_generator", DieselGeneratorBlock::new, engine());
    public static final GasolineEngineBlock GASOLINE_ENGINE = block("gasoline_engine", GasolineEngineBlock::new, engine());
    public static final GasBurnerBlock GAS_BURNER = block("gas_burner", GasBurnerBlock::new,
            engine().lightLevel(state -> state.getValue(GasBurnerBlock.LIT) ? 12 : 0));
    public static final PetroleumLampBlock PETROLEUM_LAMP = block("petroleum_lamp", PetroleumLampBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0f).sound(SoundType.LANTERN).noOcclusion()
                    .lightLevel(state -> state.getValue(PetroleumLampBlock.LIT) ? 15 : 0));

    public static final BlockEntityType<DieselGeneratorBlock.Entity> DIESEL_GENERATOR_ENTITY = entity("diesel_generator",
            new BlockEntityType<>(DieselGeneratorBlock.Entity::new, Set.of(DIESEL_GENERATOR)));
    public static final BlockEntityType<GasolineEngineBlock.Entity> GASOLINE_ENGINE_ENTITY = entity("gasoline_engine",
            new BlockEntityType<>(GasolineEngineBlock.Entity::new, Set.of(GASOLINE_ENGINE)));
    public static final BlockEntityType<GasBurnerBlock.Entity> GAS_BURNER_ENTITY = entity("gas_burner",
            new BlockEntityType<>(GasBurnerBlock.Entity::new, Set.of(GAS_BURNER)));
    public static final BlockEntityType<PetroleumLampBlock.Entity> PETROLEUM_LAMP_ENTITY = entity("petroleum_lamp",
            new BlockEntityType<>(PetroleumLampBlock.Entity::new, Set.of(PETROLEUM_LAMP)));

    private PowerBlocks() { }

    /** Touching the class registers everything; then pipes and gas lines are told what each takes. */
    public static void initialize() {
        FluidStorage.SIDED.registerForBlockEntity((generator, side) -> generator.fuel, DIESEL_GENERATOR_ENTITY);
        FluidStorage.SIDED.registerForBlockEntity((engine, side) -> engine.fuel, GASOLINE_ENGINE_ENTITY);
        GasStorage.SIDED.registerForBlockEntity((burner, side) -> burner.intake, GAS_BURNER_ENTITY);
    }

    public static List<Item> items() {
        return List.of(DIESEL_GENERATOR.asItem(), GASOLINE_ENGINE.asItem(), GAS_BURNER.asItem(), PETROLEUM_LAMP.asItem());
    }

    private static BlockBehaviour.Properties engine() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 8.0f).requiresCorrectToolForDrops()
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
