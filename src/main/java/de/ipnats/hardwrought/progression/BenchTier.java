package de.ipnats.hardwrought.progression;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a workbench is good enough to make.
 *
 * <p>The bench hewn out of a standing log is the first one a player can have, and it is meant to
 * read as exactly that: a flat surface with a stump under it. It joins what the early game is made
 * of — boards, handles, a chest, the first metal tools, the pack that follows the woven one — and
 * the moment a recipe wants more than that, it wants a bench that was itself joined rather than cut.
 *
 * <p>The list is a datapack tag of <em>results</em> rather than of recipes, and it is a list of what
 * the hewn bench <b>can</b> do rather than of what it cannot. That is the strict reading on purpose:
 * something the tag has not heard of is refused, so a recipe added by a later milestone or another
 * mod does not quietly become available at the first bench in the game. The cost is that every new
 * early recipe has to be added here, and forgetting one shows up as a bench that will not make it.
 */
public final class BenchTier {
    /** Results the hewn bench may produce. Everything else wants a joined crafting table. */
    public static final TagKey<Item> HEWN_BENCH_PRODUCTS =
            TagKey.create(Registries.ITEM, Hardwrought.id("hewn_bench_products"));

    private BenchTier() { }

    /** True where this block is the first bench: hewn out of a log, and limited to early work. */
    public static boolean isHewnBench(BlockState state) {
        return state != null && state.getBlock() instanceof HewnWorkbenchBlock;
    }

    /**
     * Whether a bench of this kind may make that result. A station that is not the hewn bench — the
     * inventory grid, a joined crafting table, anything a later milestone adds — is not limited here.
     */
    public static boolean allows(boolean hewnBench, ItemStack result) {
        if (!hewnBench) return true;
        return !result.isEmpty() && result.is(HEWN_BENCH_PRODUCTS);
    }
}
