package de.ipnats.hardwrought.chemistry;

import de.ipnats.hardwrought.core.registry.ModItems;
import de.ipnats.hardwrought.water.WaterEvents;
import de.ipnats.hardwrought.water.WaterQuality;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * What the still does with what is put into it (sections 62 to 65). Few things, on purpose: the
 * specification asks for a handful of useful groups rather than a periodic table.
 *
 * <ul>
 *   <li><b>Crude oil</b> is distilled into its four fractions, one to a bottle but the last: light
 *       spirit off the top, fuel, heavy oil, and bitumen left in the bottom. The sulfur in the oil
 *       comes out with the residue now and then, as a crude crust.</li>
 *   <li><b>Seawater</b> is boiled down to its salt.</li>
 *   <li><b>Raw sulfur</b> is melted and cleaned: it comes out refined.</li>
 * </ul>
 */
public final class StillRecipes {
    /** Ticks a batch takes over a fire: twenty seconds. */
    public static final int BATCH_TICKS = 400;
    /** How often the sulfur in a bucket of crude oil is enough to come out as a crust. */
    public static final double SULFUR_CHANCE = 0.35;

    private StillRecipes() { }

    /**
     * One batch: what goes in, how many glass bottles it needs, what it gives and what container
     * comes back empty.
     */
    public record Batch(int bottles, List<ItemStack> products, ItemStack returned) { }

    /** Whether the still takes this as its charge at all. */
    public static boolean accepts(ItemStack stack) {
        return stack.is(ModItems.CRUDE_OIL_BUCKET) || stack.is(ModItems.RAW_SULFUR) || saltWater(stack);
    }

    private static boolean saltWater(ItemStack stack) {
        return stack.is(Items.WATER_BUCKET) && WaterEvents.qualityOf(stack) == WaterQuality.SALT;
    }

    /** How many glass bottles distilling this takes, before anything is rolled. */
    public static int bottlesFor(ItemStack stack) {
        return stack.is(ModItems.CRUDE_OIL_BUCKET) ? 3 : 0;
    }

    /** The batch this charge makes, or null where it makes none. The chances are rolled here. */
    public static Batch batch(ItemStack charge, RandomSource random) {
        if (charge.is(ModItems.CRUDE_OIL_BUCKET)) {
            List<ItemStack> products = new ArrayList<>(List.of(
                    new ItemStack(ModItems.LIGHT_FRACTION), new ItemStack(ModItems.FUEL_FRACTION),
                    new ItemStack(ModItems.HEAVY_OIL), new ItemStack(ModItems.BITUMEN)));
            if (random.nextDouble() < SULFUR_CHANCE) {
                products.add(Purity.with(new ItemStack(ModItems.RAW_SULFUR),
                        Purity.rounded(0.55 + random.nextDouble() * 0.25)));
            }
            return new Batch(3, products, new ItemStack(Items.BUCKET));
        }
        if (saltWater(charge)) {
            return new Batch(0, List.of(Purity.with(new ItemStack(ModItems.SALT),
                    Purity.rounded(0.85 + random.nextDouble() * 0.10))), new ItemStack(Items.BUCKET));
        }
        if (charge.is(ModItems.RAW_SULFUR)) {
            return new Batch(0, List.of(Purity.with(new ItemStack(ModItems.SULFUR),
                    Purity.rounded(Purity.refined(Purity.of(charge))))), ItemStack.EMPTY);
        }
        return null;
    }

    /** The largest a batch of this charge can come out, for checking there is room before it starts. */
    public static List<ItemStack> mostOf(ItemStack charge) {
        if (charge.is(ModItems.CRUDE_OIL_BUCKET)) {
            return List.of(new ItemStack(ModItems.LIGHT_FRACTION), new ItemStack(ModItems.FUEL_FRACTION),
                    new ItemStack(ModItems.HEAVY_OIL), new ItemStack(ModItems.BITUMEN),
                    new ItemStack(ModItems.RAW_SULFUR));
        }
        if (saltWater(charge)) return List.of(new ItemStack(ModItems.SALT));
        if (charge.is(ModItems.RAW_SULFUR)) return List.of(new ItemStack(ModItems.SULFUR));
        return List.of();
    }
}
