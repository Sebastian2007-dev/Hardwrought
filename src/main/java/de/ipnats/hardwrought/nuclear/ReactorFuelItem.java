package de.ipnats.hardwrought.nuclear;

import de.ipnats.hardwrought.core.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Something that goes into a fuel channel and changes there: a fuel assembly burns its uranium-235, a
 * thorium breeder rod breeds uranium-233. How far it has come is its {@link ModDataComponents#BURNUP},
 * shown as a bar.
 */
public class ReactorFuelItem extends Item {
    public ReactorFuelItem(Properties properties) {
        super(properties);
    }

    public static float burnup(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.BURNUP, 0.0f);
    }

    public static void setBurnup(ItemStack stack, float burnup) {
        stack.set(ModDataComponents.BURNUP, Math.clamp(burnup, 0.0f, 1.0f));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return burnup(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * (1.0f - burnup(stack)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return stack.is(NuclearItems.THORIUM_BREEDER_ROD) ? 0x6FD0C0 : 0x8FE04F;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        String percent = String.format(Locale.ROOT, "%.0f", burnup(stack) * 100);
        builder.accept(Component.translatable(stack.is(NuclearItems.THORIUM_BREEDER_ROD)
                ? "item.hardwrought.thorium_breeder_rod.bred" : "item.hardwrought.fuel_assembly.burnup", percent)
                .withStyle(ChatFormatting.GRAY));
    }
}
