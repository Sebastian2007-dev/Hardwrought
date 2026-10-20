package de.ipnats.hardwrought.carried;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * A device that runs on a cell (stage E4). A cell clicked onto it in an inventory goes in, and the one
 * that was in it comes out onto the cursor; a right click on it with nothing held takes its cell out.
 * Its bar is the charge of its cell. Without a cell, or with an empty one, it does nothing — a drill is a
 * block of metal.
 */
public class PoweredItem extends Item {
    public PoweredItem(Properties properties) {
        super(properties);
    }

    /** Whether it works with a part that wears, and does nothing without one. */
    public boolean usesPart() {
        return false;
    }

    /** Whether this is the part that wears in it — a drill's bit, a saw's chain — that is fitted by clicking it on. */
    public boolean fits(ItemStack part) {
        return false;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
        if (!slot.allowModification(player)) return false;
        if (fits(other) && other.getCount() == 1) {
            ItemStack old = self.getOrDefault(Cells.WORKING_PART, ItemStack.EMPTY);
            self.set(Cells.WORKING_PART, other.copy());
            carried.set(old.copy());
            player.playSound(SoundEvents.CHAIN_PLACE, 0.6f, 1.2f);
            return true;
        }
        if (other.getItem() instanceof CellItem && other.getCount() == 1) {
            ItemStack old = self.getOrDefault(Cells.CELL, ItemStack.EMPTY);
            self.set(Cells.CELL, other.copy());
            carried.set(old.copy());
            player.playSound(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.5f, 1.6f);
            return true;
        }
        if (other.isEmpty() && action == ClickAction.SECONDARY && self.has(Cells.CELL)) {
            ItemStack old = self.get(Cells.CELL);
            self.remove(Cells.CELL);
            carried.set(old == null ? ItemStack.EMPTY : old.copy());
            player.playSound(SoundEvents.IRON_TRAPDOOR_OPEN, 0.5f, 1.6f);
            return true;
        }
        return false;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.has(Cells.CELL);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * (float) Cells.fraction(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Cells.BAR_COLOR;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Cells.describe(stack).copy().withStyle(ChatFormatting.GRAY));
        ItemStack part = stack.get(Cells.WORKING_PART);
        if (part != null && !part.isEmpty()) {
            builder.accept(Component.translatable("tooltip.hardwrought.device.part", part.getHoverName(),
                    part.getMaxDamage() - part.getDamageValue(), part.getMaxDamage()).withStyle(ChatFormatting.GRAY));
        } else if (usesPart()) {
            builder.accept(Component.translatable("tooltip.hardwrought.device.no_part").withStyle(ChatFormatting.RED));
        }
        if (stack.has(Cells.SWITCHED_ON)) {
            builder.accept(Component.translatable(Cells.on(stack) ? "tooltip.hardwrought.device.on" : "tooltip.hardwrought.device.off")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Switches a switchable device; returns whether it is on now. */
    public static boolean toggle(ItemStack stack) {
        boolean on = !Cells.on(stack);
        stack.set(Cells.SWITCHED_ON, on);
        return on;
    }
}
