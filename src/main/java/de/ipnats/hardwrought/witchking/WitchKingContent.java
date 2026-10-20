package de.ipnats.hardwrought.witchking;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.underworld.Underworld;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** What the Witch King's fight is made of, registered: him, his blades and souls, his throne and crystals, his mantle; and the throne's seat. */
public final class WitchKingContent {
    public static final EntityType<WitchKing> WITCH_KING = entity("witch_king",
            EntityType.Builder.of(WitchKing::new, MobCategory.MONSTER).sized(0.7f, 2.0f).eyeHeight(1.75f).fireImmune()
                    .clientTrackingRange(16).notInPeaceful());
    public static final EntityType<HexBlade> HEX_BLADE = entity("hex_blade",
            EntityType.Builder.<HexBlade>of(HexBlade::new, MobCategory.MISC).sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(1).noSave());
    public static final EntityType<SoulOrb> SOUL_ORB = entity("soul_orb",
            EntityType.Builder.<SoulOrb>of(SoulOrb::new, MobCategory.MISC).sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(2).noSave());
    /** What one sits on, on the throne or its seat. */
    public static final EntityType<SeatMount> SEAT_MOUNT = entity("seat_mount",
            EntityType.Builder.<SeatMount>of(SeatMount::new, MobCategory.MISC).sized(0.25f, 0.25f).noSummon().clientTrackingRange(10).updateInterval(20));

    public static final Block THRONE = block("witch_king_throne", WitchKingThrone::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0f, 3600000.0f).noLootTable()
                    .sound(SoundType.POLISHED_DEEPSLATE).noOcclusion().lightLevel(state -> 4));
    public static final Block CRYSTAL = block("witch_king_crystal", WitchKingCrystal::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(1.2f, 3600000.0f).noLootTable()
                    .sound(SoundType.AMETHYST_CLUSTER).noOcclusion().lightLevel(state -> 13).pushReaction(PushReaction.IMMOVEABLE));
    public static final BlockEntityType<WitchKingThrone.Entity> THRONE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hardwrought.id("witch_king_throne"), new BlockEntityType<>(WitchKingThrone.Entity::new, Set.of(THRONE)));
    /** The throne's counterpart in the overworld: unbreakable, picked up by sneaking. */
    public static final Block SEAT = block("witch_king_seat", WitchKingSeat::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0f, 3600000.0f).noLootTable()
                    .sound(SoundType.POLISHED_DEEPSLATE).noOcclusion().lightLevel(state -> 6).pushReaction(PushReaction.IMMOVEABLE));
    public static final BlockEntityType<WitchKingSeat.Entity> SEAT_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hardwrought.id("witch_king_seat"), new BlockEntityType<>(WitchKingSeat.Entity::new, Set.of(SEAT)));
    /** The throne a seat picked up is bound to. */
    public static final DataComponentType<BlockPos> THRONE_LINK = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            Hardwrought.id("throne_link"), DataComponentType.<BlockPos>builder()
                    .persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC).build());

    public static final ResourceKey<EquipmentAsset> MANTLE_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Hardwrought.id("witch_king_mantle"));
    /** The Witch King's mantle: the magic way to fly. */
    public static final Item MANTLE = item("witch_king_mantle", WitchKingMantle::new, new Item.Properties().durability(640)
            .rarity(Rarity.EPIC).enchantable(15).component(DataComponents.GLIDER, Unit.INSTANCE).repairable(Underworld.PALE_SHARD)
            .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).setEquipSound(SoundEvents.ARMOR_EQUIP_ELYTRA)
                    .setAsset(MANTLE_ASSET).setDamageOnHurt(false).build()));
    /** Found in the ruins of the Underworld; points to the nearest Witch King still living. */
    public static final Item SOUL_COMPASS = item("soul_compass", SoulCompass::new, new Item.Properties().stacksTo(1)
            .rarity(Rarity.RARE).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
    public static final Item THRONE_ITEM = blockItem(THRONE, new Item.Properties());
    public static final Item SEAT_ITEM = blockItem(SEAT, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());

    private WitchKingContent() { }

    public static void initialize() {
        de.ipnats.hardwrought.knowledge.LootSources.inWorld(() -> THRONE.asItem());
        de.ipnats.hardwrought.knowledge.LootSources.inWorld(() -> SEAT_ITEM);
        FabricDefaultAttributeRegistry.register(WITCH_KING, WitchKing.createAttributes());
        WitchKingMantle.initialize();
        Citadel.initialize();
    }

    public static List<Item> items() {
        return List.of(MANTLE, SOUL_COMPASS, THRONE_ITEM, SEAT_ITEM);
    }

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
    }

    private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    private static Item blockItem(Block block, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
        BlockItem item = new BlockItem(block, properties.setId(key).useBlockDescriptionPrefix());
        item.registerBlocks(Item.BY_BLOCK, item);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }
}
