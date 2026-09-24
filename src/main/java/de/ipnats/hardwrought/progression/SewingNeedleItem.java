package de.ipnats.hardwrought.progression;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * A needle with a cord through its eye. Held in the main hand with cord in the other and used for a
 * while, it weaves a piece of cloth; see {@link Weaving}. A wooden one is slow going; one forged from
 * iron is fine and hard, and much quicker.
 */
public class SewingNeedleItem extends Item {
    /** Ten seconds a piece with a needle of sharpened wood. */
    public static final int WOODEN_WEAVE_TICKS = 200;
    /** Three seconds with a forged iron one. */
    public static final int IRON_WEAVE_TICKS = 60;

    private final int weaveTicks;

    public SewingNeedleItem(Properties properties, int weaveTicks) {
        super(properties);
        this.weaveTicks = weaveTicks;
    }

    public int weaveTicks() {
        return weaveTicks;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!Weaving.hasCord(player) && !Weaving.hasCloth(player)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.hardwrought.weaving_needs_cord",
                        Weaving.CORD_PER_WEAVE));
            }
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    /** Weaving a piece of cloth takes the needle's own time; sewing a pack twice as long. */
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return entity instanceof Player player && Weaving.hasCloth(player)
                ? weaveTicks * Weaving.PACK_TIME_FACTOR : weaveTicks;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BRUSH;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            if (Weaving.hasCloth(player)) Weaving.sewPack(player, stack);
            else Weaving.weave(player, stack);
        }
        return stack;
    }
}
