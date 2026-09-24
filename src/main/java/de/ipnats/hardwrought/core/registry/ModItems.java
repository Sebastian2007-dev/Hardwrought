package de.ipnats.hardwrought.core.registry;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import de.ipnats.hardwrought.environment.SafetyLampItem;
import de.ipnats.hardwrought.survival.WaterskinItem;
import net.minecraft.world.item.ToolMaterial;

import java.util.function.Function;

/** Central registration point for Hardwrought items. */
public final class ModItems {
    public static final Item FLINT_SHARD = register("flint_shard", Item::new, new Item.Properties());
    public static final Item COBBLESTONE_PIECE = register("cobblestone_piece", Item::new, new Item.Properties());
    public static final Item DIRT_BLOB = register("dirt_blob", Item::new, new Item.Properties());
    public static final Item DIRT_SLAB = register("dirt_slab", properties -> new BlockItem(ModBlocks.DIRT_SLAB,
            properties), new Item.Properties().useBlockDescriptionPrefix());
    public static final Item LEAF_STRING = register("leaf_string", Item::new, new Item.Properties());
    // Milestone 8, section 79: the compendium is a book a player carries as well as a key they press.
    public static final Item COMPENDIUM = register("compendium",
            de.ipnats.hardwrought.knowledge.CompendiumItem::new, new Item.Properties().stacksTo(1));
    // The pack is what unlocks the inventory at all, so the plainest one is reachable from leaf
    // fibre alone and in the two-by-two grid a player always has.
    public static final Item STARTER_BACKPACK = register("starter_backpack",
            properties -> new de.ipnats.hardwrought.equipment.BackpackItem(
                    de.ipnats.hardwrought.equipment.BackpackTier.STARTER, properties),
            new Item.Properties().stacksTo(1));
    public static final Item BASIC_BACKPACK = register("basic_backpack",
            properties -> new de.ipnats.hardwrought.equipment.BackpackItem(
                    de.ipnats.hardwrought.equipment.BackpackTier.BASIC, properties),
            new Item.Properties().stacksTo(1));
    /** A stick rubbed to a point on stone: the start of a needle. */
    public static final Item POINTED_STICK = register("pointed_stick", Item::new, new Item.Properties());
    /** Sharpened wood with a cord through it: weaves leaf cord into cloth, slowly. */
    public static final Item SEWING_NEEDLE = register("sewing_needle",
            properties -> new de.ipnats.hardwrought.progression.SewingNeedleItem(properties,
                    de.ipnats.hardwrought.progression.SewingNeedleItem.WOODEN_WEAVE_TICKS),
            new Item.Properties().durability(16));
    /** A needle forged from iron at the anvil: weaves much faster, and lasts. */
    public static final Item IRON_SEWING_NEEDLE = register("iron_sewing_needle",
            properties -> new de.ipnats.hardwrought.progression.SewingNeedleItem(properties,
                    de.ipnats.hardwrought.progression.SewingNeedleItem.IRON_WEAVE_TICKS),
            new Item.Properties().durability(128));
    /** A piece of cloth woven from leaf cord. Four make a woven pack. */
    public static final Item WOVEN = register("woven", Item::new, new Item.Properties());
    /** Hardcore's second pack: the woven one lashed to a frame of sticks. */
    public static final Item FRAME_BACKPACK = register("frame_backpack",
            properties -> new de.ipnats.hardwrought.equipment.BackpackItem(
                    de.ipnats.hardwrought.equipment.BackpackTier.FRAME, properties),
            new Item.Properties().stacksTo(1));
    /** Fibre stripped from leaves, still green: binds a tool as it is, dries on a rack into cord. */
    public static final Item GREEN_FIBRE = register("green_fibre", Item::new, new Item.Properties());
    public static final Item DRYING_RACK = register("drying_rack",
            properties -> new BlockItem(ModBlocks.DRYING_RACK, properties),
            new Item.Properties().useBlockDescriptionPrefix());
    public static final Item HEWN_WORKBENCH = register("hewn_workbench",
            properties -> new BlockItem(ModBlocks.HEWN_WORKBENCH, properties),
            new Item.Properties().useBlockDescriptionPrefix());
    public static final Item NAILED_WORKBENCH = register("nailed_workbench",
            properties -> new BlockItem(ModBlocks.NAILED_WORKBENCH, properties),
            new Item.Properties().useBlockDescriptionPrefix());
    // Milestone 7, section 56: three parts copper to one of tin is bronze, and the mixture is what
    // goes back into the fire. The metals themselves, tin included, are registered from the table
    // in de.ipnats.hardwrought.metallurgy.Metal rather than one by one here.
    public static final Item BRONZE_MIXTURE = register("bronze_mixture", Item::new, new Item.Properties());
    /** Nailing the hewn bench together is what turns it into the second one. */
    public static final Item BRONZE_NAILS = register("bronze_nails", Item::new, new Item.Properties());
    /** A wooden mallet: the first smith's hammer. Moves one square of metal per blow. */
    public static final Item WOODEN_HAMMER = register("wooden_hammer", Item::new, new Item.Properties().durability(48));
    /** A stone head on a stick: drives the nails into the hewn bench; two by two squares at the anvil. */
    public static final Item HAMMER = register("hammer", Item::new, new Item.Properties().durability(96));
    /** The stone hammer with an iron head: three by three squares at the anvil. */
    public static final Item IRON_HAMMER = register("iron_hammer", Item::new, new Item.Properties().durability(320));
    /** Worn on the strap; without them hot metal cannot be held. */
    public static final Item WOODEN_ANVIL = register("wooden_anvil",
            properties -> new BlockItem(ModBlocks.WOODEN_ANVIL, properties),
            new Item.Properties().useBlockDescriptionPrefix());
    public static final Item FORGE = register("forge",
            properties -> new BlockItem(ModBlocks.FORGE, properties),
            new Item.Properties().useBlockDescriptionPrefix());
    public static final Item BELLOWS = register("bellows",
            properties -> new BlockItem(ModBlocks.BELLOWS, properties),
            new Item.Properties().useBlockDescriptionPrefix());
    /** Clay with grit worked into it, fired into brick that stands a forge's heat. */
    public static final Item FIRECLAY = register("fireclay", Item::new, new Item.Properties());
    public static final Item REFRACTORY_BRICK = register("refractory_brick", Item::new, new Item.Properties());
    public static final Item SMITHING_GLOVES = register("smithing_gloves", Item::new, new Item.Properties().stacksTo(1));
    public static final Item BRONZE_INGOT = register("bronze_ingot", Item::new, new Item.Properties());
    // Section 71: fire by friction. The sticks wear out, which is why a player wants a flint and
    // steel eventually rather than because the sticks stop working.
    public static final Item LIGHTING_STICKS = register("lighting_sticks",
            de.ipnats.hardwrought.progression.LightingSticksItem::new,
            new Item.Properties().durability(16));
    public static final Item BRICK_FURNACE = register("brick_furnace",
            properties -> new BlockItem(ModBlocks.BRICK_FURNACE, properties),
            new Item.Properties().useBlockDescriptionPrefix());
    public static final Item FILLED_WATERSKIN = register("filled_waterskin", WaterskinItem::new,
            new Item.Properties().durability(9).stacksTo(1));
    // Milestone 2 weapon classes that vanilla has no item for. Values follow the class profiles in
    // data/hardwrought/hardwrought/weapon_profiles; vanilla still supplies the base attack damage.
    public static final Item FLINT_DAGGER = register("flint_dagger", Item::new,
            new Item.Properties().sword(ModToolMaterials.FLINT, 0.5F, -1.4F));
    public static final Item FLINT_HATCHET = register("flint_hatchet", Item::new,
            new Item.Properties().axe(ModToolMaterials.FLINT, 5.0F, -3.1F));
    public static final Item FLINT_HOE = register("flint_hoe", Item::new,
            new Item.Properties().hoe(ModToolMaterials.FLINT, -1.0F, -1.8F));
    public static final Item FLINT_PICKAXE = register("flint_pickaxe", Item::new,
            new Item.Properties().pickaxe(ModToolMaterials.FLINT, 1.0F, -2.8F));
    public static final Item FLINT_SHOVEL = register("flint_shovel", Item::new,
            new Item.Properties().shovel(ModToolMaterials.FLINT, 1.5F, -3.0F));
    public static final Item FLINT_SWORD = register("flint_sword", Item::new,
            new Item.Properties().sword(ModToolMaterials.FLINT, 3.0F, -2.4F));
    public static final Item STONE_HATCHET = register("stone_hatchet", Item::new,
            new Item.Properties().axe(ToolMaterial.STONE, 6.0F, -3.2F));
    public static final Item STONE_PICKAXE = register("stone_pickaxe", Item::new,
            new Item.Properties().pickaxe(ToolMaterial.STONE, 1.0F, -2.8F));
    public static final Item IRON_HATCHET = register("iron_hatchet", Item::new,
            new Item.Properties().axe(ToolMaterial.IRON, 6.0F, -3.1F));
    public static final Item IRON_PICKAXE = register("iron_pickaxe", Item::new,
            new Item.Properties().pickaxe(ToolMaterial.IRON, 1.0F, -2.8F));
    public static final Item IRON_DAGGER = register("iron_dagger", Item::new,
            new Item.Properties().sword(ToolMaterial.IRON, 1.0F, -1.5F));
    public static final Item IRON_GREATSWORD = register("iron_greatsword", Item::new,
            new Item.Properties().sword(ToolMaterial.IRON, 6.0F, -3.2F));
    public static final Item IRON_HALBERD = register("iron_halberd", Item::new,
            new Item.Properties().sword(ToolMaterial.IRON, 5.0F, -3.1F));
    public static final Item BRONZE_HATCHET = register("bronze_hatchet", Item::new,
            new Item.Properties().axe(ModToolMaterials.BRONZE, 5.5F, -3.1F));
    public static final Item BRONZE_PICKAXE = register("bronze_pickaxe", Item::new,
            new Item.Properties().pickaxe(ModToolMaterials.BRONZE, 1.0F, -2.8F));

    /** Milestone 3, section 18.3: the primitive instrument that makes bad air readable. */
    public static final Item SAFETY_LAMP = register("safety_lamp", SafetyLampItem::new,
            new Item.Properties().stacksTo(1));

    // Milestone-72 groundwork. The bar that turns has no item at all: it is only ever drawn.
    public static final Item SHAFT = register("shaft",
            properties -> new BlockItem(ModBlocks.SHAFT, properties),
            new Item.Properties().useBlockDescriptionPrefix());
    public static final Item CRANK_BOX = register("crank_box",
            properties -> new BlockItem(ModBlocks.CRANK_BOX, properties),
            new Item.Properties().useBlockDescriptionPrefix());
    public static final Item HAND_CRANK = register("hand_crank",
            properties -> new BlockItem(ModBlocks.HAND_CRANK, properties),
            new Item.Properties().useBlockDescriptionPrefix());
    public static final Item STARTER_CRUSHER = register("starter_crusher",
            properties -> new BlockItem(ModBlocks.STARTER_CRUSHER, properties),
            new Item.Properties().useBlockDescriptionPrefix());
    public static final Item COGWHEEL = register("cogwheel",
            properties -> new BlockItem(ModBlocks.COGWHEEL, properties),
            new Item.Properties().useBlockDescriptionPrefix());
    public static final Item LARGE_COGWHEEL = register("large_cogwheel",
            properties -> new BlockItem(ModBlocks.LARGE_COGWHEEL, properties),
            new Item.Properties().useBlockDescriptionPrefix());
    public static final Item GEARBOX = register("gearbox",
            properties -> new BlockItem(ModBlocks.GEARBOX, properties),
            new Item.Properties().useBlockDescriptionPrefix());
    public static final Item WATER_WHEEL = register("water_wheel",
            properties -> new BlockItem(ModBlocks.WATER_WHEEL, properties),
            new Item.Properties().useBlockDescriptionPrefix());
    public static final Item WINDMILL = register("windmill",
            properties -> new BlockItem(ModBlocks.WINDMILL, properties),
            new Item.Properties().useBlockDescriptionPrefix());
    /** Section 73: laid round two parallel shafts, it makes them turn together. */
    public static final Item BELT = register("belt", de.ipnats.hardwrought.machinery.BeltItem::new,
            new Item.Properties().stacksTo(16));

    private ModItems() {
    }

    private static <T extends Item> T register(String name, Function<Item.Properties, T> factory,
                                              Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        T item = factory.apply(properties.setId(key));
        if (item instanceof BlockItem blockItem) blockItem.registerBlocks(Item.BY_BLOCK, item);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void initialize() {
        // Calling this method loads the class and registers its static item fields.
        if (Hardwrought.config().debugLogging()) {
            Hardwrought.LOGGER.info("Registered starter item: {}", BuiltInRegistries.ITEM.getKey(FLINT_SHARD));
        }
    }
}
