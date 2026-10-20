package de.ipnats.hardwrought.carried;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An electric drill (stage E4): a motor, a gearbox and a chuck, and a bit in the chuck. It digs stone
 * and earth alike, faster than any hand tool of its bit's metal — a steel bit about twice as fast as a
 * steel pickaxe, a tungsten-steel bit faster still and through what only tungsten steel cuts — and
 * every block costs {@link #JOULES_PER_BLOCK} of its cell and a little of its bit. A new bit is clicked
 * onto it. With an empty cell, or no bit, it is a block of metal.
 */
public class DrillItem extends PoweredItem {
    public static final double JOULES_PER_BLOCK = 120.0;

    public DrillItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean usesPart() {
        return true;
    }

    @Override
    public boolean fits(ItemStack part) {
        return part.is(CarriedItems.STEEL_DRILL_BIT) || part.is(CarriedItems.TUNGSTEN_STEEL_DRILL_BIT);
    }

    private static boolean tungsten(ItemStack stack) {
        return Cells.part(stack).is(CarriedItems.TUNGSTEN_STEEL_DRILL_BIT);
    }

    /** Whether it turns at all: charge in its cell and a bit in its chuck. */
    public static boolean works(ItemStack stack) {
        return Cells.charged(stack) && !Cells.part(stack).isEmpty();
    }

    private static boolean drills(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    /** What its bit is too soft for, as a pickaxe of that metal is. */
    private static TagKey<Block> tooHard(ItemStack stack) {
        return tungsten(stack) ? BlockTags.INCORRECT_FOR_NETHERITE_TOOL : BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (!works(stack) || !drills(state) || state.is(tooHard(stack))) return 1.0f;
        return tungsten(stack) ? 22.0f : 15.0f;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return works(stack) && drills(state) && !state.is(tooHard(stack));
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity owner) {
        if (level.isClientSide() || !works(stack) || state.getDestroySpeed(level, pos) == 0.0f) return true;
        Cells.draw(stack, JOULES_PER_BLOCK);
        Cells.wearPart(stack);
        return true;
    }
}
