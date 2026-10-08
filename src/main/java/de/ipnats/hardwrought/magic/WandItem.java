package de.ipnats.hardwrought.magic;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * A wand: what every spell passes through (magic specification section 17). Using it opens the
 * drawing surface on the client; the spell itself is cast by the server when the drawing is sent.
 */
public class WandItem extends Item {
    /** Set by the client: opens the drawing surface. The server never calls it. */
    public static Consumer<InteractionHand> openCanvas = hand -> { };

    private final WandTier tier;

    public WandItem(WandTier tier, Properties properties) {
        super(properties.durability(tier.durability()));
        this.tier = tier;
    }

    public WandTier tier() {
        return tier;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) openCanvas.accept(hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        InteractionResult result = MagicTableBlock.awaken(context.getLevel(), context.getClickedPos(), player,
                context.getHand());
        return result == InteractionResult.PASS ? super.useOn(context) : result;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.hardwrought.wand.tier", tier.tier())
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    /** The wand in either hand, the main hand first; {@code null} without one. */
    public static ItemStack held(Player player) {
        if (player.getMainHandItem().getItem() instanceof WandItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof WandItem) return player.getOffhandItem();
        return null;
    }
}
