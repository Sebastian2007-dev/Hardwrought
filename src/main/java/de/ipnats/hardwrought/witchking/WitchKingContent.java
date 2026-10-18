package de.ipnats.hardwrought.witchking;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.underworld.Underworld;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
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

/** What the Witch King's fight is made of, registered: him, his blades and souls, his throne and crystals, his mantle. */
public final class WitchKingContent {
    public static final EntityType<WitchKing> WITCH_KING = entity("witch_king",
            EntityType.Builder.of(WitchKing::new, MobCategory.MONSTER).sized(0.7f, 2.0f).eyeHeight(1.75f).fireImmune()
                    .clientTrackingRange(16).notInPeaceful());
    public static final EntityType<HexBlade> HEX_BLADE = entity("hex_blade",
            EntityType.Builder.<HexBlade>of(HexBlade::new, MobCategory.MISC).sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(1).noSave());
    public static final EntityType<SoulOrb> SOUL_ORB = entity("soul_orb",
            EntityType.Builder.<SoulOrb>of(SoulOrb::new, MobCategory.MISC).sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(2).noSave());

    public static final Block THRONE = block("witch_king_throne", WitchKingThrone::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0f, 3600000.0f).noLootTable()
                    .sound(SoundType.POLISHED_DEEPSLATE).noOcclusion().lightLevel(state -> 4));
    public static final Block CRYSTAL = block("witch_king_crystal", WitchKingCrystal::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(1.2f, 3600000.0f).noLootTable()
                    .sound(SoundType.AMETHYST_CLUSTER).noOcclusion().lightLevel(state -> 13).pushReaction(PushReaction.IMMOVEABLE));
    public static final BlockEntityType<WitchKingThrone.Entity> THRONE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Hardwrought.id("witch_king_throne"), new BlockEntityType<>(WitchKingThrone.Entity::new, Set.of(THRONE)));

    public static final ResourceKey<EquipmentAsset> MANTLE_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Hardwrought.id("witch_king_mantle"));
    /** The Witch King's mantle: the magic way to fly. */
    public static final Item MANTLE = item("witch_king_mantle", WitchKingMantle::new, new Item.Properties().durability(640)
            .rarity(Rarity.EPIC).enchantable(15).component(DataComponents.GLIDER, Unit.INSTANCE).repairable(Underworld.PALE_SHARD)
            .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).setEquipSound(SoundEvents.ARMOR_EQUIP_ELYTRA)
                    .setAsset(MANTLE_ASSET).setDamageOnHurt(false).build()));
    public static final Item THRONE_ITEM = blockItem(THRONE);

    private WitchKingContent() { }

    public static void initialize() {
        de.ipnats.hardwrought.knowledge.LootSources.inWorld(() -> THRONE.asItem());
        FabricDefaultAttributeRegistry.register(WITCH_KING, WitchKing.createAttributes());
        WitchKingMantle.initialize();
        Citadel.initialize();
    }

    public static List<Item> items() {
        return List.of(MANTLE, THRONE_ITEM);
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

    private static Item blockItem(Block block) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
        BlockItem item = new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix());
        item.registerBlocks(Item.BY_BLOCK, item);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }
}
