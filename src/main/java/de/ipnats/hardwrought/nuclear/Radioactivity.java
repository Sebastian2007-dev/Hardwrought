package de.ipnats.hardwrought.nuclear;

import de.ipnats.hardwrought.metallurgy.Metal;
import de.ipnats.hardwrought.metallurgy.MetalBlocks;
import de.ipnats.hardwrought.metallurgy.ModMetals;
import de.ipnats.hardwrought.metallurgy.OrePowders;
import de.ipnats.hardwrought.multiblock.BigMachines;
import de.ipnats.hardwrought.smithing.MetalStock;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * How radioactive things are, and how well blocks stop it.
 *
 * <p>Dose rates are in sieverts an hour, as a dosimeter reads them. An item's is what it gives the one
 * who carries it; a block's is what it gives at a metre. The numbers follow the real ones in their
 * proportions — natural uranium is nearly harmless to hold, fresh fuel little worse, spent fuel kills
 * in under an hour — and are rounded to what a game can show.
 *
 * <p>Shielding is what a block lets through, as a share. Real lead or concrete stops far more than a
 * block of it does here; a reactor would otherwise need nothing round it but a single wall.
 */
public final class Radioactivity {
    /** Natural uranium held in the hand: some twenty microsieverts an hour. */
    static final double NATURAL_URANIUM = 2.0e-5;
    static final double THORIUM = 1.0e-5;

    private static final Map<Item, Double> ITEMS = new HashMap<>();

    private Radioactivity() { }

    /** The dose rate one of this item gives its carrier, in Sv/h. */
    public static double of(ItemStack stack) {
        if (stack.isEmpty()) return 0.0;
        Double each = table().get(stack.getItem());
        if (each == null) return 0.0;
        double rate = each * stack.getCount();
        // Fuel that has burnt carries its fission products: the further burnt, the hotter.
        if (stack.is(NuclearItems.FUEL_ASSEMBLY) || stack.is(NuclearItems.THORIUM_BREEDER_ROD)) {
            rate += ReactorFuelItem.burnup(stack) * 4.0;
        }
        return rate;
    }

    private static Map<Item, Double> table() {
        if (!ITEMS.isEmpty()) return ITEMS;
        for (Metal metal : new Metal[] {Metal.URANIUM, Metal.THORIUM}) {
            double each = metal == Metal.URANIUM ? NATURAL_URANIUM : THORIUM;
            ITEMS.put(ModMetals.raw(metal), each);
            ITEMS.put(ModMetals.ingot(metal), each);
            ITEMS.put(OrePowders.powder(metal), each);
            ITEMS.put(ModMetals.ore(metal).asItem(), each * 0.2);
            ITEMS.put(ModMetals.deepslateOre(metal).asItem(), each * 0.2);
            ITEMS.put(MetalStock.rod(metal.id()), each * 0.25);
            ITEMS.put(MetalStock.plate(metal.id()), each);
            Block block = MetalBlocks.block(metal.id());
            if (block != null) ITEMS.put(block.asItem(), each * 9);
        }
        ITEMS.put(NuclearItems.YELLOWCAKE, 3.0e-5);
        ITEMS.put(NuclearItems.URANIUM_DIOXIDE, 5.0e-5);
        ITEMS.put(NuclearItems.GREEN_PELLET, 1.5e-5);
        ITEMS.put(NuclearItems.FUEL_PELLET, 1.5e-5);
        ITEMS.put(NuclearItems.FUEL_ROD, 6.0e-5);
        ITEMS.put(NuclearItems.FUEL_ASSEMBLY, 5.0e-4);
        ITEMS.put(NuclearItems.DEPLETED_URANIUM, 1.0e-5);
        ITEMS.put(NuclearItems.THORIUM_DIOXIDE, THORIUM);
        ITEMS.put(NuclearItems.THORIUM_PELLET, THORIUM);
        ITEMS.put(NuclearItems.THORIUM_BREEDER_ROD, 1.0e-4);
        // Uranium-233 bred out of thorium comes with uranium-232, whose daughters shine hard.
        ITEMS.put(NuclearItems.IRRADIATED_THORIUM_ROD, 5.0);
        ITEMS.put(NuclearItems.SPENT_FUEL_ASSEMBLY, 12.0);
        ITEMS.put(NuclearBlocks.CORIUM.asItem(), 200.0);
        ITEMS.put(NuclearBlocks.RADIOACTIVE_DEBRIS.asItem(), 0.05);
        return ITEMS;
    }

    /** The dose rate a block gives at a metre, in Sv/h; zero for almost everything. */
    public static double of(BlockState state) {
        if (state.is(NuclearBlocks.CORIUM)) return 200.0;
        if (state.is(NuclearBlocks.RADIOACTIVE_DEBRIS)) return 0.05;
        return 0.0;
    }

    /** What share of radiation passes through the block at this place. */
    public static double passes(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getFluidState().is(FluidTags.WATER)) return 0.3;
        if (state.isAir()) return 1.0;
        Block block = state.getBlock();
        if (block == NuclearBlocks.FUEL_CHANNEL || block == NuclearBlocks.CONTROL_ROD_DRIVE || block == NuclearBlocks.CORIUM
                || block == NuclearBlocks.RADIOACTIVE_DEBRIS) {
            return 1.0;
        }
        if (block == MetalBlocks.block("lead") || block == MetalBlocks.block("uranium") || block == MetalBlocks.block("tungsten")) {
            return 0.02;
        }
        if (block == NuclearBlocks.REACTOR_VESSEL_WALL) return 0.05;
        if (block == BigMachines.GRAPHITE_ELECTRODE) return 0.5;
        if (!state.isCollisionShapeFullBlock(level, pos)) return 0.9;
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (path.endsWith("concrete")) return 0.15;
        if (path.contains("glass")) return 0.8;
        // Rock and metal stop more than earth and wood: the harder, the denser, near enough.
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0 || hardness >= 3.0f) return 0.25;
        return hardness >= 1.5f ? 0.3 : 0.6;
    }
}
