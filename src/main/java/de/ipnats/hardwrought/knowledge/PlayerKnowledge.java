package de.ipnats.hardwrought.knowledge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What one player has found out so far, and the only thing about knowledge that is written to disk.
 *
 * <p>Two sets rather than a level per entry: everything held is in the first, everything worked with
 * in the second, and anything in neither is unknown. That keeps the save small, makes the common
 * question (do I know this?) a set lookup, and cannot drift into a state the compendium would have
 * to guess about.
 *
 * <p>The ribbons a player has laid into their compendium are kept here as well. They are not
 * knowledge, but they belong to the same book and to the same player in the same world.
 */
public record PlayerKnowledge(Set<Identifier> discovered, Set<Identifier> studied, List<Identifier> bookmarks) {
    public static final Codec<PlayerKnowledge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.listOf().optionalFieldOf("discovered", List.of())
                    .forGetter(knowledge -> List.copyOf(knowledge.discovered())),
            Identifier.CODEC.listOf().optionalFieldOf("studied", List.of())
                    .forGetter(knowledge -> List.copyOf(knowledge.studied())),
            Identifier.CODEC.listOf().optionalFieldOf("bookmarks", List.of())
                    .forGetter(knowledge -> List.copyOf(knowledge.bookmarks()))
    ).apply(instance, (discovered, studied, bookmarks) ->
            new PlayerKnowledge(new LinkedHashSet<>(discovered), new LinkedHashSet<>(studied),
                    new ArrayList<>(bookmarks))));

    public PlayerKnowledge {
        if (discovered == null || studied == null || bookmarks == null) {
            throw new IllegalArgumentException("Knowledge needs both of its sets");
        }
    }

    public PlayerKnowledge(Set<Identifier> discovered, Set<Identifier> studied) {
        this(discovered, studied, new ArrayList<>());
    }

    public static PlayerKnowledge empty() {
        return new PlayerKnowledge(new LinkedHashSet<>(), new LinkedHashSet<>(), new ArrayList<>());
    }

    /** What this player knows about one thing. */
    public KnowledgeLevel level(Identifier entry) {
        if (entry == null) return KnowledgeLevel.UNKNOWN;
        if (studied.contains(entry)) return KnowledgeLevel.STUDIED;
        return discovered.contains(entry) ? KnowledgeLevel.DISCOVERED : KnowledgeLevel.UNKNOWN;
    }

    /** Records having held this. Returns true where that was news. */
    public boolean discover(Identifier entry) {
        if (entry == null) return false;
        return discovered.add(entry);
    }

    /**
     * Records having worked with this. Working with something the player had never held still counts
     * as having held it, so the two sets can never contradict each other.
     */
    public boolean study(Identifier entry) {
        if (entry == null) return false;
        boolean news = studied.add(entry);
        news |= discovered.add(entry);
        return news;
    }

    /**
     * Lays a ribbon at this entry, or takes out the one that is there. A book has only so many
     * ribbons; with all of them in use a new one is refused rather than an old one lost. Returns
     * true where the ribbons changed.
     */
    public boolean toggleBookmark(Identifier entry, int most) {
        if (entry == null) return false;
        if (bookmarks.remove(entry)) return true;
        if (bookmarks.size() >= most) return false;
        return bookmarks.add(entry);
    }

    public int count() {
        return discovered.size();
    }
}
