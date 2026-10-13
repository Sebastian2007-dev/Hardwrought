package de.ipnats.hardwrought.client.environment;

import de.ipnats.hardwrought.water.BucketWater;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * How much is in the bucket, in figures.
 *
 * <p>The sprite shows three steps and the bar under the item shows thirteen, which between them
 * answer <q>roughly how full is it</q> at a glance. This answers <q>exactly how full is it</q>,
 * which matters the moment a player is trying to fill something to a mark, carry a measured amount
 * to a field, or work out whether one more dip will finish the job.
 *
 * <p>Only part-full buckets say anything. A full one is the ordinary case and needs no line of
 * explanation; an empty one is an empty bucket.
 */
public final class BucketTooltip {
    private BucketTooltip() { }

    public static void initialize() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!BucketWater.isBucket(stack)) return;
            int held = BucketWater.held(stack);
            if (held <= 0 || held >= BucketWater.CAPACITY) return;
            lines.add(Component.translatable("tooltip.hardwrought.bucket_water",
                    held, BucketWater.CAPACITY).withStyle(ChatFormatting.DARK_AQUA));
        });
    }
}
