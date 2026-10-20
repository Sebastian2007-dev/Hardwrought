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
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * A rune tome (magic specification §4.1, docs/magic-roadmap.md M6): found in old chests, read, it
 * teaches one rune its reader does not know yet, chosen at random, and is used up. A reader who knows
 * every rune learns nothing from it and keeps it.
 */
public class RuneTomeItem extends Item {
    public RuneTomeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer reader)) return InteractionResult.SUCCESS;
        Glyph glyph = teach(reader);
        if (glyph == null) {
            reader.sendOverlayMessage(Component.translatable("magic.hardwrought.rune_tome.nothing").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.9f);
        return InteractionResult.SUCCESS;
    }

    /** Teaches a random rune the reader does not know; the rune, or {@code null} if they know them all. */
    public static Glyph teach(ServerPlayer reader) {
        List<Glyph> unknown = new ArrayList<>();
        for (Glyph glyph : Glyph.values()) if (!RuneKnowledge.knows(reader, glyph)) unknown.add(glyph);
        if (unknown.isEmpty()) return null;
        Glyph glyph = unknown.get(reader.getRandom().nextInt(unknown.size()));
        RuneKnowledge.learn(reader, glyph);
        reader.sendSystemMessage(Component.translatable("magic.hardwrought.rune_tome.learned",
                Component.translatable(glyph.translationKey()).withColor(glyph.color())).withStyle(ChatFormatting.ITALIC));
        return glyph;
    }
}
