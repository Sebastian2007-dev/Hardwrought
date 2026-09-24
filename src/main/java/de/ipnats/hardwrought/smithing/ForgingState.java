package de.ipnats.hardwrought.smithing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * A piece half-way through the anvil: what it is becoming, what shape it has now, and how the work
 * on it has gone so far.
 *
 * <p>Kept on the piece rather than at the anvil, because the work outlives the heat. Iron goes cold
 * long before a pickaxe head is finished; the player takes it back to the fire, and when it comes out
 * again it has to still be the same half-made head, with the same good and bad blows in it.
 *
 * <p>Both shapes are sixteen by sixteen squares — an item's own pixels — packed into four longs.
 *
 * @param result  the item the piece turns into when {@code current} matches {@code target}
 * @param current which squares of the grid hold metal now
 * @param target  which squares the finished item has
 * @param strikes every blow landed so far
 * @param good    blows that moved metal where it was wanted
 * @param initial how many squares were wrong when the work began, the measure of the whole job
 * @param cap     the best craftsmanship the poorest anvil used so far allows
 */
public record ForgingState(Identifier result, List<Long> current, List<Long> target,
                           int strikes, int good, int initial, float cap) {
    public static final Codec<ForgingState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("result").forGetter(ForgingState::result),
            Codec.LONG.listOf(4, 4).fieldOf("current").forGetter(ForgingState::current),
            Codec.LONG.listOf(4, 4).fieldOf("target").forGetter(ForgingState::target),
            Codec.INT.fieldOf("strikes").forGetter(ForgingState::strikes),
            Codec.INT.fieldOf("good").forGetter(ForgingState::good),
            Codec.INT.fieldOf("initial").forGetter(ForgingState::initial),
            Codec.FLOAT.fieldOf("cap").forGetter(ForgingState::cap)
    ).apply(instance, ForgingState::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgingState> STREAM_CODEC = StreamCodec.of(
            (buffer, state) -> {
                buffer.writeIdentifier(state.result);
                for (long word : state.current) buffer.writeLong(word);
                for (long word : state.target) buffer.writeLong(word);
                buffer.writeVarInt(state.strikes);
                buffer.writeVarInt(state.good);
                buffer.writeVarInt(state.initial);
                buffer.writeFloat(state.cap);
            },
            buffer -> {
                Identifier result = buffer.readIdentifier();
                Long[] current = new Long[4];
                Long[] target = new Long[4];
                for (int word = 0; word < 4; word++) current[word] = buffer.readLong();
                for (int word = 0; word < 4; word++) target[word] = buffer.readLong();
                return new ForgingState(result, List.of(current), List.of(target), buffer.readVarInt(),
                        buffer.readVarInt(), buffer.readVarInt(), buffer.readFloat());
            });

    public ForgingState {
        current = List.copyOf(current);
        target = List.copyOf(target);
        if (current.size() != 4 || target.size() != 4) throw new IllegalArgumentException("A shape is four words");
    }

    public ForgingState(Identifier result, Mask current, Mask target, float cap) {
        this(result, current.words(), target.words(), 0, 0, current.mismatch(target), cap);
    }

    public Mask currentMask() {
        return Mask.of(current);
    }

    public Mask targetMask() {
        return Mask.of(target);
    }

    /** How much of the job is done, 0 to 1. */
    public double progress() {
        if (initial <= 0) return 1.0;
        return 1.0 - currentMask().mismatch(targetMask()) / (double) initial;
    }

    public boolean finished() {
        return currentMask().mismatch(targetMask()) == 0;
    }

    /**
     * Section 38's craftsmanship: how much of the hammering was to the point, and how economically it
     * was done. Capped by the poorest anvil the piece has been worked on. Cold metal does not come into
     * it: a hammer does nothing to metal below working heat, so no blow on it is ever counted.
     *
     * <p>Every blow moves at most nine squares, so a job of {@code initial} wrong squares cannot be
     * done in fewer than a ninth as many blows; doing it in a third as many counts as full economy.
     */
    public float craftsmanship() {
        if (strikes <= 0) return cap;
        double accuracy = good / (double) strikes;
        double ideal = Math.max(1.0, initial / 3.0);
        double economy = Math.min(1.0, ideal / strikes);
        double value = 0.20 + 0.55 * accuracy + 0.25 * economy;
        return (float) Math.clamp(value, 0.0, cap);
    }

    public ForgingState struck(Mask now, boolean useful, float anvilCap) {
        return new ForgingState(result, now.words(), target, strikes + 1, good + (useful ? 1 : 0),
                initial, Math.min(cap, anvilCap));
    }
}
