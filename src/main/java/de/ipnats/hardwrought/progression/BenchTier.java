package de.ipnats.hardwrought.progression;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How good a workbench has to be before it can make a thing.
 *
 * <p>A ladder rather than a switch. The grid a player carries in their hands is the bottom of it,
 * the bench hewn out of a standing log is the next rung, and each rung after that is a station that
 * had to be earned. Section 71 asks for the physical interaction with the world to be believable,
 * and a bar of bronze coming off a stump with a flat top is not.
 *
 * <p><b>The tags say the minimum, not the permission.</b> That is the whole trick, and it is what
 * keeps this from becoming unmaintainable once there are four or five rungs. Each result is listed
 * once, in the tag of the lowest bench that may make it, and a bench may make anything whose minimum
 * is at or below itself. There is no inheritance to keep in step, nothing is listed twice, and
 * adding a rung above costs one new tag rather than a rewrite of the ones below.
 *
 * <p>Anything no tag has heard of needs the best bench there is. That is the strict reading on
 * purpose: a recipe added by a later milestone or another mod does not quietly become available at
 * the first bench in the game. The cost is that every new early recipe has to be listed, and
 * forgetting one shows up as a bench that will not make it — which is the failure that gets noticed,
 * rather than the one that does not.
 */
public final class BenchTier {
    /** The grid a player carries in their hands. Two by two, and only the opening moves. */
    public static final int INVENTORY = 0;
    /** The bench hewn out of a standing log: wood, stone, flint and fibre. No metal. */
    public static final int HEWN = 1;
    /** The first bench that was built rather than cut. Everything metal starts here. */
    public static final int JOINED = 2;
    /** What a result needs when no tag has heard of it. */
    public static final int HIGHEST = JOINED;

    /**
     * Results each rung may make, listed at the lowest rung that may make them. A tier has no tag of
     * its own where everything it adds is simply "whatever nothing lower was allowed to make".
     */
    private static final TagKey<Item>[] PRODUCTS = tags("tier0_products", "tier1_products");

    private BenchTier() { }

    /** True where this block is the bench hewn out of a log, and therefore limited to early work. */
    public static boolean isHewnBench(BlockState state) {
        return state != null && state.getBlock() instanceof HewnWorkbenchBlock;
    }

    public static boolean isNailedBench(BlockState state) {
        return state != null && state.getBlock() instanceof NailedWorkbenchBlock;
    }

    /**
     * Which rung this block is. Blocks that are not a bench at all answer {@link #INVENTORY}, which
     * is what the player's own grid is and the safest thing to be wrong about.
     */
    public static int of(BlockState state) {
        if (state == null) return INVENTORY;
        if (isHewnBench(state)) return HEWN;
        if (isNailedBench(state)) return JOINED;
        // A vanilla crafting table is no bench here: it is never made, never generated, and one left
        // standing from before falls apart when used (see StructureLoot).
        return INVENTORY;
    }

    /** The lowest bench that may make this. Anything unlisted needs the best one there is. */
    public static int required(ItemStack result) {
        if (result == null || result.isEmpty()) return HIGHEST;
        for (int tier = 0; tier < PRODUCTS.length; tier++) {
            if (result.is(PRODUCTS[tier])) return tier;
        }
        return HIGHEST;
    }

    /**
     * Whether a bench of this rung may make that result. Nothing at all is never a product: an
     * empty stack reports the highest rung, so without this the best bench in the game would be
     * allowed to make it.
     */
    public static boolean allows(int benchTier, ItemStack result) {
        if (result == null || result.isEmpty()) return false;
        return benchTier >= required(result);
    }

    @SuppressWarnings("unchecked")
    private static TagKey<Item>[] tags(String... names) {
        TagKey<Item>[] keys = new TagKey[names.length];
        for (int index = 0; index < names.length; index++) {
            keys[index] = TagKey.create(Registries.ITEM, Hardwrought.id(names[index]));
        }
        return keys;
    }
}
