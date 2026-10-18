package de.ipnats.hardwrought.nether;

import de.ipnats.hardwrought.Hardwrought;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The ashcork: the tree of the Ash Grove, the Nether's one cool corner (see docs/endgame.md).
 *
 * <p>Its logs are what a start over in the Nether carves its first workbench out of — with an iron axe,
 * like any log — and its wood does not catch fire: it grew in the Nether. Its <b>bark</b> comes off under an axe, and is what keeps the heat
 * out: armour of it shields its wearer from the hot air ({@link HeatShield}), the Nether's own answer
 * to a cooling suit. It holds a little warmth in the cold, too, as cork does.
 *
 * <p>Its sapling grows on netherrack and soul soil, in the dark: it lives on the heat, not on light.
 */
public final class Ashcork {
    public static final ResourceKey<Feature> TREE = ResourceKey.create(Registries.FEATURE, Hardwrought.id("ashcork_tree"));
    public static final TreeGrower GROWER = new TreeGrower(Hardwrought.MOD_ID + ":ashcork",
            WeightedList.of(TREE), WeightedList.of(), WeightedList.of(), TREE);

    public static final Block LOG = register("ashcork_log", RotatedPillarBlock::new, wood(MapColor.COLOR_LIGHT_GRAY));
    public static final Block STRIPPED_LOG = register("stripped_ashcork_log", RotatedPillarBlock::new, wood(MapColor.TERRACOTTA_ORANGE));
    public static final Block PLANKS = register("ashcork_planks", Block::new, wood(MapColor.TERRACOTTA_ORANGE).strength(2.0f, 3.0f));
    public static final Block LEAVES = register("ashcork_leaves",
            p -> new UntintedParticleLeavesBlock(0.02f, ParticleTypes.WHITE_ASH, AmbientLeavesBlockSoundPlayer.noAmbientSound(), p),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.2f).randomTicks().sound(SoundType.AZALEA_LEAVES)
                    .noOcclusion().isValidSpawn(Blocks::ocelotOrParrot).isSuffocating(Blocks::never)
                    .pushReaction(PushReaction.POPPED).isRedstoneConductor(Blocks::never));
    public static final Block SAPLING = register("ashcork_sapling", p -> new Sapling(GROWER, p),
            BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak()
                    .sound(SoundType.GRASS).pushReaction(PushReaction.POPPED));

    public static final Item BARK = item("ashcork_bark", Item::new, new Item.Properties());

    /** Bark is light and does not turn a blade; it is the heat it is for. */
    public static final TagKey<Item> REPAIRS_ARMOR = TagKey.create(Registries.ITEM, Hardwrought.id("ashcork_armor_materials"));
    public static final ArmorMaterial ARMOR_MATERIAL = armorMaterial();
    private static final Map<ArmorType, Item> ARMOR = new EnumMap<>(ArmorType.class);

    /** How much bark one log gives up. */
    static final int BARK_PER_LOG = 2;

    private Ashcork() { }

    public static void initialize() {
        item("ashcork_log", p -> new BlockItem(LOG, p), new Item.Properties().useBlockDescriptionPrefix());
        item("stripped_ashcork_log", p -> new BlockItem(STRIPPED_LOG, p), new Item.Properties().useBlockDescriptionPrefix());
        item("ashcork_planks", p -> new BlockItem(PLANKS, p), new Item.Properties().useBlockDescriptionPrefix());
        item("ashcork_leaves", p -> new BlockItem(LEAVES, p), new Item.Properties().useBlockDescriptionPrefix());
        item("ashcork_sapling", p -> new BlockItem(SAPLING, p), new Item.Properties().useBlockDescriptionPrefix());
        for (ArmorType type : List.of(ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS)) {
            ARMOR.put(type, item("ashcork_" + type.getName(), Item::new, new Item.Properties().humanoidArmor(ARMOR_MATERIAL, type)));
        }
        // An axe strips the log, and the bark comes off with it. Vanilla's stripping is a data-driven
        // rule list for its own woods; this one wood is stripped here.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            ItemStack held = player.getItemInHand(hand);
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (!state.is(LOG) || !held.is(ItemTags.AXES) || player.isSpectator() || player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel server) {
                server.setBlock(pos, STRIPPED_LOG.defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS)), Block.UPDATE_ALL_IMMEDIATE);
                var at = hit.getLocation();
                server.addFreshEntity(new ItemEntity(server, at.x, at.y, at.z, new ItemStack(BARK, BARK_PER_LOG)));
                server.playSound(null, pos, SoundEvents.AXE_STRIP.value(), SoundSource.BLOCKS, 1.0f, 0.8f);
                server.sendParticles(ParticleTypes.WHITE_ASH, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.01);
                held.hurtAndBreak(1, player, hand.asEquipmentSlot());
            }
            return InteractionResult.SUCCESS;
        });
    }

    /** What the axe does to an ashcork log, for the book: the stripped log, and the bark. */
    public static List<de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe> worldRecipes() {
        var inputs = de.ipnats.hardwrought.knowledge.WorldRecipes.one(new ItemStack(LOG), new ItemStack(net.minecraft.world.item.Items.STONE_AXE));
        return List.of(
                new de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe(de.ipnats.hardwrought.Hardwrought.id("stripping/stripped_ashcork_log"),
                        inputs, new ItemStack(STRIPPED_LOG), ItemStack.EMPTY),
                new de.ipnats.hardwrought.knowledge.WorldRecipes.WorldRecipe(de.ipnats.hardwrought.Hardwrought.id("stripping/ashcork_bark"),
                        inputs, new ItemStack(BARK, BARK_PER_LOG), ItemStack.EMPTY));
    }

    /** Everything of the Nether start over — ores, ground, ashcork — for the creative tab. */
    public static List<Item> items() {
        List<Item> items = new java.util.ArrayList<>(PortalStabilizers.items());
        items.add(de.ipnats.hardwrought.wither.WitherPowers.ESSENCE);
        items.addAll(de.ipnats.hardwrought.underworld.UnderworldGate.items());
        items.add(de.ipnats.hardwrought.underworld.UnderworldRuins.STALE_BREAD);
        items.addAll(de.ipnats.hardwrought.underworld.GraveUndead.items());
        items.addAll(de.ipnats.hardwrought.witchking.WitchKingContent.items());
        items.add(de.ipnats.hardwrought.end.dragon.DragonHeart.HEART);
        items.addAll(de.ipnats.hardwrought.ritual.RitualContent.items());
        items.add(de.ipnats.hardwrought.underworld.Underworld.PALE_SHARD);
        items.add(de.ipnats.hardwrought.underworld.Underworld.PALE_SHARD_CLUSTER.asItem());
        items.addAll(NetherOres.items());
        items.addAll(List.of(CastObsidian.ITEM, AshGrove.NETHER_GRASS.asItem(), LOG.asItem(), STRIPPED_LOG.asItem(), PLANKS.asItem(), LEAVES.asItem(), SAPLING.asItem(), BARK,
                ARMOR.get(ArmorType.HELMET), ARMOR.get(ArmorType.CHESTPLATE), ARMOR.get(ArmorType.LEGGINGS),
                ARMOR.get(ArmorType.BOOTS)));
        return items;
    }

    public static Item armor(ArmorType type) {
        return ARMOR.get(type);
    }

    private static BlockBehaviour.Properties wood(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASS).strength(2.0f).sound(SoundType.WOOD);
    }

    private static ArmorMaterial armorMaterial() {
        Map<ArmorType, Integer> defence = new EnumMap<>(ArmorType.class);
        defence.put(ArmorType.HELMET, 1);
        defence.put(ArmorType.CHESTPLATE, 3);
        defence.put(ArmorType.LEGGINGS, 2);
        defence.put(ArmorType.BOOTS, 1);
        defence.put(ArmorType.BODY, 3);
        @SuppressWarnings("unchecked")
        ResourceKey<EquipmentAsset> asset = ResourceKey.create(
                (ResourceKey<? extends Registry<EquipmentAsset>>) EquipmentAssets.ROOT_ID, Hardwrought.id("ashcork"));
        return new ArmorMaterial(7, defence, 12, SoundEvents.ARMOR_EQUIP_LEATHER, 0, 0, REPAIRS_ARMOR, asset);
    }

    private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory,
                                                BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
    }

    private static <T extends Item> T item(String name, Function<Item.Properties, T> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Hardwrought.id(name));
        T item = factory.apply(properties.setId(key));
        if (item instanceof BlockItem blockItem) blockItem.registerBlocks(Item.BY_BLOCK, item);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    /** Grows on Nether grass, netherrack and soul soil as well as on earth, and in the dark: it lives on heat, not light. */
    public static final class Sapling extends SaplingBlock {
        public Sapling(TreeGrower grower, BlockBehaviour.Properties properties) {
            super(grower, properties);
        }

        @Override
        protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
            return state.is(AshGrove.NETHER_GRASS) || state.is(Blocks.NETHERRACK) || state.is(Blocks.SOUL_SOIL)
                    || super.mayPlaceOn(state, level, pos);
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            boolean warm = level.dimension() == Level.NETHER || level.getMaxLocalRawBrightness(pos.above()) >= 9;
            if (warm && random.nextInt(7) == 0) advanceTree(level, pos, state, random);
        }
    }
}
