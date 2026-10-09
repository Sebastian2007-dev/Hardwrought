package de.ipnats.hardwrought.client.smithing;

import de.ipnats.hardwrought.core.registry.ModDataComponents;
import de.ipnats.hardwrought.smithing.ForgeQuality;
import de.ipnats.hardwrought.smithing.ForgingState;
import de.ipnats.hardwrought.smithing.Heat;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * What a piece of metal says about itself: how hot it is, how far along the anvil it has got, and —
 * once it is a finished part or tool — how well it was made and what that does to it.
 */
public final class SmithingTooltip {
    private SmithingTooltip() { }

    public static void initialize() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (stack.isEmpty()) return;
            Minecraft client = Minecraft.getInstance();
            long now = client.level == null ? 0 : client.level.getGameTime();

            if (stack.has(ModDataComponents.HEAT)) {
                double celsius = Heat.of(stack, now);
                if (celsius > Heat.COLD_BELOW) {
                    lines.add(Component.translatable("tooltip.hardwrought.heat",
                            String.format(Locale.ROOT, "%.0f", celsius)).withStyle(celsius > 500
                            ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
                }
            }
            if (stack.is(de.ipnats.hardwrought.atlas.Atlas.ITEM)) {
                lines.add(Component.translatable("tooltip.hardwrought.atlas").withStyle(ChatFormatting.GRAY));
            }
            // What its runes cost whoever carries the piece: seen from farther off, and mended more often.
            if (de.ipnats.hardwrought.survival.Hardship.runes(stack) > 0) {
                int runes = de.ipnats.hardwrought.survival.Hardship.runes(stack), over = de.ipnats.hardwrought.survival.Hardship.overcharge(stack);
                lines.add(Component.translatable("tooltip.hardwrought.arcane_burden",
                        Math.round(100 * (runes * de.ipnats.hardwrought.survival.Hardship.SCENT_PER_RUNE
                                + over * de.ipnats.hardwrought.survival.Hardship.SCENT_PER_OVERCHARGE)),
                        Math.round(100 * de.ipnats.hardwrought.survival.Hardship.wearChance(stack))).withStyle(ChatFormatting.DARK_PURPLE));
            }
            // What a part still wants to become a tool or a piece of armor: the recipe takes exactly
            // this many, and a grid with fewer in it simply shows nothing.
            var unfinished = de.ipnats.hardwrought.smithing.ToolParts.partOf(stack.getItem());
            if (unfinished != null) {
                var needs = switch (unfinished) {
                    case HELMET_SHELL, SABATONS -> Component.translatable("tooltip.hardwrought.part_leather", 1);
                    case GREAVES -> Component.translatable("tooltip.hardwrought.part_leather", 2);
                    case CUIRASS -> Component.translatable("tooltip.hardwrought.part_leather", 3);
                    case SWORD_BLADE, DAGGER_BLADE -> Component.translatable("tooltip.hardwrought.part_sticks", 1);
                    case HAMMER_HEAD -> Component.translatable("tooltip.hardwrought.part_hammer");
                    default -> Component.translatable("tooltip.hardwrought.part_sticks", 2);
                };
                lines.add(needs.withStyle(ChatFormatting.GRAY));
            }
            if (de.ipnats.hardwrought.smithing.UpgradeEquipment.templates().contains(stack.getItem())) {
                lines.add(Component.translatable("tooltip.hardwrought.upgrade_template").withStyle(ChatFormatting.GRAY));
            }
            var cast = de.ipnats.hardwrought.smeltery.Casts.of(stack);
            var unfired = de.ipnats.hardwrought.smeltery.Casts.unfired(stack);
            if (stack.is(de.ipnats.hardwrought.smeltery.Casts.BLANK)) {
                lines.add(Component.translatable("tooltip.hardwrought.cast_blank").withStyle(ChatFormatting.GRAY));
            } else if (unfired != null) {
                lines.add(Component.translatable("tooltip.hardwrought.cast_unfired").withStyle(ChatFormatting.GRAY));
            } else if (cast != null) {
                lines.add(Component.translatable("tooltip.hardwrought.cast_amount",
                        cast.amount() / de.ipnats.hardwrought.smeltery.MoltenMetals.INGOT).withStyle(ChatFormatting.GRAY));
                if (cast.part() != null) {
                    lines.add(Component.translatable("tooltip.hardwrought.cast_rough").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            int reach = de.ipnats.hardwrought.smithing.Hammers.reach(stack);
            if (reach > 0) {
                lines.add(Component.translatable("tooltip.hardwrought.hammer_reach", reach)
                        .withStyle(ChatFormatting.GRAY));
            }
            ForgingState state = stack.get(ModDataComponents.FORGING_STATE);
            if (state != null) {
                var result = BuiltInRegistries.ITEM.getValue(state.result());
                lines.add(Component.translatable("tooltip.hardwrought.forging_into",
                        new net.minecraft.world.item.ItemStack(result).getHoverName(),
                        Math.round(state.progress() * 100)).withStyle(ChatFormatting.GRAY));
            }
            // Plain steel rusts: how far along it is, or a reminder that it will.
            if (de.ipnats.hardwrought.smithing.Rust.rusts(stack)) {
                float rust = de.ipnats.hardwrought.smithing.Rust.of(stack);
                lines.add(rust > 0
                        ? Component.translatable("tooltip.hardwrought.rust", Math.round(rust * 100)).withStyle(ChatFormatting.RED)
                        : Component.translatable("tooltip.hardwrought.rusts").withStyle(ChatFormatting.DARK_GRAY));
            }
            ForgeQuality quality = ForgeQuality.of(stack);
            if (quality != null) {
                lines.add(Component.translatable("tooltip.hardwrought.craftsmanship",
                        Math.round(quality.total() * 100)).withStyle(ChatFormatting.GRAY));
                // "Air-cooled" would be a lie about a piece still glowing in the fire.
                boolean stillHot = quality.treatment() == ForgeQuality.Treatment.AIR && Heat.hot(stack, now);
                lines.add(Component.translatable("tooltip.hardwrought.treatment."
                        + (stillHot ? "air_hot" : quality.treatment().getSerializedName())).withStyle(ChatFormatting.DARK_GRAY));
                var part = de.ipnats.hardwrought.smithing.ToolParts.partOf(stack.getItem());
                if (part != null || quality.passes() > 0) {
                    lines.add(Component.translatable("tooltip.hardwrought.polish", Math.round(quality.polish() * 100),
                            quality.passes(), de.ipnats.hardwrought.smithing.Grinding.PASSES).withStyle(ChatFormatting.DARK_GRAY));
                }
                // Only what the piece is for: armor does not dig or hit, it turns blows aside.
                boolean armor = part != null ? de.ipnats.hardwrought.smithing.ToolParts.ARMOR.contains(part)
                        : stack.has(net.minecraft.core.component.DataComponents.EQUIPPABLE);
                lines.add((armor
                        ? Component.translatable("tooltip.hardwrought.forged_armor_stats",
                                percent(quality.protectionFactor()), percent(quality.durabilityFactor()))
                        : Component.translatable("tooltip.hardwrought.forged_stats",
                                percent(quality.speedFactor()), percent(quality.durabilityFactor()),
                                percent(quality.damageFactor()))).withStyle(ChatFormatting.DARK_GRAY));
            }
        });
    }

    private static String percent(double factor) {
        return String.valueOf(Math.round(factor * 100));
    }
}
