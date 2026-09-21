package de.ipnats.hardwrought.knowledge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

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
 */
public record PlayerKnowledge(Set<Identifier> discovered, Set<Identifier> studied) {
    public static final Codec<PlayerKnowledge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.listOf().optionalFieldOf("discovered", List.of())
                    .forGetter(knowledge -> List.copyOf(knowledge.discovered())),
            Identifier.CODEC.listOf().optionalFieldOf("studied", List.of())
                    .forGetter(knowledge -> List.copyOf(knowledge.studied()))
    ).apply(instance, (discovered, studied) ->
            new PlayerKnowledge(new LinkedHashSet<>(discovered), new LinkedHashSet<>(studied))));

    public PlayerKnowledge {
        if (discovered == null || studied == null) {
            throw new IllegalArgumentException("Knowledge needs both of its sets");
        }
    }

    public static PlayerKnowledge empty() {
        return new PlayerKnowledge(new LinkedHashSet<>(), new LinkedHashSet<>());
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

    public int count() {
        return discovered.size();
    }
}
