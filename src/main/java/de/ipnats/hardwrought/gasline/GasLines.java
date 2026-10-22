package de.ipnats.hardwrought.gasline;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModBlockEntities;
import de.ipnats.hardwrought.core.registry.ModItems;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Gas lines, the gas tank and the canister of liquefied gas, and which blocks hold gas for the lines. */
public final class GasLines {
    /** Units one canister holds, as the drilling rig fills them. */
    public static final int CANISTER_UNITS = 16;

    public static final Map<GasLineTier, GasLineBlock> LINES = new EnumMap<>(GasLineTier.class);

    static {
        for (GasLineTier tier : GasLineTier.values()) {
            LINES.put(tier, block("gas_line_" + tier.serializedName(), properties -> new GasLineBlock(tier, properties),
                    BlockBehaviour.Properties.of().strength(1.5f + tier.ordinal() * 0.5f, 6.0f).requiresCorrectToolForDrops()
                            .sound(SoundType.COPPER).noOcclusion()));
        }
    }

    /** A stop valve in iron line: shut by hand or by redstone, it parts a line in two. */
    public static final GasValveBlock VALVE = block("gas_valve", GasValveBlock::new,
            BlockBehaviour.Properties.of().strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.COPPER).noOcclusion());

    public static final GasTankBlock TANK = block("gas_tank", GasTankBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 8.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** Gas pumps: gas in at the back, out at the front, never back (see {@link GasCompressor}). */
    public static final GasPumpBlock PUMP = block("gas_pump", GasPumpBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER));
    public static final ElectricGasPumpBlock ELECTRIC_PUMP = block("electric_gas_pump", ElectricGasPumpBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    /** Liquefied gas off a refinery: burns in a furnace like natural gas, and gives its canister back. */
    public static final Item LPG_CANISTER;

    static {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id("lpg_canister"));
        LPG_CANISTER = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().stacksTo(1)
                .craftRemainder(ModItems.GAS_CANISTER).setId(key)
                .cookingFuel(ResourceKey.create(Registries.CONTEXT_INT_PROVIDER, Hardwrought.id("cooking/time_lpg_canister")))));
    }

    public static final BlockEntityType<GasLineBlock.Entity> LINE_ENTITY = entity("gas_line",
            new BlockEntityType<>(GasLineBlock.Entity::new, lineBlocks()));
    public static final BlockEntityType<GasTankBlock.Entity> TANK_ENTITY = entity("gas_tank",
            new BlockEntityType<>(GasTankBlock.Entity::new, Set.of(TANK)));
    public static final BlockEntityType<GasPumpBlock.Entity> PUMP_ENTITY = entity("gas_pump",
            new BlockEntityType<>(GasPumpBlock.Entity::new, Set.of(PUMP)));
    public static final BlockEntityType<ElectricGasPumpBlock.Entity> ELECTRIC_PUMP_ENTITY = entity("electric_gas_pump",
            new BlockEntityType<>(ElectricGasPumpBlock.Entity::new, Set.of(ELECTRIC_PUMP)));

    private GasLines() { }

    /** Every block that is gas line: the five metals, and the valve. */
    private static Set<Block> lineBlocks() {
        Set<Block> blocks = new java.util.HashSet<>(LINES.values());
        blocks.add(VALVE);
        return Set.copyOf(blocks);
    }

    /** Touching the class registers everything; then the blocks that hold gas are offered to the lines. */
    public static void initialize() {
        GasStorage.SIDED.registerForBlockEntity((tank, side) -> tank.store, TANK_ENTITY);
        GasStorage.SIDED.registerForBlockEntity((pump, side) -> pump.storage(side), PUMP_ENTITY);
        GasStorage.SIDED.registerForBlockEntity((pump, side) -> pump.storage(side), ELECTRIC_PUMP_ENTITY);
        GasStorage.SIDED.registerForBlockEntity((rig, side) -> rig.gasOutlet(), ModBlockEntities.DRILLING_RIG);
    }

    /** The full canister of a gas; null for chlorine, steam and the hexafluorides, which are not bottled. */
    public static Item canister(GasKind kind) {
        return switch (kind) {
            case LPG -> LPG_CANISTER;
            case NATURAL_GAS -> ModItems.NATURAL_GAS_CANISTER;
            case CHLORINE, STEAM, URANIUM_HEXAFLUORIDE, ENRICHED_HEXAFLUORIDE -> null;
        };
    }

    /** What gas a full canister holds; null for anything else. */
    public static GasKind kindOf(ItemStack stack) {
        if (stack.is(ModItems.NATURAL_GAS_CANISTER)) return GasKind.NATURAL_GAS;
        if (stack.is(LPG_CANISTER)) return GasKind.LPG;
        return null;
    }

    public static List<Item> items() {
        List<Item> items = new ArrayList<>();
        for (GasLineBlock line : LINES.values()) items.add(line.asItem());
        items.add(VALVE.asItem());
        items.add(TANK.asItem());
        items.add(PUMP.asItem());
        items.add(ELECTRIC_PUMP.asItem());
        items.add(LPG_CANISTER);
        return items;
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
