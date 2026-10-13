package de.ipnats.hardwrought.refining;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.oil.CrudeOilBlock;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.EmptyItemFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.FullItemFluidStorage;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the liquids of refining ({@link RefineryProduct}): for each its still and flowing fluid,
 * the block it stands in the world as, and its bucket. They all burn, as crude oil does. The still's
 * bottles hold the same liquids, a third of a bucket each, so they pour into a tank and fill from one.
 */
public final class RefineryFluids {
    static {
        for (RefineryProduct product : RefineryProduct.values()) {
            String name = product.getSerializedName();
            product.source = Registry.register(BuiltInRegistries.FLUID, Hardwrought.id(name), new ProductFluid.Source(product));
            product.flowing = Registry.register(BuiltInRegistries.FLUID, Hardwrought.id("flowing_" + name), new ProductFluid.Flowing(product));
            ResourceKey<net.minecraft.world.level.block.Block> blockKey = ResourceKey.create(Registries.BLOCK, Hardwrought.id(name));
            product.block = Registry.register(BuiltInRegistries.BLOCK, blockKey, new CrudeOilBlock(product.source,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).mapColor(MapColor.COLOR_BROWN).ignitedByLava().setId(blockKey)));
            ResourceKey<Item> bucketKey = ResourceKey.create(Registries.ITEM, Hardwrought.id(name + "_bucket"));
            product.bucket = Registry.register(BuiltInRegistries.ITEM, bucketKey, new BucketItem(product.source,
                    new Item.Properties().stacksTo(1).craftRemainder(Items.BUCKET).setId(bucketKey)
                            .cookingFuel(ResourceKey.create(Registries.CONTEXT_INT_PROVIDER, Hardwrought.id("cooking/time_" + name + "_bucket")))));
        }
    }

    private RefineryFluids() { }

    public static void initialize() {
        for (RefineryProduct product : RefineryProduct.values()) {
            FlammableBlockRegistry.getDefaultInstance().add(product.block(), CrudeOilBlock.CATCH_CHANCE, CrudeOilBlock.BURN_CHANCE);
        }
        bottle(ModItems.LIGHT_FRACTION, RefineryProduct.LIGHT_FRACTION);
        bottle(ModItems.FUEL_FRACTION, RefineryProduct.FUEL_FRACTION);
        bottle(ModItems.HEAVY_OIL, RefineryProduct.HEAVY_OIL);
    }

    /**
     * A bottle of the still is a third of a bucket of its liquid, as a water bottle is: a tank or a pipe
     * empties it into the liquid, and fills an empty glass bottle with it.
     */
    private static void bottle(Item full, RefineryProduct product) {
        FluidStorage.combinedItemApiProvider(full).register(context ->
                new FullItemFluidStorage(context, Items.GLASS_BOTTLE, FluidVariant.of(product.source()), FluidConstants.BOTTLE));
        FluidStorage.combinedItemApiProvider(Items.GLASS_BOTTLE).register(context ->
                new EmptyItemFluidStorage(context, full, product.source(), FluidConstants.BOTTLE));
    }

    /** The bottle the still fills with this liquid; null for a liquid it does not bottle. */
    public static Item bottleOf(RefineryProduct product) {
        return switch (product) {
            case LIGHT_FRACTION -> ModItems.LIGHT_FRACTION;
            case FUEL_FRACTION -> ModItems.FUEL_FRACTION;
            case HEAVY_OIL -> ModItems.HEAVY_OIL;
            default -> null;
        };
    }

    /** The buckets, for the creative tab. */
    public static List<Item> items() {
        List<Item> items = new ArrayList<>();
        for (RefineryProduct product : RefineryProduct.values()) items.add(product.bucket());
        return items;
    }
}
