package de.ipnats.hardwrought.water;

import de.ipnats.hardwrought.core.registry.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

/**
 * How much water a bucket is carrying, and everything that follows from it being a number rather
 * than a yes or a no.
 *
 * <p>Vanilla has two items and no middle: a bucket, or a bucket containing exactly one block of
 * water. That cannot share a world with finite water. Dipping a bucket in a puddle holding a third
 * of a block either invents two thirds of a block or refuses the puddle, and pouring a bucket into
 * a cell with room for half of it either loses half a bucket or refuses to pour. Both of those were
 * real behaviours here before this.
 *
 * <p>So the water bucket carries {@link ModDataComponents#WATER_AMOUNT}. The item is still
 * {@code minecraft:water_bucket} — that keeps it stackable with nothing, renderable, and
 * recognisable to anything that looks — but what it is worth is the number on it.
 *
 * <p>A bucket with no amount on it at all is treated as full. Creative gives those out, other mods
 * make them, and loot tables contain them; assuming full is the reading that never invents water
 * out of an unmarked item and never destroys it either.
 */
public final class BucketWater {
    /** What a bucket holds when it is full. */
    public static final int CAPACITY = WaterAmounts.BUCKET;

    /**
     * How many steps the model is drawn in, not counting empty.
     *
     * <p>Three, and that is the sprite's doing rather than a choice: a bucket is opaque below its
     * rim, so the only place water can be seen is the three rows inside the mouth. Anything finer
     * would be a difference nobody could see. The bar under the item and the tooltip carry the
     * precision; this is the glance.
     *
     * <p>The dispatch goes through {@link CustomModelData} because that is the one property an item
     * model may branch on that a mod is able to set.
     */
    public static final int MODEL_STEPS = 3;

    private BucketWater() { }

    /** Whether this stack is a bucket that could hold water, full, part full or empty. */
    public static boolean isBucket(ItemStack stack) {
        return stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET);
    }

    /**
     * What is in this bucket, in millibuckets. An empty bucket holds nothing; a water bucket with
     * no amount written on it is a full one.
     */
    public static int held(ItemStack stack) {
        if (stack.is(Items.BUCKET)) return 0;
        if (!stack.is(Items.WATER_BUCKET)) return 0;
        Integer written = stack.get(ModDataComponents.WATER_AMOUNT);
        return written == null ? CAPACITY : Math.clamp(written, 0, CAPACITY);
    }

    /** Room left for more. */
    public static int room(ItemStack stack) {
        return CAPACITY - held(stack);
    }

    /** Whether vanilla should be allowed to treat this as a full bucket of water. */
    public static boolean isFull(ItemStack stack) {
        return stack.is(Items.WATER_BUCKET) && held(stack) >= CAPACITY;
    }

    /**
     * A bucket holding this much of this water, built from the one it came from so that a name, an
     * enchantment or anything else another mod put on it survives being filled and poured.
     */
    public static ItemStack with(ItemStack from, int millibuckets, WaterQuality quality) {
        int amount = Math.clamp(millibuckets, 0, CAPACITY);
        ItemStack result = from.copyWithCount(1);
        if (amount <= 0) {
            result = convert(result, Items.BUCKET);
            result.remove(ModDataComponents.WATER_AMOUNT);
            result.remove(ModDataComponents.WATER_QUALITY);
            result.remove(DataComponents.CUSTOM_MODEL_DATA);
            return result;
        }
        result = convert(result, Items.WATER_BUCKET);
        result.set(ModDataComponents.WATER_AMOUNT, amount);
        if (quality != null) result.set(ModDataComponents.WATER_QUALITY, quality);
        result.set(DataComponents.CUSTOM_MODEL_DATA, modelData(amount));
        return result;
    }

    /**
     * Which of the drawn steps this amount falls in, 1 to {@link #MODEL_STEPS}.
     *
     * <p>Rounded up, so any water at all shows as some water. A bucket that looks empty but is not
     * is the one reading a player cannot recover from.
     */
    public static int modelStep(int millibuckets) {
        if (millibuckets <= 0) return 0;
        int step = (int) Math.ceil(millibuckets * (double) MODEL_STEPS / CAPACITY);
        return Math.clamp(step, 1, MODEL_STEPS);
    }

    private static CustomModelData modelData(int millibuckets) {
        return new CustomModelData(List.of((float) modelStep(millibuckets)), List.of(), List.of(), List.of());
    }

    /** Same components, different item: there is no vanilla call that does this. */
    private static ItemStack convert(ItemStack stack, net.minecraft.world.item.Item item) {
        if (stack.is(item)) return stack;
        ItemStack converted = new ItemStack(item, 1);
        converted.applyComponents(stack.getComponentsPatch());
        return converted;
    }

    /**
     * The quality of what is in the bucket once this much of that has been added to it.
     *
     * <p>Mixing by volume, the same way {@link WaterQualityStorage} mixes water that flows together,
     * so topping a bucket of clean water up out of a swamp does not leave it clean.
     */
    public static WaterQuality mixed(ItemStack stack, int added, WaterQuality adding) {
        int had = held(stack);
        WaterQuality carried = stack.get(ModDataComponents.WATER_QUALITY);
        if (had <= 0 || carried == null) return adding;
        if (adding == null || adding == carried) return carried;
        // Whichever makes up more of what is now in the bucket decides what it is.
        return added > had ? adding : carried;
    }
}
