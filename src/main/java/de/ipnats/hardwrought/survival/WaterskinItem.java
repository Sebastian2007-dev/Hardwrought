package de.ipnats.hardwrought.survival;

import de.ipnats.hardwrought.core.events.CoreLifecycle;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;

/** Eight-use hydration source. Source-water quality can be connected when Milestone 4 exists. */
public final class WaterskinItem extends Item {
    public static final int CAPACITY = 8;

    public WaterskinItem(Properties properties) { super(properties); }

    public static int drinksRemaining(ItemStack stack) {
        return Math.max(0, CAPACITY - stack.getDamageValue());
    }

    public static boolean takeDrink(ItemStack stack) {
        if (drinksRemaining(stack) == 0) return false;
        stack.setDamageValue(stack.getDamageValue() + 1);
        return true;
    }

    public static void refill(ItemStack stack) {
        stack.setDamageValue(0);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            var hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
            var fluid = level.getFluidState(hit.getBlockPos());
            if (hit.getType() == HitResult.Type.BLOCK && fluid.isSource() && fluid.is(FluidTags.WATER)) {
                if (!level.isClientSide()) {
                    refill(stack);
                    player.sendSystemMessage(Component.translatable("message.hardwrought.waterskin_refilled"));
                }
                return InteractionResult.SUCCESS;
            }
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (!takeDrink(stack)) {
                player.sendSystemMessage(Component.translatable("message.hardwrought.waterskin_empty"));
                return InteractionResult.FAIL;
            }
            var runtime = CoreLifecycle.find(serverPlayer.level().getServer());
            if (runtime != null) runtime.survival().drink(serverPlayer, 24.0);
        }
        return InteractionResult.SUCCESS;
    }
}
