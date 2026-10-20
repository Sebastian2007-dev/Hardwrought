package de.ipnats.hardwrought.magic;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.underworld.Underworld;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Magic in the world and in a fight (docs/magic-roadmap.md, stage M6): what the place does to an
 * element, what worn and held gear does to the hand, the mobs that unsettle magic, and the rune tomes
 * and deep ruins that teach runes.
 */
public final class MagicWorld {
    /** Above this height the air is thin and wind magic carries further. */
    static final int HIGH = 150;
    /** Stability lost to a piece of metal armour; a chestplate costs twice that. */
    static final double METAL_PIECE = 0.02;
    static final double SHIELD = 0.04;
    static final double TWO_HANDED = 0.03;
    /** Stability lost to a cast within {@link #EVOKER_REACH} of an evoker. */
    static final double EVOKER = 0.1;
    static final double EVOKER_REACH = 12;
    /** How close an endermite must come to a sigil or a standing shield to unsettle it. */
    static final double ENDERMITE_REACH = 3;
    /** How far an open far gate draws endermites from. */
    static final double GATE_LURE = 24;

    /** Weapons held in both hands; they leave none free for the wand's steadying. */
    public static final TagKey<Item> TWO_HANDED_WEAPONS = TagKey.create(Registries.ITEM, Hardwrought.id("two_handed"));
    /** What armour is made of metal: by name, so every metal of the smithy counts without a list. */
    private static final String[] METALS = {"iron", "chainmail", "gold", "copper", "bronze", "steel", "netherite",
            "mithril", "titanium", "tungsten", "adamant"};

    public static final RuneTomeItem RUNE_TOME = Registry.register(BuiltInRegistries.ITEM,
            ResourceKey.create(Registries.ITEM, Hardwrought.id("rune_tome")),
            new RuneTomeItem(new Item.Properties().stacksTo(1).setId(ResourceKey.create(Registries.ITEM, Hardwrought.id("rune_tome")))));

    /** The vanilla chests a rune tome may lie in, and how likely; the Underworld's ruins name it in their own table. */
    private static final Set<ResourceKey<LootTable>> TOME_CHESTS = Set.of(BuiltInLootTables.SIMPLE_DUNGEON,
            BuiltInLootTables.STRONGHOLD_LIBRARY, BuiltInLootTables.ANCIENT_CITY);
    static final double TOME_CHANCE = 0.25;

    /** Rune ruins underground: in the Nether, in the Underworld and the deep dark. */
    public static final ResourceKey<PlacedFeature> DEEP_RUIN = ResourceKey.create(Registries.PLACED_FEATURE,
            Hardwrought.id("rune_ruin_deep"));
    static final com.mojang.serialization.MapCodec<DeepRuin> DEEP_RUIN_TYPE = Registry.register(BuiltInRegistries.FEATURE_TYPE,
            Hardwrought.id("rune_ruin_deep"), com.mojang.serialization.MapCodec.unit(DeepRuin.INSTANCE));

    private MagicWorld() { }

    static void initialize() {
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            if (table.unwrapKey().filter(TOME_CHESTS::contains).isEmpty()) return;
            if (context.getRandom().nextDouble() < TOME_CHANCE) drops.add(new ItemStack(RUNE_TOME));
        });
        de.ipnats.hardwrought.knowledge.LootSources.register(() -> RUNE_TOME, de.ipnats.hardwrought.knowledge.LootSources.KIND_CONTAINER,
                () -> BuiltInLootTables.STRONGHOLD_LIBRARY.identifier());
        BiomeModifications.create(Hardwrought.id("rune_ruins_deep")).add(ModificationPhase.ADDITIONS,
                BiomeSelectors.foundInTheNether().or(BiomeSelectors.tag(TagKey.create(Registries.BIOME, Hardwrought.id("is_underworld")))),
                context -> context.getGenerationSettings().addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, DEEP_RUIN));
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            if (level.getGameTime() % 20 == 0) endermites(level);
        });
    }

    /**
     * What the place does to an element (magic specification §22): rain, water, the dimension, the
     * open sky by day or night, the rock overhead, and the heights.
     */
    public static double surroundings(ServerLevel level, Vec3 at, Rune core) {
        BlockPos pos = BlockPos.containing(at);
        double f = 1;
        boolean wet = level.getFluidState(pos).is(FluidTags.WATER);
        // The heightmap, not the sky light: it knows of a roof the moment it is laid.
        boolean sky = pos.getY() >= level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        if (wet) {
            if (core == Rune.FIRE) f *= 0.3;
            if (core == Rune.WATER) f *= 1.4;
        } else if (level.isRainingAt(pos)) {
            if (core == Rune.FIRE) f *= 0.6;
            if (core == Rune.WATER || core == Rune.LIGHTNING) f *= 1.3;
        }
        if (level.dimension() == Level.NETHER) {
            if (core == Rune.WATER) f *= 0.5;
            if (core == Rune.FIRE) f *= 1.2;
        } else if (level.dimension() == Underworld.LEVEL) {
            if (core == Rune.LIGHT) f *= 0.7;
            if (core == Rune.DARK) f *= 1.3;
        } else if (level.dimension() == Level.END) {
            if (core == Rune.DARK) f *= 1.2;
        } else if (sky && level.dimensionType().hasSkyLight()) {
            boolean day = level.isBrightOutside();
            if (core == Rune.LIGHT) f *= day ? 1.2 : 0.8;
            if (core == Rune.DARK) f *= day ? 0.8 : 1.2;
        }
        if (!sky && level.dimension() != Level.NETHER) {
            if (core == Rune.EARTH) f *= 1.2;
            if (core == Rune.WIND) f *= 0.8;
        }
        if (pos.getY() > HIGH && core == Rune.WIND) f *= 1.3;
        return f;
    }

    /**
     * What gear disturbs the hand (full specification §29): metal armour, a shield in the off hand, a
     * weapon held in both. Cloth, leather and the Witch King's mantle cost nothing.
     */
    public static double interference(LivingEntity caster) {
        double cost = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack worn = caster.getItemBySlot(slot);
            if (metal(worn)) cost += slot == EquipmentSlot.CHEST ? 2 * METAL_PIECE : METAL_PIECE;
        }
        if (caster.getOffhandItem().is(Items.SHIELD)) cost += SHIELD;
        for (ItemStack held : new ItemStack[]{caster.getMainHandItem(), caster.getOffhandItem()}) {
            if (held.is(TWO_HANDED_WEAPONS)) {
                cost += TWO_HANDED;
                break;
            }
        }
        return cost;
    }

    static boolean metal(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.contains("witch_king") || path.contains("leather") || path.contains("cloth")) return false;
        for (String metal : METALS) if (path.contains(metal)) return true;
        return false;
    }

    /** Evokers unsettle magic cast near them (mob specification §34). */
    public static double evokers(ServerLevel level, Vec3 at) {
        AABB box = new AABB(at, at).inflate(EVOKER_REACH);
        return level.getEntitiesOfClass(Evoker.class, box, e -> e.isAlive() && e.position().distanceTo(at) <= EVOKER_REACH).isEmpty()
                ? 0 : EVOKER;
    }

    /**
     * Endermites (mob specification §19): drawn to open far gates, and unsettling what sigils and
     * standing shields they come close to.
     */
    private static void endermites(ServerLevel level) {
        for (Vec3 gate : Portals.openGates(level)) {
            for (Endermite mite : level.getEntitiesOfClass(Endermite.class, new AABB(gate, gate).inflate(GATE_LURE), Endermite::isAlive)) {
                if (mite.position().distanceTo(gate) > 2) mite.getNavigation().moveTo(gate.x, gate.y, gate.z, 1.0);
            }
        }
        for (Endermite mite : level.getEntities(net.minecraft.world.entity.EntityTypes.ENDERMITE, Endermite::isAlive)) {
            BlockPos center = mite.blockPosition();
            int r = (int) Math.ceil(ENDERMITE_REACH);
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
                if (pos.distToCenterSqr(mite.position()) > ENDERMITE_REACH * ENDERMITE_REACH) continue;
                if (level.getBlockEntity(pos) instanceof SigilBlockEntity sigil) sigil.unsettle();
            }
            MagicShields.unsettle(level, mite.position(), ENDERMITE_REACH);
        }
    }

    /** A rune ruin in a cave or under the Nether's roof: it looks down from where it is placed for a floor. */
    record DeepRuin() implements Feature {
        static final DeepRuin INSTANCE = new DeepRuin();

        @Override
        public com.mojang.serialization.MapCodec<DeepRuin> codec() {
            return DEEP_RUIN_TYPE;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            BlockPos.MutableBlockPos at = origin.mutable();
            for (int i = 0; i < 32 && at.getY() > level.getMinY() + 1; i++, at.move(0, -1, 0)) {
                if (!level.getBlockState(at.below()).isSolidRender()) continue;
                boolean room = true;
                for (int y = 0; y < 4 && room; y++) room = level.getBlockState(at.above(y)).isAir();
                if (!room) continue;
                Glyph glyph = Glyph.values()[random.nextInt(Glyph.values().length)];
                return RuneRuins.build(level, at.immutable(), random, glyph);
            }
            return false;
        }
    }
}
