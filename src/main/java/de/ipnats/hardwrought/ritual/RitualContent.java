package de.ipnats.hardwrought.ritual;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * What the end of the game is made of (endgame § 5): the altar for the dragon's heart and the eight
 * pedestals for the offerings, made and set in place by the players in the ritual chamber of a temple they
 * build round them (see {@link Temple}).
 */
public final class RitualContent {
    public static final Block ALTAR = block("ritual_altar", RitualAltar::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
            .strength(6.0f, 1200.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_TILES).noOcclusion().lightLevel(state -> 10)
            .pushReaction(PushReaction.IMMOVEABLE));
    public static final Block PEDESTAL = block("ritual_pedestal", RitualPedestal::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
            .strength(5.0f, 1200.0f).requiresCorrectToolForDrops().sound(SoundType.POLISHED_DEEPSLATE).noOcclusion().lightLevel(state -> 6)
            .pushReaction(PushReaction.IMMOVEABLE));
    public static final BlockEntityType<RitualAltar.Entity> ALTAR_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hardwrought.id("ritual_altar"), new BlockEntityType<>(RitualAltar.Entity::new, Set.of(ALTAR)));
    public static final BlockEntityType<RitualPedestal.Entity> PEDESTAL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hardwrought.id("ritual_pedestal"), new BlockEntityType<>(RitualPedestal.Entity::new, Set.of(PEDESTAL)));

    public static final Item ALTAR_ITEM = blockItem(ALTAR);
    public static final Item PEDESTAL_ITEM = blockItem(PEDESTAL);

    /**
     * The eight offerings, one from every part of the game, in the order of the pedestals round the altar:
     * the Wither's star, the first dragon's egg, a shard of the Underworld, the void's crystal, adamant from
     * the forge, a motor from the electric line, diesel from the refinery, and a wand of magic.
     */
    public static final List<Identifier> OFFERINGS = List.of(
            Identifier.withDefaultNamespace("nether_star"),
            Identifier.withDefaultNamespace("dragon_egg"),
            Hardwrought.id("pale_shard"),
            Hardwrought.id("void_crystal_block"),
            Hardwrought.id("adamantium_ingot"),
            Hardwrought.id("electric_motor"),
            Hardwrought.id("diesel_bucket"),
            Hardwrought.id("golden_wand"));

    private RitualContent() { }

    public static void initialize() {
        Ritual.initialize();
        TempleFaults.initialize();
        Temple.initialize();
    }

    public static List<Item> items() {
        return List.of(ALTAR_ITEM, PEDESTAL_ITEM);
    }

    /** Whether this is one of the eight offerings. */
    public static boolean isOffering(net.minecraft.world.item.ItemStack stack) {
        return !stack.isEmpty() && OFFERINGS.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** The offering this pedestal wants. */
    public static Item offering(int index) {
        if (index < 0 || index >= OFFERINGS.size()) return Items.AIR;
        return BuiltInRegistries.ITEM.getValue(OFFERINGS.get(index));
    }

    private static Item blockItem(Block block) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
        BlockItem item = new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix().rarity(Rarity.EPIC));
        item.registerBlocks(Item.BY_BLOCK, item);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
    }
}
