package de.ipnats.hardwrought.chemistry;

import de.ipnats.hardwrought.core.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Section 65: chemical raw materials have a purity. Sulfur out of the residue of a still is a yellow
 * crust with half the dirt of the oil still in it; refined, it is nearly nothing but sulfur. Later
 * processes will ask how pure what they are given is, and the number is on the item for that.
 */
public final class Purity {
    /** What a stack without a stated purity counts as: the crude thing, whatever it is. */
    public static final float UNSTATED = 0.5f;

    private Purity() { }

    public static float of(ItemStack stack) {
        Float purity = stack.get(ModDataComponents.PURITY);
        return purity == null ? UNSTATED : purity;
    }

    public static ItemStack with(ItemStack stack, double purity) {
        stack.set(ModDataComponents.PURITY, (float) Math.max(0.0, Math.min(1.0, purity)));
        return stack;
    }

    /**
     * What one pass of refining leaves of what was not the thing itself: a twelfth. Seventy percent
     * sulfur comes out at about ninety-seven and a half, which is refined; a second pass is not
     * worth it at this stage and is not offered.
     */
    public static final double REFINING_LEAVES = 1.0 / 12.0;

    public static double refined(double purity) {
        return 1.0 - (1.0 - purity) * REFINING_LEAVES;
    }

    /** Stacks only join where their purity matches to the tenth of a percent, so it is rounded to that. */
    public static double rounded(double purity) {
        return Math.round(purity * 1000.0) / 1000.0;
    }
}
