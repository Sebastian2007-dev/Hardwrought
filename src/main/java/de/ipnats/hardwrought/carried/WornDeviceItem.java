package de.ipnats.hardwrought.carried;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A device worn rather than held — a headlamp, night vision goggles, an exoskeleton. Used it is put on
 * as armour is; used while sneaking it is switched on or off. What it does while worn and on, and what
 * that costs its cell, is in {@link CarriedPower}.
 */
public class WornDeviceItem extends PoweredItem {
    public WornDeviceItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return super.use(level, player, hand);
        ItemStack stack = player.getItemInHand(hand);
        boolean on = toggle(stack);
        player.playSound(on ? SoundEvents.LEVER_CLICK : SoundEvents.STONE_BUTTON_CLICK_OFF, 0.6f, 1.4f);
        if (player instanceof ServerPlayer server) {
            server.sendOverlayMessage(Component.translatable(on ? "tooltip.hardwrought.device.on" : "tooltip.hardwrought.device.off"));
        }
        return InteractionResult.SUCCESS;
    }
}
