package de.ipnats.hardwrought.progression;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.core.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Specification section 71.1: which blocks give way to what.
 *
 * <p>The classification is read off tags rather than invented here, and mostly off vanilla's own.
 * Vanilla already says which blocks want an axe, a pick or a shovel; what Hardwrought adds is that
 * the answer <em>matters</em>. A block that wants a pick and is met with a fist does not slowly
 * surrender after thirty seconds of punching — it does not move at all.
 *
 * <pre>
 *   #minecraft:mineable/pickaxe, /axe   solid material: stone, ore, timber, built structure
 *   #minecraft:mineable/shovel          soil: hands work, badly (section 71.1.2)
 *   #hardwrought:shatters_by_hand       glass: breaks, nothing to pick up (section 71.1.7)
 *   #hardwrought:hand_gatherable        loose material, always free (section 71.1.1)
 *   everything else                     plants, cloth, snow — hands are fine
 * </pre>
 *
 * <p>Whether the held item is the <em>right kind</em> of tool is asked of the item itself: an item
 * that mines this block faster than a fist would has an affinity for it. That works for every tool
 * any mod adds without a list of tool types here, and it keeps the tier question with vanilla, which
 * already refuses the drops of a block mined with too soft a tool.
 */
public final class BlockBreaking {
    /** Section 71.1.1: loose material that hands may always take, whatever else the tags say. */
    public static final TagKey<Block> HAND_GATHERABLE =
            TagKey.create(Registries.BLOCK, Hardwrought.id("hand_gatherable"));
    /** Section 71.1.2: ground that can be moved by hand, slowly and at a price. */
    public static final TagKey<Block> HAND_DIGGABLE =
            TagKey.create(Registries.BLOCK, Hardwrought.id("hand_diggable"));
    /** Section 71.1.7: breaks under a fist, leaves nothing worth keeping. */
    public static final TagKey<Block> SHATTERS_BY_HAND =
            TagKey.create(Registries.BLOCK, Hardwrought.id("shatters_by_hand"));

    /** Stamina one ordinary block costs with the right tool, before hardness and verdict. */
    public static final double BASE_STAMINA = 0.18;
    /** However bad the tool and however hard the rock, one block never costs more than this. */
    public static final double MAX_STAMINA = 6.0;

    /**
     * How much of vanilla's breaking speed is left on material that has to be worked rather than
     * picked. Section 71.1.9 already made a bad tool expensive; this is the other half of it — even
     * the right tool against rock is work, and a mod whose first tool is a knapped flint edge should
     * not shift stone at the pace of a creative-mode diamond pick.
     *
     * <p>Two numbers rather than one, because a flat multiplier gets the feel wrong in both
     * directions: it makes gravel tedious while barely touching deepslate. Scaling with the block's
     * own hardness keeps soil quick and turns rock and timber into the part of the day that costs
     * something.
     */
    public static final double LABOR_BASE = 0.55;
    /** How much further each point of hardness drags the same swing. */
    public static final double LABOR_PER_HARDNESS = 0.08;
    /** However hard the material, a swing never falls below this share of its vanilla speed. */
    public static final double MIN_LABOR = 0.30;

    private BlockBreaking() { }

    /**
     * What breaking this block with this in hand does. Pure: an item stack and a block state in, a
     * verdict out, so the rule can be checked without a world and asked from both sides.
     */
    public static BreakingVerdict verdict(ItemStack held, BlockState state) {
        if (held == null || state == null) throw new IllegalArgumentException("Nothing to break with");
        if (state.isAir()) return BreakingVerdict.LOOSE;

        boolean affinity = hasAffinity(held, state);
        boolean tiered = held.isCorrectToolForDrops(state);
        if (affinity && tiered) return BreakingVerdict.PROPER;
        // The right kind of tool but too soft for the material: it still works, and vanilla already
        // refuses the drops, which is section 71.1.10 with no help from here.
        if (affinity) return BreakingVerdict.IMPROVISED;

        if (state.is(HAND_GATHERABLE)) return BreakingVerdict.LOOSE;
        boolean tool = isTool(held);
        if (state.is(HAND_DIGGABLE)) return tool ? BreakingVerdict.IMPROVISED : BreakingVerdict.DIGGABLE;
        if (state.is(SHATTERS_BY_HAND)) return tool ? BreakingVerdict.IMPROVISED : BreakingVerdict.SHATTERS;
        if (solid(state)) return tool ? BreakingVerdict.IMPROVISED : BreakingVerdict.IMPOSSIBLE;
        return BreakingVerdict.LOOSE;
    }

    /**
     * Section 71.1.3 to 71.1.6: timber, stone, ore, metal and anything built out of them. Vanilla's
     * own mining tags already draw that line; a block that wants a pick or an axe is solid material.
     */
    public static boolean solid(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_AXE)
                || state.requiresCorrectToolForDrops();
    }

    /** True where this item mines this block faster than a bare hand would: the right kind of tool. */
    public static boolean hasAffinity(ItemStack held, BlockState state) {
        return held.getDestroySpeed(state) > 1.0f;
    }

    /** True where the player is holding a tool at all, whatever it was made for. */
    public static boolean isTool(ItemStack held) {
        return held.has(DataComponents.TOOL);
    }

    /**
     * What is left of vanilla's speed once the material's own resistance is paid for, for a player
     * holding the right tool. One, and no slowdown at all, for everything that is gathered rather
     * than worked: grass, crops, leaves, cloth, snow. Punching a bush was never the problem.
     *
     * <p>Deliberately not applied on top of {@link BreakingVerdict#speedFactor()}. That factor is
     * the price of the wrong tool and was tuned on its own; multiplying the two would put a flint
     * pick against iron ore into the minutes, which teaches nothing the refused drops do not.
     */
    public static double laborFactor(BlockState state) {
        if (state == null || !worked(state)) return 1.0;
        double hardness = Math.max(0.2, state.getBlock().defaultDestroyTime());
        return Math.max(MIN_LABOR, LABOR_BASE - hardness * LABOR_PER_HARDNESS);
    }

    /**
     * Material that is worked with a tool rather than gathered by hand: rock, ore, timber, built
     * structure, and the ground itself. Read off the same vanilla tags the rest of this class uses,
     * so a modded block lands on the right side without a line here.
     */
    public static boolean worked(BlockState state) {
        return solid(state) || state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    /**
     * Section 71.1.9: what this block costs to break. Hard material costs more than soft, and a bad
     * tool costs several times what the right one does — which is what makes a better tool feel like
     * one before any number is shown.
     */
    public static double staminaCost(BlockState state, BreakingVerdict verdict) {
        if (!verdict.breaks()) return 0;
        double hardness = Math.max(0.2, state.getBlock().defaultDestroyTime());
        return Math.min(MAX_STAMINA, BASE_STAMINA * hardness * verdict.staminaFactor());
    }

    /**
     * Section 71.1.2: what a handful of dug ground actually is. Hands bring up loose material, not a
     * clean block — dirt comes up as blobs of it, gravel as the shards and loose stones in it.
     */
    public static ItemStack handful(BlockState state) {
        if (state.is(BlockTags.DIRT)) return new ItemStack(ModItems.DIRT_BLOB, 2);
        if (state.is(Blocks.GRAVEL)) return new ItemStack(ModItems.FLINT_SHARD, 1);
        if (state.is(BlockTags.BASE_STONE_OVERWORLD)) return new ItemStack(ModItems.COBBLESTONE_PIECE, 1);
        return ItemStack.EMPTY;
    }
}
