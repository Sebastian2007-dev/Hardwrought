package de.ipnats.hardwrought.chemistry;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.knowledge.WorldRecipes;
import de.ipnats.hardwrought.water.WaterQuality;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;

import java.util.List;
import java.util.Set;

/**
 * Salt the old way: seawater let stand in a shallow iron pan until it is gone and the salt is left.
 *
 * <p>The sun does it, slowly, under an open sky on a dry day; a fire under the pan does it four times
 * as fast. Seawater goes in by the bucket or, pumped out of the sea, by pipe; the salt is taken out by
 * hand or by a hopper under the pan. See {@link SaltPanBlock}.
 */
public final class SaltPan {
    public static final SaltPanBlock BLOCK;
    public static final BlockEntityType<SaltPanBlock.Entity> ENTITY;

    static {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Hardwrought.id("salt_pan"));
        BLOCK = Registry.register(BuiltInRegistries.BLOCK, key, new SaltPanBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL).strength(2.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.METAL)
                .noOcclusion().setId(key)));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Hardwrought.id("salt_pan"));
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                new BlockItem(BLOCK, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Hardwrought.id("salt_pan"),
                new BlockEntityType<>(SaltPanBlock.Entity::new, Set.of(BLOCK)));
    }

    private SaltPan() { }

    /** Touching the class registers the pan; then pipes may fill it and hoppers under it empty it. */
    public static void initialize() {
        FluidStorage.SIDED.registerForBlockEntity((pan, side) -> pan.brine, ENTITY);
        ItemStorage.SIDED.registerForBlockEntity((pan, side) -> side == Direction.DOWN ? pan.salt : null, ENTITY);
    }

    public static List<Item> items() {
        return List.of(BLOCK.asItem());
    }

    /** Water of this kind, as pipes and tanks carry it; plain water for fresh. */
    public static FluidVariant water(WaterQuality quality) {
        if (quality == null || quality == WaterQuality.FRESH) return FluidVariant.of(Fluids.WATER);
        return FluidVariant.of(Fluids.WATER, DataComponentPatch.builder().set(ModDataComponents.WATER_QUALITY, quality).build());
    }

    /** What kind of water a variant is; fresh for water that says nothing, null for anything not water. */
    public static WaterQuality quality(FluidVariant variant) {
        if (!variant.isOf(Fluids.WATER)) return null;
        WaterQuality quality = variant.get(ModDataComponents.WATER_QUALITY);
        return quality == null ? WaterQuality.FRESH : quality;
    }

    /** A full bucket of seawater, as the book shows it. */
    public static ItemStack seawaterBucket() {
        ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
        bucket.set(ModDataComponents.WATER_QUALITY, WaterQuality.SALT);
        return bucket;
    }

    /** For the book: a bucket of seawater in the pan leaves a salt behind. */
    public static List<WorldRecipes.WorldRecipe> worldRecipes() {
        return List.of(new WorldRecipes.WorldRecipe(Hardwrought.id("salt_pan/salt"),
                WorldRecipes.one(seawaterBucket()), new ItemStack(ModItems.SALT, SaltPanBlock.Entity.SALT_PER_BUCKET),
                new ItemStack(BLOCK)));
    }
}
