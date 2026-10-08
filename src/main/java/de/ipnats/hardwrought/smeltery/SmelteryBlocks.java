package de.ipnats.hardwrought.smeltery;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * A smeltery in the manner of Tinkers' Construct: blocks, their entities, the fuel and the cast.
 * See {@link SmelteryControllerBlockEntity} for how it works.
 */
public final class SmelteryBlocks {
    public static final Block SMELTERY_BRICKS = block("smeltery_bricks", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_BRICKS));
    public static final Block SMELTERY_GLASS = block("smeltery_glass", TransparentBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).strength(1.5f));
    public static final Block DRAIN = block("smeltery_drain", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_BRICKS));
    /** Lava for fuel, in the wall: brick with a window, like the glass. */
    public static final SmelteryTankBlock TANK = block("smeltery_tank", SmelteryTankBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_BRICKS).noOcclusion().lightLevel(state -> 7));
    public static final SmelteryControllerBlock CONTROLLER = block("smeltery_controller", SmelteryControllerBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_BRICKS)
                    .lightLevel(state -> state.getValue(SmelteryControllerBlock.LIT) ? 13 : 0));
    public static final FaucetBlock FAUCET = block("faucet", FaucetBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_BRICKS).noOcclusion());
    public static final CastingTableBlock CASTING_TABLE = block("casting_table", CastingTableBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.NETHER_BRICKS).noOcclusion());
    /** Never placed: the look of molten metal, one state per metal, drawn by the smeltery's renderer. */
    public static final MoltenMetalBlock MOLTEN_METAL = register("molten_metal", MoltenMetalBlock::new,
            BlockBehaviour.Properties.of().noCollision().noOcclusion().lightLevel(state -> 15).noLootTable());

    public static final Item COKE = item("coke", Item::new);
    public static final Item UNFIRED_INGOT_CAST = item("unfired_ingot_cast", Item::new);
    public static final Item INGOT_CAST = item("ingot_cast", Item::new);

    public static final BlockEntityType<SmelteryControllerBlockEntity> CONTROLLER_ENTITY = entity("smeltery_controller",
            new BlockEntityType<>(SmelteryControllerBlockEntity::new, Set.of(CONTROLLER)));
    public static final BlockEntityType<SmelteryTankBlockEntity> TANK_ENTITY = entity("smeltery_tank",
            new BlockEntityType<>(SmelteryTankBlockEntity::new, Set.of(TANK)));
    public static final BlockEntityType<FaucetBlockEntity> FAUCET_ENTITY = entity("faucet",
            new BlockEntityType<>(FaucetBlockEntity::new, Set.of(FAUCET)));
    public static final BlockEntityType<CastingTableBlockEntity> CASTING_TABLE_ENTITY = entity("casting_table",
            new BlockEntityType<>(CastingTableBlockEntity::new, Set.of(CASTING_TABLE)));

    private SmelteryBlocks() { }

    /** Touching the class registers everything in it; the menu type with it. */
    public static void initialize() {
        SmelteryMenu.initialize();
    }

    /** Everything a player can hold, for the creative tab. */
    public static List<Item> items() {
        return List.of(SMELTERY_BRICKS.asItem(), SMELTERY_GLASS.asItem(), TANK.asItem(), CONTROLLER.asItem(), DRAIN.asItem(),
                FAUCET.asItem(), CASTING_TABLE.asItem(), UNFIRED_INGOT_CAST, INGOT_CAST, COKE);
    }

    private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory,
                                                BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
    }

    private static <T extends Block> T block(String name, Function<BlockBehaviour.Properties, T> factory,
                                             BlockBehaviour.Properties properties) {
        T block = register(name, factory, properties);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
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
