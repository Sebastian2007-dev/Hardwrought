package de.ipnats.hardwrought.client.knowledge;

import de.ipnats.hardwrought.core.networking.CompendiumPagePayload;
import de.ipnats.hardwrought.knowledge.LootSources;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.Locale;

/**
 * How a place of origin is named and pictured in the browser.
 *
 * <p>A mob and a loot table are not items, so neither has an icon of its own. A spawn egg stands in
 * for the mob and a chest for the table, which is what a player already reads those two pictures as.
 */
public final class LootSourceLabels {
    private LootSourceLabels() { }

    /** The picture beside one place of origin, empty where there is nothing sensible to show. */
    public static ItemStack icon(CompendiumPagePayload.Source source) {
        return switch (source.kind()) {
            case LootSources.KIND_MOB -> {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(source.id());
                yield type == null ? ItemStack.EMPTY : SpawnEggItem.byId(type)
                        .map(holder -> new ItemStack(holder.value())).orElse(ItemStack.EMPTY);
            }
            case LootSources.KIND_BLOCK -> {
                Item item = BuiltInRegistries.ITEM.getValue(source.id());
                yield item == null ? ItemStack.EMPTY : new ItemStack(item);
            }
            default -> new ItemStack(Items.CHEST);
        };
    }

    /** What one place of origin is called, masked where it is a block the player has never held. */
    public static Component label(CompendiumPagePayload.Source source, boolean known) {
        return switch (source.kind()) {
            case LootSources.KIND_MOB -> {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(source.id());
                yield Component.translatable("gui.hardwrought.compendium.dropped_by",
                        type == null ? Component.literal(source.id().getPath()) : type.getDescription());
            }
            case LootSources.KIND_BLOCK -> {
                Item item = BuiltInRegistries.ITEM.getValue(source.id());
                Component name = !known || item == null
                        ? ShadowItem.UNKNOWN_NAME : new ItemStack(item).getHoverName();
                yield Component.translatable("gui.hardwrought.compendium.broken_out_of", name);
            }
            default -> Component.translatable("gui.hardwrought.compendium.found_in", readable(source));
        };
    }

    /**
     * A loot table has no translated name, so its path is made readable instead:
     * {@code chests/abandoned_mineshaft} becomes {@code Abandoned Mineshaft}.
     */
    private static String readable(CompendiumPagePayload.Source source) {
        String path = source.id().getPath();
        int slash = path.lastIndexOf('/');
        String last = slash < 0 ? path : path.substring(slash + 1);
        StringBuilder readable = new StringBuilder(last.length());
        boolean capitalise = true;
        for (char character : last.toCharArray()) {
            if (character == '_') {
                readable.append(' ');
                capitalise = true;
            } else {
                readable.append(capitalise ? Character.toUpperCase(character) : character);
                capitalise = false;
            }
        }
        return readable.toString().toLowerCase(Locale.ROOT).isEmpty() ? path : readable.toString();
    }
}
