package de.ipnats.hardwrought.underworld;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The journals of the Lantern Company, who came down through the seal long before the player: what they
 * saw, the eyes, the Graveyard, the Ossuary and, deeper, the Witch King. Each is a written book whose name
 * and pages are translated, so it reads in the player's own language; found one at a time, in ruin chests
 * and on lecterns, they make a trail of lore that also points the way down.
 */
public final class Journals {
    /** One journal: its key, who wrote it, and how many pages it has. */
    public record Journal(String key, String author, int pages) { }

    public static final List<Journal> ALL = List.of(
            new Journal("arrival", "Maren Holt", 3),
            new Journal("ore", "Odric Venn", 3),
            new Journal("eyes", "Aldo", 3),
            new Journal("graves", "Jory Pell", 3),
            new Journal("grove", "Maren Holt", 2),
            new Journal("king", "Aldo", 3),
            new Journal("last", "Tamsin Ware", 3),
            new Journal("scratched", "?", 2));

    /** Picks a journal at random: a loot function, {@code hardwrought:underworld_journal}. */
    public static final class Function extends LootItemConditionalFunction {
        public static final MapCodec<Function> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i).apply(i, Function::new));

        Function(Optional<Holder<LootItemCondition>> condition) {
            super(condition);
        }

        @Override
        protected ItemStack run(ItemStack stack, LootContext context) {
            return random(context.getRandom());
        }

        @Override
        public MapCodec<Function> codec() {
            return MAP_CODEC;
        }
    }

    private Journals() { }

    public static void initialize() {
        Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, Hardwrought.id("underworld_journal"), Function.MAP_CODEC);
    }

    public static ItemStack random(RandomSource random) {
        return stack(ALL.get(random.nextInt(ALL.size())));
    }

    /** The journal as a written book, tattered, already resolved: nothing in it changes when it is opened. */
    public static ItemStack stack(Journal journal) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = new ArrayList<>();
        for (int page = 1; page <= journal.pages(); page++) {
            pages.add(Filterable.passThrough(Component.translatable("journal.hardwrought." + journal.key() + "." + page)));
        }
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(journal.key()), journal.author(), 3, pages, true));
        book.set(DataComponents.CUSTOM_NAME, Component.translatable("journal.hardwrought." + journal.key())
                .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)));
        return book;
    }
}
