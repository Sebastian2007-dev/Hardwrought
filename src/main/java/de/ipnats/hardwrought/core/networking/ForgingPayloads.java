package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The messages of a session at the anvil: open, begin, strike, and give up.
 *
 * <p>The server stays the judge of all of it: the piece's shape, its heat, every blow and what the
 * blow did live on the item, and the client only ever says where it struck. The one thing the client
 * supplies is the two shapes at the start, because only the client has the item textures they are
 * read from.
 */
public final class ForgingPayloads {
    /** Most things a piece could be forged into, and far more than any metal offers. */
    public static final int MAX_OPTIONS = 16;

    private ForgingPayloads() { }

    /** Server to client: the anvil at this position takes the piece in the off hand; these are its options. */
    public record Open(BlockPos anvil, List<Identifier> results) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Hardwrought.id("forging_open_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of(
                (buffer, open) -> {
                    buffer.writeBlockPos(open.anvil);
                    buffer.writeVarInt(open.results.size());
                    open.results.forEach(buffer::writeIdentifier);
                },
                buffer -> {
                    BlockPos anvil = buffer.readBlockPos();
                    int count = Math.min(buffer.readVarInt(), MAX_OPTIONS);
                    List<Identifier> results = new ArrayList<>(count);
                    for (int index = 0; index < count; index++) results.add(buffer.readIdentifier());
                    return new Open(anvil, results);
                });

        public Open {
            results = List.copyOf(results);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: start turning the piece into this result; the two shapes as the textures show them. */
    public record Begin(BlockPos anvil, Identifier result, long[] source, long[] target) implements CustomPacketPayload {
        public static final Type<Begin> TYPE = new Type<>(Hardwrought.id("forging_begin_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Begin> CODEC = StreamCodec.of(
                (buffer, begin) -> {
                    buffer.writeBlockPos(begin.anvil);
                    buffer.writeIdentifier(begin.result);
                    for (long word : begin.source) buffer.writeLong(word);
                    for (long word : begin.target) buffer.writeLong(word);
                },
                buffer -> {
                    BlockPos anvil = buffer.readBlockPos();
                    Identifier result = buffer.readIdentifier();
                    long[] source = new long[4];
                    long[] target = new long[4];
                    for (int word = 0; word < 4; word++) source[word] = buffer.readLong();
                    for (int word = 0; word < 4; word++) target[word] = buffer.readLong();
                    return new Begin(anvil, result, source, target);
                });

        public Begin {
            if (source.length != 4 || target.length != 4) throw new IllegalArgumentException("A shape is four words");
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: one blow of the hammer on this square of the piece. */
    public record Strike(BlockPos anvil, int x, int y) implements CustomPacketPayload {
        public static final Type<Strike> TYPE = new Type<>(Hardwrought.id("forging_strike_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Strike> CODEC = StreamCodec.of(
                (buffer, strike) -> {
                    buffer.writeBlockPos(strike.anvil);
                    buffer.writeByte(strike.x);
                    buffer.writeByte(strike.y);
                },
                buffer -> new Strike(buffer.readBlockPos(), buffer.readByte(), buffer.readByte()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: give up on the piece in the off hand and get back what went into it. */
    public record Cancel() implements CustomPacketPayload {
        public static final Type<Cancel> TYPE = new Type<>(Hardwrought.id("forging_cancel_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Cancel> CODEC = StreamCodec.unit(new Cancel());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
