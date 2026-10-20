package de.ipnats.hardwrought.magic;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.function.Consumer;

/** A bound scroll: used, the spell on it is cast at once and the scroll is gone. See {@link Scrolls}. */
public class ScrollItem extends Item {
    public ScrollItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Scrolls.Writing writing = stack.get(Scrolls.WRITING);
        if (writing == null) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer reader)) return InteractionResult.SUCCESS;
        if (!SpellCaster.read(reader, writing.strokes(), writing.size(), writing.qualities())) return InteractionResult.FAIL;
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.7f);
        reader.getCooldowns().addCooldown(stack, Scrolls.COOLDOWN);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        Scrolls.Writing writing = stack.get(Scrolls.WRITING);
        return writing == null ? super.getName(stack) : Component.translatable("item.hardwrought.scroll.of", writing.name());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        Scrolls.Writing writing = stack.get(Scrolls.WRITING);
        if (writing == null) return;
        for (Scrolls.Judged judged : writing.judged()) {
            builder.accept(Component.translatable("magic.hardwrought.quality", percent(judged.accuracy()), percent(judged.stability()),
                    percent(judged.power())).withStyle(ChatFormatting.GRAY));
        }
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.0f", value * 100.0);
    }
}
