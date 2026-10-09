package de.ipnats.hardwrought.core.networking;

import de.ipnats.hardwrought.Hardwrought;
import de.ipnats.hardwrought.enchanting.RuneWork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The messages of a sitting at the enchanting table: the server says which runes lie ready and which
 * the piece already carries, and the client sends back what was written and what was wiped off.
 *
 * <p>As at the anvil, the one thing only the client has is the shape of the piece, because it is read
 * from the item's texture; everything else — which runes were ready, what they cost, what they give —
 * the server works out again for itself.
 */
public final class RunePayloads {
    /** More runes than any table offers, and more than any piece takes. */
    public static final int MAX_RUNES = 64, MAX_PLACED = 16;

    private RunePayloads() { }

    private static void write(RegistryFriendlyByteBuf buffer, List<RuneWork.Placement> runes) {
        buffer.writeVarInt(runes.size());
        for (RuneWork.Placement rune : runes) {
            buffer.writeIdentifier(rune.rune());
            buffer.writeByte(rune.x());
            buffer.writeByte(rune.y());
            buffer.writeByte(rune.turns());
            buffer.writeByte(rune.ink().ordinal());
        }
    }

    private static List<RuneWork.Placement> read(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(buffer.readVarInt(), MAX_PLACED);
        List<RuneWork.Placement> runes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            runes.add(new RuneWork.Placement(buffer.readIdentifier(), buffer.readByte(), buffer.readByte(), buffer.readByte() & 3,
                    RuneWork.Ink.values()[Math.clamp(buffer.readByte(), 0, RuneWork.Ink.values().length - 1)]));
        }
        return runes;
    }

    /**
     * Server to client: the piece in the main hand lies on this table.
     *
     * @param shelves  bookshelves round the table
     * @param level    the writer's level on sitting down
     * @param capacity how many runes the piece takes, before what its inks add
     * @param part     whether it is a part, on which a rune works itself in fully
     * @param runes    the runes that lie ready
     * @param locked   how many more would suit the piece, with more shelves or levels
     * @param carried  the runes already written on the piece, where they lie
     * @param loose    enchantments the piece carries that were never written on it as runes
     * @param coarser  how much larger than on adamant runes come out on this piece: 0, 1 or 2 squares
     */
    public record Open(BlockPos table, int shelves, int level, int capacity, boolean part, List<Identifier> runes,
                       int locked, List<RuneWork.Placement> carried, int loose, int coarser) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Hardwrought.id("runes_open_v3"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of(
                (buffer, open) -> {
                    buffer.writeBlockPos(open.table);
                    buffer.writeVarInt(open.shelves);
                    buffer.writeVarInt(open.level);
                    buffer.writeVarInt(open.capacity);
                    buffer.writeBoolean(open.part);
                    buffer.writeVarInt(open.runes.size());
                    open.runes.forEach(buffer::writeIdentifier);
                    buffer.writeVarInt(open.locked);
                    write(buffer, open.carried);
                    buffer.writeVarInt(open.loose);
                    buffer.writeVarInt(open.coarser);
                },
                buffer -> {
                    BlockPos table = buffer.readBlockPos();
                    int shelves = buffer.readVarInt(), level = buffer.readVarInt(), capacity = buffer.readVarInt();
                    boolean part = buffer.readBoolean();
                    int count = Math.min(buffer.readVarInt(), MAX_RUNES);
                    List<Identifier> runes = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) runes.add(buffer.readIdentifier());
                    int locked = buffer.readVarInt();
                    List<RuneWork.Placement> carried = read(buffer);
                    int loose = buffer.readVarInt();
                    return new Open(table, shelves, level, capacity, part, runes, locked, carried, loose, buffer.readVarInt());
                });

        public Open {
            runes = List.copyOf(runes);
            carried = List.copyOf(carried);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client to server: these runes were written on the piece, whose shape is this, and these of the
     * runes it carried — by their place in {@link Open#carried} — were wiped off first.
     */
    public record Write(BlockPos table, long[] piece, List<RuneWork.Placement> runes, List<Integer> wiped)
            implements CustomPacketPayload {
        public static final Type<Write> TYPE = new Type<>(Hardwrought.id("runes_write_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Write> CODEC = StreamCodec.of(
                (buffer, write) -> {
                    buffer.writeBlockPos(write.table);
                    for (long word : write.piece) buffer.writeLong(word);
                    write(buffer, write.runes);
                    buffer.writeVarInt(write.wiped.size());
                    write.wiped.forEach(buffer::writeVarInt);
                },
                buffer -> {
                    BlockPos table = buffer.readBlockPos();
                    long[] piece = new long[4];
                    for (int word = 0; word < 4; word++) piece[word] = buffer.readLong();
                    List<RuneWork.Placement> runes = read(buffer);
                    int count = Math.min(buffer.readVarInt(), MAX_PLACED);
                    List<Integer> wiped = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) wiped.add(buffer.readVarInt());
                    return new Write(table, piece, runes, wiped);
                });

        public Write {
            if (piece.length != 4) throw new IllegalArgumentException("A shape is four words");
            runes = List.copyOf(runes);
            wiped = List.copyOf(wiped);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
