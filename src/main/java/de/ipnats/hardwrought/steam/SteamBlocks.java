package de.ipnats.hardwrought.steam;

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

/**
 * Steam (development plan section 85): a firebox, a boiler on it, a safety valve on that, gas line to a
 * steam engine that turns a shaft, and a whistle. The stop valve is a gas line's, in {@code GasLines}.
 * See docs/steam.md.
 */
public final class SteamBlocks {
    public static final FireboxBlock FIREBOX = block("firebox", FireboxBlock::new, iron(3.5f)
            .lightLevel(state -> state.getValue(FireboxBlock.LIT) ? 13 : 0));
    public static final BoilerBlock BOILER = block("boiler", BoilerBlock::new, iron(4.0f));
    public static final SafetyValveBlock SAFETY_VALVE = block("safety_valve", SafetyValveBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f, 6.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER).noOcclusion());
    public static final SteamEngineBlock STEAM_ENGINE = block("steam_engine", SteamEngineBlock::new, iron(3.5f).noOcclusion());
    public static final SteamWhistleBlock STEAM_WHISTLE = block("steam_whistle", SteamWhistleBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(2.0f, 6.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER).noOcclusion());

    /** The cylinder of a steam engine, with its piston, rod and slide valve: the part that turns steam into work. */
    public static final Item STEAM_CYLINDER;

    static {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id("steam_cylinder"));
        STEAM_CYLINDER = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().stacksTo(16).setId(key)));
    }

    public static final BlockEntityType<FireboxBlock.Entity> FIREBOX_ENTITY = entity("firebox",
            new BlockEntityType<>(FireboxBlock.Entity::new, Set.of(FIREBOX)));
    public static final BlockEntityType<BoilerBlock.Entity> BOILER_ENTITY = entity("boiler",
            new BlockEntityType<>(BoilerBlock.Entity::new, Set.of(BOILER)));
    public static final BlockEntityType<SteamEngineBlock.Entity> STEAM_ENGINE_ENTITY = entity("steam_engine",
            new BlockEntityType<>(SteamEngineBlock.Entity::new, Set.of(STEAM_ENGINE)));
    public static final BlockEntityType<SteamWhistleBlock.Entity> STEAM_WHISTLE_ENTITY = entity("steam_whistle",
            new BlockEntityType<>(SteamWhistleBlock.Entity::new, Set.of(STEAM_WHISTLE)));

    private SteamBlocks() { }

    /** Touching the class registers everything; then pipes and gas lines are told what each holds. */
    public static void initialize() {
        FluidStorage.SIDED.registerForBlockEntity((boiler, side) -> boiler.water, BOILER_ENTITY);
        GasStorage.SIDED.registerForBlockEntity((boiler, side) -> boiler.steam, BOILER_ENTITY);
        GasStorage.SIDED.registerForBlockEntity((engine, side) ->
                side == engine.getBlockState().getValue(SteamEngineBlock.FACING).getOpposite() ? null : engine.inlet, STEAM_ENGINE_ENTITY);
        GasStorage.SIDED.registerForBlockEntity((whistle, side) -> whistle.inlet, STEAM_WHISTLE_ENTITY);
    }

    public static List<Item> items() {
        return List.of(FIREBOX.asItem(), BOILER.asItem(), SAFETY_VALVE.asItem(), STEAM_ENGINE.asItem(), STEAM_WHISTLE.asItem(),
                STEAM_CYLINDER);
    }

    private static BlockBehaviour.Properties iron(float hardness) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(hardness, 8.0f).requiresCorrectToolForDrops()
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
