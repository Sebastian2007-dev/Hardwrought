package de.ipnats.hardwrought.carried;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** A cell: charged at a charging station, put into a device by clicking it onto the device. See {@link Cells}. */
public class CellItem extends Item {
    private final Cell cell;

    public CellItem(Cell cell, Properties properties) {
        super(properties);
        this.cell = cell;
    }

    public Cell cell() {
        return cell;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
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
    }
}
