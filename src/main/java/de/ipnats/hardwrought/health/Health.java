package de.ipnats.hardwrought.health;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a player is ill with, what they have only just thrown off and are proof against for a while, and
 * which diseases they have been through and so can name. Immutable: a change is a new value, which is
 * what sends it to the player's client.
 */
public record Health(List<Infection> infections, Map<Disease, Integer> immunity, Set<Disease> known) {
    public static final Health NONE = new Health(List.of(), Map.of(), Set.of());

    /** One disease being fought. */
    public record Infection(Disease disease, int age, double load, double peak) {
        public static final Codec<Infection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Disease.CODEC.fieldOf("disease").forGetter(Infection::disease),
                Codec.INT.fieldOf("age").forGetter(Infection::age),
                Codec.DOUBLE.fieldOf("load").forGetter(Infection::load),
                Codec.DOUBLE.optionalFieldOf("peak", 0.0).forGetter(Infection::peak)
        ).apply(instance, Infection::new));
        public static final StreamCodec<ByteBuf, Infection> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.map(ordinal -> Disease.values()[ordinal], Disease::ordinal), Infection::disease,
                ByteBufCodecs.VAR_INT, Infection::age,
                ByteBufCodecs.DOUBLE, Infection::load,
                ByteBufCodecs.DOUBLE, Infection::peak,
                Infection::new);

        /** Where it stands: unfelt, rising, at its height, critical, or on the way out. */
        public Disease.Stage stage() {
            if (age < disease.incubation()) return Disease.Stage.INCUBATING;
            if (load >= Diseases.CRITICAL) return Disease.Stage.CRITICAL;
            if (load >= Diseases.SEVERE) return Disease.Stage.SEVERE;
            if (load < peak - Diseases.TURNING) return Disease.Stage.RECOVERING;
            return Disease.Stage.SYMPTOMS;
        }

        /** Whether it is felt at all. */
        public boolean felt() {
            return stage() != Disease.Stage.INCUBATING && load > 0;
        }
    }

    public static final Codec<Health> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Infection.CODEC.listOf().optionalFieldOf("infections", List.of()).forGetter(Health::infections),
            Codec.unboundedMap(Disease.CODEC, Codec.INT).optionalFieldOf("immunity", Map.of()).forGetter(Health::immunity),
            Disease.CODEC.listOf().xmap(list -> (Set<Disease>) (list.isEmpty() ? EnumSet.noneOf(Disease.class) : EnumSet.copyOf(list)),
                    List::copyOf).optionalFieldOf("known", Set.of()).forGetter(Health::known)
    ).apply(instance, Health::new));

    public static final StreamCodec<ByteBuf, Health> STREAM_CODEC = StreamCodec.composite(
            Infection.STREAM_CODEC.apply(ByteBufCodecs.list()), Health::infections,
            ByteBufCodecs.VAR_INT, health -> {
                int mask = 0;
                for (Disease disease : health.known) mask |= 1 << disease.ordinal();
                return mask;
            },
            (infections, mask) -> {
                Set<Disease> known = EnumSet.noneOf(Disease.class);
                for (Disease disease : Disease.values()) if ((mask & 1 << disease.ordinal()) != 0) known.add(disease);
                // The client is not told how long a player stays proof against what they had.
                return new Health(infections, Map.of(), known);
            });

    public Health {
        infections = List.copyOf(infections);
        immunity = Map.copyOf(immunity);
        known = Set.copyOf(known);
    }

    public Infection infection(Disease disease) {
        for (Infection infection : infections) if (infection.disease() == disease) return infection;
        return null;
    }

    /** Whether anything at all is felt: what makes a player hungrier and wearier. */
    public boolean sick() {
        for (Infection infection : infections) if (infection.felt()) return true;
        return false;
    }

    public boolean immune(Disease disease) {
        return immunity.getOrDefault(disease, 0) > 0;
    }

    /** This with one infection replaced, added, or — given null — taken away. */
    public Health with(Disease disease, Infection infection) {
        List<Infection> next = new ArrayList<>();
        for (Infection present : infections) if (present.disease() != disease) next.add(present);
        if (infection != null) next.add(infection);
        return new Health(next, immunity, known);
    }

    /** Thrown off: proof against it for a while, and from now on known by name. */
    public Health recovered(Disease disease, int immuneSeconds) {
        Map<Disease, Integer> proof = new EnumMap<>(Disease.class);
        proof.putAll(immunity);
        proof.put(disease, immuneSeconds);
        Set<Disease> names = EnumSet.noneOf(Disease.class);
        names.addAll(known);
        names.add(disease);
        return new Health(with(disease, null).infections, proof, names);
    }

    /** A second of protection gone. */
    public Health immunityPassed() {
        if (immunity.isEmpty()) return this;
        Map<Disease, Integer> proof = new EnumMap<>(Disease.class);
        immunity.forEach((disease, seconds) -> {
            if (seconds > 1) proof.put(disease, seconds - 1);
        });
        return new Health(infections, proof, known);
    }
}
